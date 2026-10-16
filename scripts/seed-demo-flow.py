#!/usr/bin/env python3
"""El flujo del negocio, sembrado de verdad: facturas de compra con sus materiales, su recepción, su colocación en las
ubicaciones (que es lo que mete existencias) y órdenes de venta con su surtido.

Va contra el API y no contra la base: cada paso pasa por las mismas reglas que usa una persona — la cantidad que suma,
la asignación que cuadra, el conteo, la colocación, la validación de la orden y el surtido con lo que hay en el almacén.
Por eso el stock que queda es stock de verdad, con sus movimientos y su kardex.

Uso:  scripts/seed-demo-flow.py
"""
from __future__ import annotations

import json
import random
import urllib.error
import urllib.request
from datetime import date, timedelta
from pathlib import Path
from uuid import uuid4

API = "http://localhost:8080/api/v1"
REPO = Path(__file__).resolve().parent.parent


def setting(name: str) -> str:
	for line in (REPO / ".env").read_text().splitlines():
		if line.startswith(f"{name}="):
			return line.split("=", 1)[1].strip()
	raise SystemExit(f"{name} no está en .env")


TOKEN = ""


def call(method: str, path: str, body=None, token: str | None = None):
	request = urllib.request.Request(
		API + path,
		method=method,
		data=None if body is None else json.dumps(body).encode(),
		headers={
			"Content-Type": "application/json",
			"X-Client-Channel": "web",
			# Una creación lleva su clave: repetir la misma petición no crea dos veces (api.md sección 2).
			"Idempotency-Key": str(uuid4()),
			**({"Authorization": f"Bearer {token or TOKEN}"} if (token or TOKEN) else {}),
		},
	)
	try:
		with urllib.request.urlopen(request) as answer:
			raw = answer.read().decode()
			return json.loads(raw) if raw else None
	except urllib.error.HTTPError as failure:
		detail = failure.read().decode()
		raise SystemExit(f"{method} {path} → {failure.code}: {detail[:400]}") from None


def login() -> None:
	global TOKEN
	TOKEN = call("POST", "/auth/login", {"username": "e2e-admin", "password": setting("E2E_ADMIN_PASSWORD")}, token="")["token"]


def one(path: str, body) -> dict:
	rows = call("POST", path, body)
	data = rows["data"] if isinstance(rows, dict) else rows
	return data[0] if data else {}


def operators_for(warehouse_id: str, how_many: int = 2) -> list[dict]:
	"""Los operadores del almacén: se crean si no hay, y quedan en su plantilla (que es de donde salen las asignaciones)."""
	members = call("GET", f"/warehouses/{warehouse_id}/staff")
	if len(members) >= how_many:
		return members[:how_many]
	made = []
	for index in range(how_many - len(members)):
		created = call("POST", "/users", {"username": f"almacen{index + 1}", "name": f"Operador de almacén {index + 1}", "roles": ["WAREHOUSE_OPERATOR"]})
		user_id = created["user"]["id"]
		call("PUT", f"/users/{user_id}/warehouses", {"warehouseIds": [warehouse_id]})
		made.append(user_id)
	call("PUT", f"/warehouses/{warehouse_id}/staff", {"userIds": [member["id"] for member in members] + made})
	return call("GET", f"/warehouses/{warehouse_id}/staff")[:how_many]


def receive(invoice: dict, staff: list[dict], locations: list[dict]) -> None:
	"""La recepción de una factura: se asigna cada línea a una persona (que es lo que la pone en asignación), y luego se
	cuenta y se coloca lo recibido. Es lo que mete la existencia, con su movimiento."""
	assignments = []
	for index, line in enumerate(invoice["lines"]):
		operator = staff[index % len(staff)]
		assigned = call("PUT", f"/invoices/{invoice['id']}/lines/{line['id']}/assignments", {"assignments": [{"operatorId": operator["id"], "quantity": line["quantity"]}]})
		assignments.append((line, next(row for row in assigned["assignments"] if row["operator"]["id"] == operator["id"]), locations[index % len(locations)]))
	# Se cuenta todo primero: la factura pasa a "en asignación" cuando ya se contó todo lo asignado, y solo entonces se
	# puede colocar. Al colocar todo, el API la cierra sola.
	for line, assignment, _location in assignments:
		call("POST", f"/receipts/assignments/{assignment['id']}/count", {"countedQuantity": line["quantity"]})
	for line, assignment, location in assignments:
		call("POST", f"/receipts/assignments/{assignment['id']}/placements", {"placements": [{"locationId": location["id"], "quantity": line["quantity"]}]})


def main() -> None:
	login()
	random.seed(7)

	warehouses = {row["code"]: row for row in call("POST", "/warehouses/query", {"page": 1, "limit": 50})["data"]}
	warehouse = warehouses["ALM-CEN"]
	others = [warehouses[code] for code in ("ALM-NOR", "ALM-SUR") if code in warehouses]
	suppliers = call("POST", "/suppliers/query", {"page": 1, "limit": 50})["data"]
	customers = call("POST", "/customers/query", {"page": 1, "limit": 50})["data"]
	products = call("POST", "/products/query", {"page": 1, "limit": 100})["data"]
	if not (suppliers and customers and products):
		raise SystemExit("Faltan los datos base: corre primero scripts/seed-demo.sh")

	# Las ubicaciones del almacén, para colgar de ellas lo que se recibe.
	locations = [row for row in call("POST", "/locations/query", {"page": 1, "limit": 100, "filters": {"warehouseId": warehouse["id"], "active": "true"}})["data"] if row.get("active")]
	if not locations:
		raise SystemExit("El almacén central no tiene ubicaciones: corre scripts/seed-demo.sh")

	staff = operators_for(warehouse["id"], 2)
	today = date.today()

	def purchase(number: str, supplier: dict, how_many: int, close: bool) -> dict:
		"""Una factura de compra con sus materiales; si se cierra, se recibe y se coloca todo (y queda existencia)."""
		chosen = random.sample(products, how_many)
		lines = [{"productId": product["id"], "quantity": random.choice([12, 20, 24, 40, 60, 100])} for product in chosen]
		invoice = call("POST", "/invoices", {
			"number": number,
			"supplierId": supplier["id"],
			"invoiceDate": today.isoformat(),
			"warehouseId": warehouse["id"],
			"lines": lines,
		})
		if close:
			receive(invoice, staff, locations)
			invoice = call("GET", f"/invoices/{invoice['id']}")
		print(f"  factura {number}: {len(lines)} materiales, estado {invoice['status']['code']}")
		return invoice, (chosen if close else [])

	def stock_rows() -> list[dict]:
		"""Dónde está cada material: la existencia vive en una ubicación, y de ahí se surte."""
		rows = call("POST", "/stock/balances/query", {"page": 1, "limit": 300, "filters": {"warehouseId": warehouse["id"]}})
		return [row for row in rows["data"] if float(row.get("quantity") or 0) >= 1 and row.get("locationId") and row.get("productId")]

	def sale(number: str, customer: dict, how_many: int, fulfill: bool, available: list[dict]) -> dict:
		"""Una orden de venta. El almacén la surte (asignación y surtido de cada línea) y la oficina la valida después: la
		validación pide la orden surtida, que es lo que confirma la oficina."""
		# Lo que se vende es lo que hay, y se surte de la ubicación donde está.
		chosen = random.sample(available, min(how_many, len(available)))
		lines = [{"productId": row["productId"], "quantity": min(random.choice([1, 2, 3, 5, 8]), int(float(row["quantity"])))} for row in chosen]
		order = call("POST", "/orders", {
			"number": number,
			"warehouseId": warehouse["id"],
			"customerId": customer["id"],
			"dueDate": (today + timedelta(days=7)).isoformat(),
			"lines": lines,
		})
		call("PUT", f"/orders/{order['id']}/assignment", {"operatorId": staff[0]["id"]})
		order = call("GET", f"/orders/{order['id']}")
		picking = order["lines"] if fulfill else order["lines"][: max(1, len(lines) // 3)]
		for index, line in enumerate(picking):
			# La ubicación de la que sale: la que tiene existencia de ese material.
			row = next(candidate for candidate in chosen if candidate["productId"] == line["product"]["id"])
			call("POST", f"/orders/{order['id']}/picks", {"lineId": line["id"], "locationId": row["locationId"], "quantity": line["quantity"]})
		if fulfill:
			order = call("POST", f"/orders/{order['id']}/validation", {})
		order = call("GET", f"/orders/{order['id']}")
		print(f"  orden {number}: {len(lines)} materiales, estado {order['status']['code']}")
		return order

	print("Compras: se reciben y se colocan en el almacén central")
	_first, first_products = purchase("F-ACN-1042", suppliers[0], 16, close=True)
	_second, second_products = purchase("F-TUB-2087", suppliers[1], 12, close=True)
	purchase("F-ELC-3310", suppliers[2], 10, close=False)
	# Un material puede venir en las dos facturas: la orden no repite una línea del mismo material.
	# Los materiales que entraron, para lo que sigue.
	received = list({product["id"]: product for product in first_products + second_products}.values())

	print("Ventas: salen del almacén central")
	# Lo que hay, con su ubicación: de ahí salen las órdenes.
	available = []
	for row in stock_rows():
		if all(row["productId"] != seen["productId"] for seen in available):
			available.append(row)
	_first_products = None
	sale("OC-CON-5501", customers[0], 14, True, available)
	sale("OC-FER-5502", customers[1], 10, False, available)
	sale("OC-ING-5503", customers[2], 12, False, available)

	# Lo que quedó: existencias del almacén central, que es lo que se ve en la pantalla.
	left = stock_rows()
	total = sum(float(row.get("quantity") or 0) for row in left)
	print(f"Existencias en {warehouse['name']}: {len(left)} renglones con existencia, {total:.0f} piezas.")
	print("Listo: compras recibidas y colocadas, y órdenes en tres estados (validada, surtiendo y por surtir).")


if __name__ == "__main__":
	main()
