-- Order alerts (docs/modules/order-fulfillment.md section 7a): one row per person, order and day, so the same day is
-- never raised twice. `alert_date` is the day the check raised it, and `due_date` the order's due date then, which is
-- what the message says.

create table order_alerts (
    id uuid primary key,
    order_id uuid not null references orders (id),
    user_id uuid not null references users (id),
    alert_date date not null,
    due_date date not null,
    -- BEFORE (the day before the due date), DUE (on it) or OVERDUE (after it).
    kind varchar(8) not null,
    created_at timestamptz not null,
    constraint order_alerts_once unique (order_id, user_id, alert_date),
    constraint order_alerts_kind check (kind in ('BEFORE', 'DUE', 'OVERDUE'))
);

create index order_alerts_by_user on order_alerts (user_id, alert_date desc);
