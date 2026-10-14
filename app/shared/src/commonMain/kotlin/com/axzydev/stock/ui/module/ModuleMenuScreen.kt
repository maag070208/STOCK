package com.axzydev.stock.ui.module

import androidx.compose.runtime.Composable
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.ComingSoon
import com.axzydev.stock.ui.components.PageRow
import com.axzydev.stock.ui.components.PageScaffold
import com.axzydev.stock.ui.nav.ModuleEntry
import com.axzydev.stock.ui.nav.ModulePage

// A module's own menu: what it holds, one row per page. A page the API does not serve yet keeps its row greyed with
// its notice, so the shape of the app is visible before the phase that fills it; when every row is like that, the
// module itself says it is still to come.
@Composable
fun ModuleMenuScreen(entry: ModuleEntry, onBack: () -> Unit, onOpenPage: (ModulePage) -> Unit) {
	val module = entry.module
	PageScaffold(
		title = text(module.title),
		subtitle = text(module.hint),
		onBack = onBack,
		backLabel = text(Text.BACK_HOME),
		backTag = "module-back",
	) {
		if (entry.comingSoon) ComingSoon("coming-soon-${module.id}")
		entry.pages.forEach { page ->
			PageRow(
				icon = page.icon,
				title = text(page.title),
				hint = text(page.hint),
				onClick = { onOpenPage(page) },
				tag = "page-${module.id}-${page.id}",
				enabled = !page.comingSoon,
			)
		}
	}
}
