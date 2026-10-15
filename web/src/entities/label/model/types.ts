// A label template: its key never changes; each change of its ZPL is a new version (labels.md sections 2 to 4).
export interface LabelTemplate {
	id: string;
	key: string;
	name: string;
	nameEs: string;
	nameEn: string;
	version: number;
	zpl: string;
	placeholders: string[];
	active: boolean;
	updatedAt: string;
	// The kit's table wants its rows to be records (web.md section 8).
	[key: string]: unknown;
}

export interface TemplateVersion {
	version: number;
	zpl: string;
	createdAt: string;
}

// One problem of a template, with the command or placeholder that causes it.
export interface ZplProblem {
	code: string;
	token: string | null;
	message: string;
}

export interface TemplateInput {
	nameEs: string;
	nameEn: string;
	zpl: string;
}

// A printer, as an administrator sees it. The port is always 9100.
export interface Printer {
	id: string;
	name: string;
	warehouseId: string;
	warehouseCode: string | null;
	ipAddress: string;
	port: number;
	active: boolean;
	updatedAt: string;
	// The kit's table wants its rows to be records (web.md section 8).
	[key: string]: unknown;
}

export interface PrinterInput {
	name: string;
	warehouseId: string;
	ipAddress: string;
}

// A printer as whoever prints sees it: never its address.
export interface PrintablePrinter {
	id: string;
	name: string;
	warehouseId: string;
	warehouseCode: string | null;
}

export interface PrintRequest {
	templateKey: string;
	printerId: string;
	copies: number;
	productId?: string;
	locationId?: string;
}

export interface PrintResult {
	printerId: string;
	templateKey: string;
	version: number;
	copies: number;
}
