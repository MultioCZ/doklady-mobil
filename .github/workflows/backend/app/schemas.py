from datetime import date, datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field


class ORM(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class LoginIn(BaseModel):
    email: str
    password: str


class ActivateIn(BaseModel):
    code: str
    password: str


class InvitationIn(BaseModel):
    email: str
    name: str | None = None
    company_id: int | None = None  # admin: pro kterou firmu; jinak aktuální
    send_email: bool = True


class InvitationOut(BaseModel):
    email: str
    code: str
    link: str          # https odkaz na stránku aktivace (pro e-mail / QR)
    app_link: str      # odkaz, který rovnou otevře aplikaci
    expires: datetime
    emailed: bool


class CompanyOut(ORM):
    id: int
    name: str
    ico: str | None = None
    dic: str | None = None
    vat_payer: bool = True


class UserOut(ORM):
    id: int
    email: str
    name: str | None = None
    role: str
    company_id: int | None = None


class LoginOut(BaseModel):
    token: str
    user: UserOut
    companies: list[CompanyOut]


class DocumentOut(ORM):
    id: int
    company_id: int
    created_at: datetime
    source: str
    status: str
    error: str | None = None
    original_filename: str | None = None
    original_mime: str
    has_processed: bool = False
    processing_info: dict | None = None
    doc_type: str | None = None
    doc_number: str | None = None
    variable_symbol: str | None = None
    supplier_name: str | None = None
    supplier_ico: str | None = None
    supplier_dic: str | None = None
    supplier_address: str | None = None
    supplier_account: str | None = None
    customer_name: str | None = None
    customer_ico: str | None = None
    customer_dic: str | None = None
    issue_date: date | None = None
    taxable_date: date | None = None
    due_date: date | None = None
    currency: str | None = None
    total: Decimal | None = None
    total_base: Decimal | None = None
    total_vat: Decimal | None = None
    vat_breakdown: list | None = None
    payment_method: str | None = None
    reverse_charge: bool | None = None
    confidence: float | None = None
    extraction_method: str | None = None
    checks: dict | None = None
    note: str | None = None

    @classmethod
    def of(cls, d) -> "DocumentOut":
        out = cls.model_validate(d)
        out.has_processed = bool(d.processed_path)
        out.checks = (d.raw_extraction or {}).get("checks")
        return out


class DocumentPatch(BaseModel):
    doc_type: str | None = None
    doc_number: str | None = None
    variable_symbol: str | None = None
    supplier_name: str | None = None
    supplier_ico: str | None = None
    supplier_dic: str | None = None
    supplier_address: str | None = None
    supplier_account: str | None = None
    customer_name: str | None = None
    customer_ico: str | None = None
    customer_dic: str | None = None
    issue_date: date | None = None
    taxable_date: date | None = None
    due_date: date | None = None
    currency: str | None = None
    total: Decimal | None = None
    total_base: Decimal | None = None
    total_vat: Decimal | None = None
    vat_breakdown: list | None = None
    payment_method: str | None = None
    reverse_charge: bool | None = None
    note: str | None = None
    status: str | None = Field(default=None, pattern="^(extracted|checked|error|uploaded)$")


class CustomerIn(BaseModel):
    name: str
    ico: str | None = None
    dic: str | None = None
    street: str | None = None
    city: str | None = None
    zip: str | None = None
    country: str = "Česká republika"
    email: str | None = None
    due_days: int | None = None
    note: str | None = None


class CustomerOut(CustomerIn, ORM):
    id: int


class ItemIn(BaseModel):
    description: str
    quantity: Decimal = Decimal(1)
    unit: str | None = None
    unit_price: Decimal
    vat_rate: int = 21


class ItemOut(ItemIn, ORM):
    id: int


class InvoiceIn(BaseModel):
    customer_id: int
    items: list[ItemIn]
    issue_date: date | None = None
    taxable_date: date | None = None
    due_days: int | None = None
    currency: str = "CZK"
    reverse_charge: bool = False
    payment_method: str = "prevod"
    note: str | None = None
    send_to: str | None = None  # rovnou odeslat e-mailem


class InvoiceOut(ORM):
    id: int
    number: str
    variable_symbol: str
    customer: CustomerOut
    issue_date: date
    taxable_date: date
    due_date: date
    currency: str
    reverse_charge: bool
    status: str
    sent_at: datetime | None = None
    sent_to: str | None = None
    total_base: Decimal
    total_vat: Decimal
    total: Decimal
    note: str | None = None
    items: list[ItemOut]


class SendIn(BaseModel):
    email: str | None = None
    message: str | None = None


class RecurringIn(BaseModel):
    customer_id: int
    name: str
    interval_months: int = Field(default=1, ge=1, le=24)
    next_date: date
    end_date: date | None = None
    due_days: int | None = None
    currency: str = "CZK"
    reverse_charge: bool = False
    note: str | None = None
    items: list[ItemIn]
    auto_send: bool = True
    email_to: str | None = None
    active: bool = True


class RecurringOut(ORM):
    id: int
    customer: CustomerOut
    name: str
    interval_months: int
    next_date: date
    end_date: date | None = None
    auto_send: bool
    email_to: str | None = None
    active: bool
    items: list
