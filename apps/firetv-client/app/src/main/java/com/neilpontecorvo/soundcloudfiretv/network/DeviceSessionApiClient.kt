package com.neilpontecorvo.soundcloudfiretv.network

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class BootstrapSessionDto(
    val sessionId: String,
    val status: String,
    val verificationUri: String?,
    val verificationUriComplete: String?,
    val userCode: String?,
    val expiresAtIso: String?
)

data class SessionDto(
    val sessionId: String,
    val status: String,
    val expiresAtIso: String?,
    val authenticatedAtIso: String?,
    val accessTokenExpiresAtIso: String?
)

data class MediaCardDto(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String?,
    val creatorName: String?,
    val artworkUrl: String?,
    val durationText: String?,
    val webUrl: String?
)

data class FeedResponseDto(
    val generatedAtIso: String?,
    val cacheStatus: String?,
    val items: List<MediaCardDto>
)

data class SearchResponseDto(
    val generatedAtIso: String?,
    val cacheStatus: String?,
    val query: String,
    val items: List<MediaCardDto>
)

data class LibrarySectionDto(
    val id: String,
    val title: String,
    val items: List<MediaCardDto>
)

data class LibraryResponseDto(
    val generatedAtIso: String?,
    val cacheStatus: String?,
    val sections: List<LibrarySectionDto>
)

class ApiException(
    val statusCode: Int,
    private val errorCode: String,
    message: String
) : Exception(message) {
    val userMessage: String = if (errorCode.isBlank()) {
        message
    } else {
        "$errorCode: $message"
    }
}

class DeviceSessionApiClient(private val baseUrl: String) {

    fun bootstrapDevice(deviceName: String, appVersion: String): BootstrapSessionDto {
        val body = JSONObject()
            .put("deviceName", deviceName)
            .put("appVersion", appVersion)
        val json = request("POST", "/v1/device/bootstrap", body)
        return BootstrapSessionDto(
            sessionId = json.getString("sessionId"),
            status = json.getString("status"),
            verificationUri = json.optNullableString("verificationUri"),
            verificationUriComplete = json.optNullableString("verificationUriComplete"),
            userCode = json.optNullableString("userCode"),
            expiresAtIso = json.optNullableString("expiresAtIso")
        )
    }

    fun getSession(sessionId: String): SessionDto {
        val json = request("GET", "/v1/session/${sessionId.urlEncode()}", null)
        return json.toSessionDto()
    }

    fun exchangeAuth(sessionId: String, authorizationCode: String): SessionDto {
        val body = JSONObject()
            .put("sessionId", sessionId)
            .put("authorizationCode", authorizationCode)
        return request("POST", "/v1/auth/exchange", body).toSessionDto()
    }

    fun refreshAuth(sessionId: String): SessionDto {
        val body = JSONObject().put("sessionId", sessionId)
        return request("POST", "/v1/auth/refresh", body).toSessionDto()
    }

    fun debugAuthenticateSession(sessionId: String): SessionDto {
        val body = JSONObject().put("sessionId", sessionId)
        return request("POST", "/v1/debug/authenticate-session", body).toSessionDto()
    }

    fun getFeed(sessionId: String): FeedResponseDto {
        return request("GET", "/v1/feed", null, sessionHeaders(sessionId)).toFeedResponseDto()
    }

    fun search(sessionId: String, query: String): SearchResponseDto {
        val path = if (query.isBlank()) {
            "/v1/search"
        } else {
            "/v1/search?q=${query.urlEncode()}"
        }
        return request("GET", path, null, sessionHeaders(sessionId)).toSearchResponseDto()
    }

    fun getLibrary(sessionId: String): LibraryResponseDto {
        return request("GET", "/v1/library", null, sessionHeaders(sessionId)).toLibraryResponseDto()
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        headers: Map<String, String> = emptyMap()
    ): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }

        val statusCode = connection.responseCode
        val responseText = readResponse(connection, statusCode)
        val json = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)

        if (statusCode !in 200..299) {
            throw ApiException(
                statusCode = statusCode,
                errorCode = json.optString("error"),
                message = json.optString("message", "Request failed with HTTP $statusCode.")
            )
        }

        return json
    }

    private fun readResponse(connection: HttpURLConnection, statusCode: Int): String {
        val input = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        return input.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }
    }

    private fun JSONObject.toSessionDto(): SessionDto = SessionDto(
        sessionId = getString("sessionId"),
        status = getString("status"),
        expiresAtIso = optNullableString("expiresAtIso"),
        authenticatedAtIso = optNullableString("authenticatedAtIso"),
        accessTokenExpiresAtIso = optNullableString("accessTokenExpiresAtIso")
    )

    private fun JSONObject.toFeedResponseDto(): FeedResponseDto = FeedResponseDto(
        generatedAtIso = optNullableString("generatedAtIso"),
        cacheStatus = optNullableString("cacheStatus"),
        items = optJSONArray("items").toMediaCards()
    )

    private fun JSONObject.toSearchResponseDto(): SearchResponseDto = SearchResponseDto(
        generatedAtIso = optNullableString("generatedAtIso"),
        cacheStatus = optNullableString("cacheStatus"),
        query = optString("query"),
        items = optJSONArray("items").toMediaCards()
    )

    private fun JSONObject.toLibraryResponseDto(): LibraryResponseDto {
        val sectionsJson = optJSONArray("sections")
        val sections = mutableListOf<LibrarySectionDto>()
        for (index in 0 until (sectionsJson?.length() ?: 0)) {
            val section = sectionsJson?.optJSONObject(index) ?: continue
            sections.add(
                LibrarySectionDto(
                    id = section.optString("id"),
                    title = section.optString("title"),
                    items = section.optJSONArray("items").toMediaCards()
                )
            )
        }
        return LibraryResponseDto(
            generatedAtIso = optNullableString("generatedAtIso"),
            cacheStatus = optNullableString("cacheStatus"),
            sections = sections
        )
    }

    private fun org.json.JSONArray?.toMediaCards(): List<MediaCardDto> {
        val items = mutableListOf<MediaCardDto>()
        if (this == null) return items

        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            items.add(
                MediaCardDto(
                    id = item.optString("id"),
                    kind = item.optString("kind"),
                    title = item.optString("title"),
                    subtitle = item.optNullableString("subtitle"),
                    creatorName = item.optNullableString("creatorName"),
                    artworkUrl = item.optNullableString("artworkUrl"),
                    durationText = item.optNullableString("durationText"),
                    webUrl = item.optNullableString("webUrl")
                )
            )
        }

        return items
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun sessionHeaders(sessionId: String): Map<String, String> = mapOf("X-Session-Id" to sessionId)

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
}
