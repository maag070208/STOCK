import { ITToast } from "@axzydev/axzy_ui_system";
import { useDispatch, useSelector } from "react-redux";
import type { AppDispatch, RootState } from "@app/store";
import { toastClosed } from "./toast.slice";

export function Toasts() {
	const toasts = useSelector((state: RootState) => state.toast);
	const dispatch = useDispatch<AppDispatch>();
	return (
		<>
			{toasts.map((toast) => (
				<ITToast key={toast.id} message={toast.message} type={toast.type} duration={3000} onClose={() => dispatch(toastClosed(toast.id))} />
			))}
		</>
	);
}
