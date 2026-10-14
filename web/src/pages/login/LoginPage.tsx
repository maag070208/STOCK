import { LoginForm } from "@features/auth/login";
import { EntryLayout } from "@widgets/entry-layout/EntryLayout";
import { LanguageSwitch } from "@widgets/language-switch/LanguageSwitch";

export function LoginPage() {
	return (
		<EntryLayout>
			<LoginForm />
			<LanguageSwitch />
		</EntryLayout>
	);
}
