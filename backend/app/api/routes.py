"""REST API pro mobilní aplikaci (a cokoliv dalšího – Make.com apod.). Dokumentace: /docs"""
from __future__ import annotations

from datetime import date

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, HTTPException, Query, Response, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from .. import ares, documents, invitations, invoicing, reports, storage
from ..auth import api_company, api_user, authenticate, create_token
from ..db import get_db
from ..models import Company, Customer, Document, Invoice, RecurringInvoice, User
from ..schemas import (
    ActivateIn, InvitationIn, InvitationOut, CustomerIn, CustomerOut, DocumentOut, DocumentPatch, InvoiceIn, InvoiceOut, LoginIn, LoginOut,
    RecurringIn, RecurringOut, SendIn, UserOut, CompanyOut,
)

router = APIRouter(prefix="/api")


def _companies(db: Session, user: User) -> list[Company]:
    if user.is_admin:
        return db.query(Company).order_by(Company.name).all()
    return [user.company] if user.company else []


# ------------------------------------------------------------------ přihlášení

@router.post("/auth/login", response_model=LoginOut)
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = authenticate(db, body.email, body.password)
    if not user:
        raise HTTPException(401, "Špatný e-mail nebo heslo")
    return LoginOut(
        token=create_token(user),
        user=UserOut.model_validate(user),
        companies=[CompanyOut.model_validate(c) for c in _companies(db, user)],
    )


@router.post("/activate", response_model=LoginOut)
def activate(body: ActivateIn, db: Session = Depends(get_db)):
    """Aktivace klientského portálu kódem z pozvánky + nastavení hesla. Vrací přihlášení jako login."""
    user = invitations.activate(db, body.code, body.password)
    return LoginOut(
        token=create_token(user),
        user=UserOut.model_validate(user),
        companies=[CompanyOut.model_validate(c) for c in _companies(db, user)],
    )


@router.post("/invitations", response_model=InvitationOut, status_code=201)
def create_invitation(body: InvitationIn, user: User = Depends(api_user), company: Company = Depends(api_company),
                      db: Session = Depends(get_db)):
    """Účetní (admin) pozve klienta do jeho portálu. Volá to webová aplikace (případně aplikace účetní)."""
    if not user.is_admin:
        raise HTTPException(403, "Pozvánky posílá jen účetní kancelář")
    target = db.get(Company, body.company_id) if body.company_id else company
    if not target:
        raise HTTPException(404, "Firma nenalezena")
    invited, code = invitations.invite(db, target, body.email, body.name)
    emailed = False
    if body.send_email and invitations.mailer.configured():
        invitations.send_invitation(invited, target, code)
        emailed = True
    return InvitationOut(email=invited.email, code=code, link=invitations.web_link(code),
                         app_link=invitations.app_link(code), expires=invited.activation_expires, emailed=emailed)


@router.get("/invitations/{code}/qr.png")
def invitation_qr(code: str, user: User = Depends(api_user)):
    """QR kód pozvánky (např. k vytištění nebo ukázání klientovi na obrazovce)."""
    if not user.is_admin:
        raise HTTPException(403, "Jen pro účetní kancelář")
    return Response(invoicing.qr_png(invitations.web_link(invitations.normalize_code(code))), media_type="image/png")


@router.get("/me", response_model=LoginOut)
def me(user: User = Depends(api_user), db: Session = Depends(get_db)):
    return LoginOut(token="", user=UserOut.model_validate(user),
                    companies=[CompanyOut.model_validate(c) for c in _companies(db, user)])


# ------------------------------------------------------------------ doklady

def _doc(db: Session, company: Company, doc_id: int) -> Document:
    doc = db.get(Document, doc_id)
    if not doc or doc.company_id != company.id:
        raise HTTPException(404, "Doklad nenalezen")
    return doc


@router.post("/documents", response_model=DocumentOut, status_code=201)
async def upload_document(
    background: BackgroundTasks,
    original: UploadFile = File(..., description="Originál – fotka nebo PDF"),
    processed: UploadFile | None = File(None, description="Upravený sken z telefonu (volitelné)"),
    enhance: bool = Form(True, description="Pokud chybí upravená verze, upravit na serveru"),
    mode: str = Form("gray", pattern="^(gray|bw|color)$"),
    extract: bool = Form(True, description="Hned vytěžit"),
    note: str | None = Form(None),
    ocr_text: str | None = Form(None, description="Text přečtený v telefonu (ML Kit / Apple Vision) – vytěžení bez AI"),
    company: Company = Depends(api_company),
    user: User = Depends(api_user),
    db: Session = Depends(get_db),
):
    doc = documents.create_document(
        db, company, user,
        original=await original.read(), original_name=original.filename, original_mime=original.content_type,
        processed=await processed.read() if processed else None,
        enhance=enhance, mode=mode, source="mobile", ocr_text=ocr_text,
    )
    if note:
        doc.note = note
        db.commit()
    if extract:
        doc.status = "processing"
        db.commit()
        background.add_task(documents.extract_in_background, doc.id)
    return DocumentOut.of(doc)


@router.get("/documents", response_model=list[DocumentOut])
def list_documents(
    status: str | None = None,
    q: str | None = None,
    supplier: str | None = Query(None, description="klíč dodavatele z /reports/suppliers (IČO nebo nazev:…)"),
    date_from: date | None = Query(None, alias="from"),
    date_to: date | None = Query(None, alias="to"),
    limit: int = Query(50, le=500),
    offset: int = 0,
    company: Company = Depends(api_company),
    db: Session = Depends(get_db),
):
    if supplier or date_from or date_to:
        docs = reports.documents_in_period(db, company.id, date_from or date(2000, 1, 1),
                                           date_to or date(2100, 1, 1), supplier)
        if status:
            docs = [d for d in docs if d.status == status]
        return [DocumentOut.of(d) for d in docs[offset:offset + limit]]
    qry = db.query(Document).filter(Document.company_id == company.id)
    if status:
        qry = qry.filter(Document.status == status)
    if q:
        like = f"%{q}%"
        qry = qry.filter(Document.supplier_name.ilike(like) | Document.doc_number.ilike(like)
                         | Document.supplier_ico.ilike(like))
    return [DocumentOut.of(d) for d in qry.order_by(Document.created_at.desc()).offset(offset).limit(limit)]


@router.get("/documents/{doc_id}", response_model=DocumentOut)
def get_document(doc_id: int, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    return DocumentOut.of(_doc(db, company, doc_id))


@router.patch("/documents/{doc_id}", response_model=DocumentOut)
def patch_document(doc_id: int, body: DocumentPatch, company: Company = Depends(api_company),
                   db: Session = Depends(get_db)):
    doc = _doc(db, company, doc_id)
    for k, v in body.model_dump(exclude_unset=True).items():
        setattr(doc, k, v)
    db.commit()
    return DocumentOut.of(doc)


@router.post("/documents/{doc_id}/extract", response_model=DocumentOut)
def reextract(doc_id: int, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    return DocumentOut.of(documents.extract(db, _doc(db, company, doc_id)))


@router.post("/documents/{doc_id}/ares", response_model=DocumentOut)
def document_ares(doc_id: int, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    """Doplní název, adresu a DIČ dodavatele z ARES podle IČO na dokladu."""
    doc = _doc(db, company, doc_id)
    if not doc.supplier_ico:
        raise HTTPException(400, "Doklad nemá IČO dodavatele")
    info = ares.lookup(doc.supplier_ico)
    if not info:
        raise HTTPException(404, "IČO v ARES nenalezeno")
    doc.supplier_name = info.get("name") or doc.supplier_name
    doc.supplier_address = info.get("address") or doc.supplier_address
    doc.supplier_dic = info.get("dic") or doc.supplier_dic
    db.commit()
    return DocumentOut.of(doc)


@router.get("/documents/{doc_id}/file/{kind}")
def document_file(doc_id: int, kind: str, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    doc = _doc(db, company, doc_id)
    if kind == "original":
        rel, mime = doc.original_path, doc.original_mime
    elif kind == "processed" and doc.processed_path:
        rel, mime = doc.processed_path, "image/jpeg"
    else:
        raise HTTPException(404, "Soubor neexistuje")
    return FileResponse(storage.path(rel), media_type=mime)


@router.delete("/documents/{doc_id}", status_code=204)
def delete_document(doc_id: int, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    documents.delete_document(db, _doc(db, company, doc_id))
    return Response(status_code=204)


# ------------------------------------------------------------------ odběratelé

@router.get("/ares/{ico}")
def ares_lookup(ico: str, user: User = Depends(api_user)):
    data = ares.lookup(ico)
    if not data:
        raise HTTPException(404, "Subjekt v ARES nenalezen")
    return data


@router.get("/customers", response_model=list[CustomerOut])
def list_customers(company: Company = Depends(api_company), db: Session = Depends(get_db)):
    return db.query(Customer).filter(Customer.company_id == company.id).order_by(Customer.name).all()


@router.post("/customers", response_model=CustomerOut, status_code=201)
def create_customer(body: CustomerIn, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    c = Customer(company_id=company.id, **body.model_dump())
    db.add(c)
    db.commit()
    return c


def _customer(db: Session, company: Company, cid: int) -> Customer:
    c = db.get(Customer, cid)
    if not c or c.company_id != company.id:
        raise HTTPException(404, "Odběratel nenalezen")
    return c


@router.put("/customers/{cid}", response_model=CustomerOut)
def update_customer(cid: int, body: CustomerIn, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    c = _customer(db, company, cid)
    for k, v in body.model_dump().items():
        setattr(c, k, v)
    db.commit()
    return c


# ------------------------------------------------------------------ faktury

def _invoice(db: Session, company: Company, iid: int) -> Invoice:
    inv = db.get(Invoice, iid)
    if not inv or inv.company_id != company.id:
        raise HTTPException(404, "Faktura nenalezena")
    return inv


@router.get("/invoices", response_model=list[InvoiceOut])
def list_invoices(limit: int = Query(100, le=500), company: Company = Depends(api_company),
                  db: Session = Depends(get_db)):
    return (db.query(Invoice).filter(Invoice.company_id == company.id)
            .order_by(Invoice.issue_date.desc(), Invoice.number.desc()).limit(limit).all())


@router.post("/invoices", response_model=InvoiceOut, status_code=201)
def create_invoice(body: InvoiceIn, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    cust = _customer(db, company, body.customer_id)
    inv = invoicing.create_invoice(
        db, company, cust, [i.model_dump() for i in body.items], issue_date=body.issue_date,
        taxable_date=body.taxable_date, due_days=body.due_days, currency=body.currency,
        reverse_charge=body.reverse_charge, note=body.note, payment_method=body.payment_method,
    )
    if body.send_to:
        invoicing.send_invoice_email(inv, body.send_to)
        invoicing.mark_sent(db, inv, body.send_to)
    return inv


@router.get("/invoices/{iid}", response_model=InvoiceOut)
def get_invoice(iid: int, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    return _invoice(db, company, iid)


@router.get("/invoices/{iid}/pdf")
def invoice_pdf(iid: int, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    inv = _invoice(db, company, iid)
    return Response(invoicing.invoice_pdf(inv), media_type="application/pdf",
                    headers={"Content-Disposition": f'inline; filename="faktura_{inv.number}.pdf"'})


@router.post("/invoices/{iid}/send", response_model=InvoiceOut)
def send_invoice(iid: int, body: SendIn, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    inv = _invoice(db, company, iid)
    to = body.email or inv.customer.email
    if not to:
        raise HTTPException(400, "Odběratel nemá e-mail – zadejte příjemce")
    invoicing.send_invoice_email(inv, to, body.message)
    invoicing.mark_sent(db, inv, to)
    return inv


@router.post("/invoices/{iid}/status/{status}", response_model=InvoiceOut)
def set_invoice_status(iid: int, status: str, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    if status not in ("issued", "sent", "paid", "cancelled"):
        raise HTTPException(400, "Neplatný stav")
    inv = _invoice(db, company, iid)
    inv.status = status
    db.commit()
    return inv


# ------------------------------------------------------------------ opakované faktury

@router.get("/recurring", response_model=list[RecurringOut])
def list_recurring(company: Company = Depends(api_company), db: Session = Depends(get_db)):
    return db.query(RecurringInvoice).filter(RecurringInvoice.company_id == company.id).all()


@router.post("/recurring", response_model=RecurringOut, status_code=201)
def create_recurring(body: RecurringIn, company: Company = Depends(api_company), db: Session = Depends(get_db)):
    _customer(db, company, body.customer_id)
    data = body.model_dump()
    data["items"] = [{**i, "quantity": float(i["quantity"]), "unit_price": float(i["unit_price"])} for i in data["items"]]
    r = RecurringInvoice(company_id=company.id, **data)
    db.add(r)
    db.commit()
    return r


@router.post("/recurring/run")
def run_recurring_now(user: User = Depends(api_user), db: Session = Depends(get_db)):
    if not user.is_admin:
        raise HTTPException(403, "Jen pro administrátora")
    created = invoicing.run_recurring(db, date.today())
    return {"created": [i.number for i in created]}


# ------------------------------------------------------------------ přehledy

@router.get("/reports/summary")
def report_summary(
    date_from: date = Query(..., alias="from"),
    date_to: date = Query(..., alias="to"),
    company: Company = Depends(api_company),
    db: Session = Depends(get_db),
):
    """Doklady za období, DPH podle sazeb (vstup × výstup) a největší dodavatelé."""
    if date_to < date_from:
        raise HTTPException(400, "Konec období je před začátkem")
    return reports.summary(db, company.id, date_from, date_to)


@router.get("/reports/suppliers")
def report_suppliers(
    date_from: date = Query(..., alias="from"),
    date_to: date = Query(..., alias="to"),
    company: Company = Depends(api_company),
    db: Session = Depends(get_db),
):
    """Všichni dodavatelé za období: klíč, název, IČO, počet dokladů, celkem, DPH."""
    return reports.suppliers(db, company.id, date_from, date_to)


@router.get("/reports/suppliers.pdf")
def report_suppliers_pdf(
    date_from: date = Query(..., alias="from"),
    date_to: date = Query(..., alias="to"),
    supplier: str | None = None,
    company: Company = Depends(api_company),
    db: Session = Depends(get_db),
):
    """PDF seznam přijatých dokladů seskupený podle dodavatelů (s parametrem supplier jen jeden dodavatel)."""
    pdf = reports.supplier_list_pdf(db, company, date_from, date_to, supplier)
    return Response(pdf, media_type="application/pdf",
                    headers={"Content-Disposition": f'inline; filename="doklady_dodavatele_{date_from}_{date_to}.pdf"'})
