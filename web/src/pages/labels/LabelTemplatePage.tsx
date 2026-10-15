import { ITAccordion, ITAlert, ITBadget, ITButton, ITCard, ITCheckbox, ITChip, ITFlex, ITGrid, ITInput, ITPage, ITText, ITTextarea } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { labelTemplateApi, problemLines, type LabelTemplate, type TemplateVersion, type ZplProblem } from "@entities/label";
import { useCan } from "@entities/user";
import { formatDateTime } from "@shared/lib/format";
import { useSubmit } from "@shared/ui/useSubmit";
import { toast } from "@app/store";

// How long the editor waits after the last keystroke before it asks for the report.
const CHECK_DELAY_MS = 300;

// One template: its names, its ZPL with the report of its problems next to it as the person types, and its versions.
// /labels/new creates one; its key never changes after (labels.md sections 2 to 4). The screen is built with the
// components of the UI kit only (web.md section 8), and the way back to the list is its breadcrumbs.
export function LabelTemplatePage() {
	const { t, i18n } = useTranslation(["labels", "common"]);
	const { id } = useParams();
	const navigate = useNavigate();
	const canManage = useCan("labels.manage");
	const creating = !id;
	const [template, setTemplate] = useState<LabelTemplate | null>(null);
	const [versions, setVersions] = useState<TemplateVersion[]>([]);
	const [missing, setMissing] = useState(false);
	const [key, setKey] = useState("");
	const [nameEs, setNameEs] = useState("");
	const [nameEn, setNameEn] = useState("");
	const [zpl, setZpl] = useState("");
	const [active, setActive] = useState(true);
	const [problems, setProblems] = useState<ZplProblem[] | null>(null);
	const [checking, setChecking] = useState(false);
	const [tried, setTried] = useState(false);
	const submit = useSubmit();

	const load = useCallback(async () => {
		if (!id) return;
		try {
			const [found, history] = await Promise.all([labelTemplateApi.get(id), labelTemplateApi.versions(id)]);
			setTemplate(found);
			setVersions(history);
			setKey(found.key);
			setNameEs(found.nameEs);
			setNameEn(found.nameEn);
			setZpl(found.zpl);
			setActive(found.active);
		} catch {
			setMissing(true);
		}
	}, [id]);

	useEffect(() => {
		void load();
	}, [load]);

	// The report follows the text: a pause in the typing asks the API, and only the answer for the latest text is shown.
	useEffect(() => {
		if (!canManage || !zpl.trim()) {
			setProblems(null);
			return;
		}
		let current = true;
		setChecking(true);
		const timer = setTimeout(() => {
			labelTemplateApi
				.validate(key, zpl)
				.then((report) => current && setProblems(report.problems))
				.catch(() => current && setProblems(null))
				.finally(() => current && setChecking(false));
		}, CHECK_DELAY_MS);
		return () => {
			current = false;
			clearTimeout(timer);
		};
	}, [canManage, key, zpl, i18n.language]);

	const required = t("common:validation.required");
	const local = {
		key: creating && !key.trim() ? required : undefined,
		nameEs: !nameEs.trim() ? required : undefined,
		nameEn: !nameEn.trim() ? required : undefined,
		zpl: !zpl.trim() ? required : undefined,
	};
	const errors = {
		key: submit.fieldErrors.key ?? (tried ? local.key : undefined),
		nameEs: submit.fieldErrors.nameEs ?? (tried ? local.nameEs : undefined),
		nameEn: submit.fieldErrors.nameEn ?? (tried ? local.nameEn : undefined),
		zpl: submit.fieldErrors.zpl ?? (tried ? local.zpl : undefined),
	};

	async function save(event: FormEvent) {
		event.preventDefault();
		setTried(true);
		if (Object.values(local).some(Boolean)) return;
		if (creating) {
			const created = await submit.run(() => labelTemplateApi.create({ key: key.trim(), nameEs: nameEs.trim(), nameEn: nameEn.trim(), zpl }));
			if (created) {
				toast(t("editor.saved", { version: created.version }));
				navigate(`/labels/${created.id}`, { replace: true });
			}
			return;
		}
		const saved = await submit.run(() => labelTemplateApi.update(template!.id, { nameEs: nameEs.trim(), nameEn: nameEn.trim(), zpl, active }));
		if (saved) {
			toast(saved.version === template!.version ? t("editor.unchanged", { version: saved.version }) : t("editor.saved", { version: saved.version }));
			await load();
		}
	}

	const title = creating ? t("editor.newTitle") : template ? `${template.key} · ${template.name}` : t("templates.title");
	const crumbs = [{ label: t("common:nav.labels"), onClick: () => navigate("/labels") }, { label: title }];

	if (missing) {
		return (
			<ITPage title={t("templates.title")} breadcrumbs={crumbs} noPadding>
				<ITAlert variant="error">{t("common:errors.notFound")}</ITAlert>
			</ITPage>
		);
	}

	const placeholders = template?.placeholders ?? ["copies"];
	const lineLabel = (lines: number[]) => (lines.length === 1 ? t("editor.line", { line: lines[0] }) : t("editor.lines", { line: lines.join(", ") }));

	return (
		<ITPage title={title} breadcrumbs={crumbs} noPadding loading={!creating && !template}>
			<form onSubmit={(event) => void save(event)} noValidate className="sf:flex sf:flex-col sf:gap-6">
				<ITGrid container spacing={4}>
					<ITGrid item xs={12} md={4}>
						<ITInput
							name="key"
							label={t("editor.key")}
							value={key}
							disabled={!creating || !canManage}
							onChange={(e) => setKey(e.target.value.toUpperCase())}
							error={errors.key ?? (creating ? t("editor.keyHint") : undefined)}
							touched={!!errors.key}
							required
						/>
					</ITGrid>
					<ITGrid item xs={12} md={4}>
						<ITInput name="nameEs" label={t("editor.nameEs")} value={nameEs} disabled={!canManage} onChange={(e) => setNameEs(e.target.value)} error={errors.nameEs} touched={!!errors.nameEs} required />
					</ITGrid>
					<ITGrid item xs={12} md={4}>
						<ITInput name="nameEn" label={t("editor.nameEn")} value={nameEn} disabled={!canManage} onChange={(e) => setNameEn(e.target.value)} error={errors.nameEn} touched={!!errors.nameEn} required />
					</ITGrid>
				</ITGrid>
				<ITGrid container spacing={4}>
					<ITGrid item xs={12} lg={8}>
						<ITTextarea name="zpl" label={t("editor.zpl")} value={zpl} rows={16} disabled={!canManage} onChange={setZpl} error={errors.zpl} resize="vertical" className="sf:[&_textarea]:font-mono sf:[&_textarea]:text-code-sm" />
					</ITGrid>
					<ITGrid item xs={12} lg={4}>
						<ITCard title={t("editor.report")}>
							<ITText as="aside" aria-live="polite" data-testid="zpl-report" className="sf:flex sf:flex-col sf:gap-3">
								{checking && <ITText muted>{t("editor.checking")}</ITText>}
								{!checking && problems?.length === 0 && <ITAlert variant="success">{t("editor.valid")}</ITAlert>}
								{problems?.map((problem, index) => {
									const lines = problemLines(zpl, problem.token);
									return (
										<ITAlert key={`${problem.code}-${problem.token ?? index}`} variant="error">
											<ITText as="span" className="sf:block">
												{problem.message}
											</ITText>
											{(problem.token || lines.length > 0) && (
												<ITText as="span" className="sf:block sf:text-code-sm">
													{[problem.token, lines.length > 0 ? lineLabel(lines) : null].filter(Boolean).join(" · ")}
												</ITText>
											)}
										</ITAlert>
									);
								})}
								<ITText as="h2" className="sf:text-label-md">
									{t("editor.placeholders")}
								</ITText>
								<ITFlex wrap="wrap" gap={2}>
									{placeholders.map((name) => (
										<ITChip key={name} label={`{{${name}}}`} variant="soft" />
									))}
								</ITFlex>
							</ITText>
						</ITCard>
					</ITGrid>
				</ITGrid>
				{!creating && canManage && <ITCheckbox name="active" label={t("editor.active")} checked={active} onChange={setActive} />}
				{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
				{canManage && <ITButton type="submit" label={t("editor.save")} disabled={submit.submitting} />}
			</form>
			{versions.length > 0 && (
				<ITText as="section" data-testid="template-versions" className="sf:mt-8 sf:flex sf:flex-col sf:gap-2">
					<ITText as="h2" className="sf:text-title-md">
						{t("editor.versions")}
					</ITText>
					<ITAccordion
						variant="bordered"
						items={versions.map((version) => ({
							id: String(version.version),
							title: (
								<ITText as="span" data-version={version.version} className="sf:flex sf:items-center sf:gap-3">
									<ITText as="span">{t("editor.version", { version: version.version })}</ITText>
									<ITText as="span" muted>
										{formatDateTime(version.createdAt, i18n.language)}
									</ITText>
									{version.version === template?.version && <ITBadget color="success" label={t("editor.current")} />}
								</ITText>
							),
							content: (
								<ITText as="pre" className="sf:overflow-x-auto sf:font-mono sf:text-code-sm">
									{version.zpl}
								</ITText>
							),
						}))}
					/>
				</ITText>
			)}
		</ITPage>
	);
}
