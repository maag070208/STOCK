import { ITButton, ITCheckbox } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { staffApi, type StaffMember } from "@entities/invoice";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// The staff template of a warehouse (material-entry.md, question 4): the operators its entries are shared among.
// Who assigns sees it; who manages it chooses, from the active operators who work in the warehouse.
export function StaffTemplate(props: { warehouseId: string; canManage: boolean; onSaved: () => void }) {
	const { t } = useTranslation(["invoices", "common"]);
	const [members, setMembers] = useState<StaffMember[]>([]);
	const [editing, setEditing] = useState(false);
	const [candidates, setCandidates] = useState<StaffMember[]>([]);
	const [chosen, setChosen] = useState<Set<string>>(new Set());
	const submit = useSubmit();

	const load = useCallback(async () => setMembers(await staffApi.list(props.warehouseId).catch(() => [])), [props.warehouseId]);

	useEffect(() => {
		void load();
	}, [load]);

	async function open() {
		submit.reset();
		setChosen(new Set(members.map((m) => m.id)));
		setCandidates(await staffApi.candidates(props.warehouseId));
		setEditing(true);
	}

	async function save() {
		const saved = await submit.run(() => staffApi.replace(props.warehouseId, [...chosen]));
		if (saved) {
			setMembers(saved);
			setEditing(false);
			props.onSaved();
		}
	}

	const toggle = (id: string, on: boolean) => setChosen((current) => {
		const next = new Set(current);
		if (on) next.add(id);
		else next.delete(id);
		return next;
	});

	return (
		<section aria-label={t("staff.title")} className="sf:flex sf:flex-col sf:gap-3" data-testid="staff-template">
			<div className="sf:flex sf:items-center sf:justify-between">
				<div>
					<h2 className="sf:text-title-md">{t("staff.title")}</h2>
					<p className="sf:text-body-md sf:text-on-surface-muted">{t("staff.description")}</p>
				</div>
				{props.canManage && <ITButton size="sm" variant="outlined" label={t("staff.edit")} onClick={() => void open()} />}
			</div>
			{members.length === 0 ? (
				<p className="sf:text-body-md sf:text-on-surface-muted">{t("staff.none")}</p>
			) : (
				<ul className="sf:flex sf:flex-wrap sf:gap-2">
					{members.map((member) => (
						<li key={member.id} className="sf:rounded-full sf:border sf:border-outline-variant sf:px-3 sf:py-1 sf:text-body-md">
							{member.name}
						</li>
					))}
				</ul>
			)}
			<FormDialog isOpen={editing} title={t("staff.edit")} onClose={() => setEditing(false)} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
				<p className="sf:text-label-md">{t("staff.candidates")}</p>
				{candidates.length === 0 && <p className="sf:text-body-md sf:text-on-surface-muted">{t("staff.noCandidates")}</p>}
				<div className="sf:flex sf:min-w-80 sf:flex-col sf:gap-2">
					{candidates.map((candidate) => (
						<ITCheckbox key={candidate.id} name={`staff-${candidate.id}`} label={`${candidate.name} (${candidate.username})`} checked={chosen.has(candidate.id)} onChange={(on) => toggle(candidate.id, on)} />
					))}
				</div>
			</FormDialog>
		</section>
	);
}
