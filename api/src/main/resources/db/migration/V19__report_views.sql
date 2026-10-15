-- The rows the movement reports read (F6-02, F6-03). A movement is append-only, and a report needs it with the names of
-- what it touches: its type and direction, the warehouse, the location, the material and its unit. Read-only, like the
-- balance views of V11: every change still goes through the service that owns the table.
create view stock_movement_rows as
select m.seq, m.occurred_at, m.type_id, i.code as type_code, (i.attributes ->> 'direction') as direction,
       m.warehouse_id, w.code as warehouse_code, w.name as warehouse_name,
       m.location_id, l.code as location_code,
       m.product_id, p.code as product_code, p.name as product_name, p.unit_id,
       m.quantity, m.user_id, m.reason, m.source_type, m.source_id
from stock_movements m
join catalog_item i on i.id = m.type_id
join warehouses w on w.id = m.warehouse_id
join locations l on l.id = m.location_id
join products p on p.id = m.product_id;
