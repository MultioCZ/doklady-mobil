"""Konfigurace aplikace – vše přes proměnné prostředí (nebo soubor .env)."""
import os
from pathlib import Path


def _load_dotenv() -> None:
    env = Path(__file__).resolve().parent.parent / ".env"
    if not env.exists():
        return
    for line in env.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


_load_dotenv()

BASE_DIR = Path(__file__).resolve().parent.parent


class Settings:
    # databáze: SQLite pro vývoj, PostgreSQL pro provoz
    # např. postgresql+psycopg://doklady:heslo@localhost/doklady
    DATABASE_URL: str = os.getenv("DATABASE_URL", f"sqlite:///{BASE_DIR / 'data' / 'doklady.db'}")
    STORAGE_DIR: Path = Path(os.getenv("STORAGE_DIR", str(BASE_DIR / "data" / "files")))

    SECRET_KEY: str = os.getenv("SECRET_KEY", "ZMENTE-MNE-v-produkci")
    TOKEN_DAYS: int = int(os.getenv("TOKEN_DAYS", "30"))

    # vytěžování přes Claude
    ANTHROPIC_API_KEY: str = os.getenv("ANTHROPIC_API_KEY", "")
    CLAUDE_MODEL: str = os.getenv("CLAUDE_MODEL", "claude-haiku-5-5")
    # rules  = jen bez AI (QR, ISDOC, text PDF, OCR + pravidla)
    # hybrid = nejdřív bez AI, AI jen když něco chybí nebo nesedí součty (doporučeno)
    # ai     = vždy AI (výsledky bez AI se použijí jako nápověda a pro kontrolu)
    EXTRACTION_MODE: str = os.getenv("EXTRACTION_MODE", "hybrid")

    # odesílání e-mailů
    SMTP_HOST: str = os.getenv("SMTP_HOST", "")
    SMTP_PORT: int = int(os.getenv("SMTP_PORT", "587"))
    SMTP_USER: str = os.getenv("SMTP_USER", "")
    SMTP_PASSWORD: str = os.getenv("SMTP_PASSWORD", "")
    SMTP_FROM: str = os.getenv("SMTP_FROM", "")
    SMTP_SSL: bool = os.getenv("SMTP_SSL", "0") == "1"  # 1 = port 465 (SSL), jinak STARTTLS

    # první administrátor se vytvoří při startu, pokud v DB žádný uživatel není
    ADMIN_EMAIL: str = os.getenv("ADMIN_EMAIL", "admin@example.cz")
    ADMIN_PASSWORD: str = os.getenv("ADMIN_PASSWORD", "admin")

    # veřejná adresa serveru (portálu) – používá se v pozvánkách klientům
    PUBLIC_URL: str = os.getenv("PUBLIC_URL", "https://doklady.multio.cz").rstrip("/")
    APP_SCHEME: str = os.getenv("APP_SCHEME", "multiodoklady")  # odkaz, který otevře mobilní aplikaci
    APP_NAME: str = os.getenv("APP_NAME", "Multio Doklady")
    GOOGLE_PLAY_URL: str = os.getenv("GOOGLE_PLAY_URL", "https://play.google.com/store/apps/details?id=cz.multio.doklady")
    APP_STORE_URL: str = os.getenv("APP_STORE_URL", "https://apps.apple.com/")
    INVITATION_DAYS: int = int(os.getenv("INVITATION_DAYS", "14"))

    # maximální velikost nahraného souboru (MB)
    MAX_UPLOAD_MB: int = int(os.getenv("MAX_UPLOAD_MB", "25"))

    # automatické generování opakovaných faktur (kontrola každou hodinu)
    RECURRING_ENABLED: bool = os.getenv("RECURRING_ENABLED", "1") == "1"


settings = Settings()
settings.STORAGE_DIR.mkdir(parents=True, exist_ok=True)
if settings.DATABASE_URL.startswith("sqlite:///"):
    Path(settings.DATABASE_URL.removeprefix("sqlite:///")).parent.mkdir(parents=True, exist_ok=True)
