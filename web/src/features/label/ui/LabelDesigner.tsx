import { ITButton, ITCard, ITFlex, ITGrid, ITInput, ITSelect, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FaArrowDown, FaArrowUp, FaPlus, FaTrash } from "react-icons/fa";
import { BLOCKS, blockValue, blocksToZpl, newBlock, type Block } from "../model/blocks";
import { LabelPreview } from "./LabelPreview";
import { dyn } from "@shared/i18n";
import { IconButton } from "@shared/ui/IconButton";

// The label, built with pieces that already work (labels.md section 4). A person adds a block, says what it prints and
// where, and the designer writes the ZPL: nobody has to know the printer's language to make a label.
export function LabelDesigner(props: {
	blocks: Block[];
	fields: string[];
	width: number;
	length: number;
	values: Record<string, string>;
	zpl: string;
	onChange: (blocks: Block[]) => void;
}) {
	const { t } = useTranslation(["labels", "common"]);
	const [selected, setSelected] = useState<string | null>(null);
	const change = (id: string, patch: Partial<Block>) => props.onChange(props.blocks.map((block) => (block.id === id ? { ...block, ...patch } : block)));
	const move = (index: number, step: -1 | 1) => {
		const next = [...props.blocks];
		const [block] = next.splice(index, 1);
		next.splice(index + step, 0, block);
		props.onChange(next);
	};

	return (
		<ITStack spacing={4}>
			{/* The label itself, which is where the work happens: press a block and move it. */}
			<ITCard title={t("designer.canvas")}>
				<LabelPreview
					zpl={props.zpl}
					values={props.values}
					blocks={props.blocks}
					selected={selected}
					onSelect={setSelected}
					onMove={(id, position) => props.onChange(props.blocks.map((block) => (block.id === id ? { ...block, ...position } : block)))}
				/>
			</ITCard>

			{/* What can be put on a label: only what the API allows and a printer has already run. */}
			<ITFlex wrap="wrap" gap={2}>
				{BLOCKS.map((block) => (
					<ITButton
						key={block.kind}
						size="sm"
						variant="outlined"
						label={t(dyn(block.label))}
						icon={<FaPlus aria-hidden />}
						onClick={() => props.onChange([...props.blocks, newBlock(block.kind, props.fields)])}
					/>
				))}
			</ITFlex>

			{props.blocks.length === 0 && <ITText muted>{t("designer.empty")}</ITText>}

			{props.blocks.map((block, index) => (
				<ITCard key={block.id} className={selected === block.id ? "sf:ring-2 sf:ring-primary" : undefined}>
					<ITStack spacing={3}>
						<ITFlex justify="between" align="center" gap={2}>
							<ITText className="sf:text-label-md">{t(dyn(BLOCKS.find((candidate) => candidate.kind === block.kind)!.label))}</ITText>
							<ITFlex gap={1}>
								<IconButton icon={<FaArrowUp aria-hidden />} label={t("designer.up")} variant="text" onClick={() => move(index, -1)} disabled={index === 0} />
								<IconButton icon={<FaArrowDown aria-hidden />} label={t("designer.down")} variant="text" onClick={() => move(index, 1)} disabled={index === props.blocks.length - 1} />
								<IconButton icon={<FaTrash aria-hidden />} label={t("common:actions.remove")} color="danger" onClick={() => props.onChange(props.blocks.filter((candidate) => candidate.id !== block.id))} />
							</ITFlex>
						</ITFlex>
						<ITGrid container spacing={3}>
							<ITGrid item xs={6} sm={3}>
								<ITInput name={`x-${block.id}`} type="number" label={t("designer.x")} value={String(block.x)} onChange={(event) => change(block.id, { x: Number(event.target.value) })} />
							</ITGrid>
							<ITGrid item xs={6} sm={3}>
								<ITInput name={`y-${block.id}`} type="number" label={t("designer.y")} value={String(block.y)} onChange={(event) => change(block.id, { y: Number(event.target.value) })} />
							</ITGrid>
							{block.kind !== "box" && (
								<ITGrid item xs={12} sm={6}>
									<ITSelect
										name={`field-${block.id}`}
										label={t("designer.field")}
										value={block.field ?? ""}
										options={props.fields.filter((field) => field !== "copies").map((field) => ({ value: field, label: `{{${field}}}` }))}
										onChange={(event) => change(block.id, { field: String(event.target.value) })}
									/>
								</ITGrid>
							)}
							{block.kind === "text" && (
								<ITGrid item xs={12} sm={6}>
									<ITInput name={`prefix-${block.id}`} label={t("designer.prefix")} value={block.prefix} onChange={(event) => change(block.id, { prefix: String(event.target.value) })} />
								</ITGrid>
							)}
							{block.kind !== "box" && block.kind !== "barcode" && (
								<ITGrid item xs={6} sm={3}>
									<ITInput name={`size-${block.id}`} type="number" label={t("designer.size")} value={String(block.size)} onChange={(event) => change(block.id, { size: Number(event.target.value) })} />
								</ITGrid>
							)}
							{block.kind === "barcode" && (
								<>
									<ITGrid item xs={6} sm={3}>
										<ITInput name={`module-${block.id}`} type="number" label={t("designer.module")} value={String(block.module)} onChange={(event) => change(block.id, { module: Number(event.target.value) })} />
									</ITGrid>
									<ITGrid item xs={6} sm={3}>
										<ITInput name={`height-${block.id}`} type="number" label={t("designer.barHeight")} value={String(block.barHeight)} onChange={(event) => change(block.id, { barHeight: Number(event.target.value) })} />
									</ITGrid>
								</>
							)}
							{block.kind === "box" && (
								<>
									<ITGrid item xs={4} sm={2}>
										<ITInput name={`width-${block.id}`} type="number" label={t("designer.width")} value={String(block.width)} onChange={(event) => change(block.id, { width: Number(event.target.value) })} />
									</ITGrid>
									<ITGrid item xs={4} sm={2}>
										<ITInput name={`boxHeight-${block.id}`} type="number" label={t("designer.height")} value={String(block.height)} onChange={(event) => change(block.id, { height: Number(event.target.value) })} />
									</ITGrid>
									<ITGrid item xs={4} sm={2}>
										<ITInput name={`thickness-${block.id}`} type="number" label={t("designer.thickness")} value={String(block.thickness)} onChange={(event) => change(block.id, { thickness: Number(event.target.value) })} />
									</ITGrid>
								</>
							)}
						</ITGrid>
						{block.kind !== "box" && (
							<ITText muted className="sf:font-mono sf:text-code-sm">
								{blockValue(block) || t("designer.noField")}
							</ITText>
						)}
					</ITStack>
				</ITCard>
			))}

			{/* What the designer writes, so nobody wonders what is being saved. */}
			<ITText as="pre" data-testid="designed-zpl" className="sf:overflow-x-auto sf:rounded-sm sf:bg-secondary-100 sf:p-3 sf:font-mono sf:text-code-sm">
				{blocksToZpl(props.blocks, props.width, props.length)}
			</ITText>
		</ITStack>
	);
}
