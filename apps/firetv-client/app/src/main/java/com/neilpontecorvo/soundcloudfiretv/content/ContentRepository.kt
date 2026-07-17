package com.neilpontecorvo.soundcloudfiretv.content

import android.os.Handler
import android.os.Looper
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentCardSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentSectionSpec
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import com.neilpontecorvo.soundcloudfiretv.network.FeedResponseDto
import com.neilpontecorvo.soundcloudfiretv.network.LibraryResponseDto
import com.neilpontecorvo.soundcloudfiretv.network.MediaCardDto
import com.neilpontecorvo.soundcloudfiretv.network.PlaylistDetailDto
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

data class PlaylistDetail(
    val id: String,
    val title: String,
    val creatorName: String?,
    val artworkUrl: String?,
    val description: String?,
    val durationMs: Long?,
    val durationText: String?,
    val trackCount: Int,
    val webUrl: String?,
    val tracks: List<ContentCardSpec>
)

sealed class PlaylistLoadState {
    data object Loading : PlaylistLoadState()
    data class Success(val detail: PlaylistDetail) : PlaylistLoadState()
    data class Error(val message: String) : PlaylistLoadState()
}

class ContentRepository(private val apiClient: DeviceSessionApiClient) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchGeneration = 0

    fun loadFeed(sessionId: String, callback: (ContentLoadState) -> Unit) {
        runRequest(callback) {
            apiClient.getFeed(sessionId).toLoadState()
        }
    }

    fun search(sessionId: String, query: String, callback: (ContentLoadState) -> Unit) {
        val generation = ++searchGeneration
        mainHandler.post { callback(ContentLoadState.Loading) }
        executor.execute {
            val nextState = try {
                apiClient.search(sessionId, query).toLoadState()
            } catch (error: Exception) {
                ContentLoadState.Error(error.message ?: error.javaClass.simpleName)
            }
            mainHandler.post {
                if (generation == searchGeneration) callback(nextState)
            }
        }
    }

    fun loadLibrary(sessionId: String, callback: (ContentLoadState) -> Unit) {
        runRequest(callback) {
            apiClient.getLibrary(sessionId).toLoadState()
        }
    }

    fun loadPlaylist(
        sessionId: String,
        playlistId: String,
        callback: (PlaylistLoadState) -> Unit
    ) {
        mainHandler.post { callback(PlaylistLoadState.Loading) }
        executor.execute {
            val nextState = try {
                PlaylistLoadState.Success(apiClient.getPlaylistDetail(sessionId, playlistId).toPlaylistDetail())
            } catch (error: Exception) {
                PlaylistLoadState.Error(error.message ?: error.javaClass.simpleName)
            }
            mainHandler.post { callback(nextState) }
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

    private fun FeedResponseDto.toLoadState(): ContentLoadState {
        if (sections.any { it.items.isNotEmpty() }) {
            return ContentLoadState.Success(
                body = "",
                sections = sections
                    .filter { it.items.isNotEmpty() }
                    .map { section -> ContentSectionSpec(section.title, section.items.toContentCards()) }
            )
        }

        if (items.isEmpty()) return ContentLoadState.Empty
        return ContentLoadState.Success(
            body = "",
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
            body = if (query.isBlank()) "" else title,
            sections = listOf(ContentSectionSpec("Results", items.toContentCards()))
        )
    }

    private fun LibraryResponseDto.toLoadState(): ContentLoadState {
        if (sections.all { it.items.isEmpty() }) return ContentLoadState.Empty
        return ContentLoadState.Success(
            body = "",
            sections = sections
                .filter { it.items.isNotEmpty() }
                .map { section ->
                    ContentSectionSpec(
                        section.title,
                        section.items.toContentCards(
                            eyebrowOverride = if (section.id.equals("albums", ignoreCase = true)) {
                                "album"
                            } else {
                                null
                            }
                        )
                    )
                }
        )
    }

    private fun List<MediaCardDto>.toContentCards(
        eyebrowOverride: String? = null
    ): List<ContentCardSpec> {
        return map { item ->
            val subtitle = buildBrowseSubtitle(item)
            ContentCardSpec(
                id = item.id,
                eyebrow = eyebrowOverride ?: item.kind,
                title = item.title,
                subtitle = subtitle,
                metadata = item.durationText,
                artworkUrl = item.artworkUrl,
                webUrl = item.webUrl,
                creatorName = item.creatorName,
                description = item.description ?: item.subtitle,
                creatorAvatarUrl = item.creatorAvatarUrl,
                waveformUrl = item.waveformUrl,
                durationMs = item.durationMs,
                trackCount = item.trackCount,
                isPrivate = item.isPrivate,
                creatorProfileUrl = item.creatorProfileUrl
            )
        }
    }

    private fun PlaylistDetailDto.toPlaylistDetail(): PlaylistDetail = PlaylistDetail(
        id = id,
        title = title,
        creatorName = creatorName,
        artworkUrl = artworkUrl,
        description = description,
        durationMs = durationMs,
        durationText = durationText,
        trackCount = trackCount,
        webUrl = webUrl,
        tracks = tracks.toContentCards()
    )

    private fun buildBrowseSubtitle(item: MediaCardDto): String {
        val creator = item.creatorName?.trim()?.takeIf { it.isNotBlank() }
        val descriptor = item.subtitle
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !it.equals(item.title, ignoreCase = true) }
            ?.takeIf { it.length <= MAX_BROWSE_DESCRIPTOR_LENGTH }

        return listOfNotNull(creator, descriptor)
            .joinToString(separator = " - ")
            .ifBlank { "Ready to play" }
    }

    companion object {
        private const val MAX_BROWSE_DESCRIPTOR_LENGTH = 54
    }

}
