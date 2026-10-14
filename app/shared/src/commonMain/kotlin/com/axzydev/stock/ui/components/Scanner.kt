package com.axzydev.stock.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.scanner.CameraAccess
import com.axzydev.stock.scanner.CameraPreview
import com.axzydev.stock.scanner.rememberCameraPermission
import com.axzydev.stock.ui.theme.AppLayout
import com.axzydev.stock.ui.theme.StockFlow
import com.axzydev.stock.ui.theme.StockFlowType

// The camera's window: tall enough to aim at a label, short enough to leave the field and the result in view.
private val CAMERA_HEIGHT = 240.dp

// The camera, or what it needs: the permission to ask for, or why it cannot run here. Every page that reads a barcode
// shows the same panel (docs/modules/barcode-scanning.md).
@Composable
fun ScannerPanel(onScanned: (String) -> Unit) {
	val permission = rememberCameraPermission()
	val shape = RoundedCornerShape(AppLayout.sheetCorner / 2)
	if (permission.access == CameraAccess.GRANTED) {
		CameraPreview(
			onScanned = onScanned,
			modifier = Modifier
				.fillMaxWidth()
				.height(CAMERA_HEIGHT)
				.clip(shape)
				.border(1.dp, StockFlow.colors.outline, shape)
				.testTag("camera"),
		)
		return
	}
	Card(Modifier.testTag("camera-${permission.access.name.lowercase()}")) {
		val message = when (permission.access) {
			CameraAccess.ASK -> Text.CAMERA_ASK
			CameraAccess.DENIED -> Text.CAMERA_DENIED
			else -> Text.CAMERA_UNAVAILABLE
		}
		AppText(text(message), StockFlowType.bodyMd, StockFlow.colors.onSurfaceMuted)
		if (permission.access != CameraAccess.UNAVAILABLE) PrimaryButton(text(Text.CAMERA_ALLOW), permission.request, tag = "camera-allow")
	}
}
