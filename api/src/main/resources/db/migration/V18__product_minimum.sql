-- The minimum stock of a material (reports.md section 5). Below it, the material shows up in the report of what has to
-- be bought. One number per product, in the product's unit; zero means no minimum was set.
alter table products add column minimum numeric(18, 6) not null default 0;

alter table products add constraint products_minimum_not_negative check (minimum >= 0);
