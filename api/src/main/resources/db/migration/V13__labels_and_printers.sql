-- Labels and printers (docs/modules/labels.md). Templates are data with their versions; printers are a catalog,
-- and labels are only ever sent to a printer of it.
create table label_templates (
    id uuid primary key,
    key varchar(60) not null unique,
    name_es varchar(120) not null,
    name_en varchar(120) not null,
    -- The active version: the one printed.
    version integer not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint label_templates_key_format check (key ~ '^[A-Z][A-Z0-9_]{1,59}$')
);

-- Every version of every template stays readable.
create table label_template_versions (
    template_id uuid not null references label_templates (id),
    version integer not null,
    zpl text not null,
    created_at timestamptz not null,
    created_by uuid references users (id),
    primary key (template_id, version)
);

-- The port is the raw printing port of Zebra printers, the same for all (labels.md section 5): it is not stored.
create table printers (
    id uuid primary key,
    name varchar(60) not null,
    warehouse_id uuid not null references warehouses (id),
    ip_address varchar(15) not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id)
);
