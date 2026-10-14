import { ITAlert, ITButton, ITInput, ITPage, ITSelect } from "@axzydev/axzy_ui_system";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { catalogApi, type CatalogItem } from "@entities/catalog";
import { partnerApi, type AddressInput, type Partner, type PartnerKind } from "@entities/partner";
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
	const error = (field: keyof Values, apiField: string = field) =>
		submit.fieldErrors[apiField] ?? (tried && REQUIRED.includes(field) && !values[field].trim() ? required : undefined) ?? (tried && field === "code" && !id && !values.code.trim() ? required : undefined);
	const addressError = (field: keyof AddressInput) => error(field, `address.${field}`);

	async function save(event: FormEvent) {
		event.preventDefault();
		setTried(true);
		if (REQUIRED.some((field) => !values[field].trim()) || (!id && !values.code.trim())) return;
		const { code, name, taxId, paymentTerms, ...address } = values;
		const input = { name, taxId, paymentTerms, address };
		const saved = await submit.run(() => (id ? api.update(id, input) : api.create({ code, ...input })));
		if (saved) navigate(`/${kind}/${saved.id}`);
	}

	const select = (field: "state" | "country" | "paymentTerms", label: string, items: CatalogItem[], fieldError?: string) => (
		<ITSelect
			name={field}
			label={label}
			value={values[field]}
			onChange={(e) => set(field)(e.target.value)}
			options={items.map((item) => ({ value: item.code, label: item.label ?? item.code }))}
			valueField="value"
			labelField="label"
			error={fieldError}
			touched={!!fieldError}
			required
		/>
	);
	const input = (field: keyof AddressInput, extra: { hint?: string; required?: boolean } = {}) => (
		<ITInput
			name={field}
			label={t(`form.${field}`)}
			value={values[field]}
			onChange={(e) => set(field)(e.target.value)}
			error={addressError(field) ?? extra.hint}
			touched={!!addressError(field)}
			required={extra.required ?? true}
		/>
	);

	return (
		<ITPage title={t(id ? `${kind}.edit` : `${kind}.new`)} noPadding>
			<form onSubmit={(e) => void save(e)} noValidate className="sf:flex sf:max-w-3xl sf:flex-col sf:gap-6" aria-label={t(id ? `${kind}.edit` : `${kind}.new`)}>
				<section className="sf:flex sf:flex-col sf:gap-3">
					<h2 className="sf:text-title-md">{t("form.general")}</h2>
					<div className="sf:grid sf:grid-cols-1 sf:gap-3 sf:md:grid-cols-2">
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
						<ITInput
							name="taxId"
							label={t("form.taxId")}
							value={values.taxId}
							onChange={(e) => set("taxId")(e.target.value.toUpperCase())}
							error={error("taxId") ?? t("form.taxIdHint")}
							touched={!!error("taxId")}
							required
						/>
						<div className="sf:md:col-span-2">
							<ITInput name="name" label={t("form.name")} value={values.name} onChange={(e) => set("name")(e.target.value)} error={error("name")} touched={!!error("name")} required />
						</div>
						{select("paymentTerms", t("form.paymentTerms"), catalogs.terms, error("paymentTerms"))}
					</div>
					{catalogs.terms.length === 0 && <ITAlert variant="warning">{t("form.noPaymentTerms")}</ITAlert>}
				</section>

				<section className="sf:flex sf:flex-col sf:gap-3">
					<h2 className="sf:text-title-md">{t("form.address")}</h2>
					<div className="sf:grid sf:grid-cols-1 sf:gap-3 sf:md:grid-cols-4">
						<div className="sf:md:col-span-2">{input("street")}</div>
						{input("exteriorNumber", { hint: t("form.exteriorHint") })}
						{input("interiorNumber", { required: false })}
						<div className="sf:md:col-span-2">{input("neighborhood")}</div>
						<div className="sf:md:col-span-2">{input("city")}</div>
						{input("postalCode")}
						<div className="sf:md:col-span-2">{select("state", t("form.state"), states, addressError("state"))}</div>
						{select("country", t("form.country"), catalogs.countries, addressError("country"))}
					</div>
				</section>

				{submit.error && <ITAlert variant="error">{submit.error}</ITAlert>}
				<div className="sf:flex sf:justify-end sf:gap-2">
					<ITButton variant="outlined" label={t("form.cancel")} onClick={() => navigate(id ? `/${kind}/${id}` : `/${kind}`)} />
					<ITButton type="submit" label={t("form.save")} disabled={submit.submitting} />
				</div>
			</form>
		</ITPage>
	);
}
