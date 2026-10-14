import "i18next";
import type { resources } from "./resources";

// Keys are typed: a static key that does not exist fails to compile (web.md section 6).
declare module "i18next" {
	interface CustomTypeOptions {
		defaultNS: "common";
		resources: (typeof resources)["es"];
	}
}
