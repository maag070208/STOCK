-- Materials (docs/modules/products.md). Deactivated, never deleted. Stock belongs to locations, not here.
create table products (
    id uuid primary key,
    code varchar(20) not null unique,
    name varchar(120) not null,
    -- The default unit: an item of the UNIT_OF_MEASURE catalog.
    unit_id uuid not null references catalog_item (id),
    barcode varchar(20) not null,
    -- The row of the legacy file the product came from, so it can be traced back (products.md section 3).
    legacy_id integer unique,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint products_code_format check (code ~ '^[A-Z0-9-]{2,20}$'),
    constraint products_name_length check (char_length(name) between 2 and 120),
    constraint products_barcode_format check (barcode ~ '^[A-Z0-9-]{6,20}$')
);

-- A barcode is unique among active products only (validation.md, barcode).
create unique index products_active_barcode on products (barcode) where active;
