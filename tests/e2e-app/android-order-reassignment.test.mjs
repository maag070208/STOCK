// F5-19 · e2e: reassigning in the app. The person sees the alert of an order that is due tomorrow, opens it, scans the
// material that was already picked, moves the quantity to another open order of the same warehouse, and the source order
// keeps its lines (docs/modules/order-fulfillment.md section 8, barcode-scanning.md section 4). The device is driven
// over adb, and the API is reached through its port forward, as a person would use the app.
import { test } from "node:test";
import assert from "node:assert/strict";
import { enter, fill, launch, signIn, sleep, tap, type, waitForText, waitUntil } from "./lib/android.mjs";
import { adminToken, alerts, balances, call, dueIn, grant, newCustomer, newOrder, newProduct, newRack, newUser, newWarehouse, order, pick, staff, stockIn } from "./lib/api.mjs";

// An administrator on the app channel: the roles of the warehouse have no app access, or no reassignment, and the
// administrator has both everywhere.
async function reassigner() {
  const token = await adminToken();
  const user = await newUser(["ADMIN"], { channel: "app" });
  await grant(user.id, "orders.reassign", "ALL");
  await grant(user.id, "orders.fulfill", "ALL");
  return { token, user };
}

// The page of the orders module that holds the alerts and the reassignment.
async function openReassignment(user) {
  await launch({ fresh: true });
  await signIn(user.username, user.password);
  const home = await waitUntil((xml) => xml.includes('resource-id="module-orders"'), 30000);
  assert.ok(home, "the home offers the orders");
  tap(home, "module-orders");
  const menu = await waitUntil((xml) => xml.includes('resource-id="page-orders-reassignment"'), 15000);
  assert.ok(menu, "the orders menu holds the reassignment page");
  tap(menu, "page-orders-reassignment");
}

test("e2e: the alert of an order due tomorrow opens, and its picked material moves to another order", async () => {
  const { token, user } = await reassigner();
  const warehouse = await newWarehouse();
  const [location] = await newRack(warehouse.id);
  const product = await newProduct("Codo galvanizado de prueba");
  const customer = await newCustomer();
  // The administrator works in this warehouse, so its orders can be assigned to them and they validate them.
  await call("PUT", `/users/${user.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });
  await staff(warehouse.id, [user.id]);
  await stockIn({ warehouseId: warehouse.id, locationId: location.id, operator: user, product, quantity: 100 });
  const source = await newOrder({ warehouseId: warehouse.id, customerId: customer.id, product, quantity: 30, operatorId: user.id, dueDate: dueIn(1) });
  await pick(user, source.id, source.lines[0].id, location.id, 20);
  const target = await newOrder({ warehouseId: warehouse.id, customerId: customer.id, product, quantity: 20, assign: false });
  // The alert of the source order is the one the operator sees.
  const mine = await alerts(user);
  assert.ok(mine.some((alert) => alert.order.number === source.number && alert.kind === "BEFORE"), JSON.stringify(mine));

  await openReassignment(user);
  // The alert of an order due tomorrow is shown in the language of the person.
  const list = await waitForText(source.number, 30000);
  assert.match(list, /text="Vence mañana · Entrega /);
  assert.match(list, /resource-id="alert-ORD-/);
  tap(list, `alert-${source.number}`);
  await waitForText("Escanea el material surtido");

  // The material that was picked opens its line, with the order that needs it.
  await fill("reassign-scan", product.code);
  enter();
  const holding = await waitForText("Surtido: 20");
  assert.match(holding, new RegExp(`resource-id="reassign-target-${target.number}"`), "the order that needs the material is offered");

  // The note and the quantity: the keyboard's Next moves to the quantity, and its Done sends the move.
  tap(holding, `reassign-target-${target.number}`);
  await fill("reassign-note", "Traspaso-prueba");
  enter();
  await sleep(400);
  type("20");
  enter();
  await waitForText("Traspaso registrado.");

  // The API moved the picked quantities: the source has nothing picked, the target has it all, and no balance changed.
  const moved = await order(source.id);
  assert.equal(Number(moved.lines[0].pickedQuantity), 0);
  assert.equal(moved.status.code, "FULFILLING");
  const received = await order(target.id);
  assert.equal(Number(received.lines[0].pickedQuantity), 20);
  assert.equal(received.status.code, "FULFILLED");
  const left = await balances(warehouse.id, product.id);
  assert.equal(left.length, 1);
  assert.equal(Number(left[0].quantity), 80);
});

test("e2e: a material the order has not picked is refused by the app", async () => {
  const { token, user } = await reassigner();
  const warehouse = await newWarehouse();
  const [location] = await newRack(warehouse.id);
  const product = await newProduct("Tornillo sin surtir");
  const customer = await newCustomer();
  await call("PUT", `/users/${user.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });
  await staff(warehouse.id, [user.id]);
  await stockIn({ warehouseId: warehouse.id, locationId: location.id, operator: user, product, quantity: 10 });
  const source = await newOrder({ warehouseId: warehouse.id, customerId: customer.id, product, quantity: 10, operatorId: user.id, dueDate: dueIn(1) });

  await openReassignment(user);
  const list = await waitForText(source.number, 30000);
  tap(list, `alert-${source.number}`);
  await waitForText("Escanea el material surtido");

  // Nothing of this material has been picked: the app says so and offers no target.
  await fill("reassign-scan", product.code);
  enter();
  const refused = await waitForText("La orden no tiene material surtido de ese código.");
  assert.match(refused, /resource-id="reassign-scan"/);
  assert.doesNotMatch(refused, /resource-id="reassign-target-ORD-/);
});
