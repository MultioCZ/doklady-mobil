"""Ukládání souborů: STORAGE_DIR/<firma>/<rok-měsíc>/<uuid>_original.ext"""
import uuid
from datetime import datetime
from pathlib import Path

from .config import settings

ALLOWED = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "application/pdf": ".pdf",
}


def sniff_mime(data: bytes, declared: str | None) -> str:
    if data[:4] == b"%PDF":
        return "application/pdf"
    if data[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    return declared or "application/octet-stream"


def save(company_id: int, data: bytes, mime: str, kind: str) -> str:
    folder = Path(str(company_id)) / datetime.utcnow().strftime("%Y-%m")
    (settings.STORAGE_DIR / folder).mkdir(parents=True, exist_ok=True)
    rel = folder / f"{uuid.uuid4().hex}_{kind}{ALLOWED.get(mime, '.bin')}"
    (settings.STORAGE_DIR / rel).write_bytes(data)
    return str(rel)


def path(rel: str) -> Path:
    p = (settings.STORAGE_DIR / rel).resolve()
    if settings.STORAGE_DIR.resolve() not in p.parents:
        raise ValueError("Neplatná cesta")
    return p


def delete(rel: str | None) -> None:
    if rel:
        try:
            path(rel).unlink(missing_ok=True)
        except ValueError:
            pass
