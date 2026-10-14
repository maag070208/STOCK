// F2-13 · unit: the contact form needs a name, and an email or a phone, before it can be saved.
import { test, expect } from "@playwright/test";
import { canSaveContact, contactErrors } from "../../src/entities/partner/model/contact";

test("a contact with neither email nor phone cannot be saved", () => {
	expect(canSaveContact({ name: "Ana", email: "", phone: "" })).toBe(false);
	expect(contactErrors({ name: "Ana", email: " ", phone: "" })).toEqual({ email: "CONTACT_CHANNEL_REQUIRED", phone: "CONTACT_CHANNEL_REQUIRED" });
});

test("an email or a phone is enough", () => {
	expect(canSaveContact({ name: "Ana", email: "ana@aceros.mx", phone: "" })).toBe(true);
	expect(canSaveContact({ name: "Ana", email: "", phone: "33 1234 5678" })).toBe(true);
});

test("the name is required too", () => {
	expect(contactErrors({ name: " ", email: "ana@aceros.mx", phone: "" })).toEqual({ name: "FIELD_REQUIRED" });
	expect(canSaveContact({ name: "", email: "ana@aceros.mx", phone: "" })).toBe(false);
});
