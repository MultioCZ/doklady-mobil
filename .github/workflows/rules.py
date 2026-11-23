"""Vytěžení náležitostí z textu dokladu pomocí pravidel – bez AI.

Vstupem je text (z PDF nebo z OCR), výstupem slovník se stejnými klíči, jaké vrací AI
(doc_type, doc_number, variable_symbol, supplier_ico, … total, vat_breakdown).
Položky dokladu se záměrně nevytěžují.
"""
from __future__ import annotations

import re
import unicodedata
from datetime import date
from decimal import Decimal, InvalidOperation

VAT_RATES = (21, 15, 12, 10, 0)  # 15 a 10 % pro starší doklady

# ----------------------------------------------------------------- pomocné funkce


def fold(s: str) -> str:
    """Malá písmena bez diakritiky – OCR diakritiku často ztrácí."""
    s = unicodedata.normalize("NFKD", s)
    return "".join(c for c in s if not unicodedata.combining(c)).lower()


AMOUNT_RE = re.compile(r"(?<![\d.,])-?\d{1,3}(?:[  .]\d{3})+[.,]\d{2}(?!\d)|(?<![\d.,])-?\d+[.,]\d{2}(?!\d)")


def parse_amount(s: str) -> Decimal | None:
    s = s.replace(" ", " ").strip()
    neg = s.startswith("-")
    s = s.lstrip("-").replace(" ", "")
    if "," in s and "." in s:  # 1.284,90 nebo 1,284.90
        if s.rfind(",") > s.rfind("."):
            s = s.replace(".", "").replace(",", ".")
        else:
            s = s.replace(",", "")
    else:
        s = s.replace(",", ".")
        if s.count(".") > 1:  # 1.284.90 → tisíce + desetiny
            head, _, tail = s.rpartition(".")
            s = head.replace(".", "") + "." + tail
    try:
        v = Decimal(s).quantize(Decimal("0.01"))
    except InvalidOperation:
        return None
    return -v if neg else v


def amounts(line: str) -> list[Decimal]:
    out = []
    for m in AMOUNT_RE.finditer(line):
        v = parse_amount(m.group())
        if v is not None:
            out.append(v)
    return out


DATE_RE = re.compile(r"(?<!\d)(\d{1,2})\s?[.\-/]\s?(\d{1,2})\s?[.\-/]\s?(\d{4}|\d{2})(?!\d)|(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)")


def dates_in(line: str) -> list[date]:
    out = []
    for m in DATE_RE.finditer(line):
        try:
            if m.group(4):
                d = date(int(m.group(4)), int(m.group(5)), int(m.group(6)))
            else:
                y = int(m.group(3))
                y = y + 2000 if y < 100 else y
                d = date(y, int(m.group(2)), int(m.group(1)))
        except ValueError:
            continue
        if 2000 <= d.year <= 2100:
            out.append(d)
    return out


def ico_valid(ico: str) -> bool:
    """Kontrolní číslice IČO (modulo 11)."""
    if not re.fullmatch(r"\d{8}", ico):
        return False
    s = sum(int(ico[i]) * (8 - i) for i in range(7))
    c = (11 - s % 11) % 10
    return c == int(ico[7])


# ----------------------------------------------------------------- jednotlivá pole

ICO_RE = re.compile(r"\bi[cč]o?\s*[:.]?\s*(\d{2}\s?\d{3}\s?\d{3})\b")
DIC_RE = re.compile(r"\b(?:di[cč]|vat(?:\s*id)?|ic\s*dph)\s*[:.]?\s*([a-z]{2}\s?\d{8,10})\b")
DIC_BARE_RE = re.compile(r"\b(cz\d{8,10})\b")


def find_icos(text_f: str) -> list[tuple[int, str]]:
    found = []
    for m in ICO_RE.finditer(text_f):
        ico = m.group(1).replace(" ", "")
        if ico_valid(ico):
            found.append((m.start(), ico))
    # u právnických osob je DIČ = CZ + IČO
    for m in list(DIC_RE.finditer(text_f)) + list(DIC_BARE_RE.finditer(text_f)):
        d = m.group(1).replace(" ", "").upper()
        if d.startswith("CZ") and len(d) == 10 and ico_valid(d[2:]):
            found.append((m.start(), d[2:]))
    return sorted(found)


def find_dics(text_f: str) -> list[tuple[int, str]]:
    found = {}
    for m in list(DIC_RE.finditer(text_f)) + list(DIC_BARE_RE.finditer(text_f)):
        found.setdefault(m.group(1).replace(" ", "").upper(), m.start())
    return sorted((pos, d) for d, pos in found.items())


DATE_LABELS = {
    "taxable_date": ["duzp", "datum uskutecneni", "uskutecneni zdanitelneho", "zdanitelneho plneni", "datum zdan"],
    "due_date": ["datum splatnosti", "splatnost", "splatno", "datum splat"],
    "issue_date": ["datum vystaveni", "vystaveno", "datum vyst", "dne:"],
}


def labeled_value(lines_f: list[str], lines: list[str], labels: list[str], extract, look_ahead: int = 2):
    """Najde řádek s návěštím a hodnotu na něm (za návěštím) nebo na následujících řádcích."""
    for i, lf in enumerate(lines_f):
        for lab in labels:
            pos = lf.find(lab)
            if pos < 0:
                continue
            vals = extract(lines[i][pos:])
            if vals:
                return vals[0]
            for j in range(1, look_ahead + 1):
                if i + j < len(lines):
                    vals = extract(lines[i + j])
                    if vals:
                        return vals[0]
    return None


TOTAL_LABELS = [
    "celkem k uhrade", "castka k uhrade", "k uhrade", "zbyva uhradit", "celkem k zaplaceni",
    "k zaplaceni", "celkem s dph", "celkem vc. dph", "celkem vcetne dph",
]
TOTAL_WEAK = ["celkem", "suma", "total", "platba", "zaplaceno"]
NOT_TOTAL = ["bez dph", "zaklad", "zaokrouhl", "zalohy", "zaplaceno zalohou", "dph celkem", "celkem dph"]


def find_total(lines_f: list[str], lines: list[str]) -> Decimal | None:
    for lab in TOTAL_LABELS:
        for i, lf in enumerate(lines_f):
            if lab in lf and not any(n in lf for n in NOT_TOTAL[:3]):
                vals = amounts(lines[i][lf.find(lab):]) or (amounts(lines[i + 1]) if i + 1 < len(lines) else [])
                if vals:
                    return vals[-1] if lab in lf[-len(lab) - 25:] or len(vals) == 1 else max(vals)
    cands = []
    for i, lf in enumerate(lines_f):
        if any(w in lf for w in TOTAL_WEAK) and not any(n in lf for n in NOT_TOTAL):
            vals = amounts(lines[i]) or (amounts(lines[i + 1]) if i + 1 < len(lines) else [])
            cands.extend(v for v in vals if v > 0)
    return max(cands) if cands else None


RATE_RE = re.compile(r"(?<![\d,.])(21|15|12|10|0)(?:[,.]0{1,2})?\s?%")


def find_vat(lines: list[str]) -> list[dict]:
    """Řádky rekapitulace DPH: hledá dvojici čísel, kde DPH = základ × sazba."""
    rows: dict[int, dict] = {}
    for line in lines:
        m = RATE_RE.search(line)
        if not m:
            continue
        rate = int(m.group(1))
        if rate in rows:
            continue
        vals = [v for v in amounts(line[:m.start()] + " " + line[m.end():]) if v >= 0]
        best = None
        for b in vals:
            for v in vals:
                if b == v or b == 0:
                    continue
                expected = b * rate / 100
                if abs(expected - v) <= max(Decimal("0.05"), v * Decimal("0.005")):
                    best = (b, v)
                    break
            if best:
                break
        if best:
            rows[rate] = {"rate": rate, "base": float(best[0]), "vat": float(best[1])}
        elif rate == 0 and vals:
            rows[0] = {"rate": 0, "base": float(max(vals)), "vat": 0.0}
    return sorted(rows.values(), key=lambda r: -r["rate"])


VS_RE = re.compile(r"(?:variabilni\s*symbol|var\.?\s*symb(?:ol)?|\bv\.\s?s\.|\bvs)\s*[:.]?\s*(\d{1,10})\b")
DOCNUM_PATTERNS = [
    re.compile(r"(?:cislo\s+faktury|cislo\s+dokladu|c\.\s*dokladu|cislo\s+uctenky|uctenka\s+cislo|c\.\s*uctenky)\s*[:.]?\s*([a-z0-9][a-z0-9/\-]{1,24})"),
    re.compile(r"(?:faktura|danovy\s+doklad|dobropis|zalohova\s+faktura|doklad)[^\n]{0,25}?(?:\bc\.|\bcislo|\bno\.?|\bnr\.?)\s*[:.]?\s*([a-z0-9][a-z0-9/\-]{1,24})"),
    re.compile(r"\buct\s*:\s*(\d{1,12})"),  # Kaufland
    re.compile(r"(?:pokladni\s+doklad|bon|paragon)\s*(?:c\.|cislo)?\s*[:.]?\s*(\d{2,12})"),
]
ACCOUNT_RE = re.compile(r"(?<![\d/-])((?:\d{1,6}-)?\d{2,10}\s?/\s?\d{4})(?!\d)")
IBAN_RE = re.compile(r"\b([A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){3,7}(?:\s?[A-Z0-9]{1,4})?)\b")


def find_doc_type(tf: str) -> str:
    if "opravny danovy doklad" in tf or "dobropis" in tf:
        return "dobropis"
    if "zalohova faktura" in tf or "zalohovy list" in tf or "vyzva k platbe" in tf:
        return "zalohova"
    if "faktura" in tf or "danovy doklad" in tf:
        return "faktura"
    if any(k in tf for k in ("uctenka", "pokladni doklad", "fik:", "bkp:", "uct:", "eet", "pokladna", "prodejna")):
        return "uctenka"
    return "jine"


def find_payment(tf: str) -> str | None:
    if re.search(r"hotov(e|ost)", tf):
        return "hotove"
    if re.search(r"kart(a|ou)|visa|mastercard|maestro|contactless|bezkontakt", tf):
        return "karta"
    if re.search(r"prevod(em)?|bankovni\s+prevod|prikaz(em)?", tf):
        return "prevod"
    if "dobirk" in tf:
        return "dobirka"
    return None


def find_supplier_name(lines: list[str], lines_f: list[str]) -> str | None:
    for i, lf in enumerate(lines_f):
        if re.match(r"^\s*(dodavatel|prodavajici|vystavil)\b", lf):
            rest = lines[i].split(":", 1)[1].strip() if ":" in lines[i] else ""
            if len(rest) > 2:
                return rest
            for j in range(i + 1, min(i + 3, len(lines))):
                cand = lines[j].strip()
                if len(cand) > 2 and not re.match(r"^(i[cč]|di[cč]|odberatel)", fold(cand)):
                    return cand
    return None


# ----------------------------------------------------------------- hlavní funkce

def extract_from_text(text: str, own_ico: str | None = None) -> dict:
    """own_ico = IČO firmy, které doklad patří (odběratel) – dodavatel je ten druhý."""
    text = text.replace(" ", " ").replace("\r", "")
    lines = [l for l in (x.strip() for x in text.split("\n")) if l]
    lines_f = [fold(l) for l in lines]
    tf = "\n".join(lines_f)
    out: dict = {}

    out["doc_type"] = find_doc_type(tf)

    icos = [ico for _, ico in find_icos(tf)]
    others = [i for i in dict.fromkeys(icos) if i != own_ico]
    if others:
        out["supplier_ico"] = others[0]
    if own_ico and own_ico in icos:
        out["customer_ico"] = own_ico
    dics = [d for _, d in find_dics(tf)]
    sup_dic = next((d for d in dics if d[2:] == out.get("supplier_ico")), None)
    if not sup_dic:
        sup_dic = next((d for d in dics if not own_ico or d[2:] != own_ico), None)
    if sup_dic:
        out["supplier_dic"] = sup_dic

    for key, labels in DATE_LABELS.items():
        d = labeled_value(lines_f, lines, labels, dates_in)
        if d:
            out[key] = d.isoformat()
    if "issue_date" not in out:
        all_dates = [d for l in lines for d in dates_in(l)]
        if all_dates:  # účtenka: první datum na dokladu
            out["issue_date"] = all_dates[0].isoformat()
    if out["doc_type"] == "uctenka" and "issue_date" in out:
        out.setdefault("taxable_date", out["issue_date"])

    if m := VS_RE.search(tf):
        out["variable_symbol"] = m.group(1)
    for pat in DOCNUM_PATTERNS:
        if m := pat.search(tf):
            num = m.group(1).upper()
            if not re.fullmatch(r"[A-Z]+", num):  # ne samotné slovo
                out["doc_number"] = num
                break
    if "variable_symbol" not in out and out.get("doc_type") == "faktura" and out.get("doc_number"):
        digits = re.sub(r"\D", "", out["doc_number"])
        if digits:
            out["variable_symbol"] = digits[-10:]

    if total := find_total(lines_f, lines):
        out["total"] = float(total)
    vat = find_vat(lines)
    if vat:
        out["vat_breakdown"] = vat
        out["total_base"] = round(sum(r["base"] for r in vat), 2)
        out["total_vat"] = round(sum(r["vat"] for r in vat), 2)

    if m := IBAN_RE.search(text.upper()):
        if m.group(1).replace(" ", "")[:2] in ("CZ", "SK"):
            out["supplier_account"] = m.group(1).replace(" ", "")
    for i, lf in enumerate(lines_f):
        if re.search(r"ucet|bankovni spojeni|account", lf):
            seg = lines[i] + " " + (lines[i + 1] if i + 1 < len(lines) else "")
            if m := ACCOUNT_RE.search(seg):
                out["supplier_account"] = m.group(1).replace(" ", "")
                break

    out["currency"] = "EUR" if re.search(r"\beur\b|€", tf) and not re.search(r"\bkc\b|czk", tf) else "CZK"
    if p := find_payment(tf):
        out["payment_method"] = p
    if re.search(r"dan\s+odvede\s+zakaznik|prenesen\w*\s+danov\w*\s+povinnost|\b92a\b|reverse\s+charge", tf):
        out["reverse_charge"] = True
    if name := find_supplier_name(lines, lines_f):
        out["supplier_name"] = name
    return out
