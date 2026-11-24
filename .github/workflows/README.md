# Doklady – mobilní aplikace (Android + iPhone), verze 2.0

Mobilní aplikace je **portál** k vaší webové aplikaci. Vyfotí doklad, upraví ho (ořez, srovnání perspektivy,
vyrovnání osvětlení) a uloží ho do webové aplikace. Zobrazí vytěžené náležitosti a přehledy a umí
vystavovat a odesílat faktury. **Vytěžuje webová aplikace.** Telefon nic nevytěžuje ani neopravuje.
S webem komunikuje přes API nad stejnou PostgreSQL databází (viz `docs/API.md`, oddíl „Kdo co dělá“).

```
mobile/    Kotlin Multiplatform + Compose Multiplatform – JEDEN kód pro Android i iOS
backend/   referenční API server (Python, FastAPI, PostgreSQL) – vzor pro vaši webovou aplikaci
docs/      API.md = smlouva mezi aplikacemi, postgres_schema.sql = tabulky
```

## Verze 2.1 – režim Kancelář a náhled PDF (portál 2.2)

- **Režim „Kancelář“** pro účetní a administrátora: Dnes k vyřízení (žádosti, zprávy, doplněné podklady, datová schránka), Klienti (hledání, klepnutím do účtu klienta s pruhem „Prohlížíte jako účetní · Zpět do kanceláře“), Zprávy s rychlými odpověďmi, Sezóna přiznání s připomenutím vybraným, Více (žádosti – přijmout / dotaz / odmítnout, datová schránka – zařadit ke klientovi s lhůtou).
- **Náhled PDF přímo v detailu dokladu** (strany jako obrázek ze serveru, listování stránkami); tlačítko „Otevřít PDF“ zůstává.
- Doklad nahraný k platbě: při jisté shodě ho portál potvrdí sám (stav platby „Spárováno“), jinak čeká na účetní.

## Verze 2.0 – do obchodů, upozornění, „Co se letos změnilo?“ (portál 2.0)

- **Aktualizace:** při spuštění aplikace zjistí nejnovější verzi z portálu; nabídne aktualizaci, příliš stará verze se zastaví s výzvou (Správa → Mobilní aplikace).
- **Upozornění v telefonu** (Android, Firebase Cloud Messaging): nové úkoly, chybějící doklady, zprávy, termíny, přiznání, dopisy z úřadů – i při zavřené aplikaci. V menu *Upozornění* si klient vybere druhy. Zapne se, až bude v GitHubu tajná proměnná `GOOGLE_SERVICES_JSON` a na serveru klíč Firebase.
- **„Co se letos změnilo?“** při novém roce podkladů; **faktura za zpracování** u přiznání (PDF s QR).
- **Google Play:** podepsaný balík (.aab) se sestaví, když jsou v GitHubu tajné proměnné `KEYSTORE_*` – postup a texty do obchodu v `docs/google-play.md`.

## Verze 1.9 – jen přiznání, roční zúčtování, podání a platba (portál 1.17)

- **Omezený přístup:** klient „jen přiznání“ / „roční zúčtování“ vidí po přihlášení jen *Moje přiznání* (stav, co chybí, Doplnit podklady), menu Podklady · Napsat účetní · Hotová přiznání · Zabezpečení · Odhlásit a nabídku „Mám zájem“ o celý rok (žádost schvaluje kancelář).
- **Žádost:** třetí volba *Roční zúčtování* se 3 otázkami; když nejde, tlačítko „Přepnout na daňové přiznání“. U přiznání otázky „Máte datovou schránku?“ a „Kdo přiznání podá?“.
- **Podklady:** „Přidat jiný doklad“, „Přidat příjem nebo slevu“, doplnit jde i po odeslání; mazat soubor jen do převzetí účetní; ZIP všech podkladů.
- **Výsledek:** časová osa, přeplatek/doplatek, PDF; volba „Podejte za mě datovou schránkou“ (ID schránky, plná moc PDF k podpisu a vyfocení) nebo „Podám sám“ (XML pro Moje daně); „Souhlasím (a zaplatit)“ – platba Comgate v prohlížeči, když ji kancelář zapne. Roční zúčtování: „Odevzdáno zaměstnavateli“.
- **Napsat účetní:** jednoduché zprávy.

## Verze 1.8 – noví klienti a podklady k přiznání (portál 1.16)

- Úvodní obrazovka bez přihlášení: **Jsem klient** (přihlášení) / **Chci se stát klientem** (žádost). Na přihlášení odkaz na žádost.
- Žádost: zájem (celý rok / jen přiznání) → kontakt a IČO s načtením z ARES → u celého roku obor, popis, provozovna, web, zaměstnanci, vedení, doklady, DPH → u přiznání krátký dotazník (situace, zaměstnání, dohody DPP/DPČ každá zvlášť, podnikání, slevy; všude „Nevím“) → shrnutí se seznamem podkladů → ověření e-mailu 6místným kódem → odesláno. Doklady se v žádosti neposílají.
- Limity a texty upozornění bere aplikace z portálu (Správa → Pravidla přiznání), nejsou v aplikaci natvrdo.
- **Podklady k přiznání:** karta na Přehledu (rok, kolik hotovo, termín). Položky podle příjmů; u každé **Vyfotit** (systémový skener, víc stran → jedno PDF) nebo **Nahrát soubor** (PDF od brokera, faktury). Po vyfocení **Kontrola fotek**: náhled stran, upozornění na rozmazanou stranu, vyfotit znovu / smazat / přidat stranu. U pronájmu a ostatních příjmů celková částka, u dětí text. Odeslat účetní (i neúplné).

## Verze 1.7 – „Za co to je?“: druh dokladu podle klienta (portál 1.15)

- Při nahrání nákupu jeden řádek štítků s nejčastějšími druhy (PHM, materiál, služby…) a „Jiné…“ (spodní panel s dlaždicemi a hledáním, „Nevím – nechat na účetní“). Nepovinné.
- Daňová evidence / paušál: navíc „Soukromé (nedaňové)“; účetnictví: „Pohledávky a závazky vůči společníkům“. Seznam řídí kancelář v portálu.
- V detailu dokladu řádek „Druh“: návrh portálu (podle dodavatele / vytěžení) přerušovaně, klepnutím potvrdit nebo změnit; po kontrole účetní zamčeno. V balíčku Mini „zařadí účetní · výběr v balíčku Basic“.

## Verze 1.6 – fakturace: účet, QR, úhrada, objednávka, středisko a zakázka; balíčky; AI asistent (portál 1.14)

- **Údaje firmy** (menu): číslo účtu, IBAN, splatnost, e-mail, adresa, plátce DPH – stejné jako na webu; kontrola tvaru účtu, aby šel vytvořit QR.
- **Nová faktura:** způsob úhrady (převod / hotově / kartou / dobírka), přepínač **QR kód pro platbu**, upozornění na chybějící účet s odkazem do Údajů firmy, **číslo objednávky** (tiskne se), **středisko a zakázka** z číselníku firmy (jen pro účetnictví), DUZP u plátce DPH. Detail faktury je ukazuje.
- **Balíčky:** aplikace je ve všech balíčcích; v menu jsou funkce podle balíčku, chybějící se štítkem „Expert“ apod. a vysvětlením. Balíček se načítá při každém spuštění (změna na webu se projeví bez odhlášení). Opakované faktury jen s rozšířenou fakturací.
- **Asistent** (balíček Expert): otázky na vlastní čísla, návrhy dalších otázek, právní otázky jedním klepnutím účetní; konverzace je společná s webem.

## Verze 1.5 – nový vzhled, banka, účtenky, otisk prstu a přístupové klíče (portál 1.13)

- **Vzhled podle portálu:** logo Multio CZ, ikona na ploše stejná jako u portálu („D_“ na zelené), jedna hlavní barva, karty, štítky stavu. Dole Přehled · Doklady · **Vyfotit** · Banka · Úkoly; Faktury, Kniha jízd, Přehledy, Daň z příjmů, Zabezpečení a Nastavení v menu vpravo nahoře.
- **Přehled:** K doplnění / U účetní / Hotovo, „Co od vás potřebujeme“ (platby bez dokladu, úkoly, čitelnější kopie), poslední doklady.
- **Vyfotit:** volba Účtenky (každá fotka = samostatný doklad, jednostránkové PDF), Vícestránkový doklad (jedno PDF), Soubor. U účtenek kontrola před odesláním: náhledy, upozornění na možná rozmazanou fotku (orientační), vyfotit znovu, smazat, spojit s předchozí, nákup/prodej, poznámka; odesílá se přes frontu v telefonu. PDF se skládá v telefonu bez přepočtu fotky (`data/Pdf.kt`).
- **Banka:** pohyby po měsících, Bez dokladu / Vyřešené, detail platby (datum, VS, protiúčet, historie), vyfotit nebo vybrat doklad k platbě (rovnou se připojí), rychlé odpovědi kanceláře, nahrání výpisu ze souboru. Fio token v aplikaci není – propojení nastavuje kancelář.
- **Zabezpečení:** odemykání otiskem prstu (Android: BiometricPrompt + klíč v Android Keystore, kterým je zašifrované přihlášení; zamyká se po spuštění a po minutě na pozadí) a **přístupové klíče** (Credential Manager; stejné klíče jako na webu). Klient si vybere jedno, obojí, nebo nic. Přihlášení i uživatelským jménem.
- iPhone: Face ID/Touch ID jen jako zámek (token zatím bez Keychainu), přístupové klíče zatím ne (potřeba účet Apple Developer).
- **Stav:** napsáno, nesestaveno – sestavení na GitHubu; přihlášení klíčem potřebuje na serveru `ANDROID_APP_SHA256` (vypíše ho sestavení v Summary).

## Verze 1.4 – QR platba, kniha jízd (portál 1.12)

- **QR platba:** po naskenování faktury telefon sám přečte QR kód pro platbu (Android ML Kit, iPhone Vision – v telefonu, bez internetu) a ukáže částku, VS a splatnost. Text QR se pošle s dokladem (`qr`); portál po vytěžení doplní prázdný účet, VS, splatnost a částku.
- **Kniha jízd** (Nastavení → Kniha jízd, jen s modulem edice `trips`): jízdy po měsících se součtem služebních a soukromých km, **nová jízda** (vozidlo, datum, časy, řidič, účel, trasa se zastávkami, výpočet trasy na serveru s potvrzením, km nebo stavy tachometru) a **stav tachometru z fotky** (portál přečte číslo, řidič potvrdí; porovnání se zapsanými jízdami).
- **Odhlášení** zneplatní token i na serveru.
- Sestavení na GitHubu: APK po každém nahrání změn, chyby překladu v souhrnu běhu, stálý ladicí podpis (aktualizace bez odinstalace). Kontrola iOS jen ručně.
- Bez GPS záznamu jízd a bez offline fronty jízd (jízda se ukládá hned, bez signálu ukáže chybu).
- **Stav:** napsáno, zatím nesestaveno – první sestavení na GitHubu může najít chyby překladu.

## Verze 1.3 – vyfocení potvrzení o příjmech a přehledu OSVČ (portál 0.52)

- V průvodci přiznáním tlačítko **Vyfotit doklad** (systémový skener). Portál přečte potvrzení o zdanitelných příjmech nebo přehled OSVČ (ČSSZ / ZP) a údaje ukáže v dialogu ke kontrole a opravě; po potvrzení se rozepsané údaje uloží a přečtené se vloží do přiznání (zaměstnání, případně § 7).
- API: `POST /api/tax/returns/<id>/photo` (multipart `photo`, 1–4 stran), `POST /api/tax/returns/<id>/photo/<photo>/apply`.
- Kód není zkompilovaný – před vydáním sestavit a vyzkoušet na zařízení.

## Verze 1.2 – přiznání k dani z příjmů (portál 0.49)

- Nastavení → **Daň z příjmů – přiznání**: průvodce (osobní údaje, zaměstnání z potvrzení, podnikání se skutečnými výdaji nebo paušálem, nájem, kapitálové příjmy, odpočty, slevy a děti, zálohy). Výpočet dělá portál (`/api/tax/returns`), telefon ukáže doplatek / přeplatek, upozornění a nasdílí **XML pro Moje daně** a **PDF s výpočtem**.
- Nové platformní volání `shareFile` (Android ACTION_SEND, iPhone sdílecí list).
- Kniha jízd (GPS záznam, offline fronta, Bluetooth) v aplikaci zatím není – serverová část je hotová (`/api/vehicles`, `/api/trips`, `/api/odometer`).
- **Stav:** napsáno, nesestaveno – při sestavení v Android Studiu / Xcode se mohou objevit drobné chyby překladu.

## Verze 1.1 – napojení na portál Multio

Aplikace míří na portál `https://doklady.ucetnictvitabor.cz` (AppConfig.kt) a používá jeho API (v portálu `MOBILNI-API.md`, verze 0.32).
Referenční server v `backend/` už není potřeba – slouží jen jako vzor.

- ID dokladů a faktur jsou text (UUID portálu).
- **Úkoly:** požadavky od účetní (výpis z banky, chybějící doklad…) – sken nebo soubor a odpověď, odešle se účetní.
- **Chybějící doklady:** nad seznamem dokladů karta „K fakturám chybí N dokladů“; u každého jde rovnou vyfotit dodací list, objednávku nebo zálohu (druh se předvyplní).
- **Cesta faktury** u detailu dokladu: objednávka → zálohová faktura → daňový doklad k záloze → dodací list → faktura.
- **Nahrání:** nákup / prodej, druh dokladu (nebo automaticky), středisko a zakázka z číselníku firmy.
- **Zapomenuté heslo** na přihlašovací obrazovce (e-mail s odkazem; bez e-mailu odkaz vytvoří účetní v portálu).
- **Faktury** přes fakturaci portálu; záložka se skryje, když edice fakturaci nemá. Opakování jen měsíčně / čtvrtletně / ročně, faktury ze šablony odběrateli zatím odesíláte sami.
- Aktivace kódem z pozvánky v portálu zatím není – heslo si klient nastaví odkazem z e-mailu.

**Stav:** kód je napsaný, ale aplikace zatím **nebyla sestavena ani spuštěna** (prostředí, kde vznikla, nemá knihovny Androidu). Při prvním sestavení v Android Studiu se mohou objevit drobné chyby překladu – pošlete je a opravím je.

## Jak aplikace upravuje fotku dokladu

| Krok | Android | iPhone |
|---|---|---|
| Hledání okrajů papíru živě v náhledu, automatické vyfocení | Google ML Kit Document Scanner | Apple VisionKit |
| Ruční doladění rohů, srovnání perspektivy, ořez | ✔ | ✔ |
| Vyrovnání osvětlení, odstranění stínů, černobílý režim | ✔ (filtry ve skeneru) | ✔ (filtry ve skeneru) |
| Více stran → PDF | ✔ | ✔ (PDFKit) |

Vše běží přímo v telefonu, offline a bez Pythonu. Fotky vybrané z galerie (bez skeneru)
upraví server pomocí OpenCV (`backend/app/imaging.py`): najde papír, srovná perspektivu,
odstraní stíny a zvýší kontrast.

Doklad se nejdřív uloží do telefonu a teprve potom odešle. Když není signál, zůstane
ve frontě a odešle se při dalším otevření seznamu.

## Vytěžování (ve webové aplikaci)

`backend/app/pipeline.py` je hotová ukázka, kterou můžete převzít do webu: AI (Claude Haiku 5.5, cca 1 Kč
za 100 dokladů), ISDOC a QR kód mají přednost, ARES doplní název, adresu a DIČ dodavatele, pravidla
a kontrola součtů označí doklady ke kontrole. Režim nastavíte přes `EXTRACTION_MODE` (`ai` / `hybrid` / `rules`).
OCR na serveru (pro režim bez AI) potřebuje `sudo apt install tesseract-ocr tesseract-ocr-ces`.

## Jak se klient připojí (klientský portál)

Klient nic nenastavuje. Adresa serveru je v aplikaci pevně (`mobile/.../AppConfig.kt`, na serveru `PUBLIC_URL`).

1. Na webu založíte klientovi přístup. Web mu pošle **pozvánku e-mailem** s odkazem, QR kódem a kódem `ABCD-EFGH`.
2. Klient si stáhne aplikaci, klepne na odkaz (nebo zadá kód přes „Mám pozvánku“) a **nastaví si heslo**.
3. Hotovo. Vidí jen svůj portál (svou firmu). Příště se přihlásí e-mailem a heslem.

Účetní se přihlásí stejnou aplikací a firmy klientů přepíná v záhlaví.

## Funkce aplikace

- přihlášení e-mailem a heslem, aktivace pozvánkou (odkaz / kód), přepínání firem pro účetní
- **Doklady:** skenování, výběr fotky nebo PDF, sdílení z jiné aplikace (Android: Sdílet → Doklady),
  fronta pro odeslání bez signálu, seznam s filtry a hledáním, detail jen ke čtení (originál a upravený sken
  s přiblížením, náležitosti, rozpis DPH, stav zpracování), **poznámka nebo dotaz pro účetní**,
  smazání nezkontrolovaného dokladu
- **Faktury:** nová faktura (odběratel z ARES, položky, sazby DPH, přenesená daňová povinnost),
  PDF s QR Platbou, odeslání e-mailem, uhrazeno/storno, **opakované faktury**
  (měsíc/čtvrtletí/půlrok/rok s automatickým odesláním). Odběratelé a jejich e-maily se ukládají.
- **Přehledy** za měsíc, čtvrtletí nebo rok: počet dokladů, náklady, kolik čeká na kontrolu;
  DPH podle sazeb (vstup z dokladů × výstup z faktur, odhad k odvodu); náklady podle dodavatelů.
  **Seznam faktur podle dodavatele:** všichni dodavatelé za období s vyhledáváním, po klepnutí jejich doklady,
  a **PDF seznam** (všichni dodavatelé s mezisoučty, nebo jeden dodavatel) k odeslání nebo vytištění

## Sestavení – Android (Google Play)

1. Nainstalujte **Android Studio** (aktuální verzi).
2. File → Open → složka `mobile`. Studio stáhne Gradle a knihovny. Pokud nabídne aktualizaci verzí, potvrďte ji.
3. Připojte telefon (vývojářský režim + USB ladění) a spusťte ▶ `composeApp`.
4. Pro Google Play: Build → Generate Signed App Bundle (.aab), založte si klíč a **uložte ho bezpečně**.
   Vývojářský účet Google Play stojí jednorázově 25 USD.

## Sestavení – iPhone (App Store)

Je potřeba **Mac** s Xcode a účet Apple Developer (99 USD ročně).
1. Na Macu otevřete `mobile/iosApp/iosApp.xcodeproj`.
2. Do `mobile/iosApp/Configuration/Config.xcconfig` vyplňte `TEAM_ID` (najdete na developer.apple.com).
3. Spusťte ▶ na připojeném iPhonu. Skener na simulátoru nefunguje, protože nemá fotoaparát.
4. Product → Archive → nahrání do App Store Connect → TestFlight → odeslání ke schválení.

## Napojení na vaši webovou aplikaci (Python + PostgreSQL)

Do webové aplikace je potřeba doplnit API podle `docs/API.md`:

- **Web je ve FastAPI:** můžete převzít `backend/app/api/routes.py` téměř beze změny. Napojte `get_db`
  na vaši databázovou session a modely v `backend/app/models.py` sjednoťte s vašimi tabulkami.
- **Web je v Django nebo Flasku:** napište stejné adresy se stejným JSON. Logiku (vytěžení přes Claude,
  úpravu obrazu, PDF faktury, ARES, QR Platbu) převezmete z modulů `backend/app/*.py`, které na
  frameworku nezávisí.

Referenční server spustíte pro testování aplikace takto:
```bash
cd backend
pip install -r requirements.txt
cp .env.example .env        # vyplnit ANTHROPIC_API_KEY, SMTP…
uvicorn app.main:app --host 0.0.0.0 --port 8000
# dokumentace API: http://localhost:8000/docs   testy: pytest
```
Telefon na stejné Wi-Fi se připojí na `http://<IP počítače>:8000`. Android posílá nešifrované
`http` jen ve vývoji. Pro provoz musí server běžet na **https**.

## Bezpečnost

- Přístup k databázi má jen server. Telefon zná jen svůj přihlašovací token.
- Server u každého volání ověřuje, že doklad nebo faktura patří firmě přihlášeného uživatele.
- `SECRET_KEY` nastavte na dlouhý náhodný řetězec. API klíč ke Claude je jen na serveru.
