package com.axzydev.stock.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// F5-19 · unit: the alerts of the reassignment page read in the language of the person, in both locales.
class OrdersTextsTest {

	@Test
	fun theKindOfAnAlertReadsInSpanishAndInEnglish() {
		assertEquals("Vence mañana", textFor(Text.ALERT_BEFORE, Language.ES))
		assertEquals("Due tomorrow", textFor(Text.ALERT_BEFORE, Language.EN))
		assertEquals("Vence hoy", textFor(Text.ALERT_DUE, Language.ES))
		assertEquals("Due today", textFor(Text.ALERT_DUE, Language.EN))
		assertEquals("Vencida", textFor(Text.ALERT_OVERDUE, Language.ES))
		assertEquals("Overdue", textFor(Text.ALERT_OVERDUE, Language.EN))
	}

	@Test
	fun theReassignmentPageHasItsTextsInBothLanguages() {
		listOf(Text.PAGE_REASSIGNMENT, Text.PAGE_REASSIGNMENT_HINT, Text.REASSIGN_ALERTS, Text.REASSIGN_SCAN, Text.REASSIGN_SAVE, Text.REASSIGN_SAVED, Text.REASSIGN_NOTHING).forEach { key ->
			listOf(Language.ES, Language.EN).forEach { language ->
				assertFalse(textFor(key, language).isBlank(), "$key in ${language.tag}")
			}
		}
	}
}
