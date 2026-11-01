alter table receipt_assignments
    add column accepted_quantity numeric(18, 6);

update receipt_assignments
set accepted_quantity = counted_quantity
where counted_quantity is not null
  and counted_quantity = expected_quantity;

alter table receipt_assignments
    add constraint receipt_assignments_accepted check (accepted_quantity is null or accepted_quantity >= 0);

create index receipt_assignments_active_work on receipt_assignments (operator_id, counted_quantity, placed_quantity);
