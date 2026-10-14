package com.axzydev.stock.ui.module

import androidx.compose.runtime.Composable
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.ComingSoon
import com.axzydev.stock.ui.components.PageRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.nav.Module
import com.axzydev.stock.ui.nav.ModulePage

// A module's own menu: what it holds, one row per page. A module the API does not serve yet states its notice and
// lists its pages greyed out, so the shape of the app is visible before the phase that fills it.
@Composable
fun ModuleMenuScreen(module: Module, pages: List<ModulePage>, onBack: () -> Unit, onOpenPage: (ModulePage) -> Unit) {
	PageScaffold(
		title = text(module.title),
		subtitle = text(module.hint),
		onBack = onBack,
		backLabel = text(Text.BACK_HOME),
		backTag = "module-back",
	) {
		if (module.comingSoon) ComingSoon("coming-soon-${module.id}")
		pages.forEach { page ->
			PageRow(
				icon = page.icon,
				title = text(page.title),
				hint = text(page.hint),
				onClick = { onOpenPage(page) },
				tag = "page-${module.id}-${page.id}",
				enabled = !module.comingSoon,
			)
		}
	}
}
