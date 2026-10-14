// Decides which packages a change touches, so CI runs only their suites (testing.md section 5).
// Packages: api, web, app. A root compose file or a CI change affects all of them.
// The web's end-to-end suite runs against the API, so an API change affects the web too.
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const packages = ["api", "web", "app"];

export function affectedPackages(files) {
  const found = new Set();
  for (const file of files) {
    const path = file.replace(/^\.\//, "");
    if (path === "docker-compose.yml" || path.startsWith(".github/workflows/")) {
      return [...packages];
    }
    const top = path.split("/")[0];
    if (packages.includes(top)) found.add(top);
    if (top === "api") found.add("web");
  }
  return packages.filter((p) => found.has(p));
}

function changedFiles(argv) {
  const flag = argv.indexOf("--files");
  if (flag !== -1) return argv[flag + 1].split(",").filter(Boolean);
  const base = process.env.BASE_REF ?? "origin/main";
  return execFileSync("git", ["diff", "--name-only", `${base}...HEAD`], { encoding: "utf8" })
    .split("\n")
    .filter(Boolean);
}

// Run directly: print the affected packages as one comma-separated line, for the workflow to read.
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  console.log(affectedPackages(changedFiles(process.argv.slice(2))).join(","));
}
