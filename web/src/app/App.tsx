import { HashRouter, Navigate, Outlet, Route, Routes } from "react-router-dom";
import { screenById } from "@entities/permission";
import { AccessPage } from "@pages/access/AccessPage";
import { AuditPage } from "@pages/audit/AuditPage";
import { CatalogsPage } from "@pages/catalogs/CatalogsPage";
import { ChangePasswordPage } from "@pages/change-password/ChangePasswordPage";
import { HomePage } from "@pages/home/HomePage";
import { LoginPage } from "@pages/login/LoginPage";
import { PartnerFormPage } from "@pages/partners/PartnerFormPage";
import { PartnerPage } from "@pages/partners/PartnerPage";
import { PartnersPage } from "@pages/partners/PartnersPage";
import { ProductImportPage } from "@pages/products/ProductImportPage";
import { ProductPage } from "@pages/products/ProductPage";
import { ProductsPage } from "@pages/products/ProductsPage";
import { DiscrepanciesPage } from "@pages/invoices/DiscrepanciesPage";
import { InvoicePage } from "@pages/invoices/InvoicePage";
import { InvoicesPage } from "@pages/invoices/InvoicesPage";
import { OrderFormPage } from "@pages/orders/OrderFormPage";
import { OrderPage } from "@pages/orders/OrderPage";
import { OrdersPage } from "@pages/orders/OrdersPage";
import { LabelTemplatePage } from "@pages/labels/LabelTemplatePage";
import { LabelTemplatesPage } from "@pages/labels/LabelTemplatesPage";
import { PrintersPage } from "@pages/printers/PrintersPage";
import { InvoicesReportPage } from "@pages/reports/InvoicesReportPage";
import { KardexPage } from "@pages/reports/KardexPage";
import { MovementsReportPage } from "@pages/reports/MovementsReportPage";
import { OrdersReportPage } from "@pages/reports/OrdersReportPage";
import { StockReportPage } from "@pages/reports/StockReportPage";
import { OpeningInventoryPage } from "@pages/stock/OpeningInventoryPage";
import { StockPage } from "@pages/stock/StockPage";
import { UsersPage } from "@pages/users/UsersPage";
import { WarehousePage } from "@pages/warehouses/WarehousePage";
import { WarehousesPage } from "@pages/warehouses/WarehousesPage";
import { AppShell } from "@widgets/app-shell/AppShell";
import { BrandIntro } from "@widgets/brand-intro/BrandIntro";
import { RequirePermission, SessionGate } from "./guards/Guards";
import { Toasts } from "./toast/Toasts";

// A route of a screen in APP_SCREENS, guarded by the same permissions the menu uses.
const guarded = (id: string, element: React.ReactNode) => <RequirePermission anyOf={screenById(id).anyOf ?? []}>{element}</RequirePermission>;

export function App() {
	return (
		<HashRouter>
			<BrandIntro />
			<Toasts />
			<SessionGate>
				<Routes>
					<Route path="/login" element={<LoginPage />} />
					<Route path="/change-password" element={<ChangePasswordPage />} />
					<Route
						element={
							<AppShell>
								<Outlet />
							</AppShell>
						}
					>
						<Route index element={<HomePage />} />
						<Route path="/warehouses" element={guarded("warehouses", <WarehousesPage />)} />
						<Route path="/warehouses/:id" element={guarded("warehouses", <WarehousePage />)} />
						<Route path="/products" element={guarded("products", <ProductsPage />)} />
						{/* The import changes data: it needs products.manage, not only the screen's permission. */}
						<Route path="/products/import" element={<RequirePermission anyOf={["products.manage"]}><ProductImportPage /></RequirePermission>} />
						<Route path="/products/:id" element={guarded("products", <ProductPage />)} />
						<Route path="/invoices" element={guarded("invoices", <InvoicesPage />)} />
						<Route path="/invoices/:id" element={guarded("invoices", <InvoicePage />)} />
						<Route path="/orders" element={guarded("orders", <OrdersPage />)} />
						<Route path="/orders/new" element={<RequirePermission anyOf={["orders.create"]}><OrderFormPage /></RequirePermission>} />
						<Route path="/orders/:id" element={guarded("orders", <OrderPage />)} />
						<Route path="/discrepancies" element={guarded("discrepancies", <DiscrepanciesPage />)} />
						<Route path="/stock" element={guarded("stock", <StockPage />)} />
						<Route path="/stock/opening" element={<RequirePermission anyOf={["stock.opening"]}><OpeningInventoryPage /></RequirePermission>} />
						{/* The reports: one address per report, so a link points at one of them (reports.md section 3). */}
						<Route path="/reports" element={guarded("reports", <Navigate to="/reports/stock" replace />)} />
						<Route path="/reports/stock" element={guarded("reports", <StockReportPage />)} />
						<Route path="/reports/kardex" element={guarded("reports", <KardexPage />)} />
						<Route path="/reports/movements" element={guarded("reports", <MovementsReportPage />)} />
						<Route path="/reports/orders" element={guarded("reports", <OrdersReportPage />)} />
						<Route path="/reports/invoices" element={guarded("reports", <InvoicesReportPage />)} />
						{(["suppliers", "customers"] as const).map((kind) => [
							<Route key={kind} path={`/${kind}`} element={guarded(kind, <PartnersPage key={kind} kind={kind} />)} />,
							// Creating and editing need the manage permission: without it the form answers with the 403 page.
							<Route key={`${kind}-new`} path={`/${kind}/new`} element={<RequirePermission anyOf={[`${kind}.manage`]}><PartnerFormPage key={`${kind}-new`} kind={kind} /></RequirePermission>} />,
							<Route key={`${kind}-detail`} path={`/${kind}/:id`} element={guarded(kind, <PartnerPage key={kind} kind={kind} />)} />,
							<Route key={`${kind}-edit`} path={`/${kind}/:id/edit`} element={<RequirePermission anyOf={[`${kind}.manage`]}><PartnerFormPage key={`${kind}-edit`} kind={kind} /></RequirePermission>} />,
						])}
						<Route path="/labels" element={guarded("labels", <LabelTemplatesPage />)} />
						<Route path="/labels/new" element={<RequirePermission anyOf={["labels.manage"]}><LabelTemplatePage key="new" /></RequirePermission>} />
						<Route path="/labels/:id" element={guarded("labels", <LabelTemplatePage key="edit" />)} />
						<Route path="/printers" element={guarded("printers", <PrintersPage />)} />
						<Route path="/users" element={guarded("users", <UsersPage />)} />
						<Route path="/roles" element={guarded("roles", <AccessPage />)} />
						<Route path="/catalogs" element={guarded("catalogs", <CatalogsPage />)} />
						<Route path="/audit" element={guarded("audit", <AuditPage />)} />
					</Route>
					<Route path="*" element={<Navigate to="/" replace />} />
				</Routes>
			</SessionGate>
		</HashRouter>
	);
}
