// What the design document expects on screen, for the tests that compare pixels.
import { join } from "node:path";
import { readDesign } from "../../../scripts/design-tokens.mjs";
import { root } from "./android.mjs";

export const design = readDesign(join(root, "design/stockflow-DESIGN.md"));
export const colors = design.colors;
export const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));

// A color at an opacity, over what is behind it.
export const over = (top, alpha, under) => top.map((value, i) => Math.round(value * alpha + under[i] * (1 - alpha)));

// The workspace colors of each mode. Dark mode replaces them as docs/architecture/design-system.md section 6 lists.
export function workspace(mode) {
  if (mode === "light") {
    return { canvas: rgb(colors.background), title: rgb(colors["on-background"]), muted: rgb(colors["on-surface-muted"]), border: rgb(colors.outline) };
  }
  const canvas = rgb(colors["dark-background"]);
  return {
    canvas,
    title: rgb(colors["dark-on-surface"]),
    muted: rgb(colors["dark-on-surface-muted"]),
    border: over(rgb(colors["shell-border"]), Number(design.elevation["shell-border-opacity"]), canvas),
  };
}

// How far a pixel is from a color: the largest difference of its three channels.
export const distance = (pixel, expected) => Math.max(...pixel.map((value, i) => Math.abs(value - expected[i])));

// Whether the screen shows artwork of its own over the canvas: the brand intro, or the entry illustration. It is a
// share of the pixels of the middle of the screen that are not the canvas.
export function hasArtwork(shot, canvas) {
  let painted = 0;
  let total = 0;
  const left = Math.round(shot.width * 0.2);
  const right = Math.round(shot.width * 0.8);
  const top = Math.round(shot.height * 0.2);
  const bottom = Math.round(shot.height * 0.8);
  for (let y = top; y < bottom; y += 4) {
    for (let x = left; x < right; x += 4) {
      total += 1;
      if (distance(shot.at(x, y), canvas) > 12) painted += 1;
    }
  }
  return painted / total > 0.02;
}
