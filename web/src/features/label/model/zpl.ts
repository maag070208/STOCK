// What a ZPL template draws, read for the preview of the editor (labels.md section 4).
//
// The API only lets a template use a short list of commands (ZplRules: XA, XZ, PW, LL, LH, FO, GB, A, CF, FB, FD, FS, BY,
// BC, BQ, PQ), so this reader covers exactly those and ignores anything else: a preview is not a ZPL interpreter, it is
// what the person needs to see their label before spending a roll.

export interface ZplText {
	kind: "text";
	x: number;
	y: number;
	/** Height of the glyphs, in dots. */
	height: number;
	/** Width of the field block, when `^FB` set one: what the text aligns inside. */
	block: { width: number; lines: number; spacing: number; align: "L" | "C" | "R" | "J" } | null;
	text: string;
}

export interface ZplBarcode {
	kind: "barcode";
	x: number;
	y: number;
	height: number;
	/** Width of one module, from `^BY`. */
	moduleWidth: number;
	/** Whether the readable text goes under the bars (`^BC` third argument). */
	interpretation: boolean;
	text: string;
}

export interface ZplQr {
	kind: "qr";
	x: number;
	y: number;
	/** Magnification, from `^BQ`: the square is 21 modules plus 4 per step of it, times this. */
	magnification: number;
	text: string;
}

export interface ZplBox {
	kind: "box";
	x: number;
	y: number;
	width: number;
	height: number;
	thickness: number;
}

export type ZplElement = ZplText | ZplBarcode | ZplQr | ZplBox;

export interface ZplLabel {
	/** Print width (`^PW`) and label length (`^LL`), in dots. */
	width: number;
	length: number;
	elements: ZplElement[];
	copies: number | null;
}

const number = (value: string | undefined, fallback: number): number => {
	// An empty argument is a missing value, not a zero: Number("") is 0, and a field of height 0 disappears.
	if (value === undefined || value.trim() === "") return fallback;
	const parsed = Number(value);
	return Number.isFinite(parsed) ? parsed : fallback;
};

// The commands of a template: `^XX` plus its arguments, up to the next command. Every `~` command is left out: the API
// does not allow them.
function commands(zpl: string): { code: string; argument: string }[] {
	const parts = zpl.split("^").slice(1);
	return parts.map((part) => ({ code: part.slice(0, 2).toUpperCase(), argument: part.slice(2) }));
}

export function parseZpl(zpl: string): ZplLabel {
	const label: ZplLabel = { width: 812, length: 406, elements: [], copies: null };
	let homeX = 0;
	let homeY = 0;
	// The state a command leaves for the fields that follow it.
	let x = 0;
	let y = 0;
	let height = 30;
	let barcodeHeight = 50;
	let moduleWidth = 2;
	let block: ZplText["block"] = null;
	let data = "";
	let field: "none" | "barcode" | "qr" = "none";
	let interpretation = false;

	const commit = () => {
		if (field === "barcode" && data.trim()) {
			label.elements.push({ kind: "barcode", x: homeX + x, y: homeY + y, height: barcodeHeight, moduleWidth, interpretation, text: data });
		} else if (field === "qr" && data.trim()) {
			label.elements.push({ kind: "qr", x: homeX + x, y: homeY + y, magnification: moduleWidth, text: data });
		} else if (data.trim() || block) {
			label.elements.push({ kind: "text", x: homeX + x, y: homeY + y, height, block, text: data });
		}
		field = "none";
		data = "";
		block = null;
	};

	for (const { code, argument } of commands(zpl)) {
		const parts = argument.split(",");
		// ^A<font><orientation>,<height>[,<width>]: the font is one character, the orientation another, and the sizes
		// follow. The font can be a digit or a name (A@), so it is read by its prefix and not by a fixed code.
		if (code.startsWith("A")) {
			// The font was the second character of the command, so the argument starts with the orientation: ^A0N,40,40.
			height = number(argument.slice(2).split(",")[0], height);
			continue;
		}
		switch (code) {
			case "PW":
				label.width = number(parts[0], label.width);
				break;
			case "LL":
				label.length = number(parts[0], label.length);
				break;
			case "LH":
				homeX = number(parts[0], 0);
				homeY = number(parts[1], 0);
				break;
			case "FO":
				commit();
				x = number(parts[0], 0);
				y = number(parts[1], 0);
				break;
			case "CF":
				// ^CF<font>,<height>[,<width>]
				height = number(parts[1], height);
				break;
			case "FB":
				// ^FB<width>,<lines>[,<spacing>[,<align>]]
				block = {
					width: number(parts[0], 0),
					lines: number(parts[1], 1),
					spacing: number(parts[2], 0),
					align: (["L", "C", "R", "J"] as const).includes(parts[3]?.trim().toUpperCase() as "L") ? (parts[3].trim().toUpperCase() as "L") : "L",
				};
				break;
			case "FD":
				data = argument;
				break;
			case "FS":
				commit();
				break;
			case "GB":
				// ^GB<width>,<height>,<thickness>
				label.elements.push({ kind: "box", x: homeX + x, y: homeY + y, width: number(parts[0], 0), height: number(parts[1], 0), thickness: number(parts[2], 1) });
				break;
			case "BY":
				// ^BY<module width>[,<ratio>[,<height>]]
				moduleWidth = number(parts[0], moduleWidth);
				barcodeHeight = number(parts[2], barcodeHeight);
				break;
			case "BC":
				// ^BC<orientation>,<height>,<interpretation>,...
				barcodeHeight = number(parts[1], barcodeHeight);
				interpretation = parts[2]?.trim().toUpperCase() === "Y";
				field = "barcode";
				break;
			case "BQ":
				// ^BQ<orientation>,<model>,<magnification>,...
				moduleWidth = number(parts[2], moduleWidth);
				field = "qr";
				break;
			case "PQ":
				label.copies = number(parts[0], 1);
				break;
			default:
				break;
		}
	}
	commit();
	return label;
}

// What the placeholders of a template say in the preview: a value a person recognizes, so the label looks like the one
// that will come out. The keys are the ones the API allows for each kind of label (ZplRules).
const SAMPLES: Record<string, string> = {
	code: "M-1024",
	name: "Codo galvanizado 1/2\"",
	barcode: "7501234567890",
	unit: "Pieza",
	warehouse_code: "W-01",
	aisle: "K",
	rack: "05",
	level: "2",
	copies: "1",
};

export function sampleValues(placeholders: string[]): Record<string, string> {
	return Object.fromEntries(placeholders.map((name) => [name, SAMPLES[name] ?? `[${name}]`]));
}

// The ZPL with its placeholders filled: what the printer would receive for those values.
export function fillPlaceholders(zpl: string, values: Record<string, string>): string {
	return zpl.replace(/\{\{([^{}]*)\}\}/g, (whole, name: string) => values[name.trim()] ?? whole);
}
