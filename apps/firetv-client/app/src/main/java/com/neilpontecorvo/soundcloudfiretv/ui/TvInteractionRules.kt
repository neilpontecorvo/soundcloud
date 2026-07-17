package com.neilpontecorvo.soundcloudfiretv.ui

object TvInteractionRules {
    enum class GridDirection { LEFT, RIGHT, UP, DOWN }

    fun gridNeighbor(
        index: Int,
        direction: GridDirection,
        itemCount: Int,
        columns: Int = 6
    ): Int? {
        if (index !in 0 until itemCount || columns <= 0) return null
        val row = index / columns
        val column = index % columns
        val candidate = when (direction) {
            GridDirection.LEFT -> if (column > 0) index - 1 else return null
            GridDirection.RIGHT -> if (column < columns - 1) index + 1 else return null
            GridDirection.UP -> if (row > 0) index - columns else return null
            GridDirection.DOWN -> index + columns
        }
        return candidate.takeIf { it in 0 until itemCount }
    }

    fun normalizeSearchQuery(value: String): String = value.trim()

    fun queueTargetIndex(currentIndex: Int, delta: Int, itemCount: Int): Int? =
        (currentIndex + delta).takeIf { currentIndex >= 0 && it in 0 until itemCount }

    fun artworkCandidate(artworkUrl: String?, creatorAvatarUrl: String?): String? =
        artworkUrl?.takeIf { it.isNotBlank() } ?: creatorAvatarUrl?.takeIf { it.isNotBlank() }

    fun clampedSeek(positionMs: Long, durationMs: Long, deltaMs: Long): Long? =
        if (durationMs > 0L) (positionMs + deltaMs).coerceIn(0L, durationMs) else null

    fun scanStepMs(repeatCount: Int): Long = when {
        repeatCount <= 0 -> 60_000L
        repeatCount < 5 -> 120_000L
        repeatCount < 12 -> 300_000L
        else -> 600_000L
    }
}
