import os
import tempfile
from datetime import date, datetime

tmp = tempfile.mkdtemp()
os.environ.setdefault("DATABASE_URL", f"sqlite:///{tmp}/r.db")
os.environ.setdefault("STORAGE_DIR", f"{tmp}/files")

from app import invoicing, reports  # noqa: E402
from app.db import Base, SessionLocal, engine  # noqa: E402
from app.models import Company, Customer, Document  # noqa: E402


def test_summary():
    Base.metadata.create_all(engine)
    db = SessionLocal()
    c = Company(name="Report s.r.o.", ico="26107031", bank_account="2000145399/0800")
    db.add(c); db.flush()

    def doc(status, total, ico, name, vat, d=date(2026, 10, 5)):
        db.add(Document(company_id=c.id, original_path="x", original_mime="image/jpeg", status=status,
                        total=total, supplier_ico=ico, supplier_name=name, vat_breakdown=vat, issue_date=d,
                        created_at=datetime(2026, 10, 6)))

    doc("checked", "1210.00", "25110161", "Kaufland", [{"rate": 21, "base": 1000, "vat": 210}])
    doc("extracted", "112.00", "25110161", "Kaufland", [{"rate": 12, "base": 100, "vat": 12}])
    doc("checked", "605.00", "27082440", "Alza", [{"rate": 21, "base": 500, "vat": 105}])
    doc("error", None, None, None, None)
    doc("checked", "9999.00", "27082440", "Alza", [], d=date(2026, 9, 30))  # mimo období
    cust = Customer(company_id=c.id, name="Odběratel")
    db.add(cust); db.commit()
    invoicing.create_invoice(db, c, cust, [{"description": "Služba", "unit_price": 2000, "vat_rate": 21}],
                             issue_date=date(2026, 10, 10))

    r = reports.summary(db, c.id, date(2026, 10, 1), date(2026, 10, 31))
    assert r["documents"]["count"] == 4 and r["documents"]["total"] == "1927.00"
    assert r["documents"]["to_check"] == 2  # 1× vytěženo + 1× chyba
    assert r["vat"]["received"] == [{"rate": 21, "base": "1500.00", "vat": "315.00"},
                                    {"rate": 12, "base": "100.00", "vat": "12.00"}]
    assert r["vat"]["output_total"] == "420.00" and r["vat"]["input_total"] == "327.00"
    assert r["vat"]["balance"] == "93.00"
    assert [s["name"] for s in r["suppliers"]] == ["Kaufland", "Alza"] and r["suppliers"][0]["count"] == 2
    assert r["invoices"] == {"count": 1, "total": "2420.00"}


def test_suppliers_list_and_pdf():
    db = SessionLocal()
    c = db.query(Company).filter(Company.name == "Report s.r.o.").first()
    rows = reports.suppliers(db, c.id, date(2026, 10, 1), date(2026, 10, 31))
    assert [(r["key"], r["count"], r["total"]) for r in rows] == [("25110161", 2, "1322.00"), ("27082440", 1, "605.00")]
    docs = reports.documents_in_period(db, c.id, date(2026, 10, 1), date(2026, 10, 31), "25110161")
    assert len(docs) == 2 and all(d.supplier_ico == "25110161" for d in docs)
    pdf = reports.supplier_list_pdf(db, c, date(2026, 10, 1), date(2026, 10, 31))
    assert pdf[:4] == b"%PDF"
    open(os.path.join(tmp, "seznam.pdf"), "wb").write(pdf)
