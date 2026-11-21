import io
import os
import tempfile

tmp = tempfile.mkdtemp()
os.environ["DATABASE_URL"] = f"sqlite:///{tmp}/t.db"
os.environ["STORAGE_DIR"] = f"{tmp}/files"
os.environ["RECURRING_ENABLED"] = "0"
os.environ["ANTHROPIC_API_KEY"] = ""

import cv2  # noqa: E402
import numpy as np  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app import invoicing  # noqa: E402
from app.main import app  # noqa: E402


def fake_photo() -> bytes:
    """Bílý „papír“ s textem, natočený v perspektivě na tmavém stole se stínem."""
    paper = np.full((1100, 800, 3), 245, np.uint8)
    for i in range(20):
        cv2.putText(paper, f"Polozka {i}  123,00 Kc", (60, 80 + i * 48), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (20, 20, 20), 2)
    bg = np.full((1600, 1300, 3), 60, np.uint8)
    src = np.float32([[0, 0], [800, 0], [800, 1100], [0, 1100]])
    dst = np.float32([[250, 180], [1050, 260], [1000, 1420], [200, 1350]])
    m = cv2.getPerspectiveTransform(src, dst)
    warped = cv2.warpPerspective(paper, m, (1300, 1600))
    mask = cv2.warpPerspective(np.full((1100, 800), 255, np.uint8), m, (1300, 1600))
    bg[mask > 0] = warped[mask > 0]
    shadow = np.tile(np.linspace(0.55, 1.0, 1300), (1600, 1))[..., None]
    out = (bg * shadow).astype(np.uint8)
    return cv2.imencode(".jpg", out)[1].tobytes()


def test_flow():
    with TestClient(app) as c:
        r = c.post("/api/auth/login", json={"email": "admin@example.cz", "password": "admin"})
        assert r.status_code == 200, r.text
        h = {"Authorization": f"Bearer {r.json()['token']}"}

        r = c.post("/api/documents", headers=h, files={"original": ("u.jpg", io.BytesIO(fake_photo()), "image/jpeg")},
                   data={"extract": "false"})
        assert r.status_code == 201, r.text
        d = r.json()
        assert d["has_processed"] and d["processing_info"]["cropped"] is True
        assert c.get(f"/api/documents/{d['id']}/file/processed", headers=h).status_code == 200

        r = c.patch(f"/api/documents/{d['id']}", headers=h, json={"supplier_name": "Kaufland", "total": "123.50"})
        assert r.json()["supplier_name"] == "Kaufland"

        r = c.post(f"/api/documents/{d['id']}/extract", headers=h)
        assert r.json()["status"] == "error"  # bez API klíče

        cust = c.post("/api/customers", headers=h, json={"name": "Odběratel a.s.", "ico": "12345678",
                                                           "dic": "CZ12345678", "email": "o@example.cz"}).json()
        inv = c.post("/api/invoices", headers=h, json={"customer_id": cust["id"], "items": [
            {"description": "Vedení účetnictví", "unit_price": "1000", "vat_rate": 21},
            {"description": "Kniha", "quantity": 2, "unit_price": "100", "vat_rate": 12}]}).json()
        assert inv["total"] == "1434.00", inv
        pdf = c.get(f"/api/invoices/{inv['id']}/pdf", headers=h)
        assert pdf.content[:4] == b"%PDF"
        assert c.get("/api/documents", headers=h).json()[0]["id"] == d["id"]
        rep = c.get("/api/reports/summary", headers=h, params={"from": "2026-01-01", "to": "2030-12-31"})
        assert rep.status_code == 200 and rep.json()["invoices"]["count"] == 1, rep.text
        r = c.patch(f"/api/documents/{d['id']}", headers=h, json={"note": "Dotaz: patří na zakázku 12?"})
        assert r.json()["note"].startswith("Dotaz")


def test_iban():
    assert invoicing.czech_account_to_iban("19-2000145399/0800") == "CZ6508000000192000145399"
