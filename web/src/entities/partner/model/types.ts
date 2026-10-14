// Suppliers and customers have the same shape (suppliers.md section 7): the screens take the kind as a parameter.
export type PartnerKind = "suppliers" | "customers";

export interface CatalogRef {
	code: string;
	label: string | null;
}

export interface Address {
	street: string;
	exteriorNumber: string;
	interiorNumber: string | null;
	neighborhood: string;
	city: string;
	state: CatalogRef | null;
	postalCode: string;
	country: CatalogRef | null;
}

export interface Contact {
	id: string;
	name: string;
	email: string | null;
	phone: string | null;
	active: boolean;
}

export interface Partner {
	id: string;
	code: string;
	name: string;
	taxId: string;
	address: Address;
	paymentTerms: CatalogRef | null;
	active: boolean;
	createdAt: string;
	// Only when one supplier or customer is read.
	contacts: Contact[] | null;
	[key: string]: unknown;
}

// What the form sends. `state`, `country` and `paymentTerms` are catalog codes.
export interface AddressInput {
	street: string;
	exteriorNumber: string;
	interiorNumber: string;
	neighborhood: string;
	city: string;
	state: string;
	postalCode: string;
	country: string;
}

export interface PartnerInput {
	name: string;
	taxId: string;
	address: AddressInput;
	paymentTerms: string;
}

export interface ContactInput {
	name: string;
	email: string;
	phone: string;
}
