-- The audit log. It only grows: the database rejects a change or a removal of a record.
create table audit_log (
    id uuid primary key,
    occurred_at timestamptz not null,
    actor_id uuid,
    actor varchar(60) not null,
    action varchar(60) not null,
    target_type varchar(40) not null,
    target_id varchar(100) not null,
    before jsonb,
    after jsonb
);
create index audit_log_by_time on audit_log (occurred_at desc);
create index audit_log_by_target on audit_log (target_type, target_id);

create function audit_log_is_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'audit_log is append-only';
end;
$$;

create trigger audit_log_no_update_or_delete
    before update or delete on audit_log
    for each statement execute function audit_log_is_append_only();

create trigger audit_log_no_truncate
    before truncate on audit_log
    for each statement execute function audit_log_is_append_only();
