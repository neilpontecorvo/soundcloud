package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neilpontecorvo.soundcloudfiretv.R
import com.neilpontecorvo.soundcloudfiretv.ui.TvArtworkLoader
import com.neilpontecorvo.soundcloudfiretv.ui.TvDesign
import com.neilpontecorvo.soundcloudfiretv.ui.TvDesignMetrics
import com.neilpontecorvo.soundcloudfiretv.ui.TvInteractionRules

data class ActionSpec(val label: String, val onClick: () -> Unit)

data class ContentCardSpec(
    val id: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val metadata: String? = null,
    val artworkUrl: String? = null,
    val webUrl: String? = null,
    val creatorName: String? = null,
    val description: String? = null,
    val creatorAvatarUrl: String? = null,
    val waveformUrl: String? = null,
    val durationMs: Long? = null,
    val trackCount: Int? = null,
    val isPrivate: Boolean = false,
    val creatorProfileUrl: String? = null
)

data class ContentSectionSpec(
    val title: String,
    val cards: List<ContentCardSpec>
)

data class ScreenViewModel(
    val title: String,
    val body: String,
    val actions: List<ActionSpec> = emptyList(),
    val contentSections: List<ContentSectionSpec> = emptyList()
)

data class GridRenderOptions(
    val topInset: Int,
    val verticalGap: Int,
    val upFocusId: Int = View.NO_ID,
    val downFocusId: Int = View.NO_ID,
    val preferredCardId: String? = null,
    val onCardFocused: (String) -> Unit = {}
)

data class RenderedGrid(
    val view: View,
    val firstFocusableId: Int?,
    val lastFocusableId: Int?
)

interface ContentCardSelectionListener {
    fun onCardSelected(card: ContentCardSpec)
    fun onCardSelectedFromSection(card: ContentCardSpec, sectionCards: List<ContentCardSpec>) {
        onCardSelected(card)
    }
}

class ScreenRenderer(
    private val context: Context,
    private val metrics: TvDesignMetrics,
    private val cardSelectionListener: ContentCardSelectionListener? = null
) {
    fun render(model: ScreenViewModel): View = if (model.contentSections.isNotEmpty()) {
        renderGrid(model, GridRenderOptions(topInset = 111, verticalGap = 28)).view
    } else {
        renderPanelScreen(model)
    }

    fun renderGrid(model: ScreenViewModel, options: GridRenderOptions): RenderedGrid {
        val cards = model.contentSections
            .flatMap { it.cards }
            .distinctBy { "${it.eyebrow.lowercase()}:${it.id}" }
        return renderGridCells(cards, model.body, options)
    }

    fun renderLibraryRows(model: ScreenViewModel, options: GridRenderOptions): RenderedGrid {
        val ordered = model.contentSections.filter { it.cards.isNotEmpty() }
        if (ordered.isEmpty()) return RenderedGrid(buildEmptyStateView(model.body), null, null)

        val firstRows = mutableListOf<ContentCardSpec?>()
        ordered.forEach { section ->
            val previewCount = if (section.title == "Spotlight") 5 else COLUMN_COUNT
            firstRows.addAll(section.cards.take(previewCount))
            while (firstRows.size % COLUMN_COUNT != 0) firstRows.add(null)
        }
        val overflow = ordered.flatMap { section ->
            section.cards.drop(if (section.title == "Spotlight") 5 else COLUMN_COUNT)
        }
        return renderGridCells(firstRows + overflow, model.body, options)
    }

    private fun renderGridCells(
        cards: List<ContentCardSpec?>,
        emptyMessage: String,
        options: GridRenderOptions
    ): RenderedGrid {
        if (cards.none { it != null }) {
            return RenderedGrid(buildEmptyStateView(emptyMessage), null, null)
        }

        val layoutManager = GridLayoutManager(context, COLUMN_COUNT)
        val adapter = MediaGridAdapter(cards, options)
        val grid = RecyclerView(context).apply {
            id = View.generateViewId()
            this.layoutManager = layoutManager
            this.adapter = adapter
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            itemAnimator = null
            setHasFixedSize(true)
            setItemViewCacheSize(COLUMN_COUNT * 3)
            setPadding(
                metrics.px(63),
                metrics.px(options.topInset),
                metrics.px(45),
                metrics.px(25)
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val preferredIndex = cards.indexOfFirst { it?.id == options.preferredCardId }
            .takeIf { it >= 0 }
            ?: cards.indexOfFirst { it != null }
        grid.post {
            if (preferredIndex > 0) layoutManager.scrollToPositionWithOffset(preferredIndex, 0)
            grid.post { grid.findViewHolderForAdapterPosition(preferredIndex)?.itemView?.requestFocus() }
        }
        return RenderedGrid(grid, adapter.firstViewId, adapter.lastViewId)
    }

    fun renderSectionRails(model: ScreenViewModel, options: GridRenderOptions): RenderedGrid {
        val sections = model.contentSections
            .map { section ->
                section.copy(cards = section.cards.distinctBy { "${it.eyebrow.lowercase()}:${it.id}" })
            }
            .filter { it.cards.isNotEmpty() }
        if (sections.isEmpty()) {
            return RenderedGrid(buildEmptyStateView(model.body), null, null)
        }

        val verticalScroll = ScrollView(context).apply {
            isFillViewport = true
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, metrics.px(options.topInset), 0, metrics.px(25))
        }
        verticalScroll.addView(rows, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        val adapters = mutableListOf<MediaRailAdapter>()
        sections.forEachIndexed { railIndex, section ->
            val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            val adapter = MediaRailAdapter(section.title, section.cards, options) { sourcePosition, delta ->
                val targetRail = railIndex + delta
                if (targetRail in adapters.indices) {
                    val target = adapters[targetRail]
                    val targetPosition = target.lastFocusedPosition
                        .takeIf { it >= 0 }
                        ?: sourcePosition.coerceIn(0, target.itemCount - 1)
                    target.requestCardFocus(targetPosition)
                } else {
                    val externalId = if (delta < 0) options.upFocusId else options.downFocusId
                    if (externalId != View.NO_ID) {
                        verticalScroll.rootView.findViewById<View>(externalId)?.requestFocus()
                    }
                }
            }
            adapters.add(adapter)
            val rail = RecyclerView(context).apply {
                id = View.generateViewId()
                this.layoutManager = layoutManager
                this.adapter = adapter
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = true
                itemAnimator = null
                setHasFixedSize(true)
                setItemViewCacheSize(COLUMN_COUNT + 2)
                setPadding(metrics.px(63), 0, metrics.px(45), 0)
                setBackgroundColor(Color.TRANSPARENT)
            }
            val sectionBlock = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(section.title, 20f, TvDesign.TEXT, bold = true), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    metrics.px(28)
                ).apply {
                    marginStart = metrics.px(63)
                    marginEnd = metrics.px(45)
                    bottomMargin = metrics.px(8)
                })
                addView(rail, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    metrics.px(232)
                ))
            }
            rows.addView(sectionBlock, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (railIndex < sections.lastIndex) bottomMargin = metrics.px(options.verticalGap)
            })
        }

        var preferredRail = 0
        var preferredPosition = 0
        var preferredFound = false
        options.preferredCardId?.let { preferredId ->
            sections.forEachIndexed { sectionIndex, section ->
                if (preferredFound) return@forEachIndexed
                val itemIndex = section.cards.indexOfFirst { card ->
                    card.id == preferredId || railFocusKey(section.title, card.id) == preferredId
                }
                if (itemIndex >= 0 && !preferredFound) {
                    preferredRail = sectionIndex
                    preferredPosition = itemIndex
                    preferredFound = true
                }
            }
        }
        verticalScroll.post { adapters[preferredRail].requestCardFocus(preferredPosition) }
        return RenderedGrid(verticalScroll, adapters.first().firstViewId, adapters.last().lastViewId)
    }

    fun renderHomeRows(model: ScreenViewModel, options: GridRenderOptions): RenderedGrid {
        val ordered = model.contentSections.filter { it.cards.isNotEmpty() }
        if (ordered.isEmpty()) return RenderedGrid(buildEmptyStateView(model.body), null, null)

        val firstRows = ordered.map { section ->
            section.copy(cards = section.cards.take(if (section.title == "Spotlight") 5 else COLUMN_COUNT))
        }
        val overflow = ordered.flatMap { section ->
            section.cards.drop(if (section.title == "Spotlight") 5 else COLUMN_COUNT)
        }
        val overflowRows = overflow.chunked(COLUMN_COUNT).mapIndexed { index, cards ->
            ContentSectionSpec("More ${index + 1}", cards)
        }
        return renderSectionRails(
            model.copy(contentSections = firstRows + overflowRows),
            options
        )
    }

    private inner class MediaRailAdapter(
        private val sectionTitle: String,
        private val cards: List<ContentCardSpec>,
        private val options: GridRenderOptions,
        private val onVerticalMove: (position: Int, delta: Int) -> Unit
    ) : RecyclerView.Adapter<MediaCardHolder>() {
        private val viewIds = IntArray(cards.size) { View.generateViewId() }
        private lateinit var recyclerView: RecyclerView
        var lastFocusedPosition: Int = -1
            private set

        val firstViewId: Int get() = viewIds.first()
        val lastViewId: Int get() = viewIds.last()

        init {
            setHasStableIds(true)
        }

        override fun getItemCount(): Int = cards.size

        override fun getItemId(position: Int): Long =
            (cards[position].id.hashCode().toLong() shl 32) xor position.toLong()

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            this.recyclerView = recyclerView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaCardHolder =
            createMediaCardHolder()

        override fun onBindViewHolder(holder: MediaCardHolder, position: Int) {
            val card = cards[position]
            holder.root.id = viewIds[position]
            holder.root.background = cardBackground(holder.root.hasFocus())
            holder.root.contentDescription = listOf(card.eyebrow, card.title, card.creatorName ?: card.subtitle)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            holder.eyebrow.text = card.eyebrow.uppercase()
            holder.title.text = card.title
            holder.creator.text = card.creatorName ?: card.subtitle
            holder.root.setOnClickListener { cardSelectionListener?.onCardSelectedFromSection(card, cards) }
            holder.root.setOnFocusChangeListener { view, hasFocus ->
                view.background = cardBackground(hasFocus)
                if (hasFocus) {
                    lastFocusedPosition = holder.bindingAdapterPosition
                    options.onCardFocused(railFocusKey(sectionTitle, card.id))
                }
            }
            holder.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val current = holder.bindingAdapterPosition
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> requestCardFocus((current - 1).coerceAtLeast(0))
                    KeyEvent.KEYCODE_DPAD_RIGHT -> requestCardFocus((current + 1).coerceAtMost(cards.lastIndex))
                    KeyEvent.KEYCODE_DPAD_UP -> onVerticalMove(current, -1)
                    KeyEvent.KEYCODE_DPAD_DOWN -> onVerticalMove(current, 1)
                    else -> return@setOnKeyListener false
                }
                true
            }
            holder.root.layoutParams = RecyclerView.LayoutParams(metrics.px(284), metrics.px(232)).apply {
                marginEnd = metrics.px(18)
            }
            TvArtworkLoader.load(
                context,
                TvInteractionRules.artworkCandidate(card.artworkUrl, card.creatorAvatarUrl),
                holder.artwork,
                metrics.px(260),
                metrics.px(152)
            )
        }

        override fun onViewRecycled(holder: MediaCardHolder) {
            holder.artwork.tag = null
            holder.artwork.setImageDrawable(null)
            super.onViewRecycled(holder)
        }

        fun requestCardFocus(position: Int) {
            val safePosition = position.coerceIn(0, cards.lastIndex)
            recyclerView.findViewHolderForAdapterPosition(safePosition)?.itemView?.let { visible ->
                visible.requestFocus()
                return
            }
            recyclerView.scrollToPosition(safePosition)
            recyclerView.post {
                recyclerView.findViewHolderForAdapterPosition(safePosition)?.itemView?.requestFocus()
                    ?: recyclerView.post {
                        recyclerView.findViewHolderForAdapterPosition(safePosition)?.itemView?.requestFocus()
                    }
            }
        }
    }

    private inner class MediaGridAdapter(
        private val cards: List<ContentCardSpec?>,
        private val options: GridRenderOptions
    ) : RecyclerView.Adapter<MediaCardHolder>() {
        private val viewIds = IntArray(cards.size) { View.generateViewId() }
        private lateinit var recyclerView: RecyclerView

        val firstViewId: Int get() = viewIds[cards.indexOfFirst { it != null }]
        val lastViewId: Int get() = viewIds[cards.indexOfLast { it != null }]

        init {
            setHasStableIds(true)
        }

        override fun getItemCount(): Int = cards.size

        override fun getItemId(position: Int): Long {
            val card = cards[position] ?: return Long.MIN_VALUE + position
            return (card.id.hashCode().toLong() shl 32) xor position.toLong()
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            this.recyclerView = recyclerView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaCardHolder =
            createMediaCardHolder()

        override fun onBindViewHolder(holder: MediaCardHolder, position: Int) {
            val card = cards[position]
            holder.root.id = viewIds[position]
            holder.root.layoutParams = RecyclerView.LayoutParams(metrics.px(284), metrics.px(232)).apply {
                marginEnd = metrics.px(18)
                bottomMargin = metrics.px(options.verticalGap)
            }
            if (card == null) {
                holder.root.visibility = View.INVISIBLE
                holder.root.isFocusable = false
                holder.root.isClickable = false
                holder.root.setOnClickListener(null)
                holder.root.setOnKeyListener(null)
                holder.artwork.tag = null
                holder.artwork.setImageDrawable(null)
                return
            }
            holder.root.visibility = View.VISIBLE
            holder.root.isFocusable = true
            holder.root.isFocusableInTouchMode = true
            holder.root.isClickable = true
            holder.root.background = cardBackground(holder.root.hasFocus())
            holder.root.contentDescription = listOf(card.eyebrow, card.title, card.creatorName ?: card.subtitle)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            holder.eyebrow.text = card.eyebrow.uppercase()
            holder.title.text = card.title
            holder.creator.text = card.creatorName ?: card.subtitle
            holder.root.setOnClickListener {
                cardSelectionListener?.onCardSelectedFromSection(card, cards.filterNotNull())
            }
            holder.root.setOnFocusChangeListener { view, hasFocus ->
                view.background = cardBackground(hasFocus)
                if (hasFocus) options.onCardFocused(card.id)
            }
            holder.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> TvInteractionRules.GridDirection.LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> TvInteractionRules.GridDirection.RIGHT
                    KeyEvent.KEYCODE_DPAD_UP -> TvInteractionRules.GridDirection.UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> TvInteractionRules.GridDirection.DOWN
                    else -> return@setOnKeyListener false
                }
                moveFocus(holder.bindingAdapterPosition, direction)
            }
            TvArtworkLoader.load(
                context,
                TvInteractionRules.artworkCandidate(card.artworkUrl, card.creatorAvatarUrl),
                holder.artwork,
                metrics.px(260),
                metrics.px(152)
            )
        }

        override fun onViewRecycled(holder: MediaCardHolder) {
            holder.artwork.tag = null
            holder.artwork.setImageDrawable(null)
            super.onViewRecycled(holder)
        }

        private fun moveFocus(position: Int, direction: TvInteractionRules.GridDirection): Boolean {
            if (position !in cards.indices) return false
            val target = gridNeighborSkippingSpacers(position, direction)
            if (target != null) {
                requestCardFocus(target)
                return true
            }

            val externalId = when (direction) {
                TvInteractionRules.GridDirection.UP -> options.upFocusId
                TvInteractionRules.GridDirection.DOWN -> options.downFocusId
                TvInteractionRules.GridDirection.LEFT,
                TvInteractionRules.GridDirection.RIGHT -> View.NO_ID
            }
            if (externalId != View.NO_ID) {
                recyclerView.rootView.findViewById<View>(externalId)?.requestFocus()
            }
            return true
        }

        private fun requestCardFocus(position: Int) {
            if (cards.getOrNull(position) == null) return
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.let { visible ->
                visible.requestFocus()
                return
            }
            recyclerView.scrollToPosition(position)
            recyclerView.post {
                recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
                    ?: recyclerView.post {
                        recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
                    }
            }
        }

        private fun gridNeighborSkippingSpacers(
            position: Int,
            direction: TvInteractionRules.GridDirection
        ): Int? {
            val direct = TvInteractionRules.gridNeighbor(position, direction, cards.size) ?: return null
            if (cards[direct] != null) return direct
            if (direction != TvInteractionRules.GridDirection.UP &&
                direction != TvInteractionRules.GridDirection.DOWN
            ) return null

            val rowStart = (direct / COLUMN_COUNT) * COLUMN_COUNT
            val rowEnd = minOf(rowStart + COLUMN_COUNT, cards.size)
            val column = position % COLUMN_COUNT
            return (0 until COLUMN_COUNT)
                .flatMap { distance -> listOf(column - distance, column + distance) }
                .distinct()
                .map { rowStart + it }
                .firstOrNull { it in rowStart until rowEnd && cards[it] != null }
        }
    }

    private fun createMediaCardHolder(): MediaCardHolder {
        val cardRoot = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            background = cardBackground(focused = false)
        }

        val artwork = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(metrics.px(260), metrics.px(152)).apply {
                leftMargin = metrics.px(12)
                topMargin = metrics.px(12)
            }
        }
        cardRoot.addView(artwork)

        val eyebrow = text("", 10f, TvDesign.ORANGE, bold = true).apply {
            maxLines = 1
            layoutParams = FrameLayout.LayoutParams(metrics.px(260), metrics.px(15)).apply {
                leftMargin = metrics.px(12)
                topMargin = metrics.px(170)
            }
        }
        cardRoot.addView(eyebrow)
        val title = text("", 16f, TvDesign.TEXT, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = FrameLayout.LayoutParams(metrics.px(260), metrics.px(22)).apply {
                leftMargin = metrics.px(12)
                topMargin = metrics.px(186)
            }
        }
        cardRoot.addView(title)
        val creator = text("", 13f, TvDesign.DIM).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = FrameLayout.LayoutParams(metrics.px(260), metrics.px(18)).apply {
                leftMargin = metrics.px(12)
                topMargin = metrics.px(210)
            }
        }
        cardRoot.addView(creator)
        return MediaCardHolder(cardRoot, artwork, eyebrow, title, creator)
    }

    private class MediaCardHolder(
        val root: FrameLayout,
        val artwork: ImageView,
        val eyebrow: TextView,
        val title: TextView,
        val creator: TextView
    ) : RecyclerView.ViewHolder(root)

    private fun buildEmptyStateView(message: String): View = FrameLayout(context).apply {
        addView(text(message.ifBlank { "No content available" }, 18f, TvDesign.MUTED).apply {
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun renderPanelScreen(model: ScreenViewModel): View {
        val root = LayoutInflater.from(context).inflate(R.layout.view_panel, FrameLayout(context), false)
        root.findViewById<TextView>(R.id.panelTitle).text = model.title
        root.findViewById<TextView>(R.id.panelBody).apply {
            text = model.body
            visibility = if (model.body.isBlank()) View.GONE else View.VISIBLE
        }
        val actionsContainer = root.findViewById<LinearLayout>(R.id.panelActions)
        var first: View? = null
        model.actions.forEach { spec ->
            val button = Button(context).apply {
                text = spec.label
                setOnClickListener { spec.onClick() }
                isFocusable = true
                isFocusableInTouchMode = true
                setTextColor(TvDesign.TEXT)
                background = TvDesign.rounded(TvDesign.SURFACE_RAISED, metrics.px(8), metrics.px(2), TvDesign.BORDER)
                minWidth = metrics.px(140)
                minHeight = metrics.px(52)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textPx(15f))
            }
            if (first == null) first = button
            actionsContainer.addView(button)
        }
        root.post { first?.requestFocus() }
        return root
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textPx(size))
        includeFontPadding = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun cardBackground(focused: Boolean) = TvDesign.rounded(
        fill = TvDesign.SURFACE,
        radiusPx = metrics.px(11),
        strokeWidthPx = metrics.px(if (focused) 4 else 1),
        stroke = if (focused) TvDesign.YELLOW else TvDesign.BORDER
    )

    companion object {
        const val COLUMN_COUNT = 6

        private fun railFocusKey(sectionTitle: String, cardId: String): String =
            "${sectionTitle.length}:$sectionTitle:$cardId"
    }
}
