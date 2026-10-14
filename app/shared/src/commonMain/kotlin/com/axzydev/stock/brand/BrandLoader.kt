package com.axzydev.stock.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import stock_flow.shared.generated.resources.Res

// The loading state. The label is for screen readers; the animation is decorative.
@Composable
fun BrandLoader(theme: BrandTheme) {
	val file = BrandAnimations.file(AnimationKind.LOADER, theme)
	val label = text(Text.BRAND_LOADING)
	val result by rememberLottieComposition {
		LottieCompositionSpec.JsonString(Res.readBytes(file).decodeToString())
	}
	val composition = result
	val progress by animateLottieCompositionAsState(
		composition = composition,
		iterations = Compottie.IterateForever,
	)
	if (composition != null) {
		Image(
			painter = rememberLottiePainter(composition = composition, progress = { progress }),
			contentDescription = null,
			modifier = Modifier.size(96.dp).semantics { contentDescription = label },
		)
	}
}
