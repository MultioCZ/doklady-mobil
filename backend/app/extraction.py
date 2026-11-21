"""Vytěžení náležitostí dokladu přes Claude (vision). Výstup je vynucen přes tool schema."""
from __future__ import annotations

import base64
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path

from .config import settings

PROMPT = """Jsi asistent české účetní kanceláře. Z přiloženého dokladu vytěž náležitosti
a zavolej nástroj `ulozit_doklad`. Pravidla:
- Data vracej ve formátu RRRR-MM-DD. DUZP = datum uskutečnění zdanitelného plnění.
- Částky jako čísla s desetinnou tečkou, bez měny a mezer.
- IČO 8 číslic (doplň nuly zleva), DIČ včetně prefixu země (CZ12345678).
- Rozpis DPH po sazbách (21, 12, 0). U účtenek bez rozpisu odhadni z celkové částky jen pokud je sazba uvedena.
- Dodavatel = kdo doklad vystavil (prodávající). Odběratel = kupující.
- Účtenka z pokladny (Kaufland "Účt:", Albert/Lidl "Účtenka číslo") → doc_type "uctenka", číslo dokladu je toto číslo.
- Variabilní symbol, pokud chybí a jde o fakturu, bývá shodný s číslem faktury (jen číslice).
- Přenesená daňová povinnost (§92a, "daň odvede zákazník") → reverse_charge true.
- Položky dokladu nevytěžuj.
- Co na dokladu není, vynech (nevymýšlej). confidence 0–1 = jak čitelný a jistý doklad je."""

TOOL = {
    "name": "ulozit_doklad",
    "description": "Uloží vytěžené náležitosti dokladu.",
    "input_schema": {
        "type": "object",
        "properties": {
            "doc_type": {"type": "string", "enum": ["faktura", "uctenka", "zalohova", "dobropis", "jine"]},
            "doc_number": {"type": "string"},
            "variable_symbol": {"type": "string"},
            "supplier_name": {"type": "string"},
            "supplier_ico": {"type": "string"},
            "supplier_dic": {"type": "string"},
            "supplier_address": {"type": "string"},
            "supplier_account": {"type": "string", "description": "číslo účtu nebo IBAN dodavatele"},
            "customer_name": {"type": "string"},
            "customer_ico": {"type": "string"},
            "customer_dic": {"type": "string"},
            "issue_date": {"type": "string"},
            "taxable_date": {"type": "string"},
            "due_date": {"type": "string"},
            "currency": {"type": "string", "description": "ISO kód, např. CZK"},
            "total": {"type": "number", "description": "celkem k úhradě včetně DPH"},
            "total_base": {"type": "number"},
            "total_vat": {"type": "number"},
            "vat_breakdown": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {"rate": {"type": "number"}, "base": {"type": "number"}, "vat": {"type": "number"}},
                    "required": ["rate"],
                },
            },
            "payment_method": {"type": "string", "enum": ["hotove", "karta", "prevod", "dobirka", "jine"]},
            "reverse_charge": {"type": "boolean"},
            "confidence": {"type": "number"},
            "note": {"type": "string", "description": "upozornění pro účetní (nečitelné, chybí náležitosti…)"},
        },
        "required": ["doc_type", "confidence"],
    },
}

MEDIA = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp", ".gif": "image/gif"}


class ExtractionError(Exception):
    pass


def _content_blocks(path: Path, mime: str) -> list[dict]:
    """PDF se posílá jako obrázky stránek (max. 3) – funguje s každým modelem, který čte obrázky,
    i s nejlevnějším Haiku, a cena je předvídatelná."""
    if mime == "application/pdf":
        import cv2
        from .sources import pdf_images
        blocks = []
        for img in pdf_images(path.read_bytes(), dpi=150, max_pages=3):
            ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 85])
            if ok:
                blocks.append({"type": "image", "source": {"type": "base64", "media_type": "image/jpeg",
                                                            "data": base64.standard_b64encode(buf.tobytes()).decode()}})
        if blocks:
            return blocks
        data = base64.standard_b64encode(path.read_bytes()).decode()
        return [{"type": "document", "source": {"type": "base64", "media_type": "application/pdf", "data": data}}]
    data = base64.standard_b64encode(path.read_bytes()).decode()
    media = mime if mime.startswith("image/") else MEDIA.get(path.suffix.lower(), "image/jpeg")
    return [{"type": "image", "source": {"type": "base64", "media_type": media, "data": data}}]


def call_claude(path: Path, mime: str, hint: str | None = None) -> dict:
    if not settings.ANTHROPIC_API_KEY:
        raise ExtractionError("Chybí ANTHROPIC_API_KEY – vytěžování není nastavené.")
    import anthropic

    client = anthropic.Anthropic(api_key=settings.ANTHROPIC_API_KEY)
    msg = client.messages.create(
        model=settings.CLAUDE_MODEL,
        max_tokens=4096,
        tools=[TOOL],
        tool_choice={"type": "tool", "name": TOOL["name"]},
        messages=[{"role": "user", "content": [
            *_content_blocks(path, mime),
            {"type": "text", "text": PROMPT + (f"\n\nJiž ověřeno bez AI (použij, pokud odpovídá dokladu): {hint}" if hint else "")},
        ]}],
    )
    for block in msg.content:
        if block.type == "tool_use":
            return dict(block.input)
    raise ExtractionError("Model nevrátil strukturovaná data.")


# --- převod výsledku na sloupce modelu ---

def _date(v) -> date | None:
    if not v:
        return None
    try:
        return date.fromisoformat(str(v)[:10])
    except ValueError:
        return None


def _money(v) -> Decimal | None:
    if v is None or v == "":
        return None
    try:
        return Decimal(str(v).replace(" ", "").replace(",", ".")).quantize(Decimal("0.01"))
    except (InvalidOperation, ValueError):
        return None


def _ico(v) -> str | None:
    if not v:
        return None
    digits = "".join(ch for ch in str(v) if ch.isdigit())
    return digits.zfill(8) if digits else None


def apply_to_document(doc, data: dict) -> None:
    """Zapíše vytěžená data do dokladu (prázdné hodnoty přeskočí)."""
    text = ["doc_type", "doc_number", "variable_symbol", "supplier_name", "supplier_dic", "supplier_address",
            "supplier_account", "customer_name", "customer_dic", "payment_method", "note"]
    for key in text:
        if data.get(key) not in (None, ""):
            setattr(doc, key, str(data[key]).strip())
    doc.supplier_ico = _ico(data.get("supplier_ico")) or doc.supplier_ico
    doc.customer_ico = _ico(data.get("customer_ico")) or doc.customer_ico
    for key in ("issue_date", "taxable_date", "due_date"):
        if (d := _date(data.get(key))) is not None:
            setattr(doc, key, d)
    for key in ("total", "total_base", "total_vat"):
        if (m := _money(data.get(key))) is not None:
            setattr(doc, key, m)
    if data.get("currency"):
        doc.currency = str(data["currency"]).upper()[:3]
    if data.get("vat_breakdown"):
        doc.vat_breakdown = data["vat_breakdown"]
        if doc.total_base is None:
            doc.total_base = _money(sum(float(r.get("base") or 0) for r in data["vat_breakdown"]))
        if doc.total_vat is None:
            doc.total_vat = _money(sum(float(r.get("vat") or 0) for r in data["vat_breakdown"]))
    if "reverse_charge" in data:
        doc.reverse_charge = bool(data["reverse_charge"])
    if data.get("confidence") is not None:
        doc.confidence = float(data["confidence"])
