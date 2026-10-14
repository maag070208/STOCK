import { useTranslation } from "react-i18next";
import { APP_SCREENS, roleLabel, screensOfRole, type MatrixCell, type Role } from "@entities/permission";

// What each role sees: the screens of APP_SCREENS each role reaches through the matrix. The same source builds the menu.
export function ScreensPanel(props: { roles: Role[]; cells: MatrixCell[] }) {
	const { t } = useTranslation(["access", "common"]);
	const roles = props.roles.filter((role) => role.active);
	const seen = Object.fromEntries(roles.map((role) => [role.key, new Set(screensOfRole(APP_SCREENS, props.cells, role.key).map((s) => s.id))]));
	return (
		<table className="sf:w-full sf:text-body-md" data-testid="screens-by-role">
			<thead className="sf:bg-surface sf:text-label-sm sf:uppercase sf:text-on-surface-muted">
				<tr>
					<th className="sf:p-3 sf:text-left">{t("screens.screen")}</th>
					{roles.map((role) => (
						<th key={role.key} className="sf:p-3 sf:text-center">{roleLabel(role.key, role.name)}</th>
					))}
				</tr>
			</thead>
			<tbody>
				{APP_SCREENS.map((screen) => (
					<tr key={screen.id} className="sf:border-t sf:border-outline">
						<td className="sf:p-3">{t(`common:${screen.labelKey}`)}</td>
						{roles.map((role) => (
							<td key={role.key} className="sf:p-3 sf:text-center" data-screen={`${screen.id}|${role.key}`}>
								{seen[role.key].has(screen.id) ? <span className="sf:text-success" aria-label="✓">✓</span> : <span className="sf:text-on-surface-muted">—</span>}
							</td>
						))}
					</tr>
				))}
			</tbody>
		</table>
	);
}
