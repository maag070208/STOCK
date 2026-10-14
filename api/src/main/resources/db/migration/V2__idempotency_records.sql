-- Stored responses for Idempotency-Key requests, written in the same transaction as the request's writes.
create table idempotency_record (
    idempotency_key varchar(100) primary key,
    request_hash varchar(64) not null,
    response_status integer not null,
    response_body text not null,
    created_at timestamptz not null default now()
);
