#!/usr/bin/env bash
# Puts the end-to-end database back to something a person can read.
#
# The e2e suite creates its own data through the API and most of it stays: every run leaves users, products, suppliers,
# warehouses, invoices and audit rows behind. After a few runs the lists are hundreds of rows long, the screens answer
# slowly and a test that assumed a row was on the first page fails for a reason that has nothing to do with the app.
#
# This script keeps what the API seeds (the catalogs, the permissions, the five seed roles) and the real accounts, and
# empties everything else. It backs the database up first, so a mistake is a restore away.
#
# Usage:
#   scripts/e2e-reset.sh                      # backup, then reset
#   KEEP_USERS="aamaro e2e-admin ana" scripts/e2e-reset.sh
#   scripts/e2e-reset.sh --no-backup          # when a backup is already taken
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="${DB_CONTAINER:-stockflow-db-1}"
DB="${DB_NAME:-stockflow}"
DB_USER="${DB_USER:-stockflow}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/.stockflow-backups}"
KEEP_USERS="${KEEP_USERS:-aamaro e2e-admin}"
KEEP_ROLES="${KEEP_ROLES:-ADMIN OFFICE WAREHOUSE_MANAGER WAREHOUSE_OPERATOR APP_USER}"

# Colima keeps its own socket; the Docker CLI of a Mac often does not know about it.
if [ -z "${DOCKER_HOST:-}" ] && [ -S "$HOME/.colima/default/docker.sock" ]; then
	export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
fi

sql() {
	docker exec -i "$CONTAINER" psql -q -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB" "$@"
}

quoted() {
	# "a b" -> 'a','b'
	printf "'%s'" "$(printf "%s" "$1" | tr ' ' ',')" | sed "s/,/','/g"
}

USERS_SQL="$(quoted "$KEEP_USERS")"
ROLES_SQL="$(quoted "$KEEP_ROLES")"

if [ -t 1 ]; then BOLD="$(printf '\033[1m')"; DIM="$(printf '\033[2m')"; OFF="$(printf '\033[0m')"; else BOLD=""; DIM=""; OFF=""; fi

echo "${BOLD}End-to-end database: $DB (container $CONTAINER)${OFF}"
echo "${DIM}keeping users:  $KEEP_USERS${OFF}"
echo "${DIM}keeping roles:  $KEEP_ROLES${OFF}"

if [ "${1:-}" != "--no-backup" ]; then
	mkdir -p "$BACKUP_DIR"
	STAMP="$(date +%Y%m%d-%H%M%S)"
	FILE="$BACKUP_DIR/stockflow-$STAMP.dump"
	echo "backing up to $FILE"
	docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB" -Fc >"$FILE"
	echo "${DIM}restore with: docker exec -i $CONTAINER pg_restore -U $DB_USER -d $DB --clean --if-exists < $FILE${OFF}"
fi

echo "emptying the tables the tests fill"
sql <<SQL
BEGIN;

-- 1. Everything the tests fill, except the tables the seeds live in. TRUNCATE ... CASCADE takes whatever points at
--    them, which is also what frees the rows that reference the people we are about to delete.
DO \$\$
DECLARE target text;
DECLARE targets text[];
BEGIN
	SELECT array_agg(tablename) INTO targets FROM pg_tables
	WHERE schemaname = 'public'
		AND tablename NOT IN ('users', 'roles', 'permissions', 'role_permissions', 'user_roles', 'user_warehouses',
			'user_permissions', 'catalog', 'catalog_item', 'catalog_item_label', 'label_templates',
			'label_template_versions', 'flyway_schema_history');

	-- Some tables guard themselves: stock movements and the audit log refuse to be changed, which is what the business
	-- wants and what a reset has to lift for a moment (the transaction puts the guards back).
	FOREACH target IN ARRAY targets LOOP
		EXECUTE format('ALTER TABLE %I DISABLE TRIGGER USER', target);
	END LOOP;
	FOREACH target IN ARRAY targets LOOP
		EXECUTE format('TRUNCATE TABLE %I CASCADE', target);
	END LOOP;
	FOREACH target IN ARRAY targets LOOP
		EXECUTE format('ALTER TABLE %I ENABLE TRIGGER USER', target);
	END LOOP;
END \$\$;

-- 2. The people the tests invented, and the roles they invented for themselves. The accounts that are really used stay.
DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username NOT IN ($USERS_SQL));
DELETE FROM user_warehouses WHERE user_id IN (SELECT id FROM users WHERE username NOT IN ($USERS_SQL));
DELETE FROM user_permissions WHERE user_id IN (SELECT id FROM users WHERE username NOT IN ($USERS_SQL));

-- What the seeds point at is let go before the rows disappear: the trail of who created a catalog is not worth a row.
UPDATE catalog SET created_by = NULL WHERE created_by IN (SELECT id FROM users WHERE username NOT IN ($USERS_SQL));
UPDATE catalog_item SET created_by = NULL WHERE created_by IN (SELECT id FROM users WHERE username NOT IN ($USERS_SQL));
UPDATE user_permissions SET granted_by = NULL WHERE granted_by IN (SELECT id FROM users WHERE username NOT IN ($USERS_SQL));
UPDATE users SET created_by = NULL WHERE created_by IN (SELECT id FROM users WHERE username NOT IN ($USERS_SQL));
DELETE FROM users WHERE username NOT IN ($USERS_SQL);

DELETE FROM role_permissions WHERE role_key NOT IN ($ROLES_SQL);
DELETE FROM roles WHERE key NOT IN ($ROLES_SQL);

-- The label templates the API seeds (PRODUCT, LOCATION) stay: the print dialog asks for them by key. The ones the tests
-- wrote are named T_ and go away.
DELETE FROM label_template_versions WHERE template_id IN (SELECT id FROM label_templates WHERE key ~ '^T_');
DELETE FROM label_templates WHERE key ~ '^T_';

-- The catalogs the tests invent (a TEST_ one per run, plus the payment term they add) go away; the seeded ones stay.
DELETE FROM catalog_item_label WHERE catalog_item_id IN (
	SELECT item.id FROM catalog_item item JOIN catalog parent ON parent.id = item.catalog_id
	WHERE item.code ~ '^(E2E|TEST|PRUEBA)' OR parent.code ~ '^(E2E|TEST|PRUEBA)');
DELETE FROM catalog_item WHERE code ~ '^(E2E|TEST|PRUEBA)' OR catalog_id IN (SELECT id FROM catalog WHERE code ~ '^(E2E|TEST|PRUEBA)');
DELETE FROM catalog WHERE code ~ '^(E2E|TEST|PRUEBA)';

COMMIT;
SQL

echo "${BOLD}what is left${OFF}"
sql -c "select 'users' as tabla, count(*) from users
	union all select 'roles', count(*) from roles
	union all select 'permissions', count(*) from permissions
	union all select 'role_permissions', count(*) from role_permissions
	union all select 'catalogs', count(*) from catalog
	union all select 'catalog items', count(*) from catalog_item
	union all select 'products', count(*) from products
	union all select 'warehouses', count(*) from warehouses
	union all select 'suppliers', count(*) from suppliers
	union all select 'invoices', count(*) from invoices
	union all select 'orders', count(*) from orders
	union all select 'stock movements', count(*) from stock_movements
	union all select 'audit rows', count(*) from audit_log
	order by tabla;"

echo "${DIM}the API does not need a restart: the seeds it needs are still there.${OFF}"
