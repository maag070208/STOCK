import { ITFlex, ITStack, ITText } from "@axzydev/axzy_ui_system";
import { useTranslation } from "react-i18next";
import { code128Bars, code128Width } from "../model/code128";
import { fillPlaceholders, parseZpl, type ZplBarcode, type ZplLabel, type ZplText } from "../model/zpl";

// The label as the printer would print it (labels.md section 4): the same dots the ZPL asks for, drawn on a white label
// of the size the template declares. It is what a person checks before spending a roll of labels, and it is a drawing,
// not an interpreter: the commands the API allows are the ones it understands.
export function LabelPreview(props: { zpl: string; values: Record<string, string>; product?: string }) {
	const { t } = useTranslation(["labels"]);
	const label = parseZpl(fillPlaceholders(props.zpl, props.values));

	if (label.elements.length === 0) {
		return <ITText muted>{t("editor.previewEmpty")}</ITText>;
	}
	return (
		<ITStack spacing={3}>
			{/* The label sits on a workbench, so its white edge is visible against the card. */}
			<ITFlex justify="center" className="sf:rounded-xl sf:bg-secondary-100 sf:p-6">
				<svg
					viewBox={`0 0 ${label.width} ${label.length}`}
					role="img"
					aria-label={t("editor.previewLabel")}
					data-testid="zpl-preview"
					className="sf:h-auto sf:w-full sf:max-w-[52rem] sf:rounded-sm sf:border sf:border-outline-strong sf:bg-white sf:shadow-md"
				>
					{/* The edge of the label: where the die cuts it, so it reads as a label and not as a page. */}
					<rect x={1} y={1} width={label.width - 2} height={label.length - 2} fill="none" stroke="#c3c8cf" strokeWidth={2} strokeDasharray="12 10" />
					{label.elements.map((element, index) => {
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
