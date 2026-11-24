# Podklady pro Google Play (Doklady – Multio CZ)

Vše zkontrolujte a upravte – texty jsou návrh.

## Název aplikace (max. 30 znaků)
Doklady – Multio CZ

## Krátký popis (max. 80 znaků)
Vyfoťte doklad, zbytek zařídí vaše účetní. Banka, úkoly, faktury, přiznání.

## Úplný popis (max. 4000 znaků)
Doklady jsou aplikace pro klienty účetní kanceláře Multio CZ s.r.o. (Tábor, Pelhřimov, Vysočina).

• Doklady stačí vyfotit – i víc účtenek najednou, každá se uloží jako samostatné PDF. Rozmazanou fotku aplikace pozná.
• Vidíte, co od vás účetní potřebuje: chybějící doklady, platby v bance bez dokladu, úkoly a zprávy.
• Faktury odběratelům s QR platbou.
• Daňové přiznání a roční zúčtování: krátký dotazník, podklady vyfotíte do aplikace, uvidíte stav a výsledek.
• Upozornění na nové úkoly, termíny a zprávy od účetní.
• Přihlášení otiskem prstu nebo přístupovým klíčem.

Aplikace je určena klientům kanceláře; nový klient může požádat o spolupráci přímo v aplikaci.

## Kategorie
Finance (nebo Firma)

## Kontakt pro Google Play
E-mail, telefon a web kanceláře – doplňte.

## Zásady ochrany osobních údajů (povinný odkaz)
https://doklady.ucetnictvitabor.cz/aplikace/zasady-ochrany-osobnich-udaju/  (text na portálu nechte zkontrolovat)

## Bezpečnost dat (formulář v Play Console) – návrh odpovědí
- Shromažďuje: jméno, e-mail, telefon (účet); fotky a soubory (doklady); finanční údaje (doklady, bankovní pohyby, příjmy u přiznání); ID zařízení (token pro upozornění).
- Účel: funkce aplikace, správa účtu. Ne reklama, ne analytika třetích stran.
- Šifrováno při přenosu: ano (HTTPS). Uživatel může požádat o smazání: ano (u účetní / e-mailem).
- Sdílení s třetími stranami: jen zpracovatelé (server, Google Firebase pro upozornění, AI pro vytěžení dokladů) – v Play Console se zpracovatelé za „sdílení“ nepovažují; ověřte podle aktuálních pravidel.

## Přístup pro kontrolu Googlem
Google potřebuje demo účet: vytvořte v portálu smyšleného klienta (např. „demo-google“) se smyšlenými doklady a zadejte jméno a heslo do Play Console → Přístup k aplikaci. Heslo neposílejte nikomu jinému.

## Snímky obrazovky
Min. 2 snímky telefonu (doporučeno 4–8) – ideálně z demo účtu se smyšlenými daty: Přehled, Vyfotit účtenky, Banka, Moje přiznání.

## Ikona a grafika
- Ikona 512×512 px (máme v aplikaci)
- Grafika funkce 1024×500 px – připravím na požádání.

## Postup vydání
1. Účet vývojáře Google Play (organizace, D-U-N-S) – zakládáte vy.
2. Klíč pro nahrání: na svém počítači `keytool -genkeypair -v -keystore doklady-upload.jks -alias doklady -keyalg RSA -keysize 2048 -validity 10000`. Soubor a hesla si bezpečně uschovejte, nikomu je neposílejte.
3. Na GitHubu Settings → Secrets and variables → Actions → New repository secret: `KEYSTORE_BASE64` (obsah .jks zakódovaný base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (doklady), `KEY_PASSWORD`.
4. Sestavení vytvoří `doklady-google-play` (.aab) – ten nahrajete do Play Console (nejdřív interní testování).
5. V Play Console zapněte „Podepisování aplikací ve službě Google Play“ a otisk App signing key přidejte na server do `ANDROID_APP_SHA256` (čárkou k současnému), aby fungovalo přihlášení přístupovým klíčem.
6. Po zveřejnění zapište číslo verze v portálu: Správa → Mobilní aplikace.
