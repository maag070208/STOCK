#!/usr/bin/env bash
# Fills the database with the data the business runs on, so the screens can be looked at with something real:
# suppliers, customers, their contacts, the materials of a hardware store, three warehouses with their locations and the
# payment terms. It is idempotent (running it twice changes nothing) and it is not a migration: it never ships to
# production. The e2e suite empties the business tables when it finishes (scripts/e2e-reset.sh), so run it again after a
# test run.
#
#   scripts/seed-demo.sh
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="${DB_CONTAINER:-stockflow-db-1}"
DB="${DB_NAME:-stockflow}"
DB_USER="${DB_USER:-stockflow}"

if [ -z "${DOCKER_HOST:-}" ] && [ -S "$HOME/.colima/default/docker.sock" ]; then
	export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
fi

echo "Sembrando datos reales en $DB (contenedor $CONTAINER)"
docker exec -i "$CONTAINER" psql -q -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB" <"$REPO/scripts/seed-demo.sql"

docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB" -c "select
	(select count(*) from suppliers) as proveedores,
	(select count(*) from supplier_contacts) as contactos_de_proveedor,
	(select count(*) from customers) as clientes,
	(select count(*) from customer_contacts) as contactos_de_cliente,
	(select count(*) from products) as materiales,
	(select count(*) from warehouses) as almacenes,
	(select count(*) from locations) as ubicaciones;"
