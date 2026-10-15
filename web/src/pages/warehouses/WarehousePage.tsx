import { type Column, type ColumnFilters, ITBadget, ITButton, ITConfirmDialog, ITDataTable, ITDropdownMenu, type ITDropdownMenuItem, ITFlex, ITPage, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaEllipsisV } from "react-icons/fa";
import { useNavigate, useParams } from "react-router-dom";
import { useCan } from "@entities/user";
import { locationApi, warehouseApi, type AisleGroup, type Location, type Warehouse } from "@entities/warehouse";
import { StaffTemplate } from "@features/invoice/StaffTemplate";
import { PrintDialog, type PrintTarget } from "@features/label/PrintDialog";
import { RackDialog } from "@features/warehouse/RackDialog";
import type { TableParams } from "@shared/api/table";
import { ApiError } from "@shared/api/client";
import { toast } from "@app/store";

type Group = { aisle: string; rack: string };
type Confirm = { kind: "deactivateLocation" | "reactivateLocation"; location: Location };

// One warehouse: its aisles and racks (the groups of locations.md section 3) and its locations.
// A rack's chip filters the list to that rack. A new rack comes from the rack form. The way back is the breadcrumbs
// and everything on the screen is a component of the kit (web.md section 8).
export function WarehousePage() {
	const { t } = useTranslation(["warehouses", "common", "labels"]);
	const { id = "" } = useParams();
	const navigate = useNavigate();
	const canManage = useCan("locations.manage");
	const canStaff = useCan("staff_templates.manage");
	const canAssign = useCan("receipts.assign");
	const canPrint = useCan("labels.print");
	const [warehouse, setWarehouse] = useState<Warehouse | null>(null);
	const [missing, setMissing] = useState(false);
	const [groups, setGroups] = useState<AisleGroup[]>([]);
	const [group, setGroup] = useState<Group | null>(null);
	const [creating, setCreating] = useState(false);
	const [confirm, setConfirm] = useState<Confirm | null>(null);
	const [reload, setReload] = useState(0);
	const [printing, setPrinting] = useState<PrintTarget | null>(null);

	const load = useCallback(async () => {
		try {
			const [found, aisles] = await Promise.all([warehouseApi.get(id), warehouseApi.groups(id)]);
			setWarehouse(found);
			setGroups(aisles);
		} catch {
			setMissing(true);
		}
	}, [id]);

	useEffect(() => {
		void load();
	}, [load]);

	const refresh = () => {
		setReload((value) => value + 1);
		void load();
	};

	// The same object between renders, so the table reloads only when the warehouse or the chosen rack changes.
	const filters = useMemo<ColumnFilters>(() => ({ warehouseId: id, ...(group ? { aisle: group.aisle, rack: group.rack } : {}) }), [id, group]);

	const columns: Column<Location>[] = [
		{
			key: "code",
			label: t("columns.location"),
			type: "string",
			filter: "search",
			sortable: true,
			render: (location) => (location.temporary ? `${location.code} · ${t("detail.temporary")}` : location.code),
		},
		{ key: "aisle", label: t("columns.aisle"), type: "string", filter: "search", sortable: true, render: (location) => location.aisle ?? "" },
		{ key: "rack", label: t("columns.rack"), type: "string", filter: "search", sortable: true, render: (location) => location.rack ?? "" },
		{ key: "level", label: t("columns.level"), type: "string", render: (location) => location.level ?? "" },
		{
			key: "active",
			label: t("columns.status"),
			type: "catalog",
			filter: "catalog",
			catalogOptions: { data: [{ id: "true", name: t("common:status.active") }, { id: "false", name: t("common:status.inactive") }] },
			render: (location) => <ITBadget color={location.active ? "success" : "gray"} label={t(location.active ? "common:status.active" : "common:status.inactive")} />,
		},
		{
			key: "actions",
			label: t("columns.actions"),
			type: "actions",
			actions: (location) => {
				// The temporary location belongs to the system: it has no actions, and no label to scan.
				if (location.temporary) return null;
				const items: ITDropdownMenuItem[] = [];
				if (canPrint && location.active) {
					items.push({
						id: "print",
						label: t("labels:print.button"),
						onClick: () => setPrinting({ kind: "LOCATION", locationId: location.id, warehouseId: location.warehouseId, title: location.code }),
					});
				}
				if (canManage) {
					items.push(
						location.active
							? { id: "deactivate", label: t("actions.deactivate"), danger: true, onClick: () => setConfirm({ kind: "deactivateLocation", location }) }
							: { id: "reactivate", label: t("actions.reactivate"), onClick: () => setConfirm({ kind: "reactivateLocation", location }) },
					);
				}
				return items.length > 0 ? <ITDropdownMenu items={items} trigger={<FaEllipsisV aria-hidden />} triggerLabel={t("columns.actions")} placement="bottom-end" /> : null;
			},
		},
	];

	async function confirmed() {
		if (!confirm) return;
		const { kind, location } = confirm;
		setConfirm(null);
		try {
			await (kind === "deactivateLocation" ? locationApi.deactivate(location.id) : locationApi.reactivate(location.id));
			toast(t("common:saved"));
			refresh();
		} catch (failure) {
			toast(failure instanceof ApiError ? failure.message : t("common:errors.unexpected"), "error");
		}
	}

	const breadcrumbs = [{ label: t("title"), onClick: () => navigate("/warehouses") }, { label: warehouse ? `${warehouse.code} · ${warehouse.name}` : t("title") }];

	if (missing) {
		return (
			<ITPage title={t("title")} breadcrumbs={breadcrumbs} noPadding>
				<ITText>{t("common:errors.notFound")}</ITText>
			</ITPage>
		);
	}

	return (
		<ITPage
			title={warehouse ? `${warehouse.code} · ${warehouse.name}` : t("title")}
			description={t("description")}
			breadcrumbs={breadcrumbs}
			noPadding
			loading={!warehouse}
			actions={canManage && warehouse?.active ? <ITButton label={t("rack.new")} onClick={() => setCreating(true)} /> : undefined}
		>
			<ITStack spacing={6}>
				<ITStack spacing={3}>
					<ITText as="h2" className="sf:text-title-md">
						{t("detail.groups")}
					</ITText>
					{groups.length === 0 && <ITText muted>{t("detail.noGroups")}</ITText>}
					{groups.map((aisle) => (
						<ITFlex key={aisle.aisle} wrap="wrap" align="center" gap={2}>
							<ITText className="sf:w-24 sf:text-label-md">{t("detail.aisle", { aisle: aisle.aisle })}</ITText>
							{aisle.racks.map((rack) => {
								const code = `${aisle.aisle}${rack.rack}`;
								const selected = group?.aisle === aisle.aisle && group.rack === rack.rack;
								return (
									<ITButton
										key={code}
										size="sm"
										variant={selected ? "filled" : "outlined"}
										label={`${code} · ${rack.locations}`}
										ariaLabel={t("detail.rackLabel", { group: code, count: rack.locations })}
										onClick={() => setGroup(selected ? null : { aisle: aisle.aisle, rack: rack.rack })}
									/>
								);
							})}
						</ITFlex>
					))}
				</ITStack>

				{(canStaff || canAssign) && <StaffTemplate warehouseId={id} canManage={canStaff} onSaved={() => toast(t("common:saved"))} />}

				<ITStack spacing={3}>
					<ITFlex align="center" gap={3}>
						<ITText as="h2" className="sf:text-title-md">
							{t("detail.locations")}
						</ITText>
						{group && (
							<>
								<ITBadget color="primary" label={t("detail.showing", { group: `${group.aisle}${group.rack}` })} />
								<ITButton size="sm" variant="text" label={t("detail.showAll")} onClick={() => setGroup(null)} />
							</>
						)}
					</ITFlex>
					<ITDataTable<Location> showViewToggle={false} columns={columns} externalFilters={filters} fetchData={(params) => locationApi.query(params as TableParams)} reloadTrigger={reload} layout="fixed" />
				</ITStack>
			</ITStack>

			{warehouse && (
				<RackDialog
					warehouse={warehouse}
					isOpen={creating}
					onClose={() => setCreating(false)}
					onCreated={(locations) => {
						setCreating(false);
						toast(t("rack.created", { codes: locations.map((location) => location.code).join(", ") }));
						refresh();
					}}
				/>
			)}
			<PrintDialog target={printing} onClose={() => setPrinting(null)} />
			<ITConfirmDialog
				isOpen={!!confirm}
				onClose={() => setConfirm(null)}
				onConfirm={() => void confirmed()}
				title={confirm ? t(confirm.kind === "deactivateLocation" ? "actions.deactivate" : "actions.reactivate") : ""}
				message={confirm ? t(`confirm.${confirm.kind}`, { code: confirm.location.code }) : ""}
				confirmLabel={t("common:actions.confirm")}
				cancelLabel={t("common:actions.cancel")}
				variant={confirm?.kind === "deactivateLocation" ? "danger" : "primary"}
			/>
		</ITPage>
	);
}
