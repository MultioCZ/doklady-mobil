"""Vytěžení dokladu: nejdřív bez AI, AI jen když je potřeba.

Pořadí zdrojů (od nejspolehlivějšího):
  1. ISDOC přiložený v PDF          – strojová data, 100 %
  2. QR Faktura / QR Platba         – přesná čísla (účet, částka, VS, splatnost, IČO, DPH)
  3. text PDF / OCR + pravidla      – IČO, DIČ, data, VS, číslo dokladu, celkem, rozpis DPH
  4. ARES podle IČO                 – oficiální název, adresa a DIČ dodavatele
  5. Claude (jen v režimu hybrid, když něco chybí nebo nesedí součty; nebo v režimu ai)
"""
from __future__ import annotations

import logging
from decimal import Decimal

from . import ares, rules, sources, storage
from .config import settings
from .extraction import ExtractionError, call_claude
from .models import Company, Document

log = logging.getLogger("doklady")

TEXT_KEYS = ("doc_number", "variable_symbol", "supplier_name", "supplier_ico", "supplier_dic", "supplier_address",
             "supplier_account", "customer_name", "customer_ico", "customer_dic", "issue_date", "taxable_date",
             "due_date", "currency", "total", "total_base", "total_vat", "vat_breakdown", "payment_method",
             "reverse_charge", "doc_type")


def merge(base: dict, extra: dict, override: bool = False) -> dict:
    for k, v in extra.items():
        if v in (None, "", []):
            continue
        if override or base.get(k) in (None, "", []):
            base[k] = v
    return base


def evaluate(data: dict) -> dict:
    """Kontroly úplnosti a správnosti. Vrací {název: True/False}."""
    checks = {
        "celkova_castka": data.get("total") is not None,
        "datum": bool(data.get("issue_date")),
        "dodavatel": bool(data.get("supplier_ico") and rules.ico_valid(str(data["supplier_ico"])))
                     or bool(data.get("supplier_dic")),
    }
    vb = data.get("vat_breakdown")
    if vb and data.get("total") is not None:
        s = sum(Decimal(str(r.get("base") or 0)) + Decimal(str(r.get("vat") or 0)) for r in vb)
        # tolerance na zaokrouhlení hotovostních plateb
        checks["soucet_dph"] = abs(s - Decimal(str(data["total"]))) <= Decimal("1.00")
    if data.get("doc_type") in ("faktura", "zalohova", "dobropis"):
        checks["cislo_nebo_vs"] = bool(data.get("doc_number") or data.get("variable_symbol"))
    return checks


def _enrich_ares(data: dict) -> bool:
    ico = data.get("supplier_ico")
    if not ico or not rules.ico_valid(str(ico)):
        return False
    info = ares.lookup(str(ico))
    if not info:
        return False
    if info.get("name"):
        data["supplier_name"] = info["name"]  # oficiální název má přednost před textem z dokladu
    if info.get("address"):
        data.setdefault("supplier_address", info["address"])
    if info.get("dic") and not data.get("supplier_dic"):
        data["supplier_dic"] = info["dic"]
    return True


def collect(doc: Document, company: Company | None) -> tuple[dict, dict, list[str], str]:
    """Data bez AI. Vrací (data, strojová data z ISDOC/QR, použité metody, přečtený text)."""
    own_ico = company.ico if company else None
    data: dict = {}
    methods: list[str] = []
    original = storage.path(doc.original_path).read_bytes()
    text = ""
    images = []

    if doc.original_mime == "application/pdf":
        if (x := sources.pdf_isdoc(original)) and (isdoc := sources.parse_isdoc(x)):
            merge(data, isdoc)
            methods.append("isdoc")
        text = sources.pdf_text(original)
        if len(text.strip()) > 40:
            methods.append("pdf-text")
        images = sources.pdf_images(original, dpi=200)
    else:
        img = sources.load_image(original)
        if img is not None:
            images.append(img)
        if doc.processed_path:
            pimg = sources.load_image(storage.path(doc.processed_path).read_bytes())
            if pimg is not None:
                images.insert(0, pimg)  # upravený sken má pro OCR přednost

    # QR kódy
    qr_texts = []
    for img in images[:3]:
        qr_texts += sources.qr_codes(img)
        if qr_texts:
            break
    if qr := sources.parse_qr(qr_texts):
        merge(data, qr)
        methods.append("qr")

    machine = dict(data)  # ISDOC + QR – přesná data, AI je nesmí přepsat

    def score(t: str) -> tuple[int, dict]:
        r = merge(dict(machine), rules.extract_from_text(t, own_ico)) if t.strip() else dict(machine)
        return sum(evaluate(r).values()), r

    # text: z PDF → z telefonu → Tesseract (když telefonní text nestačí, zkusí se i server a vezme se lepší)
    best_text, best_method = (text, None) if len(text.strip()) > 40 else ("", None)
    if not best_text and doc.ocr_text and len(doc.ocr_text.strip()) > 20:
        best_text, best_method = doc.ocr_text, "ocr-telefon"
    best_score, best_data = score(best_text)
    needs_more = best_score < len(evaluate(best_data))
    if len(text.strip()) <= 40 and needs_more and images and sources.ocr_available():
        server_text = "\n".join(sources.ocr(img) for img in images[:2])
        s2, d2 = score(server_text)
        if s2 > best_score or not best_text.strip():
            best_text, best_method, best_score, best_data = server_text, "ocr-server", s2, d2
    text = best_text
    if best_method and text.strip():
        methods.append(best_method)
    data = best_data

    if company and company.ico and not data.get("customer_ico"):
        data["customer_ico"] = company.ico
    if _enrich_ares(data):
        methods.append("ares")
    return data, machine, methods, text


def apply(doc: Document, data: dict) -> None:
    from .extraction import apply_to_document
    apply_to_document(doc, {k: data.get(k) for k in TEXT_KEYS if k in data} | {"confidence": data.get("confidence")})


def extract_document(doc: Document, company: Company | None) -> None:
    data, machine, methods, text = collect(doc, company)
    checks = evaluate(data)
    complete = all(checks.values())
    mode = settings.EXTRACTION_MODE
    ai_error = None

    want_ai = mode == "ai" or (mode == "hybrid" and not complete)
    if want_ai and settings.ANTHROPIC_API_KEY:
        if doc.original_mime == "application/pdf" or not doc.processed_path:
            src, mime = storage.path(doc.original_path), doc.original_mime
        else:
            src, mime = storage.path(doc.processed_path), "image/jpeg"
        hint = {k: data[k] for k in ("supplier_ico", "total", "variable_symbol", "issue_date") if k in data}
        try:
            ai = call_claude(src, mime, hint=str(hint) if hint else None)
            merged = merge(dict(ai), data)  # AI má přednost před pravidly, pravidla doplní, co AI vynechala
            merge(merged, machine, override=True)
            data = merged
            methods.append("ai")
            if _enrich_ares(data) and "ares" not in methods:
                methods.append("ares")
            checks = evaluate(data)
            complete = all(checks.values())
        except ExtractionError as exc:
            ai_error = str(exc)
        except Exception as exc:
            log.exception("AI vytěžení selhalo")
            ai_error = f"AI nedostupná: {exc}"

    data["confidence"] = round(sum(checks.values()) / max(len(checks), 1), 2)
    apply(doc, data)
    doc.extraction_method = "+".join(methods) or None
    doc.ocr_text = text[:20000] if text else doc.ocr_text
    doc.raw_extraction = {"methods": methods, "checks": checks, "complete": complete, "ai_error": ai_error, "data": data}

    if not methods or (not complete and not any(checks.values())):
        doc.status = "error"
        doc.error = ai_error or "Doklad se nepodařilo přečíst – vyplňte údaje ručně nebo vyfoťte znovu."
    else:
        doc.status = "extracted"
        missing = [k for k, ok in checks.items() if not ok]
        doc.error = None
        if missing:
            labels = {"celkova_castka": "celková částka", "datum": "datum", "dodavatel": "dodavatel (IČO/DIČ)",
                      "soucet_dph": "součet DPH nesedí na celkovou částku", "cislo_nebo_vs": "číslo dokladu / VS"}
            doc.error = "Zkontrolujte: " + ", ".join(labels[m] for m in missing)
