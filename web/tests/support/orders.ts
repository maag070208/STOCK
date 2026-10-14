// What an order needs, created through the API as a person would (testing.md rule 4): a customer, a warehouse with a
// rack, materials with stock in it, and the operators of that warehouse in its staff template. Stock is put in a real
// location through the material entry flow (invoice, assignment, count and placement), so a pick takes it from a shelf.
import { adminToken, call, newUser, testPaymentTerms, uniqueName, type TestUser } from "./api";

export interface OrderJson {
	id: string;
	number: string;
	warehouse: { id: string; code: string };
	customer: { id: string; code: string; name: string };
	status: { code: string; label: string | null };
	operator: { id: string; name: string } | null;
	dueDate: string;
	lines: { id: string; product: { id: string; code: string; name: string }; quantity: number; pickedQuantity: number }[] | null;
}

export interface OrdersFixture {
	token: string;
	warehouse: { id: string; code: string };
	locations: { id: string; code: string }[];
	operators: TestUser[];
	customer: { id: string; code: string; name: string };
	product: (name?: string) => Promise<{ id: string; code: string; name: string }>;
	stock: (productId: string, quantity: number, locationIndex?: number) => Promise<void>;
	order: (input: { number: string; productId: string; quantity: number; operatorId?: string; dueDate?: string }) => Promise<OrderJson>;
	pick: (orderId: string, lineId: string, locationId: string, quantity: number, operator: TestUser) => Promise<void>;
	view: (orderId: string) => Promise<OrderJson>;
}

async function customer(token: string) {
	const terms = await testPaymentTerms();
	const body = {
		code: uniqueName("CL-").toUpperCase(), name: `Cliente ${uniqueName("").slice(0, 6)}`, taxId: "ADN010203AB1", paymentTerms: terms.code,
		address: { street: "Av. Juárez", exteriorNumber: "120", neighborhood: "Centro", city: "Guadalajara", state: "MX_JAL", postalCode: "44100", country: "MX" },
	};
	const created = await call("POST", "/customers", { token, body });
	if (created.status !== 201) throw new Error(`customer creation failed: ${JSON.stringify(created.json)}`);
	return created.json as { id: string; code: string; name: string };
}

async function supplier(token: string) {
	const terms = await testPaymentTerms();
	const body = {
		code: uniqueName("PR-").toUpperCase(), name: `Aceros ${uniqueName("").slice(0, 6)}`, taxId: "ADN010203AB1", paymentTerms: terms.code,
		address: { street: "Av. Juárez", exteriorNumber: "120", neighborhood: "Centro", city: "Guadalajara", state: "MX_JAL", postalCode: "44100", country: "MX" },
	};
	return (await call("POST", "/suppliers", { token, body })).json as { id: string; code: string; name: string };
}

export async function ordersFixture(): Promise<OrdersFixture> {
	const token = await adminToken();
	const client = await customer(token);
	const supplierPartner = await supplier(token);
	const warehouse = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Almacén de órdenes" } })).json as { id: string; code: string };
	const rack = (await call("POST", "/locations/racks", { token, body: { warehouseId: warehouse.id, aisle: "K", rack: "05", levels: 2 } })).json as { locations: { id: string; code: string }[] };
	const locations = rack.locations.map((location) => ({ id: location.id, code: location.code }));

	const operators: TestUser[] = [];
	const tokens = new Map<string, string>();
	for (let index = 0; index < 2; index++) {
		const operator = await newUser(["WAREHOUSE_OPERATOR"], { channel: "app" });
		await call("PUT", `/users/${operator.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });
		operators.push(operator);
		const login = await call("POST", "/auth/login", { body: { username: operator.username, password: operator.password }, channel: "app" });
		tokens.set(operator.username, login.json.token as string);
	}
	await call("PUT", `/warehouses/${warehouse.id}/staff`, { token, body: { userIds: operators.map((operator) => operator.id) } });

	const product = async (name = "Tornillo de orden") =>
		(await call("POST", "/products", { token, body: { code: uniqueName("M").toUpperCase(), name, unit: "PIECE" } })).json as { id: string; code: string; name: string };

	// Stock in a real location: the invoice flow the receiving screens use, counted and placed by the operator.
	const stock = async (productId: string, quantity: number, locationIndex = 0) => {
		const invoice = await call("POST", "/invoices", {
			token,
			body: { number: uniqueName("F-").toUpperCase(), supplierId: supplierPartner.id, invoiceDate: new Date().toISOString().slice(0, 10), warehouseId: warehouse.id, lines: [{ productId, quantity }] },
			headers: { "Idempotency-Key": uniqueName("k") },
		});
		if (invoice.status !== 201) throw new Error(`invoice creation failed: ${JSON.stringify(invoice.json)}`);
		const lineId = invoice.json.lines[0].id as string;
		const operator = operators[0];
		const assigned = await call("PUT", `/invoices/${invoice.json.id}/lines/${lineId}/assignments`, { token, body: { assignments: [{ operatorId: operator.id, quantity }] } });
		if (assigned.status !== 200) throw new Error(`assignment failed: ${JSON.stringify(assigned.json)}`);
		const assignmentId = assigned.json.assignments[0].id as string;
		const operatorToken = tokens.get(operator.username)!;
		const counted = await call("POST", `/receipts/assignments/${assignmentId}/count`, { token: operatorToken, body: { countedQuantity: String(quantity) }, channel: "app" });
		if (counted.status !== 200) throw new Error(`count failed: ${JSON.stringify(counted.json)}`);
		const placed = await call("POST", `/receipts/assignments/${assignmentId}/placements`, { token: operatorToken, body: { placements: [{ locationId: locations[locationIndex].id, quantity: String(quantity) }] }, channel: "app" });
		if (placed.status !== 200) throw new Error(`placement failed: ${JSON.stringify(placed.json)}`);
	};

	const order = async (input: { number: string; productId: string; quantity: number; operatorId?: string; dueDate?: string }) => {
		const due = input.dueDate ?? new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10);
		const created = await call("POST", "/orders", {
			token,
			body: { number: input.number, warehouseId: warehouse.id, customerId: client.id, dueDate: due, lines: [{ productId: input.productId, quantity: input.quantity }] },
			headers: { "Idempotency-Key": uniqueName("k") },
		});
		if (created.status !== 201) throw new Error(`order creation failed: ${JSON.stringify(created.json)}`);
		if (input.operatorId) {
			const assigned = await call("PUT", `/orders/${created.json.id}/assignment`, { token, body: { operatorId: input.operatorId } });
			if (assigned.status !== 200) throw new Error(`order assignment failed: ${JSON.stringify(assigned.json)}`);
		}
		return (await call("GET", `/orders/${created.json.id}`, { token })).json as OrderJson;
	};

	const pick = async (orderId: string, lineId: string, locationId: string, quantity: number, operator: TestUser) => {
		const picked = await call("POST", `/orders/${orderId}/picks`, {
			token: tokens.get(operator.username)!,
			body: { lineId, locationId, quantity },
			channel: "app",
		});
		if (picked.status !== 200) throw new Error(`pick failed: ${JSON.stringify(picked.json)}`);
	};

	const view = async (orderId: string) => (await call("GET", `/orders/${orderId}`, { token })).json as OrderJson;

	return { token, warehouse, locations, operators, customer: client, product, stock, order, pick, view };
}
