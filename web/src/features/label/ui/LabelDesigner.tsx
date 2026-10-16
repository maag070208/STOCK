import { ITAccordion, ITButton, ITCard, ITFlex, ITGrid, ITInput, ITSelect, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { FaArrowDown, FaArrowUp, FaBarcode, FaFont, FaHeading, FaPlus, FaSquare, FaTag, FaTrash } from "react-icons/fa";
import { BLOCKS, blockValue, newBlock, type Block, type BlockKind } from "../model/blocks";
import { dyn } from "@shared/i18n";
import { IconButton } from "@shared/ui/IconButton";
import { LabelPreview } from "./LabelPreview";

// The label, made of pieces that already work (labels.md section 4). The canvas is where the label is: press an element
// to choose it, drag it to place it. The panel says what can be added and what the chosen element prints, in the words
// of the person using it; the ZPL is written underneath, out of the way.

const ICONS: Record<BlockKind, ReactNode> = {
	title: <FaHeading aria-hidden />,
	code: <FaTag aria-hidden />,
	barcode: <FaBarcode aria-hidden />,
	text: <FaFont aria-hidden />,
	box: <FaSquare aria-hidden />,
};

// The sizes a person recognizes, in dots of the printer: the label is 203 dpi, so 36 dots are about 4.5 mm.
const SIZES = [24, 36, 50, 70, 90, 110, 150, 200];
const asSize = (dots: number) => ({ dots, millimeters: Math.round((dots / 203) * 25.4) });

export function LabelDesigner(props: {
	blocks: Block[];
	fields: string[];
	width: number;
	length: number;
	values: Record<string, string>;
	zpl: string;
	onChange: (blocks: Block[]) => void;
	onResize: (size: { width: number; length: number }) => void;
}) {
	const { t } = useTranslation(["labels", "common"]);
	const [selectedId, setSelectedId] = useState<string | null>(null);
	const selected = props.blocks.find((block) => block.id === selectedId) ?? null;
	const nameOf = (kind: BlockKind) => t(dyn(BLOCKS.find((block) => block.kind === kind)!.label));
	const change = (patch: Partial<Block>) => {
		if (!selected) return;
		props.onChange(props.blocks.map((block) => (block.id === selected.id ? { ...block, ...patch } : block)));
	};
	const move = (index: number, step: -1 | 1) => {
		const next = [...props.blocks];
		const [block] = next.splice(index, 1);
		next.splice(index + step, 0, block);
		props.onChange(next);
	};
	// Adding an element chooses it: what was just added is what the person wants to describe.
	const add = (kind: BlockKind) => {
		const block = newBlock(kind, props.fields);
		props.onChange([...props.blocks, block]);
		setSelectedId(block.id);
	};

	return (
		<ITGrid container spacing={4}>
			{/* The label, which takes the width it can: it is what is being made. */}
			<ITGrid item xs={12} lg={8}>
				<ITCard title={t("designer.canvas")}>
					{props.blocks.length === 0 ? (
						<ITStack spacing={4} className="sf:items-center sf:py-10">
							<ITText muted>{t("designer.empty")}</ITText>
							<ITFlex wrap="wrap" gap={2} justify="center">
								{BLOCKS.map((block) => (
									<ITButton key={block.kind} variant="outlined" size="sm" icon={ICONS[block.kind]} label={nameOf(block.kind)} onClick={() => add(block.kind)} />
								))}
							</ITFlex>
						</ITStack>
					) : (
						<LabelPreview
							zpl={props.zpl}
							values={props.values}
							blocks={props.blocks}
							selected={selectedId}
							onSelect={setSelectedId}
							guides
							onMove={(id, position) => props.onChange(props.blocks.map((block) => (block.id === id ? { ...block, ...position } : block)))}
						/>
					)}
					<ITStack spacing={2} className="sf:mt-4">
						<ITAccordion
							variant="bordered"
							items={[
								{
									id: "zpl",
									title: t("designer.seeZpl"),
									content: (
										<ITText as="pre" data-testid="designed-zpl" className="sf:overflow-x-auto sf:font-mono sf:text-code-sm">
											{props.zpl}
										</ITText>
									),
								},
							]}
						/>
					</ITStack>
				</ITCard>
			</ITGrid>

			{/* What can be done: add an element, or describe the one that is chosen. */}
			<ITGrid item xs={12} lg={4}>
				<ITStack spacing={4} className="sf:lg:sticky sf:lg:top-6">
					<ITCard title={t("designer.add")}>
						<ITStack spacing={2}>
							{BLOCKS.map((block) => (
								<ITButton key={block.kind} variant="outlined" size="sm" icon={<FaPlus aria-hidden />} className="sf:w-full sf:justify-start" label={nameOf(block.kind)} onClick={() => add(block.kind)} />
							))}
						</ITStack>
					</ITCard>

					{selected ? (
						<ITCard title={t("designer.selected", { name: nameOf(selected.kind) })}>
							<ITStack spacing={4}>
								{selected.kind !== "box" && (
									<ITSelect
										name={`field-${selected.id}`}
										label={t("designer.field")}
										value={selected.field ?? ""}
										options={props.fields.filter((field) => field !== "copies").map((field) => ({ value: field, label: t(dyn(`designer.fields.${field}`)) }))}
										onChange={(event) => change({ field: String(event.target.value) })}
									/>
								)}
								{selected.kind === "text" && (
									<ITInput name={`prefix-${selected.id}`} label={t("designer.prefix")} value={selected.prefix} onChange={(event) => change({ prefix: String(event.target.value) })} />
								)}
								{selected.kind !== "box" && selected.kind !== "barcode" && (
									<ITSelect
										name={`size-${selected.id}`}
										label={t("designer.size")}
										value={String(selected.size)}
										options={SIZES.map((size) => ({ value: String(size), label: t("designer.sizeOption", asSize(size)) }))}
										onChange={(event) => change({ size: Number(event.target.value) })}
									/>
								)}
								{selected.kind === "barcode" && (
									<ITStack spacing={4}>
										<ITSelect
											name={`barHeight-${selected.id}`}
											label={t("designer.barHeight")}
											value={String(selected.barHeight)}
											options={SIZES.map((size) => ({ value: String(size), label: t("designer.sizeOption", asSize(size)) }))}
											onChange={(event) => change({ barHeight: Number(event.target.value) })}
										/>
										<ITSelect
											name={`module-${selected.id}`}
											label={t("designer.module")}
											value={String(selected.module)}
											options={[2, 3, 4, 5].map((module) => ({ value: String(module), label: t(dyn(`designer.moduleOption.${module}`)) }))}
											onChange={(event) => change({ module: Number(event.target.value) })}
										/>
									</ITStack>
								)}
								{selected.kind === "box" && (
									<ITGrid container spacing={3}>
										<ITGrid item xs={6}>
											<ITInput name={`width-${selected.id}`} type="number" label={t("designer.width")} value={String(selected.width)} onChange={(event) => change({ width: Number(event.target.value) })} />
										</ITGrid>
										<ITGrid item xs={6}>
											<ITInput name={`boxHeight-${selected.id}`} type="number" label={t("designer.height")} value={String(selected.height)} onChange={(event) => change({ height: Number(event.target.value) })} />
										</ITGrid>
										<ITGrid item xs={6}>
											<ITInput name={`thickness-${selected.id}`} type="number" label={t("designer.thickness")} value={String(selected.thickness)} onChange={(event) => change({ thickness: Number(event.target.value) })} />
										</ITGrid>
									</ITGrid>
								)}

								{/* Where it is. Dragging it on the label is the easy way; this is for putting it exactly. */}
								<ITStack spacing={2}>
									<ITGrid container spacing={3}>
										<ITGrid item xs={6}>
											<ITInput name={`x-${selected.id}`} type="number" label={t("designer.x")} value={String(selected.x)} onChange={(event) => change({ x: Number(event.target.value) })} />
										</ITGrid>
										<ITGrid item xs={6}>
											<ITInput name={`y-${selected.id}`} type="number" label={t("designer.y")} value={String(selected.y)} onChange={(event) => change({ y: Number(event.target.value) })} />
										</ITGrid>
									</ITGrid>
									<ITText muted className="sf:text-body-sm">
										{t("designer.dragHint")}
									</ITText>
								</ITStack>

								<ITText muted className="sf:font-mono sf:text-code-sm">
									{selected.kind === "box" ? t("designer.boxHint") : blockValue(selected) || t("designer.noField")}
								</ITText>
								<ITButton
									variant="outlined"
									size="sm"
									color="danger"
									icon={<FaTrash aria-hidden />}
									label={t("common:actions.remove")}
									onClick={() => {
										props.onChange(props.blocks.filter((block) => block.id !== selected.id));
										setSelectedId(null);
									}}
								/>
							</ITStack>
						</ITCard>
					) : (
						<ITCard title={t("designer.label")}>
							<ITStack spacing={4}>
								<ITGrid container spacing={3}>
									<ITGrid item xs={6}>
										<ITInput name="labelWidth" type="number" label={t("designer.labelWidth")} value={String(props.width)} onChange={(event) => props.onResize({ width: Number(event.target.value), length: props.length })} />
									</ITGrid>
									<ITGrid item xs={6}>
										<ITInput name="labelLength" type="number" label={t("designer.labelLength")} value={String(props.length)} onChange={(event) => props.onResize({ width: props.width, length: Number(event.target.value) })} />
									</ITGrid>
								</ITGrid>
								<ITText muted className="sf:text-body-sm">
									{t("designer.sizeHint")}
								</ITText>
							</ITStack>
						</ITCard>
					)}

					{/* The elements, in the order they are printed: the list is also how one of them is chosen. */}
					{props.blocks.length > 0 && (
						<ITCard title={t("designer.elements")}>
							<ITStack spacing={1}>
								{props.blocks.map((block, index) => (
									<ITFlex key={block.id} justify="between" align="center" gap={2} className={`sf:rounded-sm sf:px-2 sf:py-1 ${selectedId === block.id ? "sf:bg-primary-50" : ""}`}>
										<ITText as="span" onClick={() => setSelectedId(block.id)} className="sf:flex sf:cursor-pointer sf:items-center sf:gap-2 sf:text-body-sm">
											{ICONS[block.kind]}
											{nameOf(block.kind)}
										</ITText>
										<ITFlex gap={1} align="center">
											<IconButton icon={<FaArrowUp aria-hidden />} label={t("designer.up")} variant="text" onClick={() => move(index, -1)} disabled={index === 0} />
											<IconButton icon={<FaArrowDown aria-hidden />} label={t("designer.down")} variant="text" onClick={() => move(index, 1)} disabled={index === props.blocks.length - 1} />
										</ITFlex>
									</ITFlex>
								))}
							</ITStack>
						</ITCard>
					)}
				</ITStack>
			</ITGrid>
		</ITGrid>
	);
}
