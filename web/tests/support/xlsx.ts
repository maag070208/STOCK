// Builds a small spreadsheet in memory, the way Excel stores one, for the import's fixtures.
// No real inventory file is ever in the repository: each test builds its own, with the legacy file's shape.

type Cell = string | number | null;
type Row = Record<string, Cell>;

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
	let c = n;
	for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
	return c >>> 0;
});

function crc32(data: Buffer): number {
	let crc = 0xffffffff;
	for (const byte of data) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
	return (crc ^ 0xffffffff) >>> 0;
}

// A zip archive with its entries stored, not compressed: enough for a reader of .xlsx files.
function zip(files: Record<string, string>): Buffer {
	const locals: Buffer[] = [];
	const central: Buffer[] = [];
	let offset = 0;
	for (const [name, text] of Object.entries(files)) {
		const data = Buffer.from(text, "utf8");
		const fileName = Buffer.from(name, "utf8");
		const crc = crc32(data);
		const local = Buffer.alloc(30);
		local.writeUInt32LE(0x04034b50, 0);
		local.writeUInt16LE(20, 4);
		local.writeUInt32LE(crc, 14);
		local.writeUInt32LE(data.length, 18);
		local.writeUInt32LE(data.length, 22);
		local.writeUInt16LE(fileName.length, 26);
		locals.push(local, fileName, data);
		const entry = Buffer.alloc(46);
		entry.writeUInt32LE(0x02014b50, 0);
		entry.writeUInt16LE(20, 4);
		entry.writeUInt16LE(20, 6);
		entry.writeUInt32LE(crc, 16);
		entry.writeUInt32LE(data.length, 20);
		entry.writeUInt32LE(data.length, 24);
		entry.writeUInt16LE(fileName.length, 28);
		entry.writeUInt32LE(offset, 42);
		central.push(entry, fileName);
		offset += local.length + fileName.length + data.length;
	}
	const directory = Buffer.concat(central);
	const end = Buffer.alloc(22);
	end.writeUInt32LE(0x06054b50, 0);
	end.writeUInt16LE(Object.keys(files).length, 8);
	end.writeUInt16LE(Object.keys(files).length, 10);
	end.writeUInt32LE(directory.length, 12);
	end.writeUInt32LE(offset, 16);
	return Buffer.concat([...locals, directory, end]);
}

const escape = (text: string) => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

export function buildXlsx(rows: Row[]): Buffer {
	const strings: string[] = [];
	const index = (text: string) => (strings.includes(text) ? strings.indexOf(text) : strings.push(text) - 1);
	const body = rows
		.map((row, i) => {
			const cells = Object.keys(row)
				.sort()
				.map((column) => {
					const value = row[column];
					const ref = `${column}${i + 1}`;
					if (value === null) return `<c r="${ref}"/>`;
					if (typeof value === "number") return `<c r="${ref}"><v>${value}</v></c>`;
					return `<c r="${ref}" t="s"><v>${index(value)}</v></c>`;
				})
				.join("");
			return `<row r="${i + 1}">${cells}</row>`;
		})
		.join("");
	const main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
	return zip({
		"xl/workbook.xml": `<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="${main}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Hoja1" sheetId="1" r:id="rId1"/></sheets></workbook>`,
		"xl/_rels/workbook.xml.rels": `<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`,
		"xl/worksheets/sheet1.xml": `<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="${main}"><sheetData>${body}</sheetData></worksheet>`,
		"xl/sharedStrings.xml": `<?xml version="1.0" encoding="UTF-8"?><sst xmlns="${main}">${strings.map((s) => `<si><t xml:space="preserve">${escape(s)}</t></si>`).join("")}</sst>`,
	});
}

// The legacy file's shape (products.md section 2): a header, a row per product, and the CONTPAQ note with the control total.
export function legacyInventory(products: { id: number; code: string; name: string; stock: number }[]): Buffer {
	const header: Row = { A: "CIDPRODUCTO", B: "CCODIGOPRODUCTO", C: "CNOMBREPRODUCTO", D: "CDESCRIPCIONPRODUCTO", E: "CUNIDADMEDIDA", F: "CEXISTENCIAS" };
	const rows: Row[] = products.map((p) => ({ A: p.id, B: p.code, C: p.name, D: p.name, E: "PZA", F: p.stock }));
	const total = products.reduce((sum, p) => sum + p.stock, 0);
	return buildXlsx([header, ...rows, {}, { G: "CONTPAQ" }, { G: total }]);
}
