import { ITPage, ITTabs } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { accessApi, type MatrixCell, type PermissionDefinition, type Policy, type PolicyAction, type Role } from "@entities/permission";
import { MatrixPanel } from "@features/permission/MatrixPanel";
import { PoliciesPanel } from "@features/permission/PoliciesPanel";
import { RolesPanel } from "@features/permission/RolesPanel";
import { ScreensPanel } from "@features/permission/ScreensPanel";
import { toast } from "@app/store";

// The access console (roles.manage only): roles, the matrix, policies, and what each role sees.
export function AccessPage() {
	const { t } = useTranslation(["access", "common"]);
	const [data, setData] = useState<{ roles: Role[]; permissions: PermissionDefinition[]; cells: MatrixCell[]; policies: Policy[]; actions: PolicyAction[] } | null>(null);
	const [error, setError] = useState<string | null>(null);

	const load = useCallback(async () => {
		try {
			const [roles, permissions, cells, policies, actions] = await Promise.all([accessApi.roles(), accessApi.permissions(), accessApi.matrix(), accessApi.policies(), accessApi.policyActions()]);
			setData({ roles, permissions, cells, policies, actions });
			setError(null);
		} catch (failure) {
			setError((failure as Error).message);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const changed = (message?: string) => {
		toast(message ?? t("common:saved"));
		void load();
	};
	const failed = (message: string) => toast(message, "error");

	return (
		<ITPage title={t("title")} description={t("description")} loading={!data && !error} error={error} onRetry={() => void load()} noPadding>
			{data && (
				<ITTabs
					variant="line"
					items={[
						{ id: "roles", label: t("tabs.roles"), content: <RolesPanel roles={data.roles} onChanged={changed} onError={failed} /> },
						{ id: "matrix", label: t("tabs.matrix"), content: <MatrixPanel permissions={data.permissions} roles={data.roles} cells={data.cells} onSaved={() => changed(t("matrix.saved"))} /> },
						{ id: "policies", label: t("tabs.policies"), content: <PoliciesPanel policies={data.policies} actions={data.actions} roles={data.roles} onChanged={changed} onError={failed} /> },
						{ id: "screens", label: t("tabs.screens"), content: <ScreensPanel roles={data.roles} cells={data.cells} /> },
					]}
				/>
			)}
		</ITPage>
	);
}
