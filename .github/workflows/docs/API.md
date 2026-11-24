# API mezi mobilní aplikací a webovou aplikací

Mobilní aplikace **nikdy nepřistupuje k databázi přímo**. Všechno jde přes REST API
webové aplikace, která pracuje se svou PostgreSQL databází. Obě aplikace tak vidí
stejná data: doklad vyfocený v telefonu je hned na webu a úprava na webu je hned v telefonu.

Referenční implementace je v `backend/app/api/routes.py` (FastAPI). Interaktivní
dokumentaci s možností vyzkoušet každé volání najdete po spuštění na `/docs`.

- Všechny adresy začínají `/api`.
- JSON je v UTF-8.
- Částky se posílají jako **text** (`"1434.00"`), aby se neztrácela přesnost.
- Data se posílají ve formátu `RRRR-MM-DD`.
- Chyba vrací HTTP kód ≥ 400 a tělo `{"detail": "Česká zpráva pro uživatele"}`. Mobilní aplikace zprávu zobrazí.

## Kdo co dělá

| Mobilní aplikace (portál) | Webová aplikace |
|---|---|
| vyfotí a upraví doklad, nahraje originál + upravený sken | uloží soubory a záznam do databáze |
| zobrazí stav, náležitosti, originál i upravený sken | **vytěží doklad** (AI, ARES, kontroly) na pozadí |
| klient připíše poznámku / dotaz pro účetní | účetní opravuje a označuje „zkontrolováno“ |
| zobrazí přehledy (doklady, DPH, dodavatelé) | spočítá přehledy z databáze |
| vystavuje a odesílá faktury | čísluje, počítá DPH, generuje PDF, posílá e-maily, opakované faktury |

Mobilní aplikace náležitosti **neopravuje** a vytěžování nespouští. Jen čte stav
(`uploaded` → `processing` → `extracted` → `checked`, případně `error`) a obnovuje ho, dokud se doklad zpracovává.

## Klientský portál a pozvánky

Jeden server (webová aplikace) obsahuje portály všech klientů. Každý klient vidí jen svou firmu.
Adresa serveru je v mobilní aplikaci pevně (`AppConfig.SERVER_URL`), klient ji nikdy nezadává.

1. Účetní na webu založí klientovi přístup: `POST /api/invitations` (jen admin)
   `{"email": "jan@firma.cz", "name": "Jan Novák", "company_id": 7, "send_email": true}`
   → `{"code": "ABCD-EFGH", "link": "https://…/aktivace?code=ABCD-EFGH", "app_link": "multiodoklady://activate?code=ABCD-EFGH", "expires": "…", "emailed": true}`
2. Klient dostane e-mail s odkazem, QR kódem (`GET /api/invitations/{code}/qr.png`) a kódem.
   Odkaz vede na stránku `/aktivace`, která otevře aplikaci nebo nabídne její stažení.
3. V aplikaci (odkaz ji otevře sám, jinak volba „Mám pozvánku“) klient zadá kód a nastaví si heslo:
   `POST /api/activate {"code": "ABCD-EFGH", "password": "…"}` → stejná odpověď jako login (token, uživatel, firma).
   Kód je jednorázový a platí `INVITATION_DAYS` dní (výchozí 14). Velikost písmen ani pomlčka nehrají roli.
4. Příště už se klient přihlašuje jen e-mailem a heslem. Novou pozvánku (např. zapomenuté heslo) pošlete stejně jako v bodě 1.

## Přihlášení

| Hlavička | Význam |
|---|---|
| `Authorization: Bearer <token>` | token z `/api/auth/login` (JWT, platí 30 dní) |
| `X-Company-Id: <id>` | firma, se kterou se pracuje (u účetní s více klienty) |

- Klient (role `client`) vždy pracuje jen se svou firmou a hlavičku `X-Company-Id` server ignoruje.
- Admin / účetní (role `admin`) si firmu vybírá a server musí ověřit, že k ní má přístup.
- Při `401` se aplikace odhlásí a zobrazí přihlašovací obrazovku.

### POST /api/auth/login
```json
→ {"email": "jan@firma.cz", "password": "..."}
← {"token": "eyJ...", "user": {"id": 1, "email": "...", "name": "...", "role": "admin", "company_id": 1},
   "companies": [{"id": 1, "name": "Firma s.r.o.", "ico": "12345678", "dic": "CZ12345678", "vat_payer": true}]}
```

### GET /api/me
Stejná odpověď jako login, jen `token` je prázdný.

## Doklady

### POST /api/documents  (multipart/form-data)
| pole | typ | popis |
|---|---|---|
| `original` | soubor | **povinné** – originál: PDF ze skeneru (všechny strany), PDF z e-mailu, nebo fotka |
| `processed` | soubor | upravená 1. strana (JPEG) ze skeneru telefonu: oříznutá, srovnaná, s vyrovnaným světlem |
| `enhance` | bool | když `processed` chybí a originál je fotka, upraví ji server (OpenCV) |
| `mode` | `gray`/`bw`/`color` | režim serverové úpravy |
| `extract` | bool | (nepovinné) web může vytěžovat vždy, aplikace posílá `true` |
| `note` | text | poznámka pro účetní |

Odpověď `201` vrací objekt **Document** se `status: "processing"`. Aplikace se pak dotazuje
přes `GET /api/documents/{id}`, dokud se stav nezmění.

### Document (odpověď)
```json
{
  "id": 15, "company_id": 1, "created_at": "2026-10-17T09:12:00", "source": "mobile",
  "status": "uploaded | processing | extracted | checked | error", "error": null,
  "original_filename": "sken.pdf", "original_mime": "application/pdf", "has_processed": true,
  "doc_type": "faktura | uctenka | zalohova | dobropis | jine",
  "doc_number": "2026001", "variable_symbol": "2026001",
  "supplier_name": "...", "supplier_ico": "12345678", "supplier_dic": "CZ12345678",
  "supplier_address": "...", "supplier_account": "...",
  "customer_name": "...", "customer_ico": "...", "customer_dic": "...",
  "issue_date": "2026-10-01", "taxable_date": "2026-10-01", "due_date": "2026-10-15",
  "currency": "CZK", "total": "1210.00", "total_base": "1000.00", "total_vat": "210.00",
  "vat_breakdown": [{"rate": 21, "base": 1000, "vat": 210}],
  "payment_method": "prevod | hotove | karta | dobirka | jine", "reverse_charge": false,
  "confidence": 1.0, "extraction_method": "qr+pdf-text+ares",
  "checks": {"celkova_castka": true, "datum": true, "dodavatel": true, "soucet_dph": true, "cislo_nebo_vs": true},
  "note": null
}
```

| Volání | Popis |
|---|---|
| `GET /api/documents?status=&q=&limit=&offset=` | seznam (nejnovější první); `q` hledá v dodavateli, IČO a čísle dokladu |
| `GET /api/documents/{id}` | detail |
| `PATCH /api/documents/{id}` | aplikace posílá **jen** `{"note": "…"}` (poznámka / dotaz klienta) |
| `DELETE /api/documents/{id}` | aplikace nabízí jen u dokladů, které ještě nejsou `checked`. Web by to měl hlídat také. |
| `GET /api/documents/{id}/file/original` | originál (PDF/JPG/PNG) |
| `GET /api/documents/{id}/file/processed` | upravený obrázek (JPEG), `404` pokud není |

### Vytěžování (věc webové aplikace)

Mobilní aplikaci nezáleží na tom, jak web doklad vytěží. Potřebuje jen vyplněná pole objektu Document
a stav. Ukázka hotového řešení je v `backend/app/pipeline.py`: AI (Claude Haiku), ISDOC a QR kód
s předností, ARES pro název, adresu a DIČ dodavatele, pravidla jako nezávislá kontrola a kontrola součtů.
Text pro aplikaci („Zkontrolujte: …“) dávejte do pole `error`.

## Odběratelé

| Volání | Popis |
|---|---|
| `GET /api/ares/{ico}` | `{name, ico, dic, street, city, zip, country}` z ARES |
| `GET /api/customers` | seznam odběratelů firmy |
| `POST /api/customers` | `{name, ico, dic, street, city, zip, country, email, due_days, note}` |
| `PUT /api/customers/{id}` | úprava |

## Faktury vydané

| Volání | Popis |
|---|---|
| `GET /api/invoices` | seznam |
| `POST /api/invoices` | vystavení, viz níže |
| `GET /api/invoices/{id}` | detail včetně položek |
| `GET /api/invoices/{id}/pdf` | PDF faktury (s QR Platbou) |
| `POST /api/invoices/{id}/send` | `{email?, message?}` – odešle PDF e-mailem (bez e-mailu na adresu odběratele) |
| `POST /api/invoices/{id}/status/{paid\|cancelled\|issued\|sent}` | změna stavu |

```json
POST /api/invoices
{
  "customer_id": 3,
  "items": [{"description": "Vedení účetnictví", "quantity": "1", "unit": "měs",
             "unit_price": "4500", "vat_rate": 21}],
  "issue_date": "2026-10-01", "due_days": 14, "currency": "CZK",
  "reverse_charge": false, "payment_method": "prevod", "note": null,
  "send_to": "ucetni@zakaznik.cz"
}
```
Číslování (RRRRNNNN), výpočet DPH podle sazeb, přenesenou daňovou povinnost a neplátce DPH
řeší server, ne telefon. Pole `send_to` znamená „po vystavení rovnou odeslat“.

## Přehledy

### GET /api/reports/summary?from=2026-10-01&to=2026-10-31
Datum dokladu = DUZP, jinak datum vystavení, jinak datum nahrání. Do součtů jdou jen doklady
`extracted` a `checked`. Faktury se počítají podle DUZP a bez stornovaných.
```json
{
  "from": "2026-10-01", "to": "2026-10-31",
  "documents": {"count": 4, "total": "1927.00", "to_check": 2,
                "by_status": {"checked": 2, "extracted": 1, "error": 1}},
  "invoices": {"count": 1, "total": "2420.00"},
  "vat": {
    "received": [{"rate": 21, "base": "1500.00", "vat": "315.00"}, {"rate": 12, "base": "100.00", "vat": "12.00"}],
    "issued":   [{"rate": 21, "base": "2000.00", "vat": "420.00"}],
    "input_total": "327.00", "output_total": "420.00", "balance": "93.00"
  },
  "suppliers": [{"name": "Kaufland", "ico": "25110161", "count": 2, "total": "1322.00"}],
  "note": "Orientační přehled z vytěžených dokladů – nenahrazuje přiznání k DPH."
}
```
`balance` kladné = odhad DPH k odvodu, záporné = nadměrný odpočet. `suppliers` = 10 největších podle částky.

### Seznam dokladů podle dodavatele

| Volání | Popis |
|---|---|
| `GET /api/reports/suppliers?from=&to=` | všichni dodavatelé za období: `[{key, name, ico, count, total, vat}]`, seřazení podle částky |
| `GET /api/documents?supplier=<key>&from=&to=` | doklady jednoho dodavatele za období (`key` = IČO, bez IČO `nazev:<název malými>`) |
| `GET /api/reports/suppliers.pdf?from=&to=[&supplier=<key>]` | PDF seznam dokladů seskupený podle dodavatelů s mezisoučty (se `supplier` jen jeden dodavatel) |

Do počtů a součtů jdou jen doklady `extracted` a `checked`. V seznamu dokladů a v PDF jsou vidět všechny doklady se stavem, kromě chybných.

## Opakované faktury

| Volání | Popis |
|---|---|
| `GET /api/recurring` | šablony |
| `POST /api/recurring` | `{customer_id, name, interval_months, next_date, due_days, items, auto_send, email_to, reverse_charge, note}` |

Server každou hodinu vystaví faktury, jejichž `next_date` nastal. Pokud je `auto_send`
zapnuté, rovnou je odešle a posune `next_date` o `interval_months`.
