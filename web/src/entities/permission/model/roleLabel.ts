import i18n, { dyn } from "@shared/i18n";

const SEED_ROLES = ["ADMIN", "OFFICE", "WAREHOUSE_MANAGER", "WAREHOUSE_OPERATOR", "APP_USER"];

// A seed role is translated (roles.<KEY>) in the language in use. A custom role shows the name it was given.
export function roleLabel(key: string, name?: string | null): string {
	if (SEED_ROLES.includes(key)) return i18n.t(dyn(`common:roles.${key}`));
	return name ?? key;
}
