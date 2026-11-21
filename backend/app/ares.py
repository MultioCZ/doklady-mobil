"""Načtení firmy z ARES podle IČO."""
import time

import httpx

ARES_URL = "https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/ekonomicke-subjekty/{ico}"


_cache: dict[str, tuple[float, dict | None]] = {}
CACHE_SECONDS = 24 * 3600


def lookup(ico: str) -> dict | None:
    """Vrátí údaje subjektu z ARES (výsledek se 24 h pamatuje – dodavatelé se opakují)."""
    ico = "".join(c for c in ico if c.isdigit()).zfill(8)
    if len(ico) != 8:
        return None
    hit = _cache.get(ico)
    if hit and time.time() - hit[0] < CACHE_SECONDS:
        return hit[1]
    result = _fetch(ico)
    _cache[ico] = (time.time(), result)
    return result


def _fetch(ico: str) -> dict | None:
    try:
        r = httpx.get(ARES_URL.format(ico=ico), timeout=10, headers={"Accept": "application/json"})
    except httpx.HTTPError:
        return None
    if r.status_code != 200:
        return None
    return parse(r.json())


def parse(j: dict) -> dict:
    s = j.get("sidlo") or {}
    cislo = str(s.get("cisloDomovni") or "")
    if s.get("cisloOrientacni"):
        cislo += f"/{s['cisloOrientacni']}{s.get('cisloOrientacniPismeno') or ''}"
    ulice = s.get("nazevUlice") or s.get("nazevCastiObce") or s.get("nazevObce") or ""
    street = f"{ulice} {cislo}".strip()
    psc = str(s.get("psc") or "")
    return {
        "name": j.get("obchodniJmeno"),
        "ico": j.get("ico"),
        "dic": j.get("dic"),
        "street": street or None,
        "city": s.get("nazevObce"),
        "zip": f"{psc[:3]} {psc[3:]}" if len(psc) == 5 else psc or None,
        "country": s.get("nazevStatu") or "Česká republika",
        "address": s.get("textovaAdresa"),
    }
