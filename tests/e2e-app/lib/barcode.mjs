// A Code 128 barcode as a PNG, the symbology the product labels print (^BC, labels.md section 4). The scanner
// tests show it to the camera instead of a printed label. Code set B covers the codes of products and locations.
import { deflateSync } from "node:zlib";

// The bar and space widths of each value, in modules (Code 128 standard). 104 starts code set B; 106 stops.
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
  "114131", "311141", "411131", "211412", "211214", "211232", "2331112",
];
const START_B = 104;
const STOP = 106;

// The modules of the symbol, dark (1) and light (0), with a quiet zone of ten modules on each side.
export function code128(text) {
  const values = [...text].map((char) => {
    const value = char.charCodeAt(0) - 32;
    if (value < 0 || value > 94) throw new Error(`"${char}" is not in Code 128 set B`);
    return value;
  });
  const check = values.reduce((sum, value, index) => sum + value * (index + 1), START_B) % 103;
  const modules = Array(10).fill(0);
  for (const value of [START_B, ...values, check, STOP]) {
    [...PATTERNS[value]].forEach((width, index) => modules.push(...Array(Number(width)).fill(index % 2 === 0 ? 1 : 0)));
  }
  modules.push(...Array(10).fill(0));
  return modules;
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = (bytes) => {
  let c = 0xffffffff;
  for (const byte of bytes) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

// The barcode as a grayscale PNG: black bars on white, `scale` pixels per module, `height` pixels tall.
export function code128Png(text, { scale = 8, height = 480 } = {}) {
  const modules = code128(text);
  const width = modules.length * scale;
  const row = Buffer.alloc(width + 1);
  modules.forEach((dark, index) => row.fill(dark ? 0 : 255, 1 + index * scale, 1 + (index + 1) * scale));
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 0; // grayscale
  const pixels = deflateSync(Buffer.concat(Array(height).fill(row)));
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk("IHDR", header), chunk("IDAT", pixels), chunk("IEND", Buffer.alloc(0))]);
}
