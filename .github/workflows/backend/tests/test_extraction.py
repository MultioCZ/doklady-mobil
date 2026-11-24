"""Vytěžování bez AI: PDF s textem + QR Platba, ISDOC v PDF, QR Faktura, fotka účtenky (OCR)."""
import io
import os
import tempfile
from datetime import date

tmp = tempfile.mkdtemp()
os.environ.setdefault("DATABASE_URL", f"sqlite:///{tmp}/x.db")
os.environ.setdefault("STORAGE_DIR", f"{tmp}/files")
os.environ["ANTHROPIC_API_KEY"] = ""

import cv2  # noqa: E402
import numpy as np  # noqa: E402
import pytest  # noqa: E402

from app import ares, invoicing, rules, sources, storage  # noqa: E402
from app.db import Base, SessionLocal, engine  # noqa: E402
from app.models import Company, Customer, Document  # noqa: E402
from app.pipeline import extract_document  # noqa: E402

FAKE_ARES = {
    "27082440": {"name": "Alza.cz a.s.", "ico": "27082440", "dic": "CZ27082440",
                 "address": "Jankovcova 1522/53, Holešovice, 17000 Praha 7"},
    "25110161": {"name": "Kaufland Česká republika v.o.s.", "ico": "25110161", "dic": "CZ25110161",
                 "address": "Bělohorská 2428/203, Břevnov, 16900 Praha 6"},
}


@pytest.fixture(autouse=True)
def no_network(monkeypatch):
    monkeypatch.setattr(ares, "_fetch", lambda ico: FAKE_ARES.get(ico))
    ares._cache.clear()


@pytest.fixture
def db():
    Base.metadata.create_all(engine)
    s = SessionLocal()
    yield s
    s.close()


def make_doc(db, company, data: bytes, mime: str, processed: bytes | None = None, ocr_text=None) -> Document:
    d = Document(company_id=company.id, original_mime=mime, original_path=storage.save(company.id, data, mime, "original"),
                 ocr_text=ocr_text)
    if processed:
        d.processed_path = storage.save(company.id, processed, "image/jpeg", "processed")
    db.add(d)
    db.commit()
    return d


def test_ico_checksum():
    assert rules.ico_valid("25110161") and rules.ico_valid("27082440") and not rules.ico_valid("12345678")


def test_amounts():
    assert [str(a) for a in rules.amounts("Celkem 1 284,90 Kč a 1.284,90 a 99.50")] == ["1284.90", "1284.90", "99.50"]


def test_pdf_invoice_text_and_qr(db):
    """Faktura vystavená jiným systémem (PDF s textem a QR Platbou) → bez AI."""
    supplier = Company(name="Alza.cz a.s.", ico="27082440", dic="CZ27082440", street="Jankovcova 1522/53",
                       city="Praha 7", zip="170 00", bank_account="2000145399/0800")
    client = Company(name="FoodTab s.r.o.", ico="26107031")
    db.add_all([supplier, client]); db.flush()
    cust = Customer(company_id=supplier.id, name=client.name, ico=client.ico, dic="CZ26107031")
    db.add(cust); db.commit()
    inv = invoicing.create_invoice(db, supplier, cust, [
        {"description": "Notebook", "unit_price": 20000, "vat_rate": 21},
        {"description": "Kniha", "unit_price": 500, "vat_rate": 12},
    ], issue_date=date(2026, 10, 5))
    pdf = invoicing.invoice_pdf(inv)

    doc = make_doc(db, client, pdf, "application/pdf")
    extract_document(doc, client)
    raw = doc.raw_extraction
    assert "qr" in raw["methods"] and "pdf-text" in raw["methods"] and "ai" not in raw["methods"]
    assert doc.status == "extracted", doc.error
    assert doc.supplier_ico == "27082440" and doc.supplier_name == "Alza.cz a.s."
    assert doc.customer_ico == "26107031"
    assert str(doc.total) == "24760.00"
    assert doc.issue_date == date(2026, 10, 5) and doc.due_date == date(2026, 10, 19)
    assert doc.variable_symbol == inv.variable_symbol
    rates = {r["rate"]: (r["base"], r["vat"]) for r in doc.vat_breakdown}
    assert rates[21] == (20000.0, 4200.0) and rates[12] == (500.0, 60.0)
    assert all(raw["checks"].values()), raw["checks"]


def test_isdoc_in_pdf(db):
    from pypdf import PdfWriter
    client = Company(name="Klient", ico="26107031")
    db.add(client); db.commit()
    xml = b"""<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="http://isdoc.cz/namespace/2013" version="6.0.1">
 <DocumentType>1</DocumentType><ID>FV2026-0042</ID><IssueDate>2026-09-30</IssueDate><TaxPointDate>2026-09-30</TaxPointDate>
 <LocalCurrencyCode>CZK</LocalCurrencyCode>
 <AccountingSupplierParty><Party><PartyIdentification><ID>25110161</ID></PartyIdentification>
  <PartyName><Name>Kaufland</Name></PartyName><PartyTaxScheme><CompanyID>CZ25110161</CompanyID></PartyTaxScheme></Party></AccountingSupplierParty>
 <TaxTotal><TaxSubTotal><TaxableAmount>1000</TaxableAmount><TaxAmount>210</TaxAmount>
  <TaxCategory><Percent>21</Percent></TaxCategory></TaxSubTotal></TaxTotal>
 <LegalMonetaryTotal><PayableAmount>1210</PayableAmount></LegalMonetaryTotal>
 <PaymentMeans><Payment><Details><PaymentDueDate>2026-10-14</PaymentDueDate><ID>123456789</ID><BankCode>0100</BankCode>
  <VariableSymbol>20260042</VariableSymbol></Details></Payment></PaymentMeans>
</Invoice>"""
    w = PdfWriter()
    w.add_blank_page(595, 842)
    w.add_attachment("faktura.isdoc", xml)
    buf = io.BytesIO(); w.write(buf)
    doc = make_doc(db, client, buf.getvalue(), "application/pdf")
    extract_document(doc, client)
    assert "isdoc" in doc.raw_extraction["methods"]
    assert doc.doc_number == "FV2026-0042" and str(doc.total) == "1210.00" and doc.variable_symbol == "20260042"
    assert doc.supplier_account == "123456789/0100" and doc.status == "extracted"


def test_qr_faktura_sid():
    sid = "SPD*1.0*ACC:CZ6508000000192000145399*AM:1210.00*CC:CZK*X-VS:2026001*DT:20261015*X-INV:SID%2A1.0%2AID:2026001%2ADD:20261001%2AAM:1210.00%2AINI:25110161%2AVII:CZ25110161%2ATB0:1000.00%2AT0:210.00"
    out = sources.parse_qr([sid])
    assert out["supplier_ico"] == "25110161" and out["doc_number"] == "2026001" and out["issue_date"] == "2026-10-01"
    assert out["vat_breakdown"] == [{"rate": 21, "base": 1000.0, "vat": 210.0}] and out["due_date"] == "2026-10-15"


def receipt_image() -> bytes:
    lines = ["KAUFLAND", "Kaufland Ceska republika v.o.s.", "Prazska 1745, 390 01 Tabor",
             "ICO: 25110161  DIC: CZ25110161", "", "Pecivo 6x            23,40", "Mleko 1l             21,90",
             "Kava 250g           119,90", "", "CELKEM            164,20", "Platba kartou     164,20", "",
             "DPH 12 %  146,61   17,59", "", "Uct:71862    16.10.2026 14:32"]
    img = np.full((60 + 46 * len(lines), 900), 255, np.uint8)
    for i, t in enumerate(lines):
        cv2.putText(img, t, (40, 60 + i * 46), cv2.FONT_HERSHEY_SIMPLEX, 1.05, 0, 2, cv2.LINE_AA)
    return cv2.imencode(".jpg", img)[1].tobytes()


@pytest.mark.skipif(not sources.ocr_available(), reason="Tesseract není nainstalovaný")
def test_receipt_photo_ocr(db):
    client = Company(name="Klient", ico="26107031")
    db.add(client); db.commit()
    jpg = receipt_image()
    doc = make_doc(db, client, jpg, "image/jpeg", processed=jpg)
    extract_document(doc, client)
    assert "ocr-server" in doc.raw_extraction["methods"], doc.raw_extraction
    assert doc.doc_type == "uctenka" and doc.supplier_ico == "25110161"
    assert doc.supplier_name == "Kaufland Česká republika v.o.s."  # z ARES
    assert str(doc.total) == "164.20" and doc.issue_date == date(2026, 10, 16)
    assert doc.doc_number == "71862" and doc.payment_method == "karta"
    assert doc.vat_breakdown == [{"rate": 12, "base": 146.61, "vat": 17.59}]
    assert doc.status == "extracted" and all(doc.raw_extraction["checks"].values())


def test_phone_ocr_text_used(db):
    """Text přečtený v telefonu má přednost – server pak OCR nedělá."""
    client = Company(name="Klient", ico="26107031")
    db.add(client); db.commit()
    text = "ALBERT\nAlbert Česká republika\nIČO 44012373 DIČ CZ44012373\nCELKEM 356,40\nHotově 400,00\n" \
           "DPH 12% 318,21 38,19\nÚčtenka číslo: 03706\n14.10.2026 18:02"
    doc = make_doc(db, client, receipt_image(), "image/jpeg", ocr_text=text)
    extract_document(doc, client)
    assert "ocr-telefon" in doc.raw_extraction["methods"] and "ocr-server" not in doc.raw_extraction["methods"]
    assert doc.supplier_ico == "44012373" and str(doc.total) == "356.40" and doc.doc_number == "03706"
    assert doc.payment_method == "hotove" and doc.status == "extracted"


def test_unreadable_without_ai(db):
    client = Company(name="Klient", ico="26107031")
    db.add(client); db.commit()
    blank = cv2.imencode(".jpg", np.full((400, 300), 255, np.uint8))[1].tobytes()
    doc = make_doc(db, client, blank, "image/jpeg", processed=blank)
    extract_document(doc, client)
    assert doc.status == "error" and doc.error
