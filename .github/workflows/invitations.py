"""Pozvánky do klientského portálu.

Účetní na webu založí klientovi přístup → klient dostane e-mail s odkazem, QR kódem
a aktivačním kódem → v mobilní aplikaci kód zadá (nebo klepne na odkaz) a nastaví si heslo.
Adresa serveru je v aplikaci pevně, klient nic nenastavuje; podle účtu vidí jen svůj portál (svou firmu).
"""
from __future__ import annotations

import html
import secrets
from datetime import datetime, timedelta

from fastapi import HTTPException
from sqlalchemy.orm import Session

from . import mailer
from .auth import hash_password
from .config import settings
from .invoicing import qr_png
from .models import Company, User

ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # bez 0/O a 1/I – snadno se opisuje


def new_code() -> str:
    raw = "".join(secrets.choice(ALPHABET) for _ in range(8))
    return f"{raw[:4]}-{raw[4:]}"


def normalize_code(code: str) -> str:
    c = "".join(ch for ch in code.upper() if ch.isalnum())
    return f"{c[:4]}-{c[4:]}" if len(c) == 8 else code.strip().upper()


def web_link(code: str) -> str:
    return f"{settings.PUBLIC_URL}/aktivace?code={code}"


def app_link(code: str) -> str:
    return f"{settings.APP_SCHEME}://activate?code={code}"


def invite(db: Session, company: Company, email: str, name: str | None = None) -> tuple[User, str]:
    """Založí (nebo obnoví) přístup klienta do portálu firmy a vrátí aktivační kód."""
    email = email.strip().lower()
    if "@" not in email:
        raise HTTPException(400, "Neplatný e-mail")
    user = db.query(User).filter(User.email == email).first()
    if user and user.company_id != company.id and not user.is_admin:
        raise HTTPException(409, "Tento e-mail už má přístup k jiné firmě")
    if user is None:
        user = User(email=email, name=name, role="client", company_id=company.id,
                    password_hash=hash_password(secrets.token_urlsafe(24)))  # heslo si nastaví při aktivaci
        db.add(user)
    user.active = True
    user.activation_code = new_code()
    user.activation_expires = datetime.utcnow() + timedelta(days=settings.INVITATION_DAYS)
    db.commit()
    return user, user.activation_code


def send_invitation(user: User, company: Company, code: str) -> None:
    if not mailer.configured():
        raise HTTPException(400, "Odesílání e-mailů není nastavené (SMTP_HOST) – předejte klientovi kód ručně.")
    link = web_link(code)
    text = (
        f"Dobrý den,\n\nvaše účetní kancelář pro vás připravila klientský portál {company.name} "
        f"v aplikaci {settings.APP_NAME}.\n\n"
        f"1. Stáhněte si aplikaci:\n   Android: {settings.GOOGLE_PLAY_URL}\n   iPhone: {settings.APP_STORE_URL}\n"
        f"2. Otevřete tento odkaz v telefonu: {link}\n"
        f"   nebo v aplikaci zvolte „Mám pozvánku“ a zadejte kód: {code}\n"
        f"3. Nastavte si heslo – hotovo.\n\nKód platí {settings.INVITATION_DAYS} dní.\n"
    )
    page = f"""<p>Dobrý den,</p>
<p>vaše účetní kancelář pro vás připravila klientský portál <b>{html.escape(company.name)}</b>
v aplikaci {html.escape(settings.APP_NAME)}.</p>
<ol>
<li>Stáhněte si aplikaci: <a href="{settings.GOOGLE_PLAY_URL}">Android</a> · <a href="{settings.APP_STORE_URL}">iPhone</a></li>
<li>V telefonu klepněte na <a href="{link}"><b>Aktivovat portál</b></a>,
nebo naskenujte QR kód v příloze, nebo v aplikaci zvolte „Mám pozvánku“ a zadejte kód
<b style="font-size:18px;letter-spacing:2px">{code}</b></li>
<li>Nastavte si heslo – hotovo.</li>
</ol>
<p style="color:#666">Kód platí {settings.INVITATION_DAYS} dní.</p>"""
    mailer.send(user.email, f"Pozvánka do klientského portálu {company.name}", text, page,
                attachments=[("aktivace-qr.png", qr_png(link), "image/png")], reply_to=company.email)


def activate(db: Session, code: str, password: str) -> User:
    if len(password) < 8:
        raise HTTPException(400, "Heslo musí mít alespoň 8 znaků")
    user = db.query(User).filter(User.activation_code == normalize_code(code)).first()
    if not user or not user.active:
        raise HTTPException(404, "Neplatný aktivační kód – zkontrolujte ho, nebo požádejte účetní o novou pozvánku.")
    if user.activation_expires and user.activation_expires < datetime.utcnow():
        raise HTTPException(410, "Platnost pozvánky vypršela – požádejte účetní o novou.")
    user.password_hash = hash_password(password)
    user.activation_code = None
    user.activation_expires = None
    db.commit()
    return user


def landing_page(code: str) -> str:
    """Stránka z odkazu v pozvánce: otevře aplikaci, nebo nabídne její stažení."""
    c = html.escape(normalize_code(code))
    app = html.escape(app_link(c))
    return f"""<!doctype html><html lang="cs"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Aktivace portálu</title>
<style>body{{font-family:system-ui,sans-serif;max-width:420px;margin:40px auto;padding:0 16px;color:#1a1c1e}}
a.b{{display:block;text-align:center;padding:14px;border-radius:24px;background:#1F5FAD;color:#fff;text-decoration:none;font-weight:600;margin:12px 0}}
a.o{{background:#fff;color:#1F5FAD;border:1px solid #1F5FAD}}.c{{font-size:26px;letter-spacing:3px;text-align:center;font-weight:700;margin:16px 0}}
p{{line-height:1.5}}</style></head><body>
<h2>{html.escape(settings.APP_NAME)}</h2>
<p>Pro aktivaci klientského portálu otevřete aplikaci. Pokud ji ještě nemáte, nejdřív si ji stáhněte.</p>
<a class="b" href="{app}">Otevřít v aplikaci</a>
<a class="b o" href="{settings.GOOGLE_PLAY_URL}">Stáhnout pro Android</a>
<a class="b o" href="{settings.APP_STORE_URL}">Stáhnout pro iPhone</a>
<p>Aktivační kód (zadáte v aplikaci volbou „Mám pozvánku“):</p><div class="c">{c}</div>
</body></html>"""
