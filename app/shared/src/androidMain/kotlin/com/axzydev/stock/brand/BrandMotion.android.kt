package com.axzydev.stock.brand

import android.provider.Settings
import com.axzydev.stock.AndroidContext

// Android turns animations off when the animator duration scale is zero (the "Remove animations" setting).
actual fun reducedMotionEnabled(): Boolean {
	val context = AndroidContext.app ?: return false
	val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
	return scale == 0f
}
