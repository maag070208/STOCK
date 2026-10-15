import { type Column, ITText } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { APP_SCREENS, roleLabel, screensOfRole, type MatrixCell, type Role } from "@entities/permission";
import { PlainTable } from "@shared/ui/PlainTable";

// One row of the table: a screen, which the kit tells apart by its id.
type ScreenRow = Record<string, unknown> & { id: string; labelKey: (typeof APP_SCREENS)[number]["labelKey"] };

// What each role sees: the screens of APP_SCREENS each role reaches through the matrix. The same source builds the menu,
// and the table is the kit's (web.md section 8).
export function ScreensPanel(props: { roles: Role[]; cells: MatrixCell[] }) {
	const { t } = useTranslation(["access", "common"]);
	const roles = props.roles.filter((role) => role.active);
	const seen = Object.fromEntries(roles.map((role) => [role.key, new Set(screensOfRole(APP_SCREENS, props.cells, role.key).map((s) => s.id))]));
	const rows: ScreenRow[] = APP_SCREENS.map((screen) => ({ id: screen.id, labelKey: screen.labelKey }));

	const columns: Column<ScreenRow>[] = [
		{ key: "screen", label: t("screens.screen"), type: "string", render: (row) => t(`common:${row.labelKey}`) },
		...roles.map((role) => ({
			key: role.key,
			label: roleLabel(role.key, role.name),
			type: "string" as const,
			className: "sf:text-center",
			render: (row: ScreenRow) =>
				seen[role.key].has(row.id) ? (
					<ITText data-screen={`${row.id}|${role.key}`} className="sf:text-success">
						✓
					</ITText>
				) : (
					<ITText data-screen={`${row.id}|${role.key}`} muted>
						—
					</ITText>
				),
		})),
	];

	return (
		<PlainTable<ScreenRow>
			columns={columns}
			data={rows}
			size="sm"
		/>
	);
}
