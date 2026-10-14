-- The rows the stock screens read (F4-06). Lists of stock need the warehouse, the location and the material of each
-- balance, so the stock module reads them through these views. They are read-only: every change still goes through
-- the service of the module that owns the table (api.md section 3). A balance at zero is not stock and is left out.
create view stock_balance_rows as
select b.location_id, b.product_id, b.warehouse_id, b.quantity, b.updated_at,
       w.code as warehouse_code, l.code as location_code, l.aisle, l.rack, l.temporary,
       p.code as product_code, p.name as product_name, p.unit_id
from stock_balances b
join warehouses w on w.id = b.warehouse_id
join locations l on l.id = b.location_id
join products p on p.id = b.product_id
where b.quantity > 0;

-- Each material's stock in each warehouse: the sum of its balances, and in how many locations it is.
create view stock_product_totals as
select r.warehouse_id, r.product_id, sum(r.quantity) as quantity, count(*) as locations,
       r.warehouse_code, r.product_code, r.product_name, r.unit_id
from stock_balance_rows r
group by r.warehouse_id, r.product_id, r.warehouse_code, r.product_code, r.product_name, r.unit_id;
