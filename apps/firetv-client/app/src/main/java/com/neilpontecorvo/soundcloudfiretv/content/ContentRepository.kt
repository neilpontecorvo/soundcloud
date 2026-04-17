package com.neilpontecorvo.soundcloudfiretv.content

import android.os.Handler
import android.os.Looper
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentCardSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentSectionSpec
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import com.neilpontecorvo.soundcloudfiretv.network.FeedResponseDto
import com.neilpontecorvo.soundcloudfiretv.network.LibraryResponseDto
import com.neilpontecorvo.soundcloudfiretv.network.MediaCardDto
import com.neilpontecorvo.soundcloudfiretv.network.SearchResponseDto
import java.util.concurrent.Executors

sealed class ContentLoadState {
    data object Loading : ContentLoadState()
    data object Empty : ContentLoadState()
    data class Success(
        val body: String,
        val sections: List<ContentSectionSpec>
    ) : ContentLoadState()
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
            body = label,
            sections = listOf(ContentSectionSpec("Latest", items.toContentCards()))
        )
    }

    private fun SearchResponseDto.toLoadState(): ContentLoadState {
        if (items.isEmpty()) {
            val label = if (query.isBlank()) "default search" else "\"$query\""
            return ContentLoadState.Success(
                body = "No results for $label",
                sections = emptyList()
            )
        }

        val title = if (query.isBlank()) "Search" else "Results for \"$query\""
        return ContentLoadState.Success(
            body = title,
            sections = listOf(ContentSectionSpec("Results", items.toContentCards()))
        )
    }

    private fun LibraryResponseDto.toLoadState(): ContentLoadState {
        if (sections.all { it.items.isEmpty() }) return ContentLoadState.Empty
        return ContentLoadState.Success(
            body = "Library",
            sections = sections
                .filter { it.items.isNotEmpty() }
                .map { section ->
                    ContentSectionSpec(section.title, section.items.toContentCards())
                }
        )
    }

    private fun List<MediaCardDto>.toContentCards(): List<ContentCardSpec> {
        return map { item ->
            val subtitle = listOfNotNull(item.creatorName, item.subtitle)
                .filter { it.isNotBlank() }
                .joinToString(separator = " - ")
                .ifBlank { "Backend item" }
            ContentCardSpec(
                id = item.id,
                eyebrow = item.kind,
                title = item.title,
                subtitle = subtitle,
                metadata = item.durationText
            )
        }
    }

}
