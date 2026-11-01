package com.axzydev.stock.ui.module

import androidx.compose.runtime.Composable
import com.axzydev.stock.i18n.Text
import com.axzydev.stock.i18n.text
import com.axzydev.stock.ui.components.BentoChoiceRow
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
			if (page.comingSoon) {
				PageRow(page.icon, text(page.title), text(page.hint), { onOpenPage(page) }, "page-${module.id}-${page.id}", enabled = false)
			} else {
				BentoChoiceRow(text(page.title), "page-${module.id}-${page.id}", { onOpenPage(page) }, subtitle = text(page.hint), icon = page.icon, tone = module.tone)
			}
		}
	}
}
