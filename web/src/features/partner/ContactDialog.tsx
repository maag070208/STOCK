import { ITGrid, ITInput } from "@axzydev/axzy_ui_system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { canSaveContact, contactErrors, partnerApi, type Contact, type ContactInput, type PartnerKind } from "@entities/partner";
import { FormDialog } from "@shared/ui/FormDialog";
import { useSubmit } from "@shared/ui/useSubmit";

const fromContact = (contact: Contact | null): ContactInput => ({ name: contact?.name ?? "", email: contact?.email ?? "", phone: contact?.phone ?? "" });

// A contact of a supplier or a customer: a name, and an email or a phone. Save stays closed until both rules hold.
export function ContactDialog(props: { kind: PartnerKind; partnerId: string; contact: Contact | null; isOpen: boolean; onClose: () => void; onSaved: () => void }) {
	const { t } = useTranslation(["partners", "common"]);
	const [values, setValues] = useState<ContactInput>(fromContact(null));
	const [touched, setTouched] = useState(false);
	const submit = useSubmit();
	const set = (field: keyof ContactInput) => (value: string) => {
		setTouched(true);
		setValues((current) => ({ ...current, [field]: value }));
	};

	useEffect(() => {
		if (props.isOpen) {
			setValues(fromContact(props.contact));
			setTouched(false);
			submit.reset();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [props.isOpen, props.contact]);

	const local = contactErrors(values);
	const message = (field: keyof ContactInput) =>
		submit.fieldErrors[field] ?? (touched && local[field] ? (local[field] === "FIELD_REQUIRED" ? t("common:validation.required") : t("contacts.channelHint")) : undefined);

	async function save() {
		setTouched(true);
		if (!canSaveContact(values)) return;
		const api = partnerApi(props.kind);
		const saved = await submit.run(() => (props.contact ? api.updateContact(props.partnerId, props.contact.id, values) : api.addContact(props.partnerId, values)));
		if (saved) props.onSaved();
	}

	return (
		<FormDialog
			isOpen={props.isOpen}
			title={props.contact ? t("contacts.edit") : t("contacts.new")}
			onClose={props.onClose}
			onSubmit={() => void save()}
			submitting={submit.submitting || !canSaveContact(values)}
			error={submit.error}
		>
			<ITGrid container spacing={3} className="sf:min-w-[28rem]">
				<ITGrid item xs={12}>
					<ITInput name="name" label={t("contacts.name")} value={values.name} onChange={(e) => set("name")(e.target.value)} error={message("name")} touched={!!message("name")} required />
				</ITGrid>
				<ITGrid item xs={12} sm={6}>
					<ITInput name="email" type="email" label={t("contacts.email")} value={values.email} onChange={(e) => set("email")(e.target.value)} error={message("email") ?? t("contacts.channelHint")} touched={!!message("email")} />
				</ITGrid>
				<ITGrid item xs={12} sm={6}>
					<ITInput name="phone" label={t("contacts.phone")} value={values.phone} onChange={(e) => set("phone")(e.target.value)} error={message("phone")} touched={!!message("phone")} />
				</ITGrid>
			</ITGrid>
		</FormDialog>
	);
}
