package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.neilpontecorvo.soundcloudfiretv.R

data class ActionSpec(val label: String, val onClick: () -> Unit)

data class ContentCardSpec(
    val id: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val metadata: String? = null
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

class ScreenRenderer(private val context: Context) {

    fun render(model: ScreenViewModel): View {
        // Check if this is a content screen (has sections) or a settings/diagnostic screen
        return if (model.contentSections.isNotEmpty()) {
            renderContentScreen(model)
        } else {
            renderPanelScreen(model)
        }
    }

    private fun renderContentScreen(model: ScreenViewModel): View {
        val root = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        root.addView(container)

        var firstFocusable: View? = null

        // Render each content section as a rail
        model.contentSections.forEach { section ->
            val sectionView = buildMediaRail(section) { focusable ->
                if (firstFocusable == null) firstFocusable = focusable
            }
            container.addView(sectionView)
        }

        // Show body message if no content
        if (model.contentSections.isEmpty() && model.body.isNotBlank()) {
            val messageView = buildEmptyStateView(model.body)
            container.addView(messageView)
        }

        root.post {
            firstFocusable?.requestFocus()
        }

        return root
    }

    private fun buildMediaRail(
        section: ContentSectionSpec,
        onFirstFocusable: (View) -> Unit
    ): View {
        val railContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        // Section title
        val titleView = TextView(context).apply {
            text = section.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(12), 0, dp(12))
        }
        railContainer.addView(titleView)

        // Horizontal scroll with cards
        val railScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val rail = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipToPadding = false
            setPadding(0, dp(4), dp(48), dp(4))
        }
        railScroll.addView(rail)

        var isFirst = true
        section.cards.forEach { card ->
            val cardView = buildMediaCard(card)
            if (isFirst) {
                onFirstFocusable(cardView)
                isFirst = false
            }
            rail.addView(cardView)
        }

        railContainer.addView(railScroll)
        return railContainer
    }

    private fun buildMediaCard(card: ContentCardSpec): View {
        val cardWidth = dp(220)
        val cardHeight = dp(160)
        val artworkHeight = dp(90)

        val cardRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setBackgroundResource(R.drawable.tv_media_card_background)
            clipToPadding = false
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                rightMargin = dp(16)
            }
        }

        // Artwork placeholder area
        val artworkContainer = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.artwork_placeholder)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                artworkHeight
            )
        }

        // Eyebrow badge on artwork
        val eyebrowBadge = TextView(context).apply {
            text = card.eyebrow.uppercase()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                marginStart = dp(8)
                bottomMargin = dp(8)
            }
        }
        artworkContainer.addView(eyebrowBadge)
        cardRoot.addView(artworkContainer)

        // Text content area
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val titleView = TextView(context).apply {
            text = card.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(titleView)

        val subtitleView = TextView(context).apply {
            text = card.subtitle
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }
        textContainer.addView(subtitleView)

        if (!card.metadata.isNullOrBlank()) {
            val metaView = TextView(context).apply {
                text = card.metadata
                setTextColor(0xFF666666.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
            }
            textContainer.addView(metaView)
        }

        cardRoot.addView(textContainer)

        // Apply focus styling
        TvFocusStyler.apply(cardRoot, focusedScale = 1.08f)

        return cardRoot
    }

    private fun buildEmptyStateView(message: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(80), dp(48), dp(80))

            val messageView = TextView(context).apply {
                text = message
                setTextColor(0xFF888888.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            }
            addView(messageView)
        }
    }

    private fun renderPanelScreen(model: ScreenViewModel): View {
        val root = LayoutInflater.from(context).inflate(R.layout.view_panel, null)
        root.findViewById<TextView>(R.id.panelTitle).apply {
            text = model.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        root.findViewById<TextView>(R.id.panelBody).apply {
            text = model.body
            visibility = if (model.body.isBlank()) View.GONE else View.VISIBLE
        }

        var firstFocusable: View? = null

        val actionsContainer = root.findViewById<LinearLayout>(R.id.panelActions)
        model.actions.forEach { spec ->
            val button = Button(context).apply {
                text = spec.label
                setOnClickListener { spec.onClick.invoke() }
                isFocusable = true
                isFocusableInTouchMode = true
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.tv_focusable_background)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
                ).apply {
                    bottomMargin = dp(8)
                    marginEnd = dp(8)
                }
                setPadding(dp(20), 0, dp(20), 0)
            }
            TvFocusStyler.apply(button, focusedScale = 1.05f)
            if (firstFocusable == null) firstFocusable = button
            actionsContainer.addView(button)
        }

        root.post {
            firstFocusable?.requestFocus()
        }

        return root
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
