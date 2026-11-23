"""Odesílání e-mailů (SMTP podle .env)."""
import smtplib
from email.message import EmailMessage

from .config import settings


def configured() -> bool:
    return bool(settings.SMTP_HOST)


def send(to: str, subject: str, text: str, html: str | None = None,
         attachments: list[tuple[str, bytes, str]] | None = None, reply_to: str | None = None) -> None:
    """attachments = [(název souboru, data, mime typ)]"""
    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = settings.SMTP_FROM or settings.SMTP_USER
    msg["To"] = to
    if reply_to:
        msg["Reply-To"] = reply_to
    msg.set_content(text)
    if html:
        msg.add_alternative(html, subtype="html")
    for name, data, mime in attachments or []:
        main, _, sub = mime.partition("/")
        msg.add_attachment(data, maintype=main, subtype=sub, filename=name)
    smtp_cls = smtplib.SMTP_SSL if settings.SMTP_SSL else smtplib.SMTP
    with smtp_cls(settings.SMTP_HOST, settings.SMTP_PORT, timeout=30) as s:
        if not settings.SMTP_SSL:
            s.starttls()
        if settings.SMTP_USER:
            s.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
        s.send_message(msg)
