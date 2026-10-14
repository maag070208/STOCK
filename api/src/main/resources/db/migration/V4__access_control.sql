-- RBAC with scopes, per-user exceptions and ABAC policies (docs/rules/access-control.md section 3).
-- Roles, permissions and policies use their key as natural key.

create table permissions (
    key varchar(60) primary key,
    module varchar(40) not null,
    name_key varchar(100) not null,
    scopes varchar(4)[] not null,
    sensitive boolean not null default false,
    active boolean not null default true,
    sort_order integer not null,
    constraint permissions_key_format check (key ~ '^[a-z_]+(\.[a-z_]+)+$')
);

create table roles (
    key varchar(40) primary key,
    name varchar(120),
    system boolean not null default false,
    active boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint roles_key_format check (key ~ '^[A-Z][A-Z0-9_]{1,39}$')
);

-- A missing row means NONE.
create table role_permissions (
    role_key varchar(40) not null references roles (key) on delete cascade,
    permission_key varchar(60) not null references permissions (key),
    scope varchar(4) not null,
    primary key (role_key, permission_key),
    constraint role_permissions_scope check (scope in ('OWN', 'AREA', 'ALL'))
);

create table user_roles (
    user_id uuid not null references users (id),
    role_key varchar(40) not null references roles (key),
    primary key (user_id, role_key)
);

-- An exception for one person. It replaces what the roles give, and can also take access away (NONE).
create table user_permissions (
    id uuid primary key,
    user_id uuid not null references users (id),
    permission_key varchar(60) not null references permissions (key),
    scope varchar(4) not null,
    reason varchar(500) not null,
    expires_at timestamptz,
    granted_by uuid not null references users (id),
    created_at timestamptz not null,
    unique (user_id, permission_key),
    constraint user_permissions_scope check (scope in ('NONE', 'OWN', 'AREA', 'ALL'))
);

-- The warehouses of a user's AREA scope. The warehouses table arrives in F2, which adds the foreign key.
create table user_warehouses (
    user_id uuid not null references users (id),
    warehouse_id uuid not null,
    primary key (user_id, warehouse_id)
);

create table policies (
    key varchar(60) primary key,
    priority integer not null,
    effect varchar(5) not null,
    action varchar(60) not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint policies_key_format check (key ~ '^[A-Z][A-Z0-9_]{1,59}$'),
    constraint policies_effect check (effect in ('ALLOW', 'DENY'))
);

create table policy_conditions (
    policy_key varchar(60) not null references policies (key) on delete cascade,
    field varchar(60) not null,
    operator varchar(12) not null,
    value jsonb not null,
    primary key (policy_key, field, operator)
);

-- No rows for a policy means it applies to every role.
create table policy_roles (
    policy_key varchar(60) not null references policies (key) on delete cascade,
    role_key varchar(40) not null references roles (key) on delete cascade,
    primary key (policy_key, role_key)
);
