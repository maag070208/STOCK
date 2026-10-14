import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { ChangePasswordForm } from "@features/auth/change-password";
import type { RootState } from "@app/store";
import { EntryLayout } from "@widgets/entry-layout/EntryLayout";

export function ChangePasswordPage() {
	const forced = useSelector((state: RootState) => state.auth.user?.mustChangePassword ?? false);
	const navigate = useNavigate();
	return (
		<EntryLayout>
			<ChangePasswordForm forced={forced} onDone={() => navigate("/", { replace: true })} />
		</EntryLayout>
	);
}
