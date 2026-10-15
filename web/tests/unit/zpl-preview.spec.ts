// What the editor's preview reads out of a template: the seeded product and location labels, which are the shapes the API
// allows (labels.md section 4).
import { expect, test } from "@playwright/test";
import { fillPlaceholders, parseZpl, sampleValues } from "../../src/features/label/model/zpl";

const PRODUCT = `^XA
^PW812
^LL406
^LH0,0
^CF0,40
^FO30,30^FB752,2,0,L,0^FD{{name}}^FS
^FO30,150^BY3,2,110^BCN,110,Y,N,N^FD{{barcode}}^FS
^FO30,320^A0N,40,40^FD{{code}}  {{unit}}^FS
^PQ{{copies}}
^XZ`;

const LOCATION = `^XA
^PW812
^LL406
^FO30,20^A0N,200,180^FD{{code}}^FS
^FO30,250^BY3,2,100^BCN,100,Y,N,N^FD{{code}}^FS
^FO430,40^A0N,40,40^FD{{warehouse_code}}^FS
^XZ`;

test("the product label has its size, its two texts and its barcode", () => {
	const label = parseZpl(PRODUCT);
	expect(label.width).toBe(812);
	expect(label.length).toBe(406);
	expect(label.elements.map((element) => element.kind)).toEqual(["text", "barcode", "text"]);

	const [name, barcode, code] = label.elements;
	expect(name).toMatchObject({ kind: "text", x: 30, y: 30, height: 40, text: "{{name}}" });
	// The field block of the name is 752 dots wide and aligned left: the preview needs it to place the text.
	expect(name.kind === "text" && name.block).toMatchObject({ width: 752, lines: 2, align: "L" });
	// The barcode takes its module width and its height from ^BY and ^BC, and prints its readable text.
	expect(barcode).toMatchObject({ kind: "barcode", x: 30, y: 150, height: 110, moduleWidth: 3, interpretation: true, text: "{{barcode}}" });
	// A later field without ^CF keeps the font the template set, and ^A0N,40,40 says the height again.
	expect(code).toMatchObject({ kind: "text", x: 30, y: 320, height: 40, text: "{{code}}  {{unit}}" });
	expect(label.copies).toBe(1);
});

test("the location label draws its barcode and its three fields", () => {
	const label = parseZpl(LOCATION);
	expect(label.elements.map((element) => element.kind)).toEqual(["text", "barcode", "text"]);
	expect(label.elements[0]).toMatchObject({ x: 30, y: 20, height: 200 });
	expect(label.elements[1]).toMatchObject({ kind: "barcode", y: 250, height: 100 });
	expect(label.elements[2]).toMatchObject({ x: 430, y: 40 });
});

test("the placeholders are filled with values a person recognizes", () => {
	const filled = fillPlaceholders(PRODUCT, sampleValues(["name", "barcode", "code", "unit", "copies"]));
	expect(filled).not.toContain("{{");
	expect(filled).toContain("Codo galvanizado 1/2\"");
	expect(filled).toContain("7501234567890");
	// A placeholder the API does not know keeps its braces, so the preview shows something is missing.
	expect(fillPlaceholders("^FD{{unknown}}^FS", sampleValues([]))).toBe("^FD{{unknown}}^FS");
});

test("a template the API would refuse does not break the preview", () => {
	// Half-written ZPL is what the editor holds while the person types.
	expect(parseZpl("").elements).toEqual([]);
	expect(parseZpl("^XA^FO30^FDhola").elements.length).toBe(1);
	expect(parseZpl("^XA^PW600^LL300^GB100,50,3^FS^XZ").elements).toEqual([{ kind: "box", x: 0, y: 0, width: 100, height: 50, thickness: 3 }]);
});
