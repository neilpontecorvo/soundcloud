package com.neilpontecorvo.soundcloudfiretv.content

import android.os.Handler
import android.os.Looper
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import com.neilpontecorvo.soundcloudfiretv.network.FeedResponseDto
import com.neilpontecorvo.soundcloudfiretv.network.LibraryResponseDto
import com.neilpontecorvo.soundcloudfiretv.network.MediaCardDto
import com.neilpontecorvo.soundcloudfiretv.network.SearchResponseDto
import java.util.concurrent.Executors

sealed class ContentLoadState {
    data object Loading : ContentLoadState()
    data object Empty : ContentLoadState()
    data class Success(val body: String) : ContentLoadState()
    data class Error(val message: String) : ContentLoadState()
}

class ContentRepository(private val apiClient: DeviceSessionApiClient) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadFeed(sessionId: String, callback: (ContentLoadState) -> Unit) {
        runRequest(callback) {
            apiClient.getFeed(sessionId).toLoadState("Feed")
        }
    }

    fun search(sessionId: String, query: String, callback: (ContentLoadState) -> Unit) {
        runRequest(callback) {
            apiClient.search(sessionId, query).toLoadState()
        }
    }

    fun loadLibrary(sessionId: String, callback: (ContentLoadState) -> Unit) {
        runRequest(callback) {
            apiClient.getLibrary(sessionId).toLoadState()
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun runRequest(
        callback: (ContentLoadState) -> Unit,
        block: () -> ContentLoadState
    ) {
        mainHandler.post { callback(ContentLoadState.Loading) }
        executor.execute {
            val nextState = try {
                block()
            } catch (error: Exception) {
                ContentLoadState.Error(error.message ?: error.javaClass.simpleName)
            }
            mainHandler.post { callback(nextState) }
        }
    }

    private fun FeedResponseDto.toLoadState(label: String): ContentLoadState {
        if (items.isEmpty()) return ContentLoadState.Empty
        return ContentLoadState.Success(
            listOfNotNull(
                "$label loaded from backend",
                metadataLine(generatedAtIso, cacheStatus),
                items.toDisplayLines()
            ).joinToString(separator = "\n\n")
        )
    }

    private fun SearchResponseDto.toLoadState(): ContentLoadState {
        if (items.isEmpty()) {
            val label = if (query.isBlank()) "default search" else "\"$query\""
            return ContentLoadState.Success(
                listOfNotNull(
                    "No backend results for $label.",
                    metadataLine(generatedAtIso, cacheStatus)
                ).joinToString(separator = "\n\n")
            )
        }

        val title = if (query.isBlank()) "Search preview loaded from backend" else "Search results for \"$query\""
        return ContentLoadState.Success(
            listOfNotNull(
                title,
                metadataLine(generatedAtIso, cacheStatus),
                items.toDisplayLines()
            ).joinToString(separator = "\n\n")
        )
    }

    private fun LibraryResponseDto.toLoadState(): ContentLoadState {
        if (sections.all { it.items.isEmpty() }) return ContentLoadState.Empty
        val sectionLines = sections.joinToString(separator = "\n\n") { section ->
            val itemLines = if (section.items.isEmpty()) {
                "No items"
            } else {
                section.items.toDisplayLines()
            }
            "${section.title}\n$itemLines"
        }

        return ContentLoadState.Success(
            listOfNotNull(
                "Library loaded from backend",
                metadataLine(generatedAtIso, cacheStatus),
                sectionLines
            ).joinToString(separator = "\n\n")
        )
    }

    private fun List<MediaCardDto>.toDisplayLines(): String {
        return joinToString(separator = "\n") { item ->
            val subtitle = listOfNotNull(item.creatorName, item.subtitle, item.durationText)
                .filter { it.isNotBlank() }
                .joinToString(separator = " - ")
            val suffix = if (subtitle.isBlank()) "" else " - $subtitle"
            "${item.kind.uppercase()}: ${item.title}$suffix"
        }
    }

    private fun metadataLine(generatedAtIso: String?, cacheStatus: String?): String? {
        val parts = listOfNotNull(
            generatedAtIso?.let { "Generated: $it" },
            cacheStatus?.let { "Cache: $it" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")
    }
}
