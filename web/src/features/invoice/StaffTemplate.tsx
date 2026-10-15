import { ITButton, ITCheckbox, ITChip, ITFlex, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { staffApi, type StaffMember } from "@entities/invoice";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

// The staff template of a warehouse (material-entry.md, question 4): the operators its entries are shared among.
// Who assigns sees it; who manages it chooses, from the active operators who work in the warehouse. The section holds
// the UI kit and nothing else (web.md section 8), and each member is one of the kit's chips.
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
		<ITStack spacing={3}>
				<ITFlex justify="between" align="start" wrap="wrap" gap={2}>
					<ITStack spacing={1}>
						<ITText as="h2" className="sf:text-title-md">
							{t("staff.title")}
						</ITText>
						<ITText muted>{t("staff.description")}</ITText>
					</ITStack>
					{props.canManage && <ITButton size="sm" variant="outlined" label={t("staff.edit")} onClick={() => void open()} />}
				</ITFlex>
				{members.length === 0 ? (
					<ITText muted>{t("staff.none")}</ITText>
				) : (
					<ITFlex wrap="wrap" gap={2}>
						{members.map((member) => (
							<ITChip key={member.id} label={member.name} />
						))}
					</ITFlex>
				)}
				<FormDialog isOpen={editing} title={t("staff.edit")} onClose={() => setEditing(false)} onSubmit={() => void save()} submitting={submit.submitting} error={submit.error}>
					<ITText className="sf:text-label-md">{t("staff.candidates")}</ITText>
					{candidates.length === 0 && <ITText muted>{t("staff.noCandidates")}</ITText>}
					<ITStack spacing={2} className="sf:min-w-80">
						{candidates.map((candidate) => (
							<ITCheckbox key={candidate.id} name={`staff-${candidate.id}`} label={`${candidate.name} (${candidate.username})`} checked={chosen.has(candidate.id)} onChange={(on) => toggle(candidate.id, on)} />
						))}
					</ITStack>
			</FormDialog>
		</ITStack>
	);
}
