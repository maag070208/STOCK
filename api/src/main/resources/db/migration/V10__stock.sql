-- Stock (api.md section 4, locations.md section 5). Movements only grow; balances are their projection per location and
-- product, changed in the same transaction as the movement, under a row lock, and never below zero.
create table stock_movements (
    id uuid primary key,
    -- The order of the movements, for the kardex: time alone can repeat.
    seq bigint generated always as identity unique,
    -- A MOVEMENT_TYPE value. Its attribute `direction` says whether it adds to the balance or takes from it.
    type_id uuid not null references catalog_item (id),
    warehouse_id uuid not null references warehouses (id),
    location_id uuid not null references locations (id),
    product_id uuid not null references products (id),
    quantity numeric(18, 6) not null,
    user_id uuid references users (id),
    occurred_at timestamptz not null,
    reason varchar(500),
    -- The document that caused the movement, such as the opening inventory or an assignment of an invoice.
    source_type varchar(40) not null,
    source_id varchar(100) not null,
    constraint stock_movements_positive check (quantity > 0)
);
create index stock_movements_by_product on stock_movements (product_id, seq);
create index stock_movements_by_location on stock_movements (location_id, seq);
create index stock_movements_by_source on stock_movements (source_type, source_id);

create function stock_movements_are_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'stock_movements is append-only';
end;
$$;

create trigger stock_movements_no_update_or_delete
    before update or delete on stock_movements
    for each statement execute function stock_movements_are_append_only();

create trigger stock_movements_no_truncate
    before truncate on stock_movements
    for each statement execute function stock_movements_are_append_only();

create table stock_balances (
    location_id uuid not null references locations (id),
    product_id uuid not null references products (id),
    warehouse_id uuid not null references warehouses (id),
    quantity numeric(18, 6) not null default 0,
    updated_at timestamptz not null,
    primary key (location_id, product_id),
    constraint stock_balances_not_negative check (quantity >= 0)
);
create index stock_balances_by_product on stock_balances (product_id);
create index stock_balances_by_warehouse on stock_balances (warehouse_id);
