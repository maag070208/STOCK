package com.axzydev.stock.api.modules.labels

import com.axzydev.stock.api.core.audit.AuditService
import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.validation.FieldError
import com.axzydev.stock.api.core.validation.Rules
import com.axzydev.stock.api.core.validation.ValidationException
import com.axzydev.stock.api.core.validation.Validator
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class CreateTemplateRequest(val key: String? = null, val nameEs: String? = null, val nameEn: String? = null, val zpl: String? = null, val design: String? = null)

// The key is not here: it cannot change. A new ZPL is a new version.
data class UpdateTemplateRequest(val nameEs: String? = null, val nameEn: String? = null, val zpl: String? = null, val active: Boolean? = null, val design: String? = null)

data class ValidateRequest(val key: String? = null, val zpl: String? = null)

data class TemplateView(
	val id: UUID,
	val key: String,
	val name: String,
	val nameEs: String,
	val nameEn: String,
	val version: Int,
	val zpl: String,
	val placeholders: List<String>,
	val active: Boolean,
	val updatedAt: Instant,
	/** The blocks of the editor, as the screen wrote them, or null when the template was written by hand. */
	val design: String?,
)

data class VersionView(val version: Int, val zpl: String, val createdAt: Instant)

data class ProblemView(val code: String, val token: String?, val message: String)

data class ValidationReport(val problems: List<ProblemView>, val placeholders: List<String>)

// The label templates (labels.md sections 2 to 4): read with labels.view, changed with labels.manage. A change of the
// ZPL is a new version; every version stays readable; the active one is printed.
@Service
class LabelTemplateService(
	private val templates: LabelTemplateRepository,
	private val versions: LabelTemplateVersionRepository,
	private val audit: AuditService,
	private val messages: MessageSource,
	private val clock: Clock,
) {
	@Transactional(readOnly = true)
	fun list(language: String): List<TemplateView> = templates.findAll().sortedBy { it.key }.map { view(it, language) }

	@Transactional(readOnly = true)
	fun get(id: UUID, language: String): TemplateView = view(find(id), language)

	@Transactional(readOnly = true)
	fun versions(id: UUID): List<VersionView> = versions.findByTemplateIdOrderByVersionDesc(find(id).id!!).map { VersionView(it.version, it.zpl, it.createdAt) }

	// The report the editor shows as the person types: each problem with the command or placeholder that causes it.
	fun validate(request: ValidateRequest, locale: Locale): ValidationReport =
		ValidationReport(
			ZplRules.check(request.key?.trim().orEmpty(), request.zpl.orEmpty()).map { ProblemView(it.code.name, it.token, messages.getMessage(it.code.messageKey, null, locale)) },
			ZplRules.placeholders(request.key?.trim().orEmpty()),
		)

	@Transactional
	fun create(actor: CurrentUser, request: CreateTemplateRequest, language: String): TemplateView {
		val check = Validator()
		val key = check.required("key", request.key, ZplRules::key) { it.uppercase() }
		val nameEs = check.required("nameEs", request.nameEs, Rules::name)
		val nameEn = check.required("nameEn", request.nameEn, Rules::name)
		val zpl = check.required("zpl", request.zpl, ::zplLength)
		check.check()
		zplProblems(key, zpl)
		if (templates.findByKey(key) != null) throw ValidationException(listOf(FieldError("key", ErrorCode.DUPLICATE_VALUE)))
		val now = clock.instant()
		val template = templates.save(LabelTemplate(key, nameEs, nameEn, 1, actor.id, now, now))
		template.design = request.design?.takeIf { it.isNotBlank() }
		versions.save(LabelTemplateVersion(template.id!!, 1, zpl, now, actor.id))
		audit.record(actor.actor, "LABEL_TEMPLATE_CREATED", "label_template", template.id.toString(), after = mapOf("key" to key, "version" to 1))
		return view(template, language)
	}

	@Transactional
	fun update(actor: CurrentUser, id: UUID, request: UpdateTemplateRequest, language: String): TemplateView {
		val template = find(id)
		val check = Validator()
		val nameEs = request.nameEs?.let { check.required("nameEs", it, Rules::name) }
		val nameEn = request.nameEn?.let { check.required("nameEn", it, Rules::name) }
		val zpl = request.zpl?.let { check.required("zpl", it, ::zplLength) }
		check.check()
		val before = mapOf("version" to template.version, "active" to template.active)
		val now = clock.instant()
		if (zpl != null && zpl != activeZpl(template)) {
			zplProblems(template.key, zpl)
			template.version += 1
			versions.save(LabelTemplateVersion(template.id!!, template.version, zpl, now, actor.id))
		}
		nameEs?.let { template.nameEs = it }
		nameEn?.let { template.nameEn = it }
		request.active?.let { template.active = it }
		// The design is stored as it comes: the screen is the one that knows what a block is.
		if (request.zpl != null) template.design = request.design?.takeIf { it.isNotBlank() }
		template.updatedAt = now
		audit.record(actor.actor, "LABEL_TEMPLATE_UPDATED", "label_template", id.toString(), before, mapOf("version" to template.version, "active" to template.active))
		return view(template, language)
	}

	// For printing: an active template by its key, with the ZPL of its active version.
	@Transactional(readOnly = true)
	fun printable(key: String): Pair<LabelTemplate, String>? = templates.findByKey(key.trim().uppercase())?.takeIf { it.active }?.let { it to activeZpl(it) }

	// ZPL is text with line breaks; its commands are checked by ZplRules, so only its length is checked here.
	private fun zplLength(zpl: String): ErrorCode? = if (zpl.length > MAX_ZPL) ErrorCode.FIELD_TOO_LONG else null

	private fun zplProblems(key: String, zpl: String) {
		val problems = ZplRules.check(key, zpl)
		if (problems.isNotEmpty()) throw ValidationException(problems.map { it.code }.distinct().map { FieldError("zpl", it) })
	}

	private fun activeZpl(template: LabelTemplate): String = versions.findByTemplateIdAndVersion(template.id!!, template.version)!!.zpl

	private fun find(id: UUID): LabelTemplate = templates.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND) }

	private fun view(template: LabelTemplate, language: String) = TemplateView(
		id = template.id!!,
		key = template.key,
		name = if (language == "en") template.nameEn else template.nameEs,
		nameEs = template.nameEs,
		nameEn = template.nameEn,
		version = template.version,
		zpl = activeZpl(template),
		placeholders = (ZplRules.PLACEHOLDERS[template.key].orEmpty() + ZplRules.COPIES).toList(),
		active = template.active,
		updatedAt = template.updatedAt,
		design = template.design,
	)

	companion object {
		const val VIEW = "labels.view"
		const val MANAGE = "labels.manage"
		const val PRINT = "labels.print"
		private const val MAX_ZPL = 10_000
	}
}
