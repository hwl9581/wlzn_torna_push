package com.wlzn.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TornaRequest(
    val name: String = "doc.push",
    val version: String = "1.0",
    @SerialName("access_token") val accessToken: String = "",
    val data: DocPushData
)

@Serializable
data class DocPushData(
    val apis: List<DocItem>,
    val debugEnvs: List<DebugEnv> = emptyList(),
    val author: String = "",
    val commonErrorCodes: List<DocParamCode> = emptyList(),
    val isReplace: Boolean = true
)

@Serializable
data class DocItem(
    val name: String,
    val description: String = "",
    val url: String = "",
    val httpMethod: String = "",
    val contentType: String = "",
    val isFolder: Boolean = false,
    val isShow: Boolean = true,
    val author: String = "",
    val deprecated: String? = null,
    val items: List<DocItem> = emptyList(),
    val headerParams: List<DocParam> = emptyList(),
    val pathParams: List<DocParam> = emptyList(),
    val queryParams: List<DocParam> = emptyList(),
    val requestParams: List<DocParam> = emptyList(),
    val responseParams: List<DocParam> = emptyList(),
    val errorCodeParams: List<DocParamCode> = emptyList(),
    val orderIndex: Int = 0
)

@Serializable
data class DocParam(
    val name: String,
    val type: String = "string",
    val required: Boolean = false,
    val description: String = "",
    val example: String = "",
    val maxLength: String = "",
    val children: List<DocParam> = emptyList(),
    val orderIndex: Int = 0
)

@Serializable
data class DocParamCode(
    val code: String,
    val msg: String = "",
    val solution: String = ""
)

@Serializable
data class DebugEnv(
    val name: String,
    val url: String
)

@Serializable
data class TornaResponse(
    val code: String = "",
    val msg: String = "",
    val data: kotlinx.serialization.json.JsonElement? = null
)
