// F5-18 · e2e: picking in the app. The operator opens an order assigned to them, scans the material, scans the code of
// the location that holds it, enters the quantity, and the order is fulfilled (docs/modules/order-fulfillment.md
// section 7, barcode-scanning.md section 4). The device is driven over adb, and the API is reached through its port
// forward, as a person would use the app.
import { test } from "node:test";
import assert from "node:assert/strict";
import { enter, fill, launch, signIn, tap, waitForText, waitUntil } from "./lib/android.mjs";
import { balances, grant, newCustomer, newOrder, newProduct, newRack, newUser, newWarehouse, order, staff, stockIn, adminToken, call } from "./lib/api.mjs";

test("e2e: the operator picks the material of an order assigned to them", async () => {
  const token = await adminToken();
  const warehouse = await newWarehouse();
  const [location] = await newRack(warehouse.id);
  const product = await newProduct("Codo galvanizado de prueba");
  const customer = await newCustomer();
  const operator = await newUser(["WAREHOUSE_OPERATOR"]);
  // The operator works in this warehouse, reads its stock (the seed of an older database may not say so yet), and is in
  // its staff template, which is who an order can be assigned to.
  await call("PUT", `/users/${operator.id}/warehouses`, { token, body: { warehouseIds: [warehouse.id] } });
  await grant(operator.id, "stock.view", "AREA");
  await staff(warehouse.id, [operator.id]);
  await stockIn({ warehouseId: warehouse.id, locationId: location.id, operator, product, quantity: 100 });
  const created = await newOrder({ warehouseId: warehouse.id, customerId: customer.id, product, quantity: 80, operatorId: operator.id });

  await launch({ fresh: true });
  await signIn(operator.username, operator.password);
  const home = await waitUntil((xml) => xml.includes('resource-id="module-orders"'), 30000);
  assert.ok(home, "the home offers the orders to whoever fulfills them");
  tap(home, "module-orders");
  const menu = await waitUntil((xml) => xml.includes('resource-id="page-orders-my-orders"'), 15000);
  assert.ok(menu, "the orders menu holds the pick page");
  tap(menu, "page-orders-my-orders");

  // The order assigned to the operator is listed, and opens the pick form.
  const list = await waitForText(created.number, 30000);
  tap(list, `order-${created.number}`);
  await waitForText("Escanea el material");

  // The material is on the order: its line opens with the location that holds it.
  await fill("pick-scan", product.code);
  enter();
  const holding = await waitForText("Ubicaciones con existencia");
  assert.match(holding, new RegExp(`resource-id="pick-location-${location.code}"`), "the location that holds the material is offered");

  // The location is confirmed by its code, and the quantity is entered.
  await fill("pick-location-scan", location.code);
  enter();
  await fill("pick-quantity", "50");
  // The keyboard's Done sends the pick, as the other forms of the app do.
  enter();
  await waitForText("Surtido registrado.");

  // The API moved the order to FULFILLING, kept the pick on the line, and took the balance out of the location.
  const picked = await order(created.id);
  assert.equal(picked.status.code, "FULFILLING");
  assert.equal(Number(picked.lines[0].pickedQuantity), 50);
  const left = await balances(warehouse.id, product.id);
  assert.equal(left.length, 1);
  assert.equal(Number(left[0].quantity), 50);
});

test("e2e: a material that is not on the order is refused by the app", async () => {
  const warehouse = await newWarehouse();
  const [location] = await newRack(warehouse.id);
  const product = await newProduct("Tornillo de la orden");
  const other = await newProduct("Tuerca de otro pedido");
  const customer = await newCustomer();
  const operator = await newUser(["WAREHOUSE_OPERATOR"]);
  await call("PUT", `/users/${operator.id}/warehouses`, { token: await adminToken(), body: { warehouseIds: [warehouse.id] } });
  await grant(operator.id, "stock.view", "AREA");
  await staff(warehouse.id, [operator.id]);
  await stockIn({ warehouseId: warehouse.id, locationId: location.id, operator, product, quantity: 10 });
  const created = await newOrder({ warehouseId: warehouse.id, customerId: customer.id, product, quantity: 10, operatorId: operator.id });

  await launch({ fresh: true });
  await signIn(operator.username, operator.password);
  const home = await waitUntil((xml) => xml.includes('resource-id="module-orders"'), 30000);
  tap(home, "module-orders");
  const menu = await waitUntil((xml) => xml.includes('resource-id="page-orders-my-orders"'), 15000);
  tap(menu, "page-orders-my-orders");
  const list = await waitForText(created.number, 30000);
  tap(list, `order-${created.number}`);
  await waitForText("Escanea el material");

  // A material that belongs to no line of the order: the app says so and posts nothing.
  await fill("pick-scan", other.code);
  enter();
  const refused = await waitForText("Este material no está en la orden.");
  assert.match(refused, /resource-id="pick-scan"/);
  assert.doesNotMatch(refused, /resource-id="pick-line"/);
});
