package com.axzydev.stock.api.modules.identity.access

import com.axzydev.stock.api.core.security.Authenticated
import com.axzydev.stock.api.core.security.Public
import com.axzydev.stock.api.core.security.RequiresPermission
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

// The API does not start when an endpoint under /api/v1 does not declare its access,
// or when it asks for a permission that is not in the catalog. A new endpoint cannot be left open by mistake.
@Component
class AccessDeclarationCheck(
	@Qualifier("requestMappingHandlerMapping") private val mappings: RequestMappingHandlerMapping,
) : SmartInitializingSingleton {

	override fun afterSingletonsInstantiated() {
		val problems = mappings.handlerMethods.flatMap { (mapping, handler) ->
			val paths = mapping.pathPatternsCondition?.patternValues.orEmpty().filter { it.startsWith("/api/v1") }
			if (paths.isEmpty()) return@flatMap emptyList()
			val required = handler.getMethodAnnotation(RequiresPermission::class.java)
			val declared = required != null || handler.hasMethodAnnotation(Public::class.java) || handler.hasMethodAnnotation(Authenticated::class.java)
			val unknown = required?.anyOf.orEmpty().filter { it !in PermissionCatalog.keys }
			buildList {
				if (!declared) add("$paths declares no access (@Public, @Authenticated or @RequiresPermission)")
				if (required != null && required.anyOf.isEmpty()) add("$paths asks for no permission")
				if (unknown.isNotEmpty()) add("$paths asks for $unknown, which is not in the permission catalog")
			}
		}
		check(problems.isEmpty()) { "Endpoints with a wrong access declaration:\n" + problems.joinToString("\n") }
	}
}
