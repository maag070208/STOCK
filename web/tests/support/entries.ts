// What a material entry needs, created through the API as a person would (testing.md rule 4): a supplier, a warehouse
// with a rack, materials, and operators of that warehouse in its staff template.
import { adminToken, call, newUser, testPaymentTerms, uniqueName, type TestUser } from "./api";

export interface EntriesFixture {
	token: string;
	supplier: { id: string; code: string; name: string };
	inactiveSupplier: { id: string; name: string };
	warehouse: { id: string; code: string };
	locations: string[];
	operators: TestUser[];
	product: (name?: string) => Promise<{ id: string; code: string; name: string }>;
}

async function supplier(token: string, name: string) {
	const terms = await testPaymentTerms();
	const body = {
		code: uniqueName("PR-").toUpperCase(), name, taxId: "ADN010203AB1", paymentTerms: terms.code,
		address: { street: "Av. Juárez", exteriorNumber: "120", neighborhood: "Centro", city: "Guadalajara", state: "MX_JAL", postalCode: "44100", country: "MX" },
	};
	return (await call("POST", "/suppliers", { token, body })).json as { id: string; code: string; name: string };
}

export async function entriesFixture(): Promise<EntriesFixture> {
	const token = await adminToken();
	const active = await supplier(token, `Aceros ${uniqueName("").slice(0, 6)}`);
	const inactive = await supplier(token, `Inactivo ${uniqueName("").slice(0, 6)}`);
	await call("DELETE", `/suppliers/${inactive.id}`, { token });
	const warehouse = (await call("POST", "/warehouses", { token, body: { code: uniqueName("W-").toUpperCase(), name: "Almacén de entradas" } })).json as { id: string; code: string };
	const rack = (await call("POST", "/locations/racks", { token, body: { warehouseId: warehouse.id, aisle: "K", rack: "05", levels: 2 } })).json as { locations: { id: string }[] };
	const operators: TestUser[] = [];
	for (let i = 0; i < 2; i++) {
		const operator = await newUser(["WAREHOUSE_OPERATOR"], { channel: "app" });
		await call("PUT", `/users/${operator.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });
		operators.push(operator);
	}
	await call("PUT", `/warehouses/${warehouse.id}/staff`, { token, body: { userIds: operators.map((o) => o.id) } });
	const product = async (name = "Tornillo de prueba") =>
		(await call("POST", "/products", { token, body: { code: uniqueName("M").toUpperCase(), name, unit: "PIECE" } })).json as { id: string; code: string; name: string };
	return { token, supplier: active, inactiveSupplier: inactive, warehouse, locations: rack.locations.map((l) => l.id), operators, product };
}
