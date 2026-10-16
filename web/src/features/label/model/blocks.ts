// The blocks a label is built with: the pieces the API already allows and the printers already run, each one with the
// ZPL it writes. A person picks blocks and fills them; nobody types ZPL unless they want to (labels.md section 4).
//
// Every block is one field of ZPL — a text, a barcode or a box — and every command it writes is in the allowed list, so
// what the designer produces is a template the API accepts without a special case.

export type BlockKind = "title" | "text" | "code" | "barcode" | "box";

/** The ZPL orientation of a field: N is as written, R a quarter turn, I half, B three quarters. */
const ZPL_TURN: Record<number, string> = { 0: "N", 90: "R", 180: "I", 270: "B" };
export const turnLetter = (orientation: number) => ZPL_TURN[orientation] ?? "N";
export const turnOfLetter = (letter: string | undefined) => Number(Object.entries(ZPL_TURN).find(([, value]) => value === (letter ?? "N").toUpperCase())?.[0] ?? 0) as 0 | 90 | 180 | 270;

export interface Block {
	/** What tells one block from another while it is edited. */
	id: string;
	kind: BlockKind;
	x: number;
	y: number;
	/** Height of the glyphs, in dots (the width follows it: the fonts are square-ish). */
	size: number;
	/** The placeholder the block prints, or null for a box. */
	field: string | null;
	/** What goes before the value ("Pasillo ", "Rack "), which is how the seeded labels write their rows. */
	prefix: string;
	/** Which way it is drawn: 0 is as written, and the printer turns the rest (labels.md section 4). */
	orientation: 0 | 90 | 180 | 270;
	/** Barcode only: width of one module and height of the bars. */
	module: number;
	barHeight: number;
	/** Box only. */
	width: number;
	height: number;
	thickness: number;
}

export interface BlockSpec {
	kind: BlockKind;
	/** The key of its name in the labels namespace. */
	label: string;
	/** What it writes, with {x}, {y} and the block's own values filled. */
	zpl: (block: Block, value: (block: Block) => string) => string;
	defaults: Partial<Block>;
}

// The catalogue, in the order a label is usually built: the title, the code, the barcode, the rows.
export const BLOCKS: BlockSpec[] = [
	{
		kind: "title",
		label: "blocks.title",
		defaults: { y: 30, size: 80, field: "name", prefix: "" },
		// The name wraps in two lines inside the width of the label, as the seeded product label does.
		zpl: (block, value) => `^FO${block.x},${block.y}^FB752,2,0,L,0^A0${turnLetter(block.orientation)},${block.size},${block.size}^FD${value(block)}^FS`,
	},
	{
		kind: "code",
		label: "blocks.code",
		defaults: { y: 30, size: 110, field: "code", prefix: "" },
		zpl: (block, value) => `^FO${block.x},${block.y}^A0${turnLetter(block.orientation)},${block.size},${block.size}^FD${value(block)}^FS`,
	},
	{
		kind: "barcode",
		label: "blocks.barcode",
		defaults: { y: 170, size: 0, field: "barcode", module: 3, barHeight: 110 },
		zpl: (block, value) => `^FO${block.x},${block.y}^BY${block.module},2,${block.barHeight}^BC${turnLetter(block.orientation)},${block.barHeight},Y,N,N^FD${value(block)}^FS`,
	},
	{
		kind: "text",
		label: "blocks.text",
		defaults: { y: 300, size: 36, field: "unit", prefix: "" },
		zpl: (block, value) => `^FO${block.x},${block.y}^A0${turnLetter(block.orientation)},${block.size},${block.size}^FD${value(block)}^FS`,
	},
	{
		kind: "box",
		label: "blocks.box",
		defaults: { y: 30, size: 0, field: null, width: 200, height: 4, thickness: 2 },
		zpl: (block) => `^FO${block.x},${block.y}^GB${block.width},${block.height},${block.thickness}^FS`,
	},
];

const spec = (kind: BlockKind): BlockSpec => BLOCKS.find((block) => block.kind === kind)!;

// A block with what its kind needs to be drawn at once. Ids are simple counters: they only tell one block from another.
let counter = 0;
export function newBlock(kind: BlockKind, fields: string[]): Block {
	const defaults = spec(kind).defaults;
	const wanted = (defaults.field ?? fields[0] ?? null) as string | null;
	return {
		id: `b${++counter}`,
		kind,
		x: 30,
		size: 40,
		field: wanted && fields.includes(wanted) ? wanted : fieldOrFirst(fields),
		prefix: "",
		orientation: 0,
		module: 3,
		barHeight: 110,
		width: 200,
		height: 4,
		thickness: 2,
		...defaults,
	} as Block;
}

function fieldOrFirst(fields: string[]): string | null {
	// `copies` is not something a block prints: it is the quantity of the whole label.
	return fields.find((field) => field !== "copies") ?? null;
}

/** The text of a block as a person reads it: what is fixed, with its variable between double braces. */
export const blockText = (block: Block) => `${block.prefix}${block.field ? `{{${block.field}}}` : ""}`;

/** The other way: what was typed on the label becomes a fixed text and, at most, one variable. */
export function textToBlock(text: string): { prefix: string; field: string | null } {
	const found = /\{\{([^{}]*)\}\}/.exec(text);
	if (!found) return { prefix: text, field: null };
	// Anything after the first variable is literal text: the model holds one variable and a fixed text around it.
	const prefix = text.slice(0, found.index) + text.slice(found.index + found[0].length);
	const name = found[1].trim().toLowerCase().replace(/[^a-z0-9_]/g, "_").replace(/^([0-9])/, "_$1").slice(0, 40);
	return { prefix, field: name || null };
}

/** The value a block prints: its fixed text and the placeholder the API fills at print time. */
export function blockValue(block: Block): string {
	const field = block.field ? `{{${block.field}}}` : "";
	return `${block.prefix}${field}`.trim();
}

/** The label, as the template the API stores and the printer receives. */
export function blocksToZpl(blocks: Block[], width = 812, length = 406): string {
	const fields = blocks.map((block) => spec(block.kind).zpl(block, blockValue));
	return ["^XA", `^PW${width}`, `^LL${length}`, "^LH0,0", ...fields, "^PQ{{copies}}", "^XZ"].join("\n");
}

/** The blocks of a label of one kind, as a new template starts: what the seeded labels print, by hand. */
export function starterBlocks(fields: string[]): Block[] {
	const has = (field: string) => fields.includes(field);
	const blocks: Block[] = [];
	if (has("name")) blocks.push({ ...newBlock("title", fields), x: 30, y: 30, size: 80 });
	if (has("code")) blocks.push({ ...newBlock("code", fields), x: 30, y: has("name") ? 150 : 30, size: has("name") ? 60 : 110 });
	if (has("barcode")) blocks.push({ ...newBlock("barcode", fields), x: 30, y: has("name") ? 250 : 170 });
	if (!has("barcode") && has("code")) blocks.push({ ...newBlock("barcode", fields), field: "code", x: 30, y: 170 });
	return blocks;
}

export const labelKinds = (fields: string[]): BlockKind[] => BLOCKS.map((block) => block.kind).filter((kind) => kind === "box" || fields.length > 1);

/** A template read back as blocks: what the designer writes, and what the seeded labels are. */
export interface ReadLabel {
	width: number;
	length: number;
	blocks: Block[];
}

// The commands of a ZPL text, as `^XX` plus its argument.
function tokens(zpl: string): { code: string; argument: string }[] {
	return zpl
		.split("^")
		.slice(1)
		.map((part) => ({ code: part.slice(0, 2).toUpperCase(), argument: part.slice(2) }));
}

// The value of a field, split into the fixed text and the one placeholder it prints. A field with two placeholders, or
// with text after the placeholder, is not a block: it is hand-written ZPL, and the designer says so instead of guessing.
function value(text: string): { prefix: string; field: string | null } | null {
	const found = [...text.matchAll(/\{\{([^{}]*)\}\}/g)];
	if (found.length > 1) return null;
	if (found.length === 0) return { prefix: text, field: null };
	const match = found[0];
	if (match.index! + match[0].length !== text.length) return null;
	return { prefix: text.slice(0, match.index), field: match[1] };
}

/** Reads a template as blocks, or answers null when it was written by hand (labels.md section 4). */
export function blocksFromZpl(zpl: string): ReadLabel | null {
	const read: ReadLabel = { width: 812, length: 406, blocks: [] };
	let x = 30;
	let y = 0;
	let size = 40;
	let blockWidth = 0;
	let module = 3;
	let barHeight = 110;
	let kind: "text" | "barcode" | "box" | null = null;
	let orientation: 0 | 90 | 180 | 270 = 0;
	let data = "";

	for (const { code, argument } of tokens(zpl)) {
		const parts = argument.split(",");
		const num = (value: string | undefined, fallback: number) => (value === undefined || value.trim() === "" ? fallback : Number(value));
		if (code === "PW") read.width = num(parts[0], read.width);
		else if (code === "LL") read.length = num(parts[0], read.length);
		else if (code === "FO") {
			x = num(parts[0], x);
			y = num(parts[1], y);
		} else if (code === "FB") blockWidth = num(parts[0], 0);
		else if (code.startsWith("A")) {
			orientation = turnOfLetter(argument.slice(0, 1));
			size = num(argument.slice(2).split(",")[0], size);
		}
		else if (code === "BY") {
			module = num(parts[0], module);
			barHeight = num(parts[2], barHeight);
		} else if (code === "BC") {
			orientation = turnOfLetter(argument.slice(0, 1));
			barHeight = num(parts[1], barHeight);
			kind = "barcode";
		} else if (code === "BQ") return null;
		else if (code === "GB") {
			read.blocks.push({ ...blank("box"), x, y, width: num(parts[0], 200), height: num(parts[1], 4), thickness: num(parts[2], 2) });
		} else if (code === "FD") data = argument;
		else if (code === "FS") {
			// A box already wrote itself, and its `^FS` closes nothing: it does not become an empty text block.
			if (kind === null && blockWidth === 0 && data.trim() === "") continue;
			if (kind === "barcode") {
				const printed = value(data);
				if (!printed) return null;
				read.blocks.push({ ...blank("barcode"), x, y, module, barHeight, orientation, field: printed.field, prefix: printed.prefix });
			} else {
				const printed = value(data);
				if (!printed) return null;
				// A field as tall as a title, or one that wraps, is the title of the label; the rest is text or code.
				const isTitle = blockWidth > 0 || size >= 70;
				read.blocks.push({ ...blank(isTitle ? "title" : printed.field === "code" ? "code" : "text"), x, y, size, orientation, field: printed.field, prefix: printed.prefix });
			}
			kind = null;
			data = "";
			blockWidth = 0;
		} else if (code === "PQ" || code === "XA" || code === "XZ" || code === "LH") continue;
		else if (code === "CF") size = num(parts[1], size);
	}
	return read;
}

function blank(kind: BlockKind): Block {
	return { id: `r${++counter}`, kind, x: 30, y: 0, size: 40, field: null, prefix: "", orientation: 0, module: 3, barHeight: 110, width: 200, height: 4, thickness: 2 };
}

/** The design a template saved, as the designer holds it, or null when it has none or it cannot be read. */
export function reading(design: string | null | undefined): (ReadLabel & { blocks: Block[] }) | null {
	if (!design) return null;
	try {
		const stored = JSON.parse(design) as { width?: number; length?: number; blocks?: Block[] };
		if (!Array.isArray(stored.blocks) || stored.blocks.length === 0) return null;
		return { width: stored.width ?? 812, length: stored.length ?? 406, blocks: stored.blocks };
	} catch {
		// A design that cannot be read is not a reason to lose the template: its ZPL still prints.
		return null;
	}
}

/** A roll size a person knows, in inches: the label is 203 dpi, so an inch is 203 dots. */
export interface LabelSize {
	widthInches: number;
	lengthInches: number;
}

// The common rolls of a Zebra. `4 × 2` is the one the system prints on (labels.md section 4).
export const LABEL_SIZES: LabelSize[] = [
	{ widthInches: 2, lengthInches: 1 },
	{ widthInches: 2, lengthInches: 2 },
	{ widthInches: 2, lengthInches: 3 },
	{ widthInches: 2, lengthInches: 4 },
	{ widthInches: 3, lengthInches: 1 },
	{ widthInches: 3, lengthInches: 2 },
	{ widthInches: 4, lengthInches: 1 },
	{ widthInches: 4, lengthInches: 2 },
	{ widthInches: 4, lengthInches: 3 },
	{ widthInches: 4, lengthInches: 6 },
];

export const DOTS_PER_INCH = 203;
export const sizeInDots = (size: LabelSize) => ({ width: size.widthInches * DOTS_PER_INCH, length: size.lengthInches * DOTS_PER_INCH });
export const sameSize = (a: LabelSize, b: LabelSize) => a.widthInches === b.widthInches && a.lengthInches === b.lengthInches;
export const sizeOfDots = (dots: { width: number; length: number }): LabelSize | null =>
	LABEL_SIZES.find((size) => sizeInDots(size).width === dots.width && sizeInDots(size).length === dots.length) ?? null;
