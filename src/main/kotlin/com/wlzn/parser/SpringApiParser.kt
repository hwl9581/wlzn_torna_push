package com.wlzn.parser

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.wlzn.model.DocItem
import com.wlzn.model.DocParam

object SpringApiParser {

    private val MAPPING_ANNOTATIONS = mapOf(
        "org.springframework.web.bind.annotation.GetMapping" to "GET",
        "org.springframework.web.bind.annotation.PostMapping" to "POST",
        "org.springframework.web.bind.annotation.PutMapping" to "PUT",
        "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
        "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
        "org.springframework.web.bind.annotation.RequestMapping" to ""
    )

    private val SIMPLE_TYPE_MAP = mapOf(
        "int" to "integer", "java.lang.Integer" to "integer",
        "long" to "number", "java.lang.Long" to "number",
        "float" to "number", "java.lang.Float" to "number",
        "double" to "number", "java.lang.Double" to "number",
        "short" to "integer", "java.lang.Short" to "integer",
        "byte" to "integer", "java.lang.Byte" to "integer",
        "boolean" to "boolean", "java.lang.Boolean" to "boolean",
        "char" to "string", "java.lang.Character" to "string",
        "java.lang.String" to "string",
        "java.math.BigDecimal" to "number",
        "java.math.BigInteger" to "number",
        "java.util.Date" to "string",
        "java.time.LocalDate" to "string",
        "java.time.LocalDateTime" to "string",
        "java.time.LocalTime" to "string"
    )

    fun parseMethod(method: PsiMethod): DocItem? {
        val mappingInfo = extractMappingInfo(method) ?: return null
        val classPath = extractClassPath(method)

        val fullUrl = combinePath(classPath, mappingInfo.path)
        val httpMethod = mappingInfo.httpMethod
        val contentType = guessContentType(method, httpMethod)

        val headerParams = mutableListOf<DocParam>()
        val pathParams = mutableListOf<DocParam>()
        val queryParams = mutableListOf<DocParam>()
        val requestParams = mutableListOf<DocParam>()

        for (param in method.parameterList.parameters) {
            categorizeParameter(param, contentType, headerParams, pathParams, queryParams, requestParams)
        }

        val responseParams = extractResponseParams(method)
        val description = extractJavadocDescription(method)
        val name = extractApiName(method) ?: method.name
        val deprecated = if (method.isDeprecated) "已废弃" else null

        return DocItem(
            name = name,
            description = description,
            url = fullUrl,
            httpMethod = httpMethod,
            contentType = contentType,
            isFolder = false,
            isShow = true,
            deprecated = deprecated,
            headerParams = headerParams,
            pathParams = pathParams,
            queryParams = queryParams,
            requestParams = requestParams,
            responseParams = responseParams
        )
    }

    fun parseClass(psiClass: PsiClass): List<DocItem> {
        return psiClass.methods.mapNotNull { parseMethod(it) }
    }

    fun getClassName(method: PsiMethod): String {
        val psiClass = PsiTreeUtil.getParentOfType(method, PsiClass::class.java)
        return psiClass?.name ?: "Default"
    }

    private data class MappingInfo(val path: String, val httpMethod: String)

    private fun extractMappingInfo(method: PsiMethod): MappingInfo? {
        for ((annotationFqn, defaultMethod) in MAPPING_ANNOTATIONS) {
            val annotation = method.getAnnotation(annotationFqn) ?: continue
            val path = extractAnnotationValue(annotation) ?: ""
            val httpMethod = if (defaultMethod.isNotEmpty()) {
                defaultMethod
            } else {
                extractRequestMappingMethod(annotation)
            }
            return MappingInfo(path, httpMethod)
        }
        return null
    }

    private fun extractClassPath(method: PsiMethod): String {
        val psiClass = PsiTreeUtil.getParentOfType(method, PsiClass::class.java) ?: return ""
        val annotation = psiClass.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
            ?: return ""
        return extractAnnotationValue(annotation) ?: ""
    }

    private fun extractAnnotationValue(annotation: PsiAnnotation): String? {
        // findDeclaredAttributeValue only returns explicitly declared attributes
        for (attr in listOf("value", "path")) {
            val declared = annotation.findDeclaredAttributeValue(attr)
            if (declared != null) {
                resolveAnnotationStringValue(declared)?.let { return it }
            }
        }
        annotation.findDeclaredAttributeValue(null)?.let {
            resolveAnnotationStringValue(it)?.let { v -> return v }
        }
        // Fallback: computed values (handles @AliasFor resolution)
        for (attr in listOf("value", "path")) {
            resolveAnnotationStringValue(annotation.findAttributeValue(attr))?.let { return it }
        }
        return null
    }

    private fun resolveAnnotationStringValue(value: PsiAnnotationMemberValue?): String? {
        return when (value) {
            is PsiLiteral -> value.value?.toString()
            is PsiReferenceExpression -> {
                val resolved = value.resolve()
                if (resolved is PsiField) {
                    (resolved.computeConstantValue() as? String)
                } else null
            }
            is PsiArrayInitializerMemberValue -> {
                value.initializers.firstNotNullOfOrNull { resolveAnnotationStringValue(it) }
            }
            else -> {
                if (value is PsiReference) {
                    val resolved = (value as PsiReference).resolve()
                    if (resolved is PsiField) {
                        return (resolved.computeConstantValue() as? String)
                    }
                }
                val text = value?.text?.trim() ?: return null
                unquoteString(text)
            }
        }
    }

    private fun unquoteString(text: String): String? {
        if (text.length >= 2) {
            if (text.startsWith("\"") && text.endsWith("\"")) return text.substring(1, text.length - 1)
            if (text.startsWith("'") && text.endsWith("'")) return text.substring(1, text.length - 1)
        }
        return null
    }

    private fun extractRequestMappingMethod(annotation: PsiAnnotation): String {
        val methodAttr = annotation.findAttributeValue("method")
        val methodStr = when (methodAttr) {
            is PsiReferenceExpression -> methodAttr.referenceName?.removePrefix("RequestMethod.") ?: "POST"
            is PsiArrayInitializerMemberValue -> {
                methodAttr.initializers.firstOrNull()?.let { extractHttpMethodFromValue(it) } ?: "POST"
            }
            else -> extractHttpMethodFromValue(methodAttr) ?: "POST"
        }
        return methodStr.uppercase()
    }

    private fun extractHttpMethodFromValue(value: PsiAnnotationMemberValue?): String? {
        if (value is PsiReferenceExpression) return value.referenceName?.removePrefix("RequestMethod.")
        if (value is PsiReference) {
            val refText = (value as PsiReference).canonicalText
            if (refText != null) return refText.substringAfterLast(".").takeIf { it.isNotBlank() }
        }
        val text = value?.text?.trim() ?: return null
        return text.substringAfterLast(".").takeIf { it.isNotBlank() }
    }

    private fun combinePath(classPath: String, methodPath: String): String {
        val base = classPath.trimEnd('/')
        val sub = if (methodPath.startsWith("/")) methodPath else "/$methodPath"
        return if (base.isEmpty()) sub else "$base$sub"
    }

    private fun guessContentType(method: PsiMethod, httpMethod: String): String {
        val hasRequestBody = method.parameterList.parameters.any {
            it.getAnnotation("org.springframework.web.bind.annotation.RequestBody") != null
        }
        return if (hasRequestBody) "application/json" else "application/x-www-form-urlencoded"
    }

    private fun categorizeParameter(
        param: PsiParameter,
        contentType: String,
        headerParams: MutableList<DocParam>,
        pathParams: MutableList<DocParam>,
        queryParams: MutableList<DocParam>,
        requestParams: MutableList<DocParam>
    ) {
        if (isIgnoredParamType(param)) return

        when {
            param.getAnnotation("org.springframework.web.bind.annotation.RequestHeader") != null -> {
                val anno = param.getAnnotation("org.springframework.web.bind.annotation.RequestHeader")!!
                val name = extractAnnotationValue(anno) ?: param.name
                val required = extractBooleanValue(anno, "required") ?: true
                headerParams.add(
                    DocParam(
                        name = name,
                        type = mapType(param.type),
                        required = required,
                        description = extractParamDoc(param)
                    )
                )
            }

            param.getAnnotation("org.springframework.web.bind.annotation.PathVariable") != null -> {
                val anno = param.getAnnotation("org.springframework.web.bind.annotation.PathVariable")!!
                val name = extractAnnotationValue(anno) ?: param.name
                pathParams.add(
                    DocParam(
                        name = name,
                        type = mapType(param.type),
                        required = true,
                        description = extractParamDoc(param)
                    )
                )
            }

            param.getAnnotation("org.springframework.web.bind.annotation.RequestBody") != null -> {
                val children = extractObjectFields(param.type)
                if (children.isNotEmpty()) {
                    requestParams.addAll(children)
                } else {
                    requestParams.add(
                        DocParam(
                            name = param.name,
                            type = mapType(param.type),
                            required = true,
                            description = extractParamDoc(param)
                        )
                    )
                }
            }

            param.getAnnotation("org.springframework.web.bind.annotation.RequestParam") != null -> {
                val anno = param.getAnnotation("org.springframework.web.bind.annotation.RequestParam")!!
                val name = extractAnnotationValue(anno) ?: param.name
                val required = extractBooleanValue(anno, "required") ?: true
                queryParams.add(
                    DocParam(
                        name = name,
                        type = mapType(param.type),
                        required = required,
                        description = extractParamDoc(param)
                    )
                )
            }

            else -> {
                // 有非 Spring 注解的参数视为框架注入参数，跳过
                if (param.annotations.isNotEmpty()) return

                val typeName = param.type.canonicalText
                if (isSimpleType(typeName)) {
                    queryParams.add(
                        DocParam(
                            name = param.name,
                            type = mapType(param.type),
                            required = false,
                            description = extractParamDoc(param)
                        )
                    )
                } else {
                    queryParams.addAll(extractObjectFields(param.type))
                }
            }
        }
    }

    private fun isIgnoredParamType(param: PsiParameter): Boolean {
        val typeName = param.type.canonicalText
        val ignored = setOf(
            "javax.servlet.http.HttpServletRequest",
            "javax.servlet.http.HttpServletResponse",
            "jakarta.servlet.http.HttpServletRequest",
            "jakarta.servlet.http.HttpServletResponse",
            "org.springframework.web.multipart.MultipartFile",
            "org.springframework.validation.BindingResult",
            "org.springframework.ui.Model",
            "org.springframework.ui.ModelMap"
        )
        return typeName in ignored
    }

    private fun extractBooleanValue(annotation: PsiAnnotation, attrName: String): Boolean? {
        val attr = annotation.findAttributeValue(attrName) ?: return null
        return when (attr) {
            is PsiLiteral -> attr.value as? Boolean
            else -> attr.text?.trim()?.toBooleanStrictOrNull()
        }
    }

    fun extractObjectFields(type: PsiType, visited: MutableSet<String> = mutableSetOf()): List<DocParam> {
        val resolvedType = unwrapGenericType(type)
        val canonicalText = resolvedType.canonicalText

        if (canonicalText in visited || isSimpleType(canonicalText)) return emptyList()
        visited.add(canonicalText)

        val classType = resolvedType as? PsiClassType ?: return emptyList()
        val params = mutableListOf<DocParam>()
        var orderIndex = 0

        for ((field, substitutor) in getAllFieldsWithSubstitutors(classType)) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue
            if (field.hasModifierProperty(PsiModifier.TRANSIENT)) continue
            if (field.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnore") != null) continue
            if (isJsonFieldIgnored(field)) continue

            val fieldName = extractFieldName(field) ?: field.name
            val fieldType = substitutor.substitute(field.type) ?: field.type
            val fieldDoc = extractFieldDoc(field)
            val children = extractObjectFields(fieldType, visited)

            params.add(
                DocParam(
                    name = fieldName,
                    type = mapType(fieldType),
                    required = hasValidationRequired(field),
                    description = fieldDoc,
                    children = children,
                    orderIndex = orderIndex++
                )
            )
        }

        visited.remove(canonicalText)
        return params
    }

    private fun getAllFieldsWithSubstitutors(classType: PsiClassType): List<Pair<PsiField, PsiSubstitutor>> {
        val result = mutableListOf<Pair<PsiField, PsiSubstitutor>>()
        var currentType: PsiClassType? = classType

        while (currentType != null) {
            val resolveResult = currentType.resolveGenerics()
            val psiClass = resolveResult.element ?: break
            if (psiClass.qualifiedName == "java.lang.Object") break

            val substitutor = resolveResult.substitutor
            for (field in psiClass.fields) {
                result.add(field to substitutor)
            }

            val rawSuperType = psiClass.extendsListTypes.firstOrNull() ?: break
            currentType = substitutor.substitute(rawSuperType) as? PsiClassType
        }

        return result
    }

    private val JSON_FIELD_ANNOTATIONS = listOf(
        "com.alibaba.fastjson2.annotation.JSONField",
        "com.alibaba.fastjson.annotation.JSONField"
    )

    private fun extractFieldName(field: PsiField): String? {
        for (fqn in JSON_FIELD_ANNOTATIONS) {
            val anno = field.getAnnotation(fqn)
            if (anno != null) {
                val name = anno.findAttributeValue("name")
                val resolved = resolveAnnotationStringValue(name)
                if (!resolved.isNullOrEmpty()) return resolved
            }
        }
        val jsonProperty = field.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty")
        if (jsonProperty != null) {
            return extractAnnotationValue(jsonProperty)
        }
        return null
    }

    private fun isJsonFieldIgnored(field: PsiField): Boolean {
        for (fqn in JSON_FIELD_ANNOTATIONS) {
            val anno = field.getAnnotation(fqn) ?: continue
            if (extractBooleanValue(anno, "serialize") == false) return true
            if (extractBooleanValue(anno, "deserialize") == false) return true
        }
        return false
    }

    private fun hasValidationRequired(field: PsiField): Boolean {
        val annotations = listOf(
            "javax.validation.constraints.NotNull",
            "javax.validation.constraints.NotBlank",
            "javax.validation.constraints.NotEmpty",
            "jakarta.validation.constraints.NotNull",
            "jakarta.validation.constraints.NotBlank",
            "jakarta.validation.constraints.NotEmpty"
        )
        return annotations.any { field.getAnnotation(it) != null }
    }

    private fun extractResponseParams(method: PsiMethod): List<DocParam> {
        val returnType = method.returnType ?: return emptyList()
        val unwrapped = unwrapGenericType(returnType)

        if (isVoidType(returnType)) return emptyList()

        val fields = extractObjectFields(unwrapped)
        if (fields.isNotEmpty()) return fields

        if (!isSimpleType(unwrapped.canonicalText)) return emptyList()

        return listOf(
            DocParam(
                name = "data",
                type = mapType(unwrapped),
                description = ""
            )
        )
    }

    private fun unwrapGenericType(type: PsiType): PsiType {
        if (type !is PsiClassType) return type
        val resolved = type.resolve() ?: return type
        val qualifiedName = resolved.qualifiedName ?: return type

        val wrapperTypes = setOf(
            "org.springframework.http.ResponseEntity",
            "java.util.concurrent.CompletableFuture",
            "reactor.core.publisher.Mono"
        )

        if (qualifiedName in wrapperTypes) {
            val typeArgs = type.parameters
            if (typeArgs.isNotEmpty()) return unwrapGenericType(typeArgs[0])
        }

        if (qualifiedName == "java.util.List" ||
            qualifiedName == "java.util.Set" ||
            qualifiedName == "java.util.Collection"
        ) {
            val typeArgs = type.parameters
            if (typeArgs.isNotEmpty()) return unwrapGenericType(typeArgs[0])
        }

        return type
    }

    private fun isVoidType(type: PsiType): Boolean {
        return type == PsiTypes.voidType() || type.canonicalText == "java.lang.Void"
    }

    private fun isSimpleType(typeName: String): Boolean {
        return typeName in SIMPLE_TYPE_MAP || typeName.startsWith("java.lang.") && typeName in SIMPLE_TYPE_MAP
    }

    private fun mapType(type: PsiType): String {
        val unwrapped = unwrapGenericType(type)
        val canonical = unwrapped.canonicalText

        SIMPLE_TYPE_MAP[canonical]?.let { return it }

        if (type is PsiClassType) {
            val resolved = type.resolve()
            val qualifiedName = resolved?.qualifiedName
            if (qualifiedName == "java.util.List" ||
                qualifiedName == "java.util.Set" ||
                qualifiedName == "java.util.Collection"
            ) {
                return "array"
            }
            if (qualifiedName == "java.util.Map") {
                return "object"
            }
        }

        if (type is PsiArrayType) return "array"

        return "object"
    }

    private fun extractJavadocDescription(method: PsiMethod): String {
        val docComment = method.docComment ?: return ""
        val descParts = mutableListOf<String>()
        for (element in docComment.descriptionElements) {
            val text = element.text.trim()
            if (text.isNotEmpty()) descParts.add(text)
        }
        return descParts.joinToString(" ")
    }

    private fun extractApiName(method: PsiMethod): String? {
        // 优先使用 @ApiOperation / @Operation / Swagger 注解的值
        val apiOperation = method.getAnnotation("io.swagger.annotations.ApiOperation")
        if (apiOperation != null) {
            return extractAnnotationValue(apiOperation)
        }
        val operation = method.getAnnotation("io.swagger.v3.oas.annotations.Operation")
        if (operation != null) {
            val summary = operation.findAttributeValue("summary")
            return resolveAnnotationStringValue(summary)
        }
        // fallback: javadoc 首行
        val docComment = method.docComment ?: return null
        val firstLine = docComment.descriptionElements
            .map { it.text.trim() }
            .firstOrNull { it.isNotEmpty() }
        return firstLine
    }

    private fun extractParamDoc(param: PsiParameter): String {
        val method = PsiTreeUtil.getParentOfType(param, PsiMethod::class.java) ?: return ""
        val docComment = method.docComment ?: return ""
        val paramTag = docComment.findTagByName("param") ?: return ""

        for (tag in docComment.findTagsByName("param")) {
            val elements = tag.dataElements
            if (elements.isNotEmpty() && elements[0].text.trim() == param.name) {
                return elements.drop(1).joinToString(" ") { it.text.trim() }.trim()
            }
        }
        return ""
    }

    private fun extractFieldDoc(field: PsiField): String {
        // 优先: fastjson2 @JSONField(label = "xxx")
        for (fqn in JSON_FIELD_ANNOTATIONS) {
            val anno = field.getAnnotation(fqn)
            if (anno != null) {
                val label = anno.findAttributeValue("label")
                val resolved = resolveAnnotationStringValue(label)
                if (!resolved.isNullOrEmpty()) return resolved
            }
        }

        // Swagger: @ApiModelProperty / @Schema
        val apiModelProp = field.getAnnotation("io.swagger.annotations.ApiModelProperty")
        if (apiModelProp != null) {
            val v = extractAnnotationValue(apiModelProp)
            if (!v.isNullOrEmpty()) return v
        }
        val schema = field.getAnnotation("io.swagger.v3.oas.annotations.media.Schema")
        if (schema != null) {
            val desc = schema.findAttributeValue("description")
            val v = resolveAnnotationStringValue(desc)
            if (!v.isNullOrEmpty()) return v
        }

        // Javadoc
        val docComment = field.docComment
        if (docComment != null) {
            val lines = docComment.descriptionElements
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }

            val multiFieldPattern = Regex("^\\w+\\s*:.*")
            if (lines.count { it.matches(multiFieldPattern) } > 1) {
                // Multi-field example doc (e.g. JSON sample), extract only this field's line
                val fieldLine = lines.firstOrNull {
                    it.startsWith("${field.name} :") || it.startsWith("${field.name}:")
                }
                if (fieldLine != null) {
                    val desc = fieldLine.substringAfter(":").trim()
                    if (desc.isNotEmpty()) return desc
                }
            } else {
                val text = lines.joinToString(" ")
                if (text.isNotEmpty()) return text
            }
        }

        // 行尾注释 // xxx
        val nextSibling = field.nextSibling
        if (nextSibling is PsiComment) {
            return nextSibling.text.removePrefix("//").removePrefix("/*").removeSuffix("*/").trim()
        }

        return ""
    }
}
