import { ITAlert, ITButton, ITCard, ITFlex, ITGrid, ITInput, ITPage, ITSearchSelect, ITStack } from "@axzydev/axzy_ui_system";
import { trail } from "@shared/lib/breadcrumbs";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { partnerApi, postalCodeInvalid, taxIdInvalid, type AddressInput, type Partner, type PartnerKind } from "@entities/partner";
import { useSubmit } from "@shared/ui/useSubmit";

interface Values extends AddressInput {
	code: string;
	name: string;
	taxId: string;
	paymentTerms: string;
}

const EMPTY: Values = {
	code: "", name: "", taxId: "", paymentTerms: "",
	street: "", exteriorNumber: "", interiorNumber: "", neighborhood: "", city: "", state: "", postalCode: "", country: "MX",
};

const fromPartner = (partner: Partner): Values => ({
	code: partner.code,
	name: partner.name,
	taxId: partner.taxId,
	paymentTerms: partner.paymentTerms?.code ?? "",
	street: partner.address.street,
	exteriorNumber: partner.address.exteriorNumber,
	interiorNumber: partner.address.interiorNumber ?? "",
	neighborhood: partner.address.neighborhood,
	city: partner.address.city,
	state: partner.address.state?.code ?? "",
	postalCode: partner.address.postalCode,
	country: partner.address.country?.code ?? "MX",
});

const REQUIRED: (keyof Values)[] = ["name", "taxId", "paymentTerms", "street", "exteriorNumber", "neighborhood", "city", "state", "postalCode", "country"];

// The form of a supplier or a customer, new or existing. Each error from the API shows next to its field.
// The form element is the kit's one exception (it carries `onSubmit`) and everything inside it is the kit (web.md 8).
export function PartnerFormPage({ kind }: { kind: PartnerKind }) {
	const { t } = useTranslation(["partners", "common"]);
	const { id } = useParams();
	const navigate = useNavigate();
	const api = partnerApi(kind);
	const [values, setValues] = useState<Values>(EMPTY);
	const [tried, setTried] = useState(false);
	const [catalogs, setCatalogs] = useState<{ countries: CatalogItem[]; states: CatalogItem[]; terms: CatalogItem[] }>({ countries: [], states: [], terms: [] });
	const submit = useSubmit();
	const set = (field: keyof Values) => (value: string) => setValues((current) => ({ ...current, [field]: value }));

	useEffect(() => {
		void Promise.all([catalogApi.items("COUNTRY"), catalogApi.items("STATE"), catalogApi.items("PAYMENT_TERMS")]).then(([countries, states, terms]) =>
			setCatalogs({ countries, states, terms }),
		);
		if (id) void api.get(id).then((partner) => setValues(fromPartner(partner)));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [kind, id]);

	// The states of the chosen country, by the attribute each state carries.
	const states = catalogs.states.filter((state) => state.attributes.country === values.country);
	const required = t("common:validation.required");
	// The formats answer while the field has something to judge; the required check still waits for the first attempt.
	const error = (field: keyof Values, apiField: string = field) =>
		submit.fieldErrors[apiField] ??
		(tried && REQUIRED.includes(field) && !values[field].trim() ? required : undefined) ??
		(tried && field === "code" && !id && !values.code.trim() ? required : undefined) ??
		(field === "taxId" && values.taxId.trim() && taxIdInvalid(values.taxId.trim()) ? t("form.taxIdInvalid") : undefined) ??
		(field === "postalCode" && values.postalCode.trim() && postalCodeInvalid(values.postalCode.trim()) ? t("form.postalCodeInvalid") : undefined);
	const addressError = (field: keyof AddressInput) => error(field, `address.${field}`);

	// The first thing still missing or malformed: it closes the save button and says why, instead of a click that
	// answers nothing.
	const firstProblem = () => {
		const empty = REQUIRED.find((field) => !values[field].trim()) ?? (!id && !values.code.trim() ? "code" : undefined);
		if (empty) return `${t(`form.${empty}`)}: ${required}`;
		if (values.taxId.trim() && taxIdInvalid(values.taxId.trim())) return t("form.taxIdInvalid");
		if (values.postalCode.trim() && postalCodeInvalid(values.postalCode.trim())) return t("form.postalCodeInvalid");
		return null;
	};
	const blockedReason = firstProblem();

	async function save(event: FormEvent) {
		event.preventDefault();
		setTried(true);
		if (blockedReason) return;
		const { code, name, taxId, paymentTerms, ...address } = values;
		const input = { name, taxId, paymentTerms, address };
		const saved = await submit.run(() => (id ? api.update(id, input) : api.create({ code, ...input })));
		if (saved) navigate(`/${kind}/${saved.id}`);
	}

	const select = (field: "state" | "country" | "paymentTerms", label: string, items: CatalogItem[], fieldError?: string) => (
		<ITSearchSelect
			name={field}
			label={label}
			value={values[field]}
			onChange={(value) => set(field)(String(value))}
			options={items.map((item) => ({ value: item.code, label: item.label ?? item.code }))}
			valueField="value"
			labelField="label"
			error={fieldError}
			touched={!!fieldError}
			required
		/>
	);
	const input = (field: keyof AddressInput, extra: { hint?: string; required?: boolean; maxLength?: number } = {}) => (
		<ITInput
			name={field}
			label={t(`form.${field}`)}
			value={values[field]}
			onChange={(event) => set(field)(String(event.target.value))}
			error={addressError(field) ?? extra.hint}
			touched={!!addressError(field)}
			required={extra.required ?? true}
			maxLength={extra.maxLength}
		/>
	);

	const title = t(id ? `${kind}.edit` : `${kind}.new`);

	return (
		<ITPage title={title} breadcrumbs={trail(t("common:nav.home"), navigate, { label: t(`${kind}.title`), onClick: () => navigate(`/${kind}`) }, { label: title })} noPadding>
			<form onSubmit={(e) => void save(e)} noValidate aria-label={title}>
				<ITStack spacing={6}>
					<ITCard title={t("form.general")}>
						<ITGrid container spacing={3}>
							<ITGrid item xs={12} md={6}>
								<ITInput
									name="code"
									label={t("form.code")}
									value={values.code}
									disabled={!!id}
									onChange={(e) => set("code")(e.target.value.toUpperCase())}
									error={error("code") ?? (id ? undefined : t("form.codeHint"))}
									touched={!!error("code")}
									required
								/>
							</ITGrid>
							<ITGrid item xs={12} md={6}>
								<ITInput
									name="taxId"
									label={t("form.taxId")}
									value={values.taxId}
									onChange={(e) => set("taxId")(e.target.value.toUpperCase())}
									error={error("taxId") ?? t("form.taxIdHint")}
									touched={!!error("taxId")}
									required
								/>
							</ITGrid>
							<ITGrid item xs={12}>
								<ITInput name="name" label={t("form.name")} value={values.name} onChange={(e) => set("name")(e.target.value)} error={error("name")} touched={!!error("name")} required />
							</ITGrid>
							<ITGrid item xs={12} md={6}>
								{select("paymentTerms", t("form.paymentTerms"), catalogs.terms, error("paymentTerms"))}
							</ITGrid>
						</ITGrid>
						{catalogs.terms.length === 0 && <ITAlert variant="warning">{t("form.noPaymentTerms")}</ITAlert>}
					</ITCard>

					<ITCard title={t("form.address")}>
						<ITGrid container spacing={3}>
							<ITGrid item xs={12} md={6}>
								{input("street")}
							</ITGrid>
							<ITGrid item xs={12} md={3}>
								{input("exteriorNumber", { hint: t("form.exteriorHint") })}
							</ITGrid>
							<ITGrid item xs={12} md={3}>
								{input("interiorNumber", { required: false })}
							</ITGrid>
							<ITGrid item xs={12} md={6}>
								{input("neighborhood")}
							</ITGrid>
							<ITGrid item xs={12} md={6}>
								{input("city")}
							</ITGrid>
							<ITGrid item xs={12} md={3}>
								{input("postalCode", { maxLength: 5 })}
							</ITGrid>
							<ITGrid item xs={12} md={6}>
								{select("state", t("form.state"), states, addressError("state"))}
							</ITGrid>
							<ITGrid item xs={12} md={3}>
								{select("country", t("form.country"), catalogs.countries, addressError("country"))}
							</ITGrid>
						</ITGrid>
					</ITCard>

					{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
					{blockedReason && <ITAlert variant="warning">{blockedReason}</ITAlert>}
					<ITFlex justify="end" gap={2}>
						<ITButton variant="outlined" label={t("form.cancel")} onClick={() => navigate(id ? `/${kind}/${id}` : `/${kind}`)} />
						<ITButton type="submit" label={t("form.save")} disabled={submit.submitting || !!blockedReason} />
					</ITFlex>
				</ITStack>
			</form>
		</ITPage>
	);
}
