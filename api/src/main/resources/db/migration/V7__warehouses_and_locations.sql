-- Warehouses and their locations (docs/modules/locations.md). Both are deactivated, never deleted.
create table warehouses (
    id uuid primary key,
    code varchar(20) not null unique,
    name varchar(120) not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint warehouses_code_format check (code ~ '^[A-Z0-9-]{2,20}$')
);

-- user_warehouses (V4) gets its foreign key now that warehouses exist.
-- Until now no warehouse existed, so no row can point to one: rows written before this migration are removed.
delete from user_warehouses;
alter table user_warehouses add constraint user_warehouses_warehouse_fk foreign key (warehouse_id) references warehouses (id);

-- A location is `aisle + rack + level`, unique within its warehouse. The temporary location is the only other code:
-- the system creates one per warehouse, and it has no aisle, rack or level.
create table locations (
    id uuid primary key,
    warehouse_id uuid not null references warehouses (id),
    code varchar(20) not null,
    aisle varchar(1),
    rack varchar(2),
    level varchar(1),
    temporary boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    unique (warehouse_id, code),
    constraint locations_code_format check (
        (temporary and code = 'TEMPORARY' and aisle is null and rack is null and level is null)
        or (not temporary and aisle ~ '^[A-Z]$' and rack ~ '^[0-9]{2}$' and level ~ '^[A-Z]$' and code = aisle || rack || level)
    )
);

create unique index locations_one_temporary on locations (warehouse_id) where temporary;
create index locations_groups on locations (warehouse_id, aisle, rack);
