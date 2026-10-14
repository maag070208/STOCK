-- The comprobante of an order (docs/modules/order-fulfillment.md section 10): the PDF the API builds at validation and
-- keeps once, so a second download answers the same bytes. It lives in private storage: it is reached only through the
-- API, with the permission to read the order, and never at a public URL.

create table order_documents (
    order_id uuid primary key references orders (id),
    content bytea not null,
    content_type varchar(60) not null,
    file_name varchar(120) not null,
    created_at timestamptz not null
);
