// What runs when the whole suite is over: the database the tests filled goes back to a state a person can read.
//
// Every test takes away what it created (tests/support/test.ts), but the API cannot remove everything — an invoice is
// closed, never deleted; stock movements and the audit log are append-only; a killed run leaves its half-finished work
// behind. So the suite ends by resetting the database, keeping what the API seeds and the real accounts
// (scripts/e2e-reset.sh). With `E2E_RESET=0` it does not touch anything, and a machine without Docker skips it.
import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const run = promisify(execFile);

export default async function teardown() {
	if (process.env.E2E_RESET === "0") return;
	const script = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..", "scripts", "e2e-reset.sh");
	if (!existsSync(script)) return;
	try {
		// Already backed up by the developer when they need one: the teardown of every run would fill the disk.
		const { stdout } = await run("bash", [script, "--no-backup"], { timeout: 120_000 });
		const left = stdout.split("\n").filter((line) => line.includes("users") || line.includes("invoices")).join(" · ");
		console.log(`\n[e2e] the database of the tests was emptied${left ? ` (${left.trim()})` : ""}`);
	} catch (failure) {
		// A database that cannot be reached is not a reason to fail a suite that already ran: the run is reported, and so
		// is the reason the reset did not happen.
		console.log(`\n[e2e] the database was left as it is: ${(failure as Error).message.split("\n")[0]}`);
	}
}
