import { get, patch, post } from "@shared/api/client";
import type { LabelTemplate, PrintablePrinter, Printer, PrinterInput, PrintRequest, PrintResult, TemplateInput, TemplateVersion, ZplProblem } from "../model/types";

export const labelTemplateApi = {
	list: () => get<LabelTemplate[]>("/label-templates"),
	get: (id: string) => get<LabelTemplate>(`/label-templates/${id}`),
	versions: (id: string) => get<TemplateVersion[]>(`/label-templates/${id}/versions`),
	// The editor's report as the person types: writes nothing.
	validate: (key: string, zpl: string) => post<{ problems: ZplProblem[]; placeholders: string[] }>("/label-templates/validate", { key, zpl }),
	create: (input: TemplateInput & { key: string }) => post<LabelTemplate>("/label-templates", input),
	update: (id: string, changes: Partial<TemplateInput> & { active?: boolean }) => patch<LabelTemplate>(`/label-templates/${id}`, changes),
};

export const printerApi = {
	list: () => get<Printer[]>("/printers"),
	create: (input: PrinterInput) => post<Printer>("/printers", input),
	update: (id: string, changes: Partial<PrinterInput> & { active?: boolean }) => patch<Printer>(`/printers/${id}`, changes),
};

export const printApi = {
	// The printers this person may print on.
	printers: () => get<PrintablePrinter[]>("/labels/printers"),
	print: (request: PrintRequest) => post<PrintResult>("/labels/print", request),
};
