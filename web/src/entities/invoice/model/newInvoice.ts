// The four fields of the modal of a new invoice (F8-06), in the order they are shown, and where each button leaves the
// person. Nothing else is captured here: the materials are added on the detail.

export const NEW_INVOICE_FIELDS = ["number", "supplierId", "invoiceDate", "warehouseId"] as const;

export type NewInvoiceField = (typeof NEW_INVOICE_FIELDS)[number];

export interface NewInvoiceValues {
	number: string;
	supplierId: string;
	invoiceDate: Date | null;
	warehouseId: string;
}

// What is missing, field by field, in the order of the fields: the first one with a problem takes the focus.
export function newInvoiceErrors(values: NewInvoiceValues): Partial<Record<NewInvoiceField, "FIELD_REQUIRED">> {
	const errors: Partial<Record<NewInvoiceField, "FIELD_REQUIRED">> = {};
	if (!values.number.trim()) errors.number = "FIELD_REQUIRED";
	if (!values.supplierId) errors.supplierId = "FIELD_REQUIRED";
	if (!values.invoiceDate) errors.invoiceDate = "FIELD_REQUIRED";
	if (!values.warehouseId) errors.warehouseId = "FIELD_REQUIRED";
	return errors;
}

// Saving a new invoice leads to the materials of that invoice: the flow the owner asked for (F8-06).
export function newInvoiceDestination(id: string): string {
	return `/invoices/${id}`;
}

// The last supplier and warehouse are remembered for the session, which is what the mock asks for: they are the two
// fields that cost a search every time. The key is per browser tab, and a value that is not there is an empty choice.
const SUPPLIER_KEY = "stockflow.invoice.supplier";
const WAREHOUSE_KEY = "stockflow.invoice.warehouse";

export interface RememberedWorkspace {
	supplierId: string;
	warehouseId: string;
}

type WorkspaceStorage = Pick<Storage, "getItem" | "setItem">;

export function rememberedWorkspace(storage: WorkspaceStorage, save?: RememberedWorkspace): RememberedWorkspace {
	if (save) {
		storage.setItem(SUPPLIER_KEY, save.supplierId);
		storage.setItem(WAREHOUSE_KEY, save.warehouseId);
		return save;
	}
	return { supplierId: storage.getItem(SUPPLIER_KEY) ?? "", warehouseId: storage.getItem(WAREHOUSE_KEY) ?? "" };
}

export const hasRememberedWorkspace = (workspace: RememberedWorkspace): boolean => Boolean(workspace.warehouseId);

export function sessionWorkspace(): RememberedWorkspace {
	try {
		return rememberedWorkspace(window.sessionStorage);
	} catch {
		return { supplierId: "", warehouseId: "" };
	}
}

export function rememberWorkspace(workspace: RememberedWorkspace): void {
	try {
		rememberedWorkspace(window.sessionStorage, workspace);
	} catch {
		// A browser that refuses the session storage only loses the shortcut.
	}
}
