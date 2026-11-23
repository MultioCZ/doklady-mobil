"""Přihlášení: JWT token (mobilní aplikace – hlavička Authorization, web – cookie)."""
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from fastapi import Depends, Header, HTTPException, Request
from sqlalchemy.orm import Session

from .config import settings
from .db import get_db
from .models import Company, User

COOKIE = "doklady_token"


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode(), hashed.encode())
    except ValueError:
        return False


def create_token(user: User) -> str:
    payload = {
        "sub": str(user.id),
        "exp": datetime.now(timezone.utc) + timedelta(days=settings.TOKEN_DAYS),
    }
    return jwt.encode(payload, settings.SECRET_KEY, algorithm="HS256")


def _user_from_token(token: str | None, db: Session) -> User | None:
    if not token:
        return None
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=["HS256"])
    except jwt.PyJWTError:
        return None
    user = db.get(User, int(payload["sub"]))
    return user if user and user.active else None


def authenticate(db: Session, email: str, password: str) -> User | None:
    user = db.query(User).filter(User.email == email.strip().lower()).first()
    if user and user.active and verify_password(password, user.password_hash):
        return user
    return None


def _bearer(authorization: str | None) -> str | None:
    if authorization and authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return None


def api_user(
    request: Request,
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> User:
    user = _user_from_token(_bearer(authorization) or request.cookies.get(COOKIE), db)
    if not user:
        raise HTTPException(401, "Nepřihlášen")
    return user


def web_user_optional(request: Request, db: Session = Depends(get_db)) -> User | None:
    return _user_from_token(request.cookies.get(COOKIE), db)


def resolve_company(user: User, db: Session, requested_id: int | None) -> Company:
    """Klient má vždy svou firmu. Admin (účetní) si firmu vybírá – hlavička X-Company-Id / cookie."""
    if not user.is_admin:
        if not user.company_id:
            raise HTTPException(403, "Uživatel nemá přiřazenou firmu")
        return db.get(Company, user.company_id)
    company = db.get(Company, requested_id) if requested_id else None
    if company is None:
        company = db.get(Company, user.company_id) if user.company_id else None
    if company is None:
        company = db.query(Company).order_by(Company.id).first()
    if company is None:
        raise HTTPException(400, "Zatím neexistuje žádná firma – vytvořte ji ve webové aplikaci.")
    return company


def api_company(
    request: Request,
    x_company_id: int | None = Header(default=None),
    user: User = Depends(api_user),
    db: Session = Depends(get_db),
) -> Company:
    cid = x_company_id or request.cookies.get("company_id")
    return resolve_company(user, db, int(cid) if cid else None)
