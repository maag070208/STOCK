-- The reassignments of an order (F6-04): what left one order for another, or went back to a location, with the note the
-- person wrote. The picks themselves change line, so what was moved is kept here — the report of orders reads it, and
-- the audit log keeps the same facts for people.
create table order_reassignments (
    id uuid primary key,
    -- The order the material left.
    order_id uuid not null references orders (id),
    line_id uuid not null references order_lines (id),
    -- The order it went to, and the line that took it. Null when it went back to a location.
    target_order_id uuid references orders (id),
    target_line_id uuid references order_lines (id),
    location_id uuid references locations (id),
    kind varchar(10) not null,
    quantity numeric(18, 6) not null,
    note varchar(500) not null,
    user_id uuid references users (id),
    created_at timestamptz not null,
    constraint order_reassignments_kind check (kind in ('TRANSFER', 'RETURN')),
    constraint order_reassignments_quantity_positive check (quantity > 0),
    -- A move to another order names it, and a return names its location.
    constraint order_reassignments_target check (
        (kind = 'TRANSFER' and target_order_id is not null and target_line_id is not null) or
        (kind = 'RETURN' and location_id is not null)
    )
);

create index order_reassignments_by_order on order_reassignments (order_id, created_at);
create index order_reassignments_by_target on order_reassignments (target_order_id);

-- The rows the orders report reads (F6-04): every line of an order with what it asked, what was picked, what left it for
-- another order and what came in from one. Read-only, like the balance views of V11.
create view order_report_rows as
select o.id as order_id, o.number, o.warehouse_id, w.code as warehouse_code, w.name as warehouse_name,
       o.customer_id, c.code as customer_code, c.name as customer_name,
       s.id as status_id, s.code as status_code, o.due_date, o.created_at, o.validated_at,
       l.id as line_id, l.product_id, p.code as product_code, p.name as product_name, p.unit_id,
       l.quantity as asked, l.picked_quantity as picked,
       coalesce((select sum(r.quantity) from order_reassignments r where r.line_id = l.id and r.kind = 'TRANSFER'), 0) as moved_out,
       coalesce((select sum(r.quantity) from order_reassignments r where r.target_line_id = l.id and r.kind = 'TRANSFER'), 0) as moved_in
from order_lines l
join orders o on o.id = l.order_id
join warehouses w on w.id = o.warehouse_id
join customers c on c.id = o.customer_id
join catalog_item s on s.id = o.status_id
join products p on p.id = l.product_id;

-- The rows the entries report reads (F6-04): every line of an invoice with what was expected, counted, the difference and
-- what was placed, added over its assignments.
create view invoice_report_rows as
select i.id as invoice_id, i.number, i.warehouse_id, w.code as warehouse_code, w.name as warehouse_name,
       i.supplier_id, sp.code as supplier_code, sp.name as supplier_name,
       s.id as status_id, s.code as status_code, i.invoice_date,
       l.id as line_id, l.product_id, p.code as product_code, p.name as product_name, p.unit_id,
       coalesce(sum(a.expected_quantity), 0) as expected,
       coalesce(sum(a.counted_quantity), 0) as counted,
       coalesce(sum(coalesce(a.counted_quantity, a.expected_quantity) - a.expected_quantity), 0) as difference,
       coalesce(sum(a.placed_quantity), 0) as placed
from invoice_lines l
join invoices i on i.id = l.invoice_id
join warehouses w on w.id = i.warehouse_id
join suppliers sp on sp.id = i.supplier_id
join catalog_item s on s.id = i.status_id
join products p on p.id = l.product_id
left join receipt_assignments a on a.line_id = l.id
group by i.id, i.number, i.warehouse_id, w.code, w.name, i.supplier_id, sp.code, sp.name, s.id, s.code, i.invoice_date,
         l.id, l.product_id, p.code, p.name, p.unit_id;
