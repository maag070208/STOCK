package com.axzydev.stock.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Whether the app can use the camera: allowed, not asked yet, refused by the person, or not on this device.
enum class CameraAccess { GRANTED, ASK, DENIED, UNAVAILABLE }

class CameraPermission(val access: CameraAccess, val request: () -> Unit)

// The platform's camera permission. Asking shows the system's own dialog; the app never changes a setting.
@Composable
expect fun rememberCameraPermission(): CameraPermission

// The camera's picture, reading barcodes: each value it reads goes to `onScanned`, as the raw text of the code.
// Android uses CameraX with ML Kit (mobile-app.md section 2). It only runs with the permission. `torch` lights the label
// in a dark aisle; a device without a flash ignores it.
@Composable
expect fun CameraPreview(onScanned: (String) -> Unit, modifier: Modifier = Modifier, torch: Boolean = false)
