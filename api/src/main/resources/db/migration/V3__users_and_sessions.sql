-- People who sign in, and their sessions (docs/modules/identity.md).
create table users (
    id uuid primary key,
    username varchar(60) not null unique,
    name varchar(120) not null,
    email varchar(254),
    language varchar(2) not null default 'es',
    password_hash varchar(100) not null,
    must_change_password boolean not null default true,
    active boolean not null default true,
    failed_logins integer not null default 0,
    locked_at timestamptz,
    last_login_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users (id),
    constraint users_username_format check (username ~ '^[a-z0-9._-]{3,60}$'),
    constraint users_language_supported check (language in ('es', 'en')),
    constraint users_failed_logins_not_negative check (failed_logins >= 0)
);

-- A refresh token is stored as its SHA-256 hash, never in clear. It is used once: refreshing revokes it.
create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users (id),
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null
);
create index refresh_tokens_active_by_user on refresh_tokens (user_id) where revoked_at is null;
