"""Zdroje dat z dokladu bez AI: textová vrstva PDF, OCR (Tesseract), QR kódy (QR Platba /
QR Faktura) a ISDOC přiložený v PDF."""
from __future__ import annotations

import io
import logging
import re
import shutil
import xml.etree.ElementTree as ET
from urllib.parse import unquote

import cv2
import numpy as np
from PIL import Image, ImageOps

log = logging.getLogger("doklady")

# ----------------------------------------------------------------- PDF


def pdf_text(data: bytes) -> str:
    try:
        from pypdf import PdfReader
        reader = PdfReader(io.BytesIO(data))
        return "\n".join((p.extract_text() or "") for p in reader.pages[:10])
    except Exception as exc:
        log.info("PDF text nelze přečíst: %s", exc)
        return ""


def pdf_images(data: bytes, dpi: int = 200, max_pages: int = 5) -> list[np.ndarray]:
    """Vykreslí stránky PDF jako obrázky (pro QR kódy a OCR naskenovaných PDF)."""
    try:
        import pypdfium2 as pdfium
    except ImportError:
        return []
    out = []
    pdf = pdfium.PdfDocument(data)
    for i in range(min(len(pdf), max_pages)):
        pil = pdf[i].render(scale=dpi / 72).to_pil().convert("RGB")
        out.append(cv2.cvtColor(np.array(pil), cv2.COLOR_RGB2BGR))
    return out


def pdf_isdoc(data: bytes) -> bytes | None:
    """ISDOC vložený do PDF jako příloha (Pohoda, iDoklad, Fakturoid…)."""
    try:
        from pypdf import PdfReader
        reader = PdfReader(io.BytesIO(data))
        for name, contents in (reader.attachments or {}).items():
            if name.lower().endswith((".isdoc", ".xml")):
                for c in contents:
                    if b"isdoc" in c[:2000].lower():
                        return c
    except Exception as exc:
        log.info("Přílohy PDF nelze přečíst: %s", exc)
    return None


# ----------------------------------------------------------------- obrázky


def load_image(data: bytes) -> np.ndarray | None:
    try:
        img = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
        return cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    except Exception:
        return None


def ocr_available() -> bool:
    return shutil.which("tesseract") is not None


def ocr(img: np.ndarray) -> str:
    """Tesseract OCR. Čeština (ces) se použije, pokud je nainstalovaná."""
    if not ocr_available():
        return ""
    import pytesseract
    langs = set(pytesseract.get_languages(config=""))
    lang = "+".join(l for l in ("ces", "eng") if l in langs) or "eng"
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY) if img.ndim == 3 else img
    h, w = gray.shape
    if max(h, w) < 1800:  # malé fotky zvětšit – Tesseract chce cca 300 dpi
        f = 1800 / max(h, w)
        gray = cv2.resize(gray, None, fx=f, fy=f, interpolation=cv2.INTER_CUBIC)
    try:
        return pytesseract.image_to_string(gray, lang=lang, config="--psm 4")
    except Exception as exc:
        log.warning("OCR selhalo: %s", exc)
        return ""


def qr_codes(img: np.ndarray) -> list[str]:
    det = cv2.QRCodeDetector()
    found: list[str] = []
    variants = [img]
    h, w = img.shape[:2]
    if max(h, w) < 2000:
        variants.append(cv2.resize(img, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC))
    for v in variants:
        try:
            ok, texts, _, _ = det.detectAndDecodeMulti(v)
            if ok:
                found += [t for t in texts if t]
        except cv2.error:
            pass
        if not found:
            try:
                t, _, _ = det.detectAndDecode(v)
                if t:
                    found.append(t)
            except cv2.error:
                pass
        if found:
            break
    return list(dict.fromkeys(found))


# ----------------------------------------------------------------- QR Platba / QR Faktura


def _spayd_fields(s: str) -> dict[str, str]:
    parts = s.split("*")
    out = {}
    for p in parts[2:]:
        if ":" in p:
            k, _, v = p.partition(":")
            out[k.upper()] = unquote(v)
    return out


def _yyyymmdd(v: str | None) -> str | None:
    if v and re.fullmatch(r"\d{8}", v):
        return f"{v[:4]}-{v[4:6]}-{v[6:]}"
    return None


def parse_qr(texts: list[str]) -> dict:
    """SPD (QR Platba) a SID (QR Faktura – i jako X-INV uvnitř QR Platby)."""
    out: dict = {}
    for t in texts:
        t = t.strip()
        if t.startswith("SPD*"):
            f = _spayd_fields(t)
            if f.get("ACC"):
                out.setdefault("supplier_account", f["ACC"].split("+")[0])
            if f.get("AM"):
                out.setdefault("total", float(f["AM"]))
            if f.get("CC"):
                out.setdefault("currency", f["CC"])
            if f.get("X-VS"):
                out.setdefault("variable_symbol", f["X-VS"])
            if d := _yyyymmdd(f.get("DT")):
                out.setdefault("due_date", d)
            out.setdefault("payment_method", "prevod")
            if f.get("X-INV"):
                texts.append(f["X-INV"])
        elif t.startswith("SID*"):
            f = _spayd_fields(t)
            mapping = {"ID": "doc_number", "VS": "variable_symbol", "INI": "supplier_ico", "VII": "supplier_dic",
                       "INR": "customer_ico", "VIR": "customer_dic", "CC": "currency"}
            for k, key in mapping.items():
                if f.get(k):
                    out[key] = f[k]
            if f.get("AM"):
                out["total"] = float(f["AM"])
            for k, key in (("DD", "issue_date"), ("DUZP", "taxable_date"), ("DT", "due_date")):
                if d := _yyyymmdd(f.get(k)):
                    out[key] = d
            if f.get("TP") == "1":
                out["doc_type"] = "zalohova"
            elif f.get("TP") == "2":
                out["doc_type"] = "dobropis"
            else:
                out.setdefault("doc_type", "faktura")
            # TB0/T0 = základní sazba, TB1/T1 = první snížená, TB2/T2 = druhá snížená, NTB = osvobozeno
            vat = []
            for idx, rate in (("0", 21), ("1", 12), ("2", 10)):
                if f.get(f"TB{idx}"):
                    vat.append({"rate": rate, "base": float(f[f"TB{idx}"]), "vat": float(f.get(f"T{idx}") or 0)})
            if f.get("NTB"):
                vat.append({"rate": 0, "base": float(f["NTB"]), "vat": 0.0})
            if vat:
                out["vat_breakdown"] = vat
                out["total_base"] = round(sum(r["base"] for r in vat), 2)
                out["total_vat"] = round(sum(r["vat"] for r in vat), 2)
    if out.get("supplier_ico"):
        out["supplier_ico"] = out["supplier_ico"].zfill(8)
    return out


# ----------------------------------------------------------------- ISDOC

NS = {"i": "http://isdoc.cz/namespace/2013"}


def parse_isdoc(xml: bytes) -> dict:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return {}
    ns_uri = root.tag[1:].split("}")[0] if root.tag.startswith("{") else NS["i"]
    ns = {"i": ns_uri}

    def tx(path: str, base=root) -> str | None:
        el = base.find(path, ns)
        return el.text.strip() if el is not None and el.text else None

    out: dict = {"doc_type": {"1": "faktura", "2": "dobropis", "4": "zalohova"}.get(tx("i:DocumentType") or "1", "faktura")}
    out["doc_number"] = tx("i:ID")
    out["issue_date"] = tx("i:IssueDate")
    out["taxable_date"] = tx("i:TaxPointDate")
    out["currency"] = tx("i:LocalCurrencyCode")
    sup = root.find("i:AccountingSupplierParty/i:Party", ns)
    if sup is not None:
        out["supplier_ico"] = tx("i:PartyIdentification/i:ID", sup)
        out["supplier_dic"] = tx("i:PartyTaxScheme/i:CompanyID", sup)
        out["supplier_name"] = tx("i:PartyName/i:Name", sup)
        street = " ".join(filter(None, [tx("i:PostalAddress/i:StreetName", sup), tx("i:PostalAddress/i:BuildingNumber", sup)]))
        city = " ".join(filter(None, [tx("i:PostalAddress/i:PostalZone", sup), tx("i:PostalAddress/i:CityName", sup)]))
        out["supplier_address"] = ", ".join(filter(None, [street, city])) or None
    cus = root.find("i:AccountingCustomerParty/i:Party", ns)
    if cus is not None:
        out["customer_ico"] = tx("i:PartyIdentification/i:ID", cus)
        out["customer_dic"] = tx("i:PartyTaxScheme/i:CompanyID", cus)
        out["customer_name"] = tx("i:PartyName/i:Name", cus)
    total = tx("i:LegalMonetaryTotal/i:PayableAmount") or tx("i:LegalMonetaryTotal/i:TaxInclusiveAmount")
    if total:
        out["total"] = float(total)
    vat = []
    for st in root.findall("i:TaxTotal/i:TaxSubTotal", ns):
        rate = tx("i:TaxCategory/i:Percent", st)
        base = tx("i:TaxableAmount", st)
        if rate is not None and base is not None:
            vat.append({"rate": float(rate), "base": float(base), "vat": float(tx("i:TaxAmount", st) or 0)})
    if vat:
        out["vat_breakdown"] = vat
        out["total_base"] = round(sum(r["base"] for r in vat), 2)
        out["total_vat"] = round(sum(r["vat"] for r in vat), 2)
    pm = root.find("i:PaymentMeans/i:Payment", ns)
    if pm is not None:
        out["due_date"] = tx("i:Details/i:PaymentDueDate", pm)
        out["variable_symbol"] = tx("i:Details/i:VariableSymbol", pm)
        acc, bank = tx("i:Details/i:ID", pm), tx("i:Details/i:BankCode", pm)
        out["supplier_account"] = f"{acc}/{bank}" if acc and bank else (tx("i:Details/i:IBAN", pm) or acc)
        out["payment_method"] = {"10": "hotove", "42": "prevod", "48": "karta"}.get(tx("i:PaymentMeansCode", pm) or "", None)
    return {k: v for k, v in out.items() if v not in (None, "")}
