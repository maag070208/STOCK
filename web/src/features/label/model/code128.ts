// Code 128, code set B: what a label preview needs to draw the bars the printer would print (labels.md section 4).
//
// The 107 symbol patterns are the standard table (a bar/space run of six elements, each 1 to 4 modules wide, and every
// one of them 11 modules long). Value 106 is the stop character; the physical stop appends a two-module bar, which is
// why it is 13 modules.
const PATTERNS = [
	"212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
	"221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
	"221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
	"212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
	"231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
	"231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
	"314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
	"112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
	"111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
	"214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
	"114131", "311141", "411131", "211412", "211214", "211232", "233111",
];

const STOP = "2331112";
const START_B = 104;

// The symbol values of a text in code set B: the start character, one value per character (its code minus 32), and the
// check character, which is the weighted sum of everything before it, modulo 103.
export function code128Values(text: string): number[] {
	// Code set B carries the printable ASCII range; anything else is drawn as a question mark rather than dropping a bar.
	const values = [...text].map((character) => {
		const code = character.charCodeAt(0);
		return code >= 32 && code <= 127 ? code - 32 : 31;
	});
	const check = [START_B, ...values].reduce((sum, value, index) => sum + value * (index === 0 ? 1 : index), 0) % 103;
	return [START_B, ...values, check, 106];
}

// The widths of a barcode in modules, alternating bar and space and starting with a bar: what draws it. The last symbol
// is the stop, whose pattern is the six elements of value 106 plus the two-module bar that closes the symbol.
export function code128Modules(text: string): number[] {
	const symbols = code128Values(text).slice(0, -1).map((value) => PATTERNS[value] ?? "");
	// Every character is one element (a bar or a space); the stop is seven of them, not one wide number.
	return [...symbols.join(""), ...STOP].map(Number);
}

// The bars alone, as [start, width] pairs in modules: the spaces are what is left between them.
export function code128Bars(text: string): { start: number; width: number }[] {
	const modules = code128Modules(text);
	const bars: { start: number; width: number }[] = [];
	let x = 0;
	modules.forEach((width, index) => {
		// A bar is every other element, and the first one: 0, 2, 4...
		if (index % 2 === 0) bars.push({ start: x, width });
		x += width;
	});
	return bars;
}

// How wide a barcode is, in modules, before any magnification.
export const code128Width = (text: string): number => code128Modules(text).reduce((sum, width) => sum + width, 0);
