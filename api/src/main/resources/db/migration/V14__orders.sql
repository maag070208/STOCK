-- Orders (docs/modules/order-fulfillment.md): materials leaving a warehouse, from creation to validation.
-- An order belongs to one warehouse, as invoices do, and names an active customer and optionally one of its contacts.

create table orders (
    id uuid primary key,
    number varchar(30) not null,
    warehouse_id uuid not null references warehouses (id),
    customer_id uuid not null references customers (id),
    contact_id uuid references customer_contacts (id),
    due_date date not null,
    status_id uuid not null references catalog_item (id),
    operator_id uuid references users (id),
    created_by uuid references users (id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    validated_at timestamptz,
    validated_by uuid references users (id),
    constraint orders_warehouse_number_key unique (warehouse_id, number),
    constraint orders_number_format check (number ~ '^[A-Z0-9/-]{1,30}$')
);

create index orders_by_warehouse on orders (warehouse_id);
create index orders_by_status on orders (status_id);
create index orders_by_operator on orders (operator_id);
create index orders_by_customer on orders (customer_id);

-- One line per material per order. The quantity is reserved at creation; `picked_quantity` is how much of it has
-- already left a location, so what remains reserved is `quantity - picked_quantity`.
create table order_lines (
    id uuid primary key,
    order_id uuid not null references orders (id),
    product_id uuid not null references products (id),
    unit_id uuid not null references catalog_item (id),
    quantity numeric(18, 6) not null,
    picked_quantity numeric(18, 6) not null default 0,
    constraint order_lines_quantity_positive check (quantity > 0),
    constraint order_lines_picked_not_negative check (picked_quantity >= 0),
    constraint order_lines_picked_not_above check (picked_quantity <= quantity),
    constraint order_lines_one_per_product unique (order_id, product_id)
);

create index order_lines_by_order on order_lines (order_id);
create index order_lines_by_product on order_lines (product_id);
