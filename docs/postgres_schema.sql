-- Schéma PostgreSQL pro data, se kterými pracuje mobilní aplikace.
-- Pokud už máte vlastní tabulky (firmy, uživatelé), stačí doplnit chybějící sloupce a API mapovat na ně.

CREATE TABLE companies (
	id SERIAL NOT NULL, 
	name VARCHAR(255) NOT NULL, 
	ico VARCHAR(16), 
	dic VARCHAR(20), 
	street VARCHAR(255), 
	city VARCHAR(128), 
	zip VARCHAR(16), 
	country VARCHAR(64) NOT NULL, 
	registry_note VARCHAR(255), 
	email VARCHAR(255), 
	phone VARCHAR(64), 
	bank_account VARCHAR(64), 
	iban VARCHAR(34), 
	vat_payer BOOLEAN NOT NULL, 
	default_due_days INTEGER NOT NULL, 
	invoice_footer TEXT, 
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, 
	PRIMARY KEY (id)
);

CREATE TABLE customers (
	id SERIAL NOT NULL, 
	company_id INTEGER NOT NULL, 
	name VARCHAR(255) NOT NULL, 
	ico VARCHAR(16), 
	dic VARCHAR(20), 
	street VARCHAR(255), 
	city VARCHAR(128), 
	zip VARCHAR(16), 
	country VARCHAR(64) NOT NULL, 
	email VARCHAR(255), 
	due_days INTEGER, 
	note TEXT, 
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(company_id) REFERENCES companies (id)
);
CREATE INDEX ix_customers_company_id ON customers (company_id);

CREATE TABLE users (
	id SERIAL NOT NULL, 
	email VARCHAR(255) NOT NULL, 
	name VARCHAR(255), 
	password_hash VARCHAR(255) NOT NULL, 
	role VARCHAR(16) NOT NULL, 
	company_id INTEGER, 
	active BOOLEAN NOT NULL, 
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, 
	activation_code VARCHAR(16), 
	activation_expires TIMESTAMP WITHOUT TIME ZONE, 
	PRIMARY KEY (id), 
	FOREIGN KEY(company_id) REFERENCES companies (id)
);
CREATE UNIQUE INDEX ix_users_email ON users (email);
CREATE INDEX ix_users_activation_code ON users (activation_code);

CREATE TABLE documents (
	id SERIAL NOT NULL, 
	company_id INTEGER NOT NULL, 
	uploaded_by_id INTEGER, 
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, 
	source VARCHAR(16) NOT NULL, 
	original_filename VARCHAR(255), 
	original_path VARCHAR(512) NOT NULL, 
	original_mime VARCHAR(64) NOT NULL, 
	processed_path VARCHAR(512), 
	processing_info JSON, 
	status VARCHAR(16) NOT NULL, 
	error TEXT, 
	doc_type VARCHAR(32), 
	doc_number VARCHAR(64), 
	variable_symbol VARCHAR(32), 
	supplier_name VARCHAR(255), 
	supplier_ico VARCHAR(16), 
	supplier_dic VARCHAR(20), 
	supplier_address VARCHAR(512), 
	supplier_account VARCHAR(64), 
	customer_name VARCHAR(255), 
	customer_ico VARCHAR(16), 
	customer_dic VARCHAR(20), 
	issue_date DATE, 
	taxable_date DATE, 
	due_date DATE, 
	currency VARCHAR(3), 
	total NUMERIC(14, 2), 
	total_base NUMERIC(14, 2), 
	total_vat NUMERIC(14, 2), 
	vat_breakdown JSON, 
	payment_method VARCHAR(32), 
	reverse_charge BOOLEAN, 
	confidence DOUBLE PRECISION, 
	raw_extraction JSON, 
	extraction_method VARCHAR(64), 
	ocr_text TEXT, 
	note TEXT, 
	PRIMARY KEY (id), 
	FOREIGN KEY(company_id) REFERENCES companies (id), 
	FOREIGN KEY(uploaded_by_id) REFERENCES users (id)
);
CREATE INDEX ix_documents_created_at ON documents (created_at);
CREATE INDEX ix_documents_company_id ON documents (company_id);
CREATE INDEX ix_documents_status ON documents (status);

CREATE TABLE recurring_invoices (
	id SERIAL NOT NULL, 
	company_id INTEGER NOT NULL, 
	customer_id INTEGER NOT NULL, 
	name VARCHAR(255) NOT NULL, 
	interval_months INTEGER NOT NULL, 
	next_date DATE NOT NULL, 
	end_date DATE, 
	due_days INTEGER, 
	currency VARCHAR(3) NOT NULL, 
	reverse_charge BOOLEAN NOT NULL, 
	note TEXT, 
	items JSON NOT NULL, 
	auto_send BOOLEAN NOT NULL, 
	email_to VARCHAR(255), 
	active BOOLEAN NOT NULL, 
	last_run TIMESTAMP WITHOUT TIME ZONE, 
	PRIMARY KEY (id), 
	FOREIGN KEY(company_id) REFERENCES companies (id), 
	FOREIGN KEY(customer_id) REFERENCES customers (id)
);
CREATE INDEX ix_recurring_invoices_company_id ON recurring_invoices (company_id);

CREATE TABLE invoices (
	id SERIAL NOT NULL, 
	company_id INTEGER NOT NULL, 
	customer_id INTEGER NOT NULL, 
	recurring_id INTEGER, 
	number VARCHAR(32) NOT NULL, 
	variable_symbol VARCHAR(10) NOT NULL, 
	issue_date DATE NOT NULL, 
	taxable_date DATE NOT NULL, 
	due_date DATE NOT NULL, 
	currency VARCHAR(3) NOT NULL, 
	payment_method VARCHAR(32) NOT NULL, 
	reverse_charge BOOLEAN NOT NULL, 
	vat_payer BOOLEAN NOT NULL, 
	note TEXT, 
	status VARCHAR(16) NOT NULL, 
	sent_at TIMESTAMP WITHOUT TIME ZONE, 
	sent_to VARCHAR(255), 
	total_base NUMERIC(14, 2) NOT NULL, 
	total_vat NUMERIC(14, 2) NOT NULL, 
	total NUMERIC(14, 2) NOT NULL, 
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(company_id) REFERENCES companies (id), 
	FOREIGN KEY(customer_id) REFERENCES customers (id), 
	FOREIGN KEY(recurring_id) REFERENCES recurring_invoices (id)
);
CREATE INDEX ix_invoices_company_id ON invoices (company_id);
CREATE INDEX ix_invoices_number ON invoices (number);

CREATE TABLE invoice_items (
	id SERIAL NOT NULL, 
	invoice_id INTEGER NOT NULL, 
	position INTEGER NOT NULL, 
	description VARCHAR(512) NOT NULL, 
	quantity NUMERIC(14, 3) NOT NULL, 
	unit VARCHAR(16), 
	unit_price NUMERIC(14, 2) NOT NULL, 
	vat_rate INTEGER NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(invoice_id) REFERENCES invoices (id)
);
CREATE INDEX ix_invoice_items_invoice_id ON invoice_items (invoice_id);
