import { ITFlex, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useRef, type PointerEvent as ReactPointerEvent } from "react";
import { useTranslation } from "react-i18next";
import { code128Bars, code128Width } from "../model/code128";
import { blockValue, type Block } from "../model/blocks";
import { fillPlaceholders, parseZpl, type ZplBarcode, type ZplLabel, type ZplText } from "../model/zpl";

// The label as the printer would print it (labels.md section 4): the same dots the ZPL asks for, drawn on a white label
// of the size the template declares. It is what a person checks before spending a roll of labels, and it is a drawing,
// not an interpreter: the commands the API allows are the ones it understands.
export function LabelPreview(props: {
	zpl: string;
	values: Record<string, string>;
	// When the screen edits the label, it hands the blocks over: they are what is drawn (one element per block, in
	// order) and what moves when one of them is dragged across the label.
	blocks?: Block[];
	selected?: string | null;
	onSelect?: (id: string | null) => void;
	onMove?: (id: string, position: { x: number; y: number }) => void;
	// The label of a template is looked at, and a design is aligned: the guides are the middle and the margins.
	guides?: boolean;
}) {
	const { t } = useTranslation(["labels"]);
	const svg = useRef<SVGSVGElement>(null);
	const label = props.blocks ? labelOf(props.blocks, props.values) : parseZpl(fillPlaceholders(props.zpl, props.values));
	const editing = !!props.blocks && !!props.onMove;

	// What a person does with the label: press a block, move it, let it go. The distance travelled by the pointer is
	// turned into dots of the printer, which is the unit the template speaks.
	const startDrag = (event: ReactPointerEvent, block: Block) => {
		if (!editing) return;
		event.preventDefault();
		props.onSelect?.(block.id);
		const box = svg.current?.getBoundingClientRect();
		if (!box) return;
		const scale = label.width / box.width;
		const startX = event.clientX;
		const startY = event.clientY;
		const move = (moving: PointerEvent) => {
			const x = block.x + (moving.clientX - startX) * scale;
			const y = block.y + (moving.clientY - startY) * scale;
			// Close to a guide sticks to it: lining two things up by hand is what the guides are for.
			const snap = (value: number, guide: number) => (Math.abs(value - guide) <= 8 ? guide : value);
			props.onMove?.(block.id, {
				x: Math.max(0, Math.round(snap(x, 30))),
				y: Math.max(0, Math.round(snap(y, 30))),
			});
		};
		const stop = () => {
			window.removeEventListener("pointermove", move);
			window.removeEventListener("pointerup", stop);
		};
		window.addEventListener("pointermove", move);
		window.addEventListener("pointerup", stop);
	};

	if (label.elements.length === 0) {
		return <ITText muted>{t("editor.previewEmpty")}</ITText>;
	}
	return (
		<ITStack spacing={3}>
			{/* The label sits on a workbench, so its white edge is visible against the card. */}
			<ITFlex justify="center" className="sf:rounded-xl sf:bg-secondary-100 sf:p-6">
				<svg
					ref={svg}
					viewBox={`0 0 ${label.width} ${label.length}`}
					// A drag is the label's, not the page's: without this the window scrolls while a block moves.
					style={editing ? { touchAction: "none" } : undefined}
					onPointerDown={(event) => {
						// Pressing the label itself, and not a block, lets it go.
						if (editing && event.target === svg.current) props.onSelect?.(null);
					}}
					role="img"
					aria-label={t("editor.previewLabel")}
					data-testid="zpl-preview"
					className="sf:h-auto sf:w-full sf:max-w-[52rem] sf:rounded-sm sf:border sf:border-outline-strong sf:bg-white sf:shadow-md"
				>
					{/* The edge of the label: where the die cuts it, so it reads as a label and not as a page. */}
					<rect x={1} y={1} width={label.width - 2} height={label.length - 2} fill="none" stroke="#c3c8cf" strokeWidth={2} strokeDasharray="12 10" />
					{props.guides && (
						// The middle and the margins of the label, which is where a label is usually aligned to.
						<g stroke="#7aa7ff" strokeWidth={1} strokeDasharray="6 8" opacity={0.7}>
							<line x1={label.width / 2} y1={0} x2={label.width / 2} y2={label.length} />
							<line x1={0} y1={label.length / 2} x2={label.width} y2={label.length / 2} />
							<rect x={30} y={30} width={label.width - 60} height={label.length - 60} fill="none" />
						</g>
					)}
					{props.blocks
						? props.blocks.map((block) => (
								<g
									key={block.id}
									data-block={block.id}
									data-kind={block.kind}
									className={editing ? "sf:cursor-move" : undefined}
									onPointerDown={(event) => startDrag(event, block)}
								>
									<BlockShape block={block} values={props.values} />
									{props.selected === block.id && (
										// What is selected says so, with the box it occupies.
										<rect {...selectionBox(block, props.values)} fill="none" stroke="var(--sf-c-primary)" strokeWidth={2} strokeDasharray="10 6" />
									)}
								</g>
							))
						: label.elements.map((element, index) => {
								if (element.kind === "text") return <PreviewText key={index} element={element} />;
								if (element.kind === "barcode") return <PreviewBarcode key={index} element={element} />;
								if (element.kind === "box") return <rect key={index} x={element.x} y={element.y} width={element.width} height={element.height} fill="black" />;
								// A QR is drawn as the square it occupies: the preview says where it goes, not what it encodes.
								const side = (21 + 4 * Math.max(0, element.magnification - 1)) * 2;
								return <rect key={index} x={element.x} y={element.y} width={side} height={side} fill="none" stroke="black" strokeWidth={2} strokeDasharray="8 6" />;
							})}
				</svg>
			</ITFlex>
			<ITText muted className="sf:text-body-sm">
				{t("editor.previewSize", { width: label.width, length: label.length, copies: label.copies ?? 1 })}
			</ITText>
		</ITStack>
	);
}

// A field of text: the font of the template, at the height it asks for, inside its block when it has one.
function PreviewText({ element }: { element: ZplText }) {
	// ZPL places the top left corner of the field; a text in SVG is placed by its baseline, which sits near its bottom.
	const baseline = element.y + element.height * 0.8;
	const anchor = element.block?.align === "C" ? "middle" : element.block?.align === "R" ? "end" : "start";
	const x = element.block?.align === "C" ? element.x + element.block.width / 2 : element.block?.align === "R" ? element.x + element.block.width : element.x;
	return (
		<text x={x} y={baseline} fontSize={element.height} textAnchor={anchor} fill="black" fontFamily="Helvetica, Arial, sans-serif">
			{element.text}
		</text>
	);
}

// The bars of a Code 128 barcode, at the module width the template asks for, with its readable text under them.
function PreviewBarcode({ element }: { element: ZplBarcode }) {
	const bars = code128Bars(element.text);
	const width = code128Width(element.text);
	const height = element.height;
	const module = element.moduleWidth;
	return (
		<g>
			{bars.map((bar, index) => (
				<rect key={index} x={element.x + bar.start * module} y={element.y} width={bar.width * module} height={height} fill="black" />
			))}
			{element.interpretation && (
				<text x={element.x + (width * module) / 2} y={element.y + height + 30} fontSize={28} textAnchor="middle" fill="black" fontFamily="monospace">
					{element.text}
				</text>
			)}
		</g>
	);
}

// What the preview needs to draw a label of a template, for the screens that show one.
export type { ZplLabel };

// A block, drawn: the same shapes the parsed ZPL produces, from what the designer holds.
function BlockShape({ block, values }: { block: Block; values: Record<string, string> }) {
	const text = `${block.prefix}${block.field ? (values[block.field] ?? `{{${block.field}}}`) : ""}`;
	if (block.kind === "barcode") {
		return <PreviewBarcode element={{ kind: "barcode", x: block.x, y: block.y, height: block.barHeight, moduleWidth: block.module, interpretation: true, text }} />;
	}
	if (block.kind === "box") return <rect x={block.x} y={block.y} width={block.width} height={block.height} fill="black" />;
	return (
		<PreviewText
			element={{
				kind: "text",
				x: block.x,
				y: block.y,
				height: block.size,
				block: block.kind === "title" ? { width: 752, lines: 2, spacing: 0, align: "L" } : null,
				text,
			}}
		/>
	);
}

// The box a block occupies, for what is selected: as wide as what it draws, and as tall as its glyphs or its bars.
function selectionBox(block: Block, values: Record<string, string>) {
	const text = `${block.prefix}${block.field ? (values[block.field] ?? `{{${block.field}}}`) : ""}`;
	if (block.kind === "box") return { x: block.x - 6, y: block.y - 6, width: block.width + 12, height: Math.max(block.height, 6) + 12 };
	if (block.kind === "barcode") {
		const width = code128Width(text) * block.module;
		return { x: block.x - 6, y: block.y - 6, width: width + 12, height: block.barHeight + (56) + 12 };
	}
	// A rough measure of the text: the font is square-ish, which is what the printer does with font 0.
	const width = Math.min(block.kind === "title" ? 752 : 1200, text.length * block.size * 0.6 + 12);
	return { x: block.x - 6, y: block.y - 6, width, height: block.size * 1.3 + 12 };
}

// The label the blocks describe, so the canvas knows its size without going through the ZPL.
function labelOf(blocks: Block[], values: Record<string, string>): ZplLabel {
	const width = Number(values["@width"] ?? 812);
	const length = Number(values["@length"] ?? 406);
	return {
		width,
		length,
		copies: 1,
		elements: blocks.map((block) => ({
			kind: "text" as const,
			x: block.x,
			y: block.y,
			height: block.size,
			block: null,
			text: blockValue(block),
		})),
	};
}
