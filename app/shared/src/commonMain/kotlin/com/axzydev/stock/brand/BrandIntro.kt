package com.axzydev.stock.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import stock_flow.shared.generated.resources.Res

// Plays once per launch of the app, then steps aside. It never holds sign-in for longer than its length.
@Composable
fun BrandIntro(theme: BrandTheme, reduced: Boolean, onDone: () -> Unit) {
	val file = BrandAnimations.file(AnimationKind.INTRO, theme)
	val label = text(Text.BRAND_INTRO)
	val result by rememberLottieComposition {
		LottieCompositionSpec.JsonString(Res.readBytes(file).decodeToString())
	}
	val composition = result
	val progress by animateLottieCompositionAsState(
		composition = composition,
		iterations = 1,
		isPlaying = !reduced,
	)
	LaunchedEffect(Unit) {
		delay(if (reduced) BrandAnimations.REDUCED_MOTION_HOLD_MILLIS else BrandAnimations.INTRO_MILLIS)
		onDone()
	}
	Box(
		modifier = Modifier.fillMaxSize().semantics { contentDescription = label },
		contentAlignment = Alignment.Center,
	) {
		if (composition != null) {
			Image(
				painter = rememberLottiePainter(composition = composition, progress = { progress }),
				contentDescription = null,
				modifier = Modifier.size(240.dp),
			)
		}
	}
}
