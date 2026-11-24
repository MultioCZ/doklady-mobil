"""Datový model: firmy (klienti účetní kanceláře), uživatelé, doklady, odběratelé, faktury."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import JSON, Boolean, Date, DateTime, ForeignKey, Integer, Numeric, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base

Money = Numeric(14, 2)


class Company(Base):
    """Firma / klient účetní kanceláře. Každý doklad a faktura patří jedné firmě."""
    __tablename__ = "companies"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(255))
    ico: Mapped[str | None] = mapped_column(String(16))
    dic: Mapped[str | None] = mapped_column(String(20))
    street: Mapped[str | None] = mapped_column(String(255))
    city: Mapped[str | None] = mapped_column(String(128))
    zip: Mapped[str | None] = mapped_column(String(16))
    country: Mapped[str] = mapped_column(String(64), default="Česká republika")
    registry_note: Mapped[str | None] = mapped_column(String(255))  # zápis v OR / živnostenský rejstřík
    email: Mapped[str | None] = mapped_column(String(255))
    phone: Mapped[str | None] = mapped_column(String(64))
    bank_account: Mapped[str | None] = mapped_column(String(64))  # 123456789/0800
    iban: Mapped[str | None] = mapped_column(String(34))
    vat_payer: Mapped[bool] = mapped_column(Boolean, default=True)
    default_due_days: Mapped[int] = mapped_column(Integer, default=14)
    invoice_footer: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    users: Mapped[list[User]] = relationship(back_populates="company")


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    name: Mapped[str | None] = mapped_column(String(255))
    password_hash: Mapped[str] = mapped_column(String(255))
    # admin = účetní kancelář (vidí všechny firmy), client = uživatel jedné firmy
    role: Mapped[str] = mapped_column(String(16), default="client")
    company_id: Mapped[int | None] = mapped_column(ForeignKey("companies.id"))
    active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    # pozvánka do klientského portálu: jednorázový kód, kterým si klient v aplikaci nastaví heslo
    activation_code: Mapped[str | None] = mapped_column(String(16), index=True)
    activation_expires: Mapped[datetime | None] = mapped_column(DateTime)

    company: Mapped[Company | None] = relationship(back_populates="users")

    @property
    def is_admin(self) -> bool:
        return self.role == "admin"


class Document(Base):
    """Přijatý doklad (účtenka, faktura přijatá…) – originál, upravená verze a vytěžené náležitosti."""
    __tablename__ = "documents"

    id: Mapped[int] = mapped_column(primary_key=True)
    company_id: Mapped[int] = mapped_column(ForeignKey("companies.id"), index=True)
    uploaded_by_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, index=True)
    source: Mapped[str] = mapped_column(String(16), default="mobile")  # mobile | web | api

    original_filename: Mapped[str | None] = mapped_column(String(255))
    original_path: Mapped[str] = mapped_column(String(512))
    original_mime: Mapped[str] = mapped_column(String(64))
    processed_path: Mapped[str | None] = mapped_column(String(512))
    processing_info: Mapped[dict | None] = mapped_column(JSON)  # co se s obrázkem dělalo

    # uploaded -> processing -> extracted | error ; checked = zkontrolováno účetní
    status: Mapped[str] = mapped_column(String(16), default="uploaded", index=True)
    error: Mapped[str | None] = mapped_column(Text)

    # --- náležitosti dokladu ---
    doc_type: Mapped[str | None] = mapped_column(String(32))  # faktura | uctenka | zalohova | dobropis | jine
    doc_number: Mapped[str | None] = mapped_column(String(64))
    variable_symbol: Mapped[str | None] = mapped_column(String(32))
    supplier_name: Mapped[str | None] = mapped_column(String(255))
    supplier_ico: Mapped[str | None] = mapped_column(String(16))
    supplier_dic: Mapped[str | None] = mapped_column(String(20))
    supplier_address: Mapped[str | None] = mapped_column(String(512))
    supplier_account: Mapped[str | None] = mapped_column(String(64))
    customer_name: Mapped[str | None] = mapped_column(String(255))
    customer_ico: Mapped[str | None] = mapped_column(String(16))
    customer_dic: Mapped[str | None] = mapped_column(String(20))
    issue_date: Mapped[date | None] = mapped_column(Date)
    taxable_date: Mapped[date | None] = mapped_column(Date)  # DUZP
    due_date: Mapped[date | None] = mapped_column(Date)
    currency: Mapped[str | None] = mapped_column(String(3))
    total: Mapped[Decimal | None] = mapped_column(Money)
    total_base: Mapped[Decimal | None] = mapped_column(Money)
    total_vat: Mapped[Decimal | None] = mapped_column(Money)
    vat_breakdown: Mapped[list | None] = mapped_column(JSON)  # [{rate, base, vat}]
    payment_method: Mapped[str | None] = mapped_column(String(32))  # hotove | karta | prevod
    reverse_charge: Mapped[bool | None] = mapped_column(Boolean)
    confidence: Mapped[float | None] = mapped_column()
    raw_extraction: Mapped[dict | None] = mapped_column(JSON)
    extraction_method: Mapped[str | None] = mapped_column(String(64))  # isdoc, qr, text, ocr, ai…
    ocr_text: Mapped[str | None] = mapped_column(Text)  # přečtený text (z telefonu, PDF nebo Tesseractu)
    note: Mapped[str | None] = mapped_column(Text)

    company: Mapped[Company] = relationship()


class Customer(Base):
    """Odběratel pro faktury vydané – uložený pro opakovanou fakturaci."""
    __tablename__ = "customers"

    id: Mapped[int] = mapped_column(primary_key=True)
    company_id: Mapped[int] = mapped_column(ForeignKey("companies.id"), index=True)
    name: Mapped[str] = mapped_column(String(255))
    ico: Mapped[str | None] = mapped_column(String(16))
    dic: Mapped[str | None] = mapped_column(String(20))
    street: Mapped[str | None] = mapped_column(String(255))
    city: Mapped[str | None] = mapped_column(String(128))
    zip: Mapped[str | None] = mapped_column(String(16))
    country: Mapped[str] = mapped_column(String(64), default="Česká republika")
    email: Mapped[str | None] = mapped_column(String(255))  # kam se faktury posílají
    due_days: Mapped[int | None] = mapped_column(Integer)
    note: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Invoice(Base):
    __tablename__ = "invoices"

    id: Mapped[int] = mapped_column(primary_key=True)
    company_id: Mapped[int] = mapped_column(ForeignKey("companies.id"), index=True)
    customer_id: Mapped[int] = mapped_column(ForeignKey("customers.id"))
    recurring_id: Mapped[int | None] = mapped_column(ForeignKey("recurring_invoices.id"))
    number: Mapped[str] = mapped_column(String(32), index=True)
    variable_symbol: Mapped[str] = mapped_column(String(10))
    issue_date: Mapped[date] = mapped_column(Date)
    taxable_date: Mapped[date] = mapped_column(Date)
    due_date: Mapped[date] = mapped_column(Date)
    currency: Mapped[str] = mapped_column(String(3), default="CZK")
    payment_method: Mapped[str] = mapped_column(String(32), default="prevod")
    reverse_charge: Mapped[bool] = mapped_column(Boolean, default=False)
    vat_payer: Mapped[bool] = mapped_column(Boolean, default=True)  # snímek stavu firmy v době vystavení
    note: Mapped[str | None] = mapped_column(Text)
    # draft | issued | sent | paid | cancelled
    status: Mapped[str] = mapped_column(String(16), default="issued")
    sent_at: Mapped[datetime | None] = mapped_column(DateTime)
    sent_to: Mapped[str | None] = mapped_column(String(255))
    total_base: Mapped[Decimal] = mapped_column(Money, default=0)
    total_vat: Mapped[Decimal] = mapped_column(Money, default=0)
    total: Mapped[Decimal] = mapped_column(Money, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    customer: Mapped[Customer] = relationship()
    company: Mapped[Company] = relationship()
    items: Mapped[list[InvoiceItem]] = relationship(
        back_populates="invoice", cascade="all, delete-orphan", order_by="InvoiceItem.position"
    )


class InvoiceItem(Base):
    __tablename__ = "invoice_items"

    id: Mapped[int] = mapped_column(primary_key=True)
    invoice_id: Mapped[int] = mapped_column(ForeignKey("invoices.id"), index=True)
    position: Mapped[int] = mapped_column(Integer, default=0)
    description: Mapped[str] = mapped_column(String(512))
    quantity: Mapped[Decimal] = mapped_column(Numeric(14, 3), default=1)
    unit: Mapped[str | None] = mapped_column(String(16))
    unit_price: Mapped[Decimal] = mapped_column(Money)  # cena za jednotku bez DPH
    vat_rate: Mapped[int] = mapped_column(Integer, default=21)

    invoice: Mapped[Invoice] = relationship(back_populates="items")


class RecurringInvoice(Base):
    """Šablona opakované faktury – podle ní se automaticky vystavují (a případně odesílají) faktury."""
    __tablename__ = "recurring_invoices"

    id: Mapped[int] = mapped_column(primary_key=True)
    company_id: Mapped[int] = mapped_column(ForeignKey("companies.id"), index=True)
    customer_id: Mapped[int] = mapped_column(ForeignKey("customers.id"))
    name: Mapped[str] = mapped_column(String(255))
    interval_months: Mapped[int] = mapped_column(Integer, default=1)
    next_date: Mapped[date] = mapped_column(Date)
    end_date: Mapped[date | None] = mapped_column(Date)
    due_days: Mapped[int | None] = mapped_column(Integer)
    currency: Mapped[str] = mapped_column(String(3), default="CZK")
    reverse_charge: Mapped[bool] = mapped_column(Boolean, default=False)
    note: Mapped[str | None] = mapped_column(Text)
    items: Mapped[list] = mapped_column(JSON)  # [{description, quantity, unit, unit_price, vat_rate}]
    auto_send: Mapped[bool] = mapped_column(Boolean, default=True)
    email_to: Mapped[str | None] = mapped_column(String(255))  # prázdné = e-mail odběratele
    active: Mapped[bool] = mapped_column(Boolean, default=True)
    last_run: Mapped[datetime | None] = mapped_column(DateTime)

    customer: Mapped[Customer] = relationship()
