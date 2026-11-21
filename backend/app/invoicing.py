"""Faktury vydané: číslování, výpočet DPH, QR Platba, PDF, e-mail, opakované faktury."""
from __future__ import annotations

import calendar
import io
import logging
import smtplib
from collections import defaultdict
from datetime import date, datetime, timedelta
from decimal import ROUND_HALF_UP, Decimal
from email.message import EmailMessage
from pathlib import Path

import qrcode
from fastapi import HTTPException
from fpdf import FPDF
from sqlalchemy.orm import Session

from .config import settings
from .models import Company, Customer, Invoice, InvoiceItem, RecurringInvoice

log = logging.getLogger("doklady")
FONTS = Path(__file__).parent / "fonts"
CENT = Decimal("0.01")
VAT_RATES = (21, 12, 0)


def d2(v) -> Decimal:
    return Decimal(str(v)).quantize(CENT, rounding=ROUND_HALF_UP)


# ---------------------------------------------------------------- číslování

def next_number(db: Session, company_id: int, year: int) -> str:
    """Číselná řada RRRRNNNN (např. 20260001), samostatně pro každou firmu a rok."""
    prefix = str(year)
    last = (
        db.query(Invoice.number)
        .filter(Invoice.company_id == company_id, Invoice.number.like(f"{prefix}%"))
        .order_by(Invoice.number.desc())
        .first()
    )
    seq = int(last[0][len(prefix):]) + 1 if last and last[0][len(prefix):].isdigit() else 1
    return f"{prefix}{seq:04d}"


# ---------------------------------------------------------------- výpočet

def item_base(item) -> Decimal:
    return d2(Decimal(str(item.quantity)) * Decimal(str(item.unit_price)))


def vat_summary(inv: Invoice) -> list[dict]:
    """Rekapitulace DPH po sazbách. U neplátce / PDP je DPH nulová."""
    groups: dict[int, Decimal] = defaultdict(Decimal)
    for it in inv.items:
        rate = 0 if (inv.reverse_charge or not inv.vat_payer) else int(it.vat_rate)
        groups[rate] += item_base(it)
    return [
        {"rate": rate, "base": d2(base), "vat": d2(base * rate / 100)}
        for rate, base in sorted(groups.items(), reverse=True)
    ]


def recalc(inv: Invoice) -> None:
    rows = vat_summary(inv)
    inv.total_base = d2(sum((r["base"] for r in rows), Decimal()))
    inv.total_vat = d2(sum((r["vat"] for r in rows), Decimal()))
    inv.total = d2(inv.total_base + inv.total_vat)


def create_invoice(
    db: Session,
    company: Company,
    customer: Customer,
    items: list[dict],
    issue_date: date | None = None,
    taxable_date: date | None = None,
    due_days: int | None = None,
    currency: str = "CZK",
    reverse_charge: bool = False,
    note: str | None = None,
    payment_method: str = "prevod",
    recurring_id: int | None = None,
) -> Invoice:
    if not items:
        raise HTTPException(400, "Faktura musí mít alespoň jednu položku")
    if reverse_charge and not customer.dic:
        raise HTTPException(400, "Pro přenesenou daňovou povinnost musí mít odběratel DIČ")
    issue_date = issue_date or date.today()
    days = due_days if due_days is not None else (customer.due_days or company.default_due_days or 14)
    number = next_number(db, company.id, issue_date.year)
    inv = Invoice(
        company_id=company.id,
        customer_id=customer.id,
        recurring_id=recurring_id,
        number=number,
        variable_symbol=number[-10:],
        issue_date=issue_date,
        taxable_date=taxable_date or issue_date,
        due_date=issue_date + timedelta(days=days),
        currency=currency,
        reverse_charge=reverse_charge,
        vat_payer=company.vat_payer,
        note=note,
        payment_method=payment_method,
        status="issued",
    )
    for i, it in enumerate(items):
        if not str(it.get("description", "")).strip():
            continue
        inv.items.append(InvoiceItem(
            position=i,
            description=str(it["description"]).strip(),
            quantity=Decimal(str(it.get("quantity") or 1)),
            unit=it.get("unit") or None,
            unit_price=d2(it.get("unit_price") or 0),
            vat_rate=int(it.get("vat_rate") if it.get("vat_rate") not in (None, "") else 21),
        ))
    if not inv.items:
        raise HTTPException(400, "Faktura musí mít alespoň jednu položku s popisem")
    recalc(inv)
    db.add(inv)
    db.commit()
    db.refresh(inv)
    return inv


# ---------------------------------------------------------------- QR Platba

def czech_account_to_iban(account: str) -> str | None:
    """'19-2000145399/0800' → 'CZ6508000000192000145399'"""
    if not account or "/" not in account:
        return None
    acc, bank = account.replace(" ", "").split("/", 1)
    prefix, _, number = acc.rpartition("-")
    if not (bank.isdigit() and number.isdigit() and (not prefix or prefix.isdigit())):
        return None
    bban = bank.zfill(4) + prefix.zfill(6) + number.zfill(10)
    check = 98 - int(bban + "123500") % 97  # C=12, Z=35, 00
    return f"CZ{check:02d}{bban}"


def spd_string(company: Company, inv: Invoice) -> str | None:
    iban = (company.iban or "").replace(" ", "") or czech_account_to_iban(company.bank_account or "")
    if not iban:
        return None
    msg = f"Faktura {inv.number}"[:60].replace("*", "")
    return (
        f"SPD*1.0*ACC:{iban}*AM:{inv.total:.2f}*CC:{inv.currency}"
        f"*DT:{inv.due_date:%Y%m%d}*X-VS:{inv.variable_symbol}*MSG:{msg}"
    )


def qr_png(text: str) -> bytes:
    img = qrcode.make(text, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


# ---------------------------------------------------------------- PDF

def fmt_money(v, currency="CZK") -> str:
    s = f"{Decimal(v):,.2f}".replace(",", " ").replace(".", ",")
    return f"{s} {'Kč' if currency == 'CZK' else currency}"


def fmt_qty(v) -> str:
    v = Decimal(v).normalize()
    return f"{v:f}".replace(".", ",")


PAYMENT = {"prevod": "Bankovním převodem", "hotove": "Hotově", "karta": "Kartou", "dobirka": "Dobírkou"}


def _party(pdf: FPDF, x: float, y: float, title: str, lines: list[str]) -> None:
    pdf.set_xy(x, y)
    pdf.set_font("DejaVu", "B", 8)
    pdf.set_text_color(110, 110, 110)
    pdf.cell(85, 5, title.upper())
    pdf.set_text_color(0, 0, 0)
    for i, line in enumerate(lines):
        pdf.set_xy(x, y + 6 + i * 5)
        pdf.set_font("DejaVu", "B" if i == 0 else "", 10 if i == 0 else 9)
        pdf.cell(85, 5, line)


def invoice_pdf(inv: Invoice) -> bytes:
    company, cust = inv.company, inv.customer
    pdf = FPDF(format="A4")
    pdf.set_auto_page_break(True, margin=20)
    pdf.add_font("DejaVu", "", str(FONTS / "DejaVuSans.ttf"))
    pdf.add_font("DejaVu", "B", str(FONTS / "DejaVuSans-Bold.ttf"))
    pdf.add_page()
    pdf.set_margins(15, 15, 15)

    title = "Faktura – daňový doklad" if inv.vat_payer else "Faktura"
    pdf.set_font("DejaVu", "B", 18)
    pdf.set_xy(15, 15)
    pdf.cell(120, 10, title)
    pdf.set_font("DejaVu", "", 11)
    pdf.set_xy(135, 17)
    pdf.cell(60, 8, f"č. {inv.number}", align="R")
    pdf.set_draw_color(200, 200, 200)
    pdf.line(15, 28, 195, 28)

    def addr(o) -> list[str]:
        out = [o.name]
        if o.street:
            out.append(o.street)
        if o.zip or o.city:
            out.append(f"{o.zip or ''} {o.city or ''}".strip())
        if o.country and o.country != "Česká republika":
            out.append(o.country)
        if o.ico:
            out.append(f"IČO: {o.ico}")
        if o.dic:
            out.append(f"DIČ: {o.dic}")
        return out

    sup = addr(company)
    if not company.vat_payer:
        sup.append("Není plátce DPH")
    if company.registry_note:
        sup.append(company.registry_note)
    _party(pdf, 15, 33, "Dodavatel", sup)
    _party(pdf, 110, 33, "Odběratel", addr(cust))

    y = 33 + 6 + max(len(sup), len(addr(cust))) * 5 + 6
    pdf.line(15, y, 195, y)
    y += 4
    left = [
        ("Datum vystavení", f"{inv.issue_date:%d.%m.%Y}"),
        ("Datum splatnosti", f"{inv.due_date:%d.%m.%Y}"),
    ]
    if inv.vat_payer:
        left.append(("DUZP", f"{inv.taxable_date:%d.%m.%Y}"))
    right = [("Forma úhrady", PAYMENT.get(inv.payment_method, inv.payment_method))]
    if inv.payment_method == "prevod":
        if company.bank_account:
            right.append(("Číslo účtu", company.bank_account))
        if company.iban:
            right.append(("IBAN", company.iban))
        right.append(("Variabilní symbol", inv.variable_symbol))
    for i, (k, v) in enumerate(left):
        pdf.set_xy(15, y + i * 5.5)
        pdf.set_font("DejaVu", "", 9)
        pdf.cell(35, 5, k)
        pdf.set_font("DejaVu", "B", 9)
        pdf.cell(50, 5, v)
    for i, (k, v) in enumerate(right):
        pdf.set_xy(110, y + i * 5.5)
        pdf.set_font("DejaVu", "", 9)
        pdf.cell(35, 5, k)
        pdf.set_font("DejaVu", "B", 9)
        pdf.cell(50, 5, v)
    y += max(len(left), len(right)) * 5.5 + 8

    # položky
    show_vat = inv.vat_payer
    cols = [("Popis", 78, "L"), ("Množství", 22, "R"), ("Cena/j.", 28, "R")]
    if show_vat:
        cols.append(("DPH", 14, "R"))
    cols.append(("Celkem bez DPH" if show_vat else "Celkem", 180 - sum(c[1] for c in cols), "R"))
    pdf.set_xy(15, y)
    pdf.set_fill_color(242, 242, 242)
    pdf.set_font("DejaVu", "B", 8.5)
    for name, w, al in cols:
        pdf.cell(w, 7, name, align=al, fill=True)
    pdf.ln(7)
    pdf.set_font("DejaVu", "", 9)
    for it in inv.items:
        vals = [it.description, f"{fmt_qty(it.quantity)} {it.unit or ''}".strip(),
                fmt_money(it.unit_price, inv.currency)]
        if show_vat:
            vals.append("PDP" if inv.reverse_charge else f"{it.vat_rate} %")
        vals.append(fmt_money(item_base(it), inv.currency))
        lines = len(pdf.multi_cell(cols[0][1], 6, vals[0], dry_run=True, output="LINES"))
        h = 6 * max(1, lines)
        if pdf.get_y() + h > pdf.page_break_trigger:
            pdf.add_page()
        pdf.set_x(15)
        x0, y0 = pdf.get_x(), pdf.get_y()
        pdf.multi_cell(cols[0][1], 6, vals[0], new_x="RIGHT", new_y="TOP")
        pdf.set_xy(x0 + cols[0][1], y0)
        for (name, w, al), v in zip(cols[1:], vals[1:]):
            pdf.cell(w, 6, v, align=al)
        pdf.set_xy(15, y0 + h)
        pdf.set_draw_color(230, 230, 230)
        pdf.line(15, pdf.get_y(), 195, pdf.get_y())
    pdf.ln(4)

    # rekapitulace
    rows = vat_summary(inv)
    y = pdf.get_y()
    qr_y = y
    if show_vat:
        pdf.set_font("DejaVu", "B", 8.5)
        pdf.set_xy(110, y)
        for h_, w in (("Sazba", 20), ("Základ", 30), ("DPH", 35)):
            pdf.cell(w, 6, h_, align="R", fill=True)
        pdf.set_font("DejaVu", "", 9)
        for r in rows:
            y += 6
            pdf.set_xy(110, y)
            pdf.cell(20, 6, f"{r['rate']} %", align="R")
            pdf.cell(30, 6, fmt_money(r["base"], inv.currency), align="R")
            pdf.cell(35, 6, fmt_money(r["vat"], inv.currency), align="R")
        y += 8
    pdf.set_xy(110, y)
    pdf.set_font("DejaVu", "B", 13)
    pdf.cell(35, 9, "Celkem k úhradě")
    pdf.cell(50, 9, fmt_money(inv.total, inv.currency), align="R")
    y += 12

    if inv.reverse_charge:
        pdf.set_xy(110, y)
        pdf.set_font("DejaVu", "B", 9)
        pdf.multi_cell(85, 5, "Daň odvede zákazník (přenesená daňová povinnost dle § 92a zákona o DPH).")
        y = pdf.get_y() + 2

    if inv.payment_method == "prevod":
        spd = spd_string(company, inv)
        if spd:
            pdf.image(io.BytesIO(qr_png(spd)), x=15, y=qr_y, w=34, h=34)
            pdf.set_xy(15, qr_y + 34)
            pdf.set_font("DejaVu", "", 7)
            pdf.cell(34, 4, "QR Platba", align="C")
            y = max(y, qr_y + 40)

    pdf.set_y(y + 4)
    if inv.note:
        pdf.set_x(15)
        pdf.set_font("DejaVu", "", 9)
        pdf.multi_cell(180, 5, inv.note)
    if company.invoice_footer:
        pdf.set_x(15)
        pdf.set_font("DejaVu", "", 8)
        pdf.set_text_color(110, 110, 110)
        pdf.multi_cell(180, 4.5, company.invoice_footer)
    return bytes(pdf.output())


# ---------------------------------------------------------------- e-mail

def send_invoice_email(inv: Invoice, to: str, message: str | None = None) -> None:
    if not settings.SMTP_HOST:
        raise HTTPException(400, "Odesílání e-mailů není nastavené (SMTP_HOST).")
    to = to.strip()
    if "@" not in to:
        raise HTTPException(400, "Neplatný e-mail příjemce")
    company = inv.company
    msg = EmailMessage()
    msg["Subject"] = f"Faktura {inv.number} – {company.name}"
    msg["From"] = settings.SMTP_FROM or settings.SMTP_USER
    msg["To"] = to
    if company.email:
        msg["Reply-To"] = company.email
    body = message or (
        f"Dobrý den,\n\nv příloze zasíláme fakturu č. {inv.number} na částku "
        f"{fmt_money(inv.total, inv.currency)} se splatností {inv.due_date:%d.%m.%Y}.\n"
        f"Variabilní symbol: {inv.variable_symbol}\n\nS pozdravem\n{company.name}"
    )
    msg.set_content(body)
    msg.add_attachment(invoice_pdf(inv), maintype="application", subtype="pdf",
                       filename=f"faktura_{inv.number}.pdf")
    smtp_cls = smtplib.SMTP_SSL if settings.SMTP_SSL else smtplib.SMTP
    with smtp_cls(settings.SMTP_HOST, settings.SMTP_PORT, timeout=30) as s:
        if not settings.SMTP_SSL:
            s.starttls()
        if settings.SMTP_USER:
            s.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
        s.send_message(msg)


def mark_sent(db: Session, inv: Invoice, to: str) -> None:
    inv.sent_at = datetime.utcnow()
    inv.sent_to = to
    if inv.status in ("issued", "draft"):
        inv.status = "sent"
    db.commit()


# ---------------------------------------------------------------- opakované faktury

def add_months(d: date, months: int) -> date:
    m = d.month - 1 + months
    y, m = d.year + m // 12, m % 12 + 1
    return date(y, m, min(d.day, calendar.monthrange(y, m)[1]))


def run_recurring(db: Session, today: date | None = None) -> list[Invoice]:
    """Vystaví všechny opakované faktury, jejichž termín nastal (i zpětně zmeškané)."""
    today = today or date.today()
    created: list[Invoice] = []
    templates = db.query(RecurringInvoice).filter(
        RecurringInvoice.active.is_(True), RecurringInvoice.next_date <= today
    ).all()
    for t in templates:
        company = db.get(Company, t.company_id)
        while t.active and t.next_date <= today:
            if t.end_date and t.next_date > t.end_date:
                t.active = False
                break
            inv = create_invoice(
                db, company, t.customer, t.items, issue_date=t.next_date, due_days=t.due_days,
                currency=t.currency, reverse_charge=t.reverse_charge, note=t.note, recurring_id=t.id,
            )
            created.append(inv)
            to = t.email_to or t.customer.email
            if t.auto_send and to and settings.SMTP_HOST:
                try:
                    send_invoice_email(inv, to)
                    mark_sent(db, inv, to)
                except Exception as exc:
                    log.error("Odeslání opakované faktury %s selhalo: %s", inv.number, exc)
            t.next_date = add_months(t.next_date, t.interval_months)
            t.last_run = datetime.utcnow()
            db.commit()
    return created
