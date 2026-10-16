// The blocks a label is built with: what the designer writes, and what it can read back (labels.md section 4).
import { expect, test } from "@playwright/test";
import { BLOCKS, LABEL_SIZES, blocksFromZpl, blocksToZpl, blockValue, newBlock, sizeInDots, sizeOfDots } from "../../src/features/label/model/blocks";

const PRODUCT = ["name", "code", "barcode", "unit", "copies"];

test("every block writes the commands the API allows", () => {
	// The list of ZplRules: a block that wrote anything else would be refused when the template is saved.
	const allowed = ["XA", "XZ", "PW", "LL", "LH", "FO", "GB", "A", "CF", "FB", "FD", "FS", "BY", "BC", "BQ", "PQ"];
	for (const spec of BLOCKS) {
		const block = newBlock(spec.kind, PRODUCT);
		const zpl = blocksToZpl([block]);
		const commands = [...zpl.matchAll(/\^([A-Z@0-9]{2})/g)].map((match) => match[1]);
		for (const command of commands) {
			const known = allowed.some((allowed) => command.startsWith(allowed));
			expect(known, `${spec.kind} writes ^${command}`).toBe(true);
		}
	}
});

test("a block prints its fixed text and its placeholder", () => {
	const block = { ...newBlock("text", PRODUCT), prefix: "Pasillo ", field: "unit" };
	expect(blockValue(block)).toBe("Pasillo {{unit}}");
	expect(blocksToZpl([block])).toContain("^FDPasillo {{unit}}^FS");
});

test("a designed label and its ZPL are the same thing, both ways", () => {
	const blocks = [
		{ ...newBlock("title", PRODUCT), x: 30, y: 30, size: 80 },
		{ ...newBlock("barcode", PRODUCT), x: 30, y: 170, module: 3, barHeight: 110 },
		{ ...newBlock("code", PRODUCT), x: 30, y: 320, size: 40 },
		{ ...newBlock("box", PRODUCT), x: 400, y: 300, width: 120, height: 4, thickness: 2 },
	];
	const read = blocksFromZpl(blocksToZpl(blocks, 812, 406));
	expect(read).not.toBeNull();
	expect(read!.width).toBe(812);
	expect(read!.length).toBe(406);
	// The same blocks come back: the same kinds, in the same order, where they were, printing the same field.
	expect(read!.blocks.map((block) => block.kind)).toEqual(["title", "barcode", "code", "box"]);
	expect(read!.blocks.map((block) => [block.x, block.y])).toEqual([[30, 30], [30, 170], [30, 320], [400, 300]]);
	expect(read!.blocks.map((block) => block.field)).toEqual(["name", "barcode", "code", null]);
	expect(read!.blocks[1]).toMatchObject({ module: 3, barHeight: 110 });
	expect(read!.blocks[3]).toMatchObject({ width: 120, height: 4, thickness: 2 });
	// And writing them again gives the same template: the round trip does not drift.
	expect(blocksToZpl(read!.blocks, read!.width, read!.length)).toBe(blocksToZpl(blocks, 812, 406));
});

test("a template written by hand is not guessed at", () => {
	// Two placeholders in one field, a QR, or a font name: the designer says it cannot read it instead of inventing blocks.
	expect(blocksFromZpl("^XA^FO30,30^FD{{code}} {{unit}}^FS^XZ")).toBeNull();
	expect(blocksFromZpl("^XA^FO30,30^BQN,2,5^FD{{code}}^FS^XZ")).toBeNull();
	// A fixed text is a block with no placeholder.
	expect(blocksFromZpl("^XA^FO30,30^A0N,40,40^FDEtiqueta^FS^XZ")!.blocks[0]).toMatchObject({ kind: "text", prefix: "Etiqueta", field: null });
});

test("the sizes a person chooses are the rolls, in inches and in dots", () => {
	// 203 dpi: an inch is 203 dots, and 4 × 2 inches is the roll the seeded labels print on.
	const dots = LABEL_SIZES.map(sizeInDots);
	expect(dots).toContainEqual({ width: 812, length: 406 });
	expect(sizeOfDots({ width: 812, length: 406 })).toEqual({ widthInches: 4, lengthInches: 2 });
	expect(sizeOfDots({ width: 406, length: 406 })).toEqual({ widthInches: 2, lengthInches: 2 });
	// A label of a size nobody chose (an old template) is not forced into one of them.
	expect(sizeOfDots({ width: 700, length: 300 })).toBeNull();
});

test("a block can be turned, and the turn survives the round trip", () => {
	// ZPL turns a field with the letter of its orientation: N as written, R a quarter, I half, B three quarters.
	const turned = { ...newBlock("code", PRODUCT), x: 40, y: 40, orientation: 90 as const };
	expect(blocksToZpl([turned])).toContain("^A0R,");
	expect(blocksFromZpl(blocksToZpl([turned]))!.blocks[0]).toMatchObject({ orientation: 90 });
	// A barcode turns with its own command.
	const barcode = { ...newBlock("barcode", PRODUCT), orientation: 270 as const };
	expect(blocksToZpl([barcode])).toContain("^BCB,");
	expect(blocksFromZpl(blocksToZpl([barcode]))!.blocks[0]).toMatchObject({ orientation: 270 });
	// A block nobody turned comes back as written.
	expect(blocksToZpl([newBlock("code", PRODUCT)])).toContain("^A0N,");
});
