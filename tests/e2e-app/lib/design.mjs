// What the design document expects on screen, for the tests that compare pixels.
import { join } from "node:path";
import { readDesign } from "../../../scripts/design-tokens.mjs";
import { root } from "./android.mjs";

export const design = readDesign(join(root, "design/command-center-DESIGN.md"));
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

// How tall the orange is at the top of the screen, in pixels: the strip behind the status bar, plus the hero on a
// screen that has one. The intro has only the strip.
export function orangeHeight(shot) {
  const orange = rgb(colors.primary);
  let y = 0;
  while (y < shot.height && distance(shot.at(4, y), orange) <= 3) y++;
  return y;
}
