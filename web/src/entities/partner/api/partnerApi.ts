import { del, get, patch, post } from "@shared/api/client";
import { tableRequest, type TableParams } from "@shared/api/table";
import type { Contact, ContactInput, Partner, PartnerInput, PartnerKind } from "../model/types";

// The same endpoints under /suppliers and /customers.
export const partnerApi = (kind: PartnerKind) => ({
	query: (params: TableParams) => tableRequest<Partner>(`/${kind}/query`, params),
	get: (id: string) => get<Partner>(`/${kind}/${id}`),
	create: (input: PartnerInput & { code: string }) => post<Partner>(`/${kind}`, input),
	update: (id: string, changes: PartnerInput) => patch<Partner>(`/${kind}/${id}`, changes),
	deactivate: (id: string) => del<Partner>(`/${kind}/${id}`),
	reactivate: (id: string) => post<Partner>(`/${kind}/${id}/reactivate`),
	addContact: (id: string, input: ContactInput) => post<Contact>(`/${kind}/${id}/contacts`, input),
	updateContact: (id: string, contactId: string, input: ContactInput) => patch<Contact>(`/${kind}/${id}/contacts/${contactId}`, input),
	deactivateContact: (id: string, contactId: string) => del<Contact>(`/${kind}/${id}/contacts/${contactId}`),
	reactivateContact: (id: string, contactId: string) => post<Contact>(`/${kind}/${id}/contacts/${contactId}/reactivate`),
});
