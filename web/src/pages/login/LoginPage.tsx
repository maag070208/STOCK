import { LoginForm } from "@features/auth/login";
import { EntryLayout } from "@widgets/entry-layout/EntryLayout";

export function LoginPage() {
	return (
		<EntryLayout>
			<LoginForm />
		</EntryLayout>
	);
}
