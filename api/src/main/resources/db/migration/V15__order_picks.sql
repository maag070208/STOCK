-- Order picks (docs/modules/order-fulfillment.md section 7): each pick takes material out of one location, and is an OUT
-- movement. `picked_quantity` on the line is the sum of its picks, so what remains reserved is quantity - picked.

create table order_picks (
    id uuid primary key,
    line_id uuid not null references order_lines (id),
    location_id uuid not null references locations (id),
    quantity numeric(18, 6) not null,
    movement_id uuid not null references stock_movements (id),
    picked_by uuid references users (id),
    picked_at timestamptz not null,
    constraint order_picks_quantity_positive check (quantity > 0)
);

create index order_picks_by_line on order_picks (line_id);
