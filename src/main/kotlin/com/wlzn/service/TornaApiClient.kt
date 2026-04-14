package com.wlzn.service

import com.intellij.openapi.diagnostic.Logger
import com.wlzn.model.*
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

object TornaApiClient {
    private val LOG = Logger.getInstance(TornaApiClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun pushDoc(serverUrl: String, token: String, docPushData: DocPushData): Result<String> {
        return try {
            val request = TornaRequest(
                accessToken = token,
                data = docPushData
            )
            val requestBody = json.encodeToString(TornaRequest.serializer(), request)
            LOG.info("Torna push request: $requestBody")

            val cleanUrl = serverUrl.trim().trimStart('\uFEFF', '\u200B').trimEnd('/')
            val apiUrl = if (cleanUrl.endsWith("/api")) cleanUrl else "$cleanUrl/api"
            val url = URI(apiUrl).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000

            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
            }

            LOG.info("Torna push response: $responseBody")

            val tornaResp = json.decodeFromString(TornaResponse.serializer(), responseBody)
            if (tornaResp.code == "0") {
                Result.success("推送成功")
            } else {
                Result.failure(RuntimeException("推送失败: ${tornaResp.msg}"))
            }
        } catch (e: Exception) {
            LOG.error("Torna push error", e)
            Result.failure(e)
        }
    }
}
