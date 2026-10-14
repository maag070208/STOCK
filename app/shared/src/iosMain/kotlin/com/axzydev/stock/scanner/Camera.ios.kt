package com.axzydev.stock.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberCameraPermission(): CameraPermission {
	// The simulator has no camera.
	val hasCamera = remember { AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) != null }
	var status by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) }
	val access = when {
		!hasCamera -> CameraAccess.UNAVAILABLE
		status == AVAuthorizationStatusAuthorized -> CameraAccess.GRANTED
		status == AVAuthorizationStatusNotDetermined -> CameraAccess.ASK
		else -> CameraAccess.DENIED
	}
	return CameraPermission(access) {
		AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
			dispatch_async(dispatch_get_main_queue()) { status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) }
		}
	}
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(onScanned: (String) -> Unit, modifier: Modifier) {
	val latest by rememberUpdatedState(onScanned)
	val camera = remember { CameraSession { value -> latest(value) } }
	DisposableEffect(camera) {
		camera.start()
		onDispose { camera.stop() }
	}
	UIKitView(factory = { camera.view }, modifier = modifier)
}

// A capture session from the back camera to a reader of machine-readable codes, shown in a preview view.
@OptIn(ExperimentalForeignApi::class)
private class CameraSession(onRead: (String) -> Unit) {
	private val session = AVCaptureSession()
	private val reader = CodeReader(onRead)
	val view = PreviewView(session)

	init {
		val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
		val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput }
		if (input != null && session.canAddInput(input)) session.addInput(input)
		val output = AVCaptureMetadataOutput()
		if (session.canAddOutput(output)) {
			session.addOutput(output)
			output.setMetadataObjectsDelegate(reader, dispatch_get_main_queue())
			// The codes printed on the labels (Code 128) and the ones suppliers print (EAN, UPC), plus QR.
			val wanted = listOf(AVMetadataObjectTypeCode128Code, AVMetadataObjectTypeEAN13Code, AVMetadataObjectTypeEAN8Code, AVMetadataObjectTypeUPCECode, AVMetadataObjectTypeQRCode)
			output.metadataObjectTypes = wanted.filter { it in output.availableMetadataObjectTypes }
		}
	}

	// Starting the session blocks while the camera opens, so it runs off the main thread.
	fun start() = dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { session.startRunning() }

	fun stop() = session.stopRunning()
}

private class CodeReader(private val onRead: (String) -> Unit) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
	override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
		didOutputMetadataObjects.firstNotNullOfOrNull { (it as? AVMetadataMachineReadableCodeObject)?.stringValue }?.let(onRead)
	}
}

// The camera's picture fills the view as it is laid out.
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
	private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }

	init {
		layer.addSublayer(preview)
	}

	override fun layoutSubviews() {
		super.layoutSubviews()
		preview.frame = bounds
	}
}
