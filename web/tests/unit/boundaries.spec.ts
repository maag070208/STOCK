// F0-09 · unit: the layer rules are enforced by ESLint. A forbidden import fails the lint; an allowed one passes.
import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";

function lint(file: string): { code: number; output: string } {
  try {
    const output = execFileSync("pnpm", ["exec", "eslint", file], { encoding: "utf8", stdio: "pipe" });
    return { code: 0, output };
  } catch (error) {
    const e = error as { status: number; stdout: string; stderr: string };
    return { code: e.status, output: `${e.stdout}${e.stderr}` };
  }
}

test("a shared module importing from features fails the lint", () => {
  const result = lint("tests/fixtures/forbidden/shared-imports-features.ts");
  expect(result.code).not.toBe(0);
  expect(result.output).toContain("boundaries");
});

test("a shared module importing only shared passes the lint", () => {
  const result = lint("tests/fixtures/allowed/shared-imports-shared.ts");
  expect(result.code, result.output).toBe(0);
});
