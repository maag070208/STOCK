import { ITAccordion, ITButton, ITCard, ITFlex, ITGrid, ITInput, ITSelect, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useEffect, useRef, useState, type DragEvent, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { FaArrowDown, FaArrowUp, FaBarcode, FaFont, FaHeading, FaRedo, FaSquare, FaTag, FaTrash, FaUndo } from "react-icons/fa";
import { BLOCKS, LABEL_SIZES, blockValue, newBlock, sameSize, sizeInDots, sizeOfDots, type Block, type BlockKind } from "../model/blocks";
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

// The name of a variable, as the printer's data can carry it: what a person types is not always a name.
const variableName = (typed: string) => typed.toLowerCase().replace(/[^a-z0-9_]/g, "_").replace(/^([0-9])/, "_$1").slice(0, 40);
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
	// Undo and redo (Ctrl+Z, Ctrl+Shift+Z): the label is written by hand, so it is easily unwritten. The history lives
	// here because every change of the design goes through this component.
	const past = useRef<Block[][]>([]);
	const future = useRef<Block[][]>([]);
	const [, setRevision] = useState(0);
	const commit = (next: Block[]) => {
		past.current.push(props.blocks);
		future.current = [];
		setRevision((revision) => revision + 1);
		props.onChange(next);
	};
	const step = (direction: -1 | 1) => {
		const from = direction === -1 ? past : future;
		const to = direction === -1 ? future : past;
		const previous = from.current.pop();
		if (!previous) return;
		to.current.push(props.blocks);
		setRevision((revision) => revision + 1);
		props.onChange(previous);
	};
	useEffect(() => {
		const onKey = (event: KeyboardEvent) => {
			const target = document.activeElement as HTMLElement | null;
			// Inside a field, Ctrl+Z is the field's own text: it is left alone.
			if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) return;
			if (!event.ctrlKey && !event.metaKey) return;
			const key = event.key.toLowerCase();
			if (key === "z" && !event.shiftKey) {
				event.preventDefault();
				step(-1);
			} else if ((key === "z" && event.shiftKey) || key === "y") {
				event.preventDefault();
				step(1);
			}
		};
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	});
	const selected = props.blocks.find((block) => block.id === selectedId) ?? null;
	// The size of the label, as one of the rolls a person knows. A size that is not there (an old template) shows empty.
	const selectedSize = sizeOfDots({ width: props.width, length: props.length });
	const nameOf = (kind: BlockKind) => t(dyn(BLOCKS.find((block) => block.kind === kind)!.label));
	const change = (patch: Partial<Block>) => {
		if (!selected) return;
		commit(props.blocks.map((block) => (block.id === selected.id ? { ...block, ...patch } : block)));
	};
	const move = (index: number, step: -1 | 1) => {
		const next = [...props.blocks];
		const [block] = next.splice(index, 1);
		next.splice(index + step, 0, block);
		commit(next);
	};
	// Adding an element chooses it: what was just added is what the person wants to describe.
	const add = (kind: BlockKind) => {
		const block = newBlock(kind, props.fields);
		commit([...props.blocks, block]);
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
							onMove={(id, position) => commit(props.blocks.map((block) => (block.id === id ? { ...block, ...position } : block)))}
							onResizeBlock={(id, patch) => commit(props.blocks.map((block) => (block.id === id ? { ...block, ...patch } : block)))}
							onDropAt={(kind, position) => {
								const block = { ...newBlock(kind as BlockKind, props.fields), ...position };
								commit([...props.blocks, block]);
								setSelectedId(block.id);
							}}
						/>
					)}
				</ITCard>
			</ITGrid>

			{/* What can be done: add an element, or describe the one that is chosen. */}
			<ITGrid item xs={12} lg={4}>
				<ITStack spacing={4} className="sf:lg:sticky sf:lg:top-6">
					<ITCard title={t("designer.add")}>
						<ITStack spacing={2}>
							<ITFlex gap={2}>
								<IconButton icon={<FaUndo aria-hidden />} label={t("designer.undo")} variant="outlined" onClick={() => step(-1)} disabled={past.current.length === 0} />
								<IconButton icon={<FaRedo aria-hidden />} label={t("designer.redo")} variant="outlined" onClick={() => step(1)} disabled={future.current.length === 0} />
							</ITFlex>
							{BLOCKS.map((block) => (
								// It is dragged onto the label, and it is also a button: dragging is not for everybody.
								<ITText
									as="span"
									key={block.kind}
									data-palette={block.kind}
									draggable
									onDragStart={(event: DragEvent) => event.dataTransfer.setData("application/x-stockflow-block", block.kind)}
									onClick={() => add(block.kind)}
									className="sf:flex sf:cursor-grab sf:items-center sf:gap-3 sf:rounded-sm sf:bg-secondary-50 sf:px-3 sf:py-2 sf:text-body-sm sf:transition-colors sf:hover:bg-secondary-100"
								>
									{ICONS[block.kind]}
									{nameOf(block.kind)}
								</ITText>
							))}
							<ITText muted className="sf:text-body-sm">
								{t("designer.dragFromPalette")}
							</ITText>
						</ITStack>
					</ITCard>

					{selected ? (
						<ITCard title={t("designer.selected", { name: nameOf(selected.kind) })}>
							<ITStack spacing={4}>
								{selected.kind !== "box" && (
									<ITStack spacing={2}>
										<ITInput
											name={`field-${selected.id}`}
											label={t("designer.field")}
											placeholder={t("designer.fieldOpen")}
											value={selected.field ?? ""}
											onChange={(event) => change({ field: variableName(String(event.target.value)) })}
										/>
										<ITText muted className="sf:text-body-sm">
											{t("designer.fieldHint")}
										</ITText>
										{props.fields.filter((field) => field !== "copies").length > 0 && (
											<ITStack spacing={2}>
												<ITText muted className="sf:text-body-sm">
													{t("designer.suggestions")}
												</ITText>
												{/* What the label already prints: pressing one fills the name, and any other name can be written. */}
												<ITFlex wrap="wrap" gap={2}>
													{props.fields
														.filter((field) => field !== "copies")
														.map((field) => (
															<ITButton key={field} size="sm" variant="outlined" label={`{{${field}}}`} onClick={() => change({ field })} />
														))}
												</ITFlex>
											</ITStack>
										)}
									</ITStack>
								)}
								{selected.kind === "text" && (
									<ITInput name={`prefix-${selected.id}`} label={t("designer.prefix")} value={selected.prefix} onChange={(event) => change({ prefix: String(event.target.value) })} />
								)}
								{selected.kind !== "box" && selected.kind !== "barcode" && (
									<ITSelect
										name={`size-${selected.id}`}
										label={t("designer.textSize")}
										value={String(selected.size)}
										options={SIZES.map((size) => ({ value: String(size), label: t("designer.textSizeOption", asSize(size)) }))}
										onChange={(event) => change({ size: Number(event.target.value) })}
									/>
								)}
								{selected.kind === "barcode" && (
									<ITStack spacing={4}>
										<ITSelect
											name={`barHeight-${selected.id}`}
											label={t("designer.barHeight")}
											value={String(selected.barHeight)}
											options={SIZES.map((size) => ({ value: String(size), label: t("designer.textSizeOption", asSize(size)) }))}
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

								{selected.kind !== "box" && (
									<ITSelect
										name={`orientation-${selected.id}`}
										label={t("designer.rotate")}
										value={String(selected.orientation)}
										options={[0, 90, 180, 270].map((turn) => ({ value: String(turn), label: t(dyn(`designer.turn.${turn}`)) }))}
										onChange={(event) => change({ orientation: Number(event.target.value) as 0 | 90 | 180 | 270 })}
									/>
								)}

								{/* Where it is: dragging is the easy way, so the exact numbers wait behind a click. */}
								<ITAccordion
									variant="bordered"
									items={[
										{
											id: "more",
											title: t("designer.more"),
											content: (
												<ITStack spacing={3}>
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
											),
										},
									]}
								/>

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
										commit(props.blocks.filter((block) => block.id !== selected.id));
										setSelectedId(null);
									}}
								/>
							</ITStack>
						</ITCard>
					) : (
						<ITCard title={t("designer.label")}>
							<ITStack spacing={4}>
								{/* The size is chosen, not typed: these are the rolls a person buys. */}
								<ITSelect
									name="labelSize"
									label={t("designer.rollSize")}
									value={(selectedSize && `${selectedSize.widthInches}x${selectedSize.lengthInches}`) || ""}
									placeholder={t("designer.sizePick")}
									options={LABEL_SIZES.map((size) => {
										const dots = sizeInDots(size);
										return {
											value: `${size.widthInches}x${size.lengthInches}`,
											label: t("designer.rollOption", { width: size.widthInches, length: size.lengthInches, dots: `${dots.width} × ${dots.length}` }),
										};
									})}
									onChange={(event) => {
										const chosen = LABEL_SIZES.find((size) => `${size.widthInches}x${size.lengthInches}` === String(event.target.value));
										if (chosen && !selectedSize) props.onResize(sizeInDots(chosen));
										else if (chosen && selectedSize && !sameSize(chosen, selectedSize)) props.onResize(sizeInDots(chosen));
									}}
								/>
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
