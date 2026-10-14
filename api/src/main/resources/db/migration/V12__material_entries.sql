-- Material entries (docs/modules/material-entry.md, material-receiving.md): supplier invoices, their lines, each line's
-- assignments to operators with their counts, and the placements that bring the material into stock.

-- The operators the assignments of a warehouse pick from (material-entry.md, question 4).
create table staff_templates (
    warehouse_id uuid not null references warehouses (id),
    user_id uuid not null references users (id),
    primary key (warehouse_id, user_id)
);

create table invoices (
    id uuid primary key,
    number varchar(30) not null,
    supplier_id uuid not null references suppliers (id),
    contact_id uuid references supplier_contacts (id),
    invoice_date date not null,
    -- The one warehouse that receives every line; the AREA scope reads it.
    warehouse_id uuid not null references warehouses (id),
    -- An INVOICE_STATUS value.
    status_id uuid not null references catalog_item (id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    unique (supplier_id, number),
    constraint invoices_number_format check (number ~ '^[A-Z0-9/-]{1,30}$')
);
create index invoices_by_warehouse on invoices (warehouse_id);

-- A material once per invoice. Its unit comes from the material and does not change. An adjustment can bring a line
-- down to zero, when nothing arrived.
create table invoice_lines (
    id uuid primary key,
    invoice_id uuid not null references invoices (id),
    product_id uuid not null references products (id),
    unit_id uuid not null references catalog_item (id),
    quantity numeric(18, 6) not null,
    unique (invoice_id, product_id),
    constraint invoice_lines_quantity check (quantity >= 0)
);

-- One line's share for one operator: what they should receive, what they counted, and how much of it is placed.
create table receipt_assignments (
    id uuid primary key,
    line_id uuid not null references invoice_lines (id),
    operator_id uuid not null references users (id),
    expected_quantity numeric(18, 6) not null,
    counted_quantity numeric(18, 6),
    count_notes varchar(500),
    counted_at timestamptz,
    placed_quantity numeric(18, 6) not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint receipt_assignments_expected check (expected_quantity >= 0),
    constraint receipt_assignments_counted check (counted_quantity >= 0),
    constraint receipt_assignments_placed check (placed_quantity >= 0 and (counted_quantity is null and placed_quantity = 0 or placed_quantity <= counted_quantity))
);
create index receipt_assignments_by_line on receipt_assignments (line_id);
create index receipt_assignments_by_operator on receipt_assignments (operator_id);

-- Each placement of counted material into a location, with the movement that brought it into stock.
create table receipt_placements (
    id uuid primary key,
    assignment_id uuid not null references receipt_assignments (id),
    location_id uuid not null references locations (id),
    quantity numeric(18, 6) not null,
    movement_id uuid not null references stock_movements (id),
    placed_at timestamptz not null,
    placed_by uuid references users (id),
    constraint receipt_placements_quantity check (quantity > 0)
);
create index receipt_placements_by_assignment on receipt_placements (assignment_id);
