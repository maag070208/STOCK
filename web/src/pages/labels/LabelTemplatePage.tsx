import { ITAlert, ITBadget, ITButton, ITCheckbox, ITInput, ITPage, ITTextarea } from "@axzydev/axzy_ui_system";
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
// /labels/new creates one; its key never changes after (labels.md sections 2 to 4).
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

	const back = <ITButton variant="outlined" label={t("editor.back")} onClick={() => navigate("/labels")} />;
	if (missing) {
		return (
			<ITPage title={t("templates.title")} noPadding>
				<p className="sf:text-body-md">{t("common:errors.notFound")}</p>
				{back}
			</ITPage>
		);
	}

	const placeholders = template?.placeholders ?? ["copies"];
	const lineLabel = (lines: number[]) => (lines.length === 1 ? t("editor.line", { line: lines[0] }) : t("editor.lines", { lines: lines.join(", ") }));

	return (
		<ITPage title={creating ? t("editor.newTitle") : template ? `${template.key} · ${template.name}` : t("templates.title")} noPadding loading={!creating && !template} actions={back}>
			<form onSubmit={(event) => void save(event)} noValidate className="sf:flex sf:flex-col sf:gap-6">
				<div className="sf:grid sf:gap-4 sf:md:grid-cols-3">
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
					<ITInput name="nameEs" label={t("editor.nameEs")} value={nameEs} disabled={!canManage} onChange={(e) => setNameEs(e.target.value)} error={errors.nameEs} touched={!!errors.nameEs} required />
					<ITInput name="nameEn" label={t("editor.nameEn")} value={nameEn} disabled={!canManage} onChange={(e) => setNameEn(e.target.value)} error={errors.nameEn} touched={!!errors.nameEn} required />
				</div>
				<div className="sf:grid sf:gap-4 sf:lg:grid-cols-[1fr_22rem]">
					<ITTextarea name="zpl" label={t("editor.zpl")} value={zpl} rows={16} disabled={!canManage} onChange={setZpl} error={errors.zpl} resize="vertical" className="sf:[&_textarea]:font-mono sf:[&_textarea]:text-code-sm" />
					<aside className="sf:flex sf:flex-col sf:gap-3" aria-live="polite" data-testid="zpl-report">
						<h2 className="sf:text-label-md">{t("editor.report")}</h2>
						{checking && <p className="sf:text-body-sm sf:text-on-surface-muted">{t("editor.checking")}</p>}
						{!checking && problems?.length === 0 && <ITAlert variant="success">{t("editor.valid")}</ITAlert>}
						{problems?.map((problem, index) => {
							const lines = problemLines(zpl, problem.token);
							return (
								<ITAlert key={`${problem.code}-${problem.token ?? index}`} variant="error">
									<span className="sf:block">{problem.message}</span>
									{(problem.token || lines.length > 0) && (
										<span className="sf:block sf:text-code-sm">
											{[problem.token, lines.length > 0 ? lineLabel(lines) : null].filter(Boolean).join(" · ")}
										</span>
									)}
								</ITAlert>
							);
						})}
						<h2 className="sf:text-label-md">{t("editor.placeholders")}</h2>
						<ul className="sf:flex sf:flex-wrap sf:gap-2">
							{placeholders.map((name) => (
								<li key={name} className="sf:rounded sf:bg-surface sf:px-2 sf:py-1 sf:text-code-sm">{`{{${name}}}`}</li>
							))}
						</ul>
					</aside>
				</div>
				{!creating && canManage && <ITCheckbox name="active" label={t("editor.active")} checked={active} onChange={setActive} />}
				{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
				{canManage && (
					<div>
						<ITButton type="submit" label={t("editor.save")} disabled={submit.submitting} />
					</div>
				)}
			</form>
			{versions.length > 0 && (
				<section className="sf:mt-8 sf:flex sf:flex-col sf:gap-2" data-testid="template-versions">
					<h2 className="sf:text-title-md">{t("editor.versions")}</h2>
					{versions.map((version) => (
						<details key={version.version} className="sf:rounded sf:border sf:border-outline sf:p-3" data-version={version.version}>
							<summary className="sf:flex sf:cursor-pointer sf:items-center sf:gap-3 sf:text-body-md">
								<span>{t("editor.version", { version: version.version })}</span>
								<span className="sf:text-on-surface-muted">{formatDateTime(version.createdAt, i18n.language)}</span>
								{version.version === template?.version && <ITBadget color="success" label={t("editor.current")} />}
							</summary>
							<pre className="sf:mt-2 sf:overflow-x-auto sf:font-mono sf:text-code-sm">{version.zpl}</pre>
						</details>
					))}
				</section>
			)}
		</ITPage>
	);
}
