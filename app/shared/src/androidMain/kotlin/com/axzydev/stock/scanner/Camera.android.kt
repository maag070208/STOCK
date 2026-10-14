package com.axzydev.stock.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
actual fun rememberCameraPermission(): CameraPermission {
	val context = LocalContext.current
	val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
	var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
	var refused by remember { mutableStateOf(false) }
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
		granted = allowed
		refused = !allowed
	}
	val access = when {
		!hasCamera -> CameraAccess.UNAVAILABLE
		granted -> CameraAccess.GRANTED
		refused -> CameraAccess.DENIED
		else -> CameraAccess.ASK
	}
	return CameraPermission(access) { launcher.launch(Manifest.permission.CAMERA) }
}

// The codes printed on the labels (Code 128) and the ones suppliers print (EAN, UPC), plus QR.
private val FORMATS = intArrayOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_QR_CODE)

@Composable
actual fun CameraPreview(onScanned: (String) -> Unit, modifier: Modifier) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val latest by rememberUpdatedState(onScanned)
	val controller = remember { LifecycleCameraController(context) }
	DisposableEffect(lifecycleOwner, controller) {
		val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128, *FORMATS).build())
		val executor = ContextCompat.getMainExecutor(context)
		controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
		controller.setImageAnalysisAnalyzer(
			executor,
			MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
				result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue }?.let { latest(it) }
			},
		)
		controller.bindToLifecycle(lifecycleOwner)
		onDispose {
			controller.clearImageAnalysisAnalyzer()
			controller.unbind()
			scanner.close()
		}
	}
	AndroidView(
		factory = { viewContext ->
			PreviewView(viewContext).apply {
				scaleType = PreviewView.ScaleType.FILL_CENTER
				this.controller = controller
			}
		},
		modifier = modifier,
	)
}
