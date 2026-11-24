import os
import tempfile

tmp = tempfile.mkdtemp()
os.environ.setdefault("DATABASE_URL", f"sqlite:///{tmp}/i.db")
os.environ.setdefault("STORAGE_DIR", f"{tmp}/files")
os.environ["RECURRING_ENABLED"] = "0"

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402


def test_invite_and_activate():
    with TestClient(app) as c:
        adm = c.post("/api/auth/login", json={"email": "admin@example.cz", "password": "admin"}).json()
        h = {"Authorization": f"Bearer {adm['token']}"}
        company_id = adm["companies"][0]["id"]

        r = c.post("/api/invitations", headers=h, json={"email": "Klient@Firma.cz", "name": "Jan", "send_email": False,
                                                         "company_id": company_id})
        assert r.status_code == 201, r.text
        inv = r.json()
        assert inv["email"] == "klient@firma.cz" and len(inv["code"]) == 9 and inv["link"].endswith(inv["code"])
        assert inv["app_link"].startswith("multiodoklady://activate?code=")
        assert c.get(f"/api/invitations/{inv['code']}/qr.png", headers=h).content[:4] == b"\x89PNG"

        page = c.get(f"/aktivace?code={inv['code']}")
        assert page.status_code == 200 and inv["code"] in page.text and "multiodoklady://" in page.text

        # klient opíše kód malými písmeny a bez pomlčky
        assert c.post("/api/activate", json={"code": inv["code"], "password": "kratke"}).status_code == 400
        r = c.post("/api/activate", json={"code": inv["code"].replace("-", "").lower(), "password": "tajneheslo1"})
        assert r.status_code == 200, r.text
        me = r.json()
        assert me["user"]["role"] == "client" and [x["id"] for x in me["companies"]] == [company_id]
        # kód je jednorázový
        assert c.post("/api/activate", json={"code": inv["code"], "password": "tajneheslo1"}).status_code == 404
        # klient se pak normálně přihlásí, ale pozvánky posílat nemůže
        tok = c.post("/api/auth/login", json={"email": "klient@firma.cz", "password": "tajneheslo1"}).json()["token"]
        r = c.post("/api/invitations", headers={"Authorization": f"Bearer {tok}"}, json={"email": "x@y.cz"})
        assert r.status_code == 403
