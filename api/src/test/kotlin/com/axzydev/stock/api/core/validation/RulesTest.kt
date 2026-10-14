package com.axzydev.stock.api.core.validation

import com.axzydev.stock.api.core.errors.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

// F1-02 and F2-09 · unit: the field rules of validation.md section 3, shared by every module.
class RulesTest {

	@Test
	fun `an email needs one at sign, a dot in the domain and a letters-only ending`() {
		assertNull(Rules.email("ana@empresa.com.mx"))
		assertNull(Rules.email("ana.lopez+almacen@empresa-norte.mx"))
		for (bad in listOf("ana@empresa", "ana.empresa.com", "ana@@empresa.com", ".ana@empresa.com", "ana.@empresa.com", "ana@empresa.c", "ana@empresa.c0m", "ana lopez@empresa.com", "@empresa.com", "ana@.com")) {
			assertEquals(ErrorCode.EMAIL_INVALID, Rules.email(bad), bad)
		}
		assertEquals(ErrorCode.EMAIL_INVALID, Rules.email("a".repeat(64) + "@" + "b".repeat(190) + ".com"))
	}

	@Test
	fun `a username is 3 to 60 lowercase letters, digits, dots, underscores or hyphens`() {
		assertNull(Rules.username("ana.lopez_01"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.username("an"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.username("Ana"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.username("ana lopez"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, Rules.username("a".repeat(61)))
	}

	@Test
	fun `a name has 2 to 120 characters and no control characters`() {
		assertNull(Rules.name("Ana López"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.name("A"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.name("Ana\u0007"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, Rules.name("a".repeat(121)))
	}

	@Test
	fun `text is trimmed and normalized before its rule runs`() {
		val validator = Validator()
		assertEquals("ana@empresa.com", validator.required("email", "  Ana@Empresa.COM ", Rules::email) { it.lowercase() })
		assertNull(validator.optional("email", "   ", Rules::email))
		validator.check()
	}

	@Test
	fun `every failing field is reported, each with its code`() {
		val validator = Validator()
		validator.required("username", " ", Rules::username)
		validator.required("name", "A", Rules::name)
		validator.optional("email", "ana@empresa", Rules::email)
		val failure = assertThrows(ValidationException::class.java) { validator.check() }
		assertEquals(
			listOf("username" to ErrorCode.FIELD_REQUIRED, "name" to ErrorCode.FORMAT_INVALID, "email" to ErrorCode.EMAIL_INVALID),
			failure.errors.map { it.field to it.code },
		)
	}

	@Test
	fun `a code is uppercase letters, digits and hyphens, 2 to 20 characters`() {
		assertNull(Rules.code("CENTRAL"))
		assertNull(Rules.code("PROV-01"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.code("C"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.code("PROV 01"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, Rules.code("A".repeat(21)))
	}

	@Test
	fun `an RFC is 12 characters for a company and 13 for a person, with a real date`() {
		assertNull(Rules.taxId("ABC010203XY1"))
		assertNull(Rules.taxId("ABCD010203XY1"))
		assertNull(Rules.taxId("Ñ&A991231AB2"))
		// A leap day exists in 2000 and 2024, so 000229 is a real date.
		assertNull(Rules.taxId("ABC000229XY1"))
		assertEquals(ErrorCode.TAX_ID_INVALID, Rules.taxId("ABC011329XY1"))
		assertEquals(ErrorCode.TAX_ID_INVALID, Rules.taxId("ABC010230XY1"))
		assertEquals(ErrorCode.TAX_ID_INVALID, Rules.taxId("AB0102030XY1"))
		assertEquals(ErrorCode.TAX_ID_INVALID, Rules.taxId("ABCDE010203XY1"))
		assertEquals(ErrorCode.TAX_ID_INVALID, Rules.taxId("ABC010203XY"))
		assertEquals(ErrorCode.TAX_ID_INVALID, Rules.taxId("ABC-010203-XY1"))
	}

	@Test
	fun `a phone is stored as its 10 digits, without separators or the country prefix`() {
		assertEquals("5512345678", Rules.normalizePhone("(55) 1234-5678"))
		assertEquals("5512345678", Rules.normalizePhone("+52 55 1234 5678"))
		assertNull(Rules.phone("5512345678"))
		assertEquals(ErrorCode.PHONE_INVALID, Rules.phone(Rules.normalizePhone("12345678")))
		assertEquals(ErrorCode.PHONE_INVALID, Rules.phone(Rules.normalizePhone("55 1234 567A")))
		assertEquals(ErrorCode.PHONE_INVALID, Rules.phone(Rules.normalizePhone("+1 555 123 4567")))
	}

	@Test
	fun `a postal code is exactly 5 digits`() {
		assertNull(Rules.postalCode("06600"))
		assertEquals(ErrorCode.POSTAL_CODE_INVALID, Rules.postalCode("6600"))
		assertEquals(ErrorCode.POSTAL_CODE_INVALID, Rules.postalCode("0660A"))
	}

	@Test
	fun `a street number is 1 to 10 letters, digits, slashes or hyphens, and S-N is allowed`() {
		assertNull(Rules.addressNumber("123"))
		assertNull(Rules.addressNumber("S/N"))
		assertNull(Rules.addressNumber("12-B"))
		assertEquals(ErrorCode.FORMAT_INVALID, Rules.addressNumber("12 B"))
		assertEquals(ErrorCode.FIELD_TOO_LONG, Rules.addressNumber("12345678901"))
	}
}
