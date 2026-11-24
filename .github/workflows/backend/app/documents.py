"""Zpracování dokladu: uložení → (úprava obrazu) → vytěžení."""
from __future__ import annotations

import logging

from fastapi import HTTPException
from sqlalchemy.orm import Session

from . import imaging, storage
from .config import settings
from .db import SessionLocal
from .pipeline import extract_document
from .models import Company, Document, User

log = logging.getLogger("doklady")


def create_document(
    db: Session,
    company: Company,
    user: User | None,
    original: bytes,
    original_name: str | None,
    original_mime: str | None,
    processed: bytes | None = None,
    enhance: bool = True,
    mode: str = "gray",
    source: str = "mobile",
    ocr_text: str | None = None,
) -> Document:
    if not original:
        raise HTTPException(400, "Prázdný soubor")
    if len(original) > settings.MAX_UPLOAD_MB * 1024 * 1024:
        raise HTTPException(413, f"Soubor je větší než {settings.MAX_UPLOAD_MB} MB")
    mime = storage.sniff_mime(original, original_mime)
    if mime not in storage.ALLOWED:
        raise HTTPException(415, "Podporované formáty: JPG, PNG, WEBP, PDF")

    doc = Document(
        company_id=company.id,
        uploaded_by_id=user.id if user else None,
        source=source,
        original_filename=original_name,
        original_mime=mime,
        original_path=storage.save(company.id, original, mime, "original"),
        ocr_text=(ocr_text or None) and ocr_text[:20000],
    )

    if processed:
        # upravený sken z mobilu (ML Kit / VisionKit)
        pmime = storage.sniff_mime(processed, "image/jpeg")
        doc.processed_path = storage.save(company.id, processed, pmime, "processed")
        doc.processing_info = {"by": "device"}
    elif enhance and mime.startswith("image/"):
        try:
            out, info = imaging.enhance(original, mode=mode)
            doc.processed_path = storage.save(company.id, out, "image/jpeg", "processed")
            doc.processing_info = {"by": "server", **info}
        except Exception as exc:  # obrázek nejde upravit → pracuje se s originálem
            log.warning("Úprava obrázku selhala: %s", exc)
            doc.processing_info = {"by": "server", "error": str(exc)}

    db.add(doc)
    db.commit()
    db.refresh(doc)
    return doc


def extract(db: Session, doc: Document) -> Document:
    """Vytěží doklad – nejdřív bez AI (ISDOC, QR, text, OCR, ARES), AI jen podle EXTRACTION_MODE.
    Podrobnosti v pipeline.py."""
    doc.status, doc.error = "processing", None
    db.commit()
    try:
        extract_document(doc, db.get(Company, doc.company_id))
    except Exception as exc:
        log.exception("Vytěžení dokladu %s selhalo", doc.id)
        doc.status, doc.error = "error", f"Chyba vytěžování: {exc}"
    db.commit()
    db.refresh(doc)
    return doc


def extract_in_background(doc_id: int) -> None:
    db = SessionLocal()
    try:
        doc = db.get(Document, doc_id)
        if doc:
            extract(db, doc)
    finally:
        db.close()


def delete_document(db: Session, doc: Document) -> None:
    storage.delete(doc.original_path)
    storage.delete(doc.processed_path)
    db.delete(doc)
    db.commit()


EDITABLE = [
    "doc_type", "doc_number", "variable_symbol", "supplier_name", "supplier_ico", "supplier_dic",
    "supplier_address", "supplier_account", "customer_name", "customer_ico", "customer_dic",
    "issue_date", "taxable_date", "due_date", "currency", "total", "total_base", "total_vat",
    "vat_breakdown", "payment_method", "reverse_charge", "note", "status",
]
