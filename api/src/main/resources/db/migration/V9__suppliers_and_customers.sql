-- Suppliers and customers (docs/modules/suppliers.md and customers.md), each with its contacts.
-- The two have the same shape. Both are deactivated, never deleted. Catalog references are foreign keys to catalog_item.
create table suppliers (
    id uuid primary key,
    code varchar(20) not null unique,
    name varchar(120) not null,
    tax_id varchar(13) not null,
    street varchar(120) not null,
    exterior_number varchar(10) not null,
    interior_number varchar(10),
    neighborhood varchar(80) not null,
    city varchar(80) not null,
    state_id uuid not null references catalog_item (id),
    postal_code varchar(5) not null,
    country_id uuid not null references catalog_item (id),
    payment_terms_id uuid not null references catalog_item (id),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint suppliers_code_format check (code ~ '^[A-Z0-9-]{2,20}$'),
    constraint suppliers_tax_id_format check (tax_id ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$'),
    constraint suppliers_postal_code_format check (postal_code ~ '^[0-9]{5}$')
);

-- A contact needs an email or a phone, so the business can reach the supplier.
create table supplier_contacts (
    id uuid primary key,
    supplier_id uuid not null references suppliers (id),
    name varchar(120) not null,
    email varchar(254),
    phone varchar(10),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint supplier_contacts_channel check (email is not null or phone is not null),
    constraint supplier_contacts_phone_format check (phone ~ '^[0-9]{10}$')
);

create index supplier_contacts_supplier on supplier_contacts (supplier_id);

create table customers (
    id uuid primary key,
    code varchar(20) not null unique,
    name varchar(120) not null,
    tax_id varchar(13) not null,
    street varchar(120) not null,
    exterior_number varchar(10) not null,
    interior_number varchar(10),
    neighborhood varchar(80) not null,
    city varchar(80) not null,
    state_id uuid not null references catalog_item (id),
    postal_code varchar(5) not null,
    country_id uuid not null references catalog_item (id),
    payment_terms_id uuid not null references catalog_item (id),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint customers_code_format check (code ~ '^[A-Z0-9-]{2,20}$'),
    constraint customers_tax_id_format check (tax_id ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$'),
    constraint customers_postal_code_format check (postal_code ~ '^[0-9]{5}$')
);

create table customer_contacts (
    id uuid primary key,
    customer_id uuid not null references customers (id),
    name varchar(120) not null,
    email varchar(254),
    phone varchar(10),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint customer_contacts_channel check (email is not null or phone is not null),
    constraint customer_contacts_phone_format check (phone ~ '^[0-9]{10}$')
);

create index customer_contacts_customer on customer_contacts (customer_id);
