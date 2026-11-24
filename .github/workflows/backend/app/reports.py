"""Přehledy pro mobilní aplikaci: doklady za období, DPH podle sazeb, náklady podle dodavatelů."""
from __future__ import annotations

from collections import defaultdict
from datetime import date
from decimal import Decimal

from sqlalchemy import func
from sqlalchemy.orm import Session

from .invoicing import vat_summary
from .models import Document, Invoice

COUNTED = ("extracted", "checked")  # do součtů jdou jen vytěžené doklady


def _d(v) -> Decimal:
    return Decimal(str(v or 0)).quantize(Decimal("0.01"))


def doc_date_expr():
    """Datum dokladu = DUZP, jinak datum vystavení, jinak datum nahrání."""
    return func.coalesce(Document.taxable_date, Document.issue_date, func.date(Document.created_at))


def doc_date(d: Document) -> date:
    return d.taxable_date or d.issue_date or d.created_at.date()


def supplier_key(d: Document) -> str:
    """Dodavatel se pozná podle IČO, bez IČO podle názvu."""
    return d.supplier_ico or "nazev:" + (d.supplier_name or "Neznámý dodavatel").strip().lower()


def documents_in_period(db: Session, company_id: int, date_from: date, date_to: date, supplier: str | None = None):
    expr = doc_date_expr()
    q = db.query(Document).filter(Document.company_id == company_id, expr >= date_from, expr <= date_to)
    docs = q.all()
    if supplier:
        docs = [d for d in docs if supplier_key(d) == supplier]
    return sorted(docs, key=lambda d: (doc_date(d), d.id))


def suppliers(db: Session, company_id: int, date_from: date, date_to: date) -> list[dict]:
    """Všichni dodavatelé za období (jen vytěžené a zkontrolované doklady), seřazení podle částky."""
    out: dict[str, dict] = {}
    for d in documents_in_period(db, company_id, date_from, date_to):
        if d.status not in COUNTED:
            continue
        sign = -1 if d.doc_type == "dobropis" and (d.total or 0) > 0 else 1
        s = out.setdefault(supplier_key(d), {"key": supplier_key(d), "name": d.supplier_name or "Neznámý dodavatel",
                                             "ico": d.supplier_ico, "count": 0, "total": Decimal(0), "vat": Decimal(0)})
        s["count"] += 1
        s["total"] += sign * _d(d.total)
        s["vat"] += sign * _d(d.total_vat)
    rows = sorted(out.values(), key=lambda s: s["total"], reverse=True)
    return [{**r, "total": str(r["total"]), "vat": str(r["vat"])} for r in rows]


def summary(db: Session, company_id: int, date_from: date, date_to: date, top: int = 10) -> dict:
    docs = documents_in_period(db, company_id, date_from, date_to)

    by_status: dict[str, int] = defaultdict(int)
    total = Decimal(0)
    received_vat: dict[int, list[Decimal]] = defaultdict(lambda: [Decimal(0), Decimal(0)])
    for d in docs:
        by_status[d.status] += 1
        if d.status not in COUNTED:
            continue
        sign = -1 if d.doc_type == "dobropis" and (d.total or 0) > 0 else 1
        total += sign * _d(d.total)
        for r in d.vat_breakdown or []:
            rate = int(float(r.get("rate") or 0))
            received_vat[rate][0] += sign * _d(r.get("base"))
            received_vat[rate][1] += sign * _d(r.get("vat"))

    invoices = (
        db.query(Invoice)
        .filter(Invoice.company_id == company_id, Invoice.taxable_date >= date_from,
                Invoice.taxable_date <= date_to, Invoice.status != "cancelled")
        .all()
    )
    issued_vat: dict[int, list[Decimal]] = defaultdict(lambda: [Decimal(0), Decimal(0)])
    issued_total = Decimal(0)
    for inv in invoices:
        issued_total += _d(inv.total)
        for r in vat_summary(inv):
            issued_vat[r["rate"]][0] += r["base"]
            issued_vat[r["rate"]][1] += r["vat"]

    def rows(m):
        return [{"rate": k, "base": str(v[0]), "vat": str(v[1])} for k, v in sorted(m.items(), reverse=True)]

    vat_in = sum((v[1] for v in received_vat.values()), Decimal(0))
    vat_out = sum((v[1] for v in issued_vat.values()), Decimal(0))
    return {
        "from": date_from.isoformat(),
        "to": date_to.isoformat(),
        "documents": {
            "count": len(docs),
            "total": str(total),
            "by_status": dict(by_status),
            "to_check": by_status.get("extracted", 0) + by_status.get("error", 0),
        },
        "invoices": {"count": len(invoices), "total": str(issued_total)},
        "vat": {
            "received": rows(received_vat),  # DPH na vstupu (přijaté doklady)
            "issued": rows(issued_vat),      # DPH na výstupu (vydané faktury)
            "input_total": str(vat_in),
            "output_total": str(vat_out),
            "balance": str(vat_out - vat_in),  # kladné = k odvodu, záporné = nadměrný odpočet
        },
        "suppliers": suppliers(db, company_id, date_from, date_to)[:top],
        "note": "Orientační přehled z vytěžených dokladů – nenahrazuje přiznání k DPH.",
    }


# ----------------------------------------------------------------- PDF seznam dokladů podle dodavatele

STATUS = {"uploaded": "nahráno", "processing": "zpracovává se", "extracted": "ke kontrole",
          "checked": "zkontrolováno", "error": "chyba"}
TYPES = {"faktura": "faktura", "uctenka": "účtenka", "zalohova": "zálohová", "dobropis": "dobropis", "jine": "jiný"}


def supplier_list_pdf(db: Session, company, date_from: date, date_to: date, supplier: str | None = None) -> bytes:
    """Seznam přijatých dokladů seskupený podle dodavatelů (nebo jen jednoho dodavatele)."""
    from fpdf import FPDF
    from .invoicing import FONTS, fmt_money

    docs = [d for d in documents_in_period(db, company.id, date_from, date_to, supplier) if d.status != "error"]
    groups: dict[str, list[Document]] = defaultdict(list)
    for d in docs:
        groups[supplier_key(d)].append(d)
    order = sorted(groups, key=lambda k: -sum(_d(x.total) for x in groups[k]))

    pdf = FPDF(format="A4")
    pdf.add_font("DejaVu", "", str(FONTS / "DejaVuSans.ttf"))
    pdf.add_font("DejaVu", "B", str(FONTS / "DejaVuSans-Bold.ttf"))
    pdf.set_auto_page_break(True, margin=15)
    pdf.add_page()
    pdf.set_margins(15, 15, 15)
    pdf.set_font("DejaVu", "B", 15)
    title = "Přijaté doklady podle dodavatelů" if not supplier else "Přijaté doklady dodavatele"
    pdf.cell(180, 9, title, new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("DejaVu", "", 9.5)
    pdf.cell(180, 6, f"{company.name}{' · IČO ' + company.ico if company.ico else ''}   ·   "
                     f"období {date_from:%d.%m.%Y} – {date_to:%d.%m.%Y}", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(3)

    cols = [("Datum", 22, "L"), ("Číslo dokladu", 38, "L"), ("VS", 26, "L"), ("Typ", 22, "L"),
            ("Stav", 28, "L"), ("DPH", 20, "R"), ("Celkem", 24, "R")]
    grand = Decimal(0)
    for key in order:
        rows = groups[key]
        first = rows[0]
        if pdf.get_y() > 250:
            pdf.add_page()
        pdf.set_fill_color(234, 239, 247)
        pdf.set_font("DejaVu", "B", 10)
        head = first.supplier_name or "Neznámý dodavatel"
        if first.supplier_ico:
            head += f"   IČO {first.supplier_ico}"
        pdf.cell(180, 7, head, fill=True, new_x="LMARGIN", new_y="NEXT")
        pdf.set_font("DejaVu", "B", 8)
        for name, w, al in cols:
            pdf.cell(w, 6, name, align=al)
        pdf.ln(6)
        pdf.set_font("DejaVu", "", 8.5)
        subtotal = Decimal(0)
        for d in rows:
            counted = d.status in COUNTED
            if counted:
                subtotal += _d(d.total)
            vals = [f"{doc_date(d):%d.%m.%Y}", (d.doc_number or "—")[:22], (d.variable_symbol or "")[:14],
                    TYPES.get(d.doc_type or "", d.doc_type or ""), STATUS.get(d.status, d.status),
                    fmt_money(d.total_vat or 0, d.currency or "CZK") if d.total_vat is not None else "",
                    fmt_money(d.total or 0, d.currency or "CZK") if d.total is not None else "—"]
            for (name, w, al), v in zip(cols, vals):
                pdf.cell(w, 5.5, v, align=al)
            pdf.ln(5.5)
        pdf.set_font("DejaVu", "B", 9)
        pdf.cell(146, 6, f"Celkem {len(rows)} {'doklad' if len(rows) == 1 else 'doklady' if len(rows) < 5 else 'dokladů'}", align="R")
        pdf.cell(34, 6, fmt_money(subtotal), align="R", new_x="LMARGIN", new_y="NEXT")
        pdf.ln(3)
        grand += subtotal

    if not order:
        pdf.set_font("DejaVu", "", 10)
        pdf.cell(180, 8, "Za zvolené období nejsou žádné doklady.", new_x="LMARGIN", new_y="NEXT")
    else:
        pdf.set_draw_color(150, 150, 150)
        pdf.line(15, pdf.get_y(), 195, pdf.get_y())
        pdf.set_font("DejaVu", "B", 11)
        pdf.cell(130, 8, "Celkem za období", align="R")
        pdf.cell(50, 8, fmt_money(grand), align="R", new_x="LMARGIN", new_y="NEXT")
        pdf.set_font("DejaVu", "", 8)
        pdf.cell(180, 5, "Do součtů jsou započteny vytěžené a zkontrolované doklady.", align="R",
                 new_x="LMARGIN", new_y="NEXT")
    return bytes(pdf.output())
