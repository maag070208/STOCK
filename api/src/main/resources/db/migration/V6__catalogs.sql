-- Value catalogs (docs/rules/catalogs.md section 2). Codes never change. Items are deactivated, never deleted.
create table catalog (
    id uuid primary key,
    code varchar(60) not null unique,
    is_system boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint catalog_code_format check (code ~ '^[A-Z][A-Z0-9_]{1,59}$')
);

create table catalog_item (
    id uuid primary key,
    catalog_id uuid not null references catalog (id),
    code varchar(60) not null,
    sort_order integer not null default 0,
    active boolean not null default true,
    attributes jsonb not null default '{}',
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    unique (catalog_id, code),
    constraint catalog_item_code_format check (code ~ '^[A-Z][A-Z0-9_]{0,59}$')
);

create table catalog_item_label (
    catalog_item_id uuid not null references catalog_item (id),
    locale varchar(5) not null,
    label varchar(120) not null,
    description varchar(500),
    primary key (catalog_item_id, locale)
);
