"""Referenční API server pro mobilní aplikaci Doklady.

Slouží jako vzor/kontrakt pro napojení vlastní webové aplikace a jako testovací server.
Spuštění:  uvicorn app.main:app --host 0.0.0.0 --port 8000
"""
import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import HTMLResponse

from . import invitations
from .api.routes import router
from .auth import hash_password
from .config import settings
from .db import Base, SessionLocal, engine
from .models import Company, User

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("doklady")


def init_db() -> None:
    Base.metadata.create_all(engine)
    db = SessionLocal()
    try:
        if not db.query(User).first():
            company = db.query(Company).first() or Company(name="Moje firma s.r.o.")
            db.add(company)
            db.flush()
            db.add(User(email=settings.ADMIN_EMAIL.lower(), name="Administrátor", role="admin",
                        password_hash=hash_password(settings.ADMIN_PASSWORD), company_id=company.id))
            db.commit()
            log.info("Vytvořen administrátor %s", settings.ADMIN_EMAIL)
    finally:
        db.close()


async def _recurring_loop() -> None:
    from .invoicing import run_recurring
    while True:
        db = SessionLocal()
        try:
            created = run_recurring(db)
            if created:
                log.info("Vystaveny opakované faktury: %s", [i.number for i in created])
        except Exception:
            log.exception("Opakované faktury selhaly")
        finally:
            db.close()
        await asyncio.sleep(3600)


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    task = asyncio.create_task(_recurring_loop()) if settings.RECURRING_ENABLED else None
    yield
    if task:
        task.cancel()


app = FastAPI(title="Doklady API", version="1.0", lifespan=lifespan)
app.include_router(router)


@app.get("/aktivace", response_class=HTMLResponse, include_in_schema=False)
def activation_page(code: str = ""):
    """Odkaz z pozvánky: otevře aplikaci (nebo nabídne stažení) a ukáže aktivační kód."""
    return invitations.landing_page(code)


@app.get("/api/health")
def health():
    return {"ok": True}
