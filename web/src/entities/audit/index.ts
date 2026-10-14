import { tableRequest, type TableParams } from "@shared/api/table";

export interface AuditRecord {
	id: string;
	occurredAt: string;
	actorId: string | null;
	actor: string;
	action: string;
	targetType: string;
	targetId: string;
	before: unknown;
	after: unknown;
	[key: string]: unknown;
}

export const auditApi = {
	query: (params: TableParams) => tableRequest<AuditRecord>("/audit-log/query", params),
};
