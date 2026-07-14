package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.neilpontecorvo.soundcloudfiretv.R
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class ActionSpec(val label: String, val onClick: () -> Unit)

data class ContentCardSpec(
    val id: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val metadata: String? = null,
    val artworkUrl: String? = null,
    val webUrl: String? = null
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

/**
 * Callback interface for content card selection events.
 */
interface ContentCardSelectionListener {
    fun onCardSelected(card: ContentCardSpec)
    fun onCardSelectedFromSection(card: ContentCardSpec, sectionCards: List<ContentCardSpec>) {
        onCardSelected(card)
    }
}

class ScreenRenderer(
    private val context: Context,
    private val cardSelectionListener: ContentCardSelectionListener? = null
) {

    fun render(model: ScreenViewModel): View {
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
            clipToPadding = false
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            setPadding(0, dp(2), 0, dp(36))
        }
        root.addView(container)

        var firstFocusable: View? = null
        var firstRailScroll: HorizontalScrollView? = null

        model.contentSections.forEach { section ->
            val sectionView = buildMediaRail(section) { focusable, railScroll ->
                if (firstFocusable == null) firstFocusable = focusable
                if (firstRailScroll == null) firstRailScroll = railScroll
            }
            container.addView(sectionView)
        }

        if (model.contentSections.isEmpty() && model.body.isNotBlank()) {
            val messageView = buildEmptyStateView(model.body)
            container.addView(messageView)
        }

        root.post {
            firstRailScroll?.scrollTo(0, 0)
            firstFocusable?.requestFocus()
            firstRailScroll?.postDelayed({
                firstRailScroll?.scrollTo(0, 0)
                firstFocusable?.requestFocus()
            }, 120L)
        }

        return root
    }

    private fun buildMediaRail(
        section: ContentSectionSpec,
        onFirstFocusable: (View, HorizontalScrollView) -> Unit
    ): View {
        val railContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            setPadding(0, 0, 0, dp(18))
        }

        val titleView = TextView(context).apply {
            text = section.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(14), 0, dp(12))
        }
        railContainer.addView(titleView)

        val railScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        val rail = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipToPadding = false
            clipChildren = false
            setPadding(dp(8), dp(8), dp(96), dp(16))
        }
        railScroll.addView(rail)

        var isFirst = true
        val cardViews = mutableListOf<View>()
        section.cards.forEach { card ->
            val cardView = buildMediaCard(card, section.cards, railScroll)
            if (isFirst) {
                onFirstFocusable(cardView, railScroll)
                isFirst = false
            }
            rail.addView(cardView)
            cardViews.add(cardView)
        }

        cardViews.forEachIndexed { index, cardView ->
            cardView.nextFocusLeftId = cardViews.getOrNull(index - 1)?.id ?: cardView.id
            cardView.nextFocusRightId = cardViews.getOrNull(index + 1)?.id ?: cardView.id
        }

        railContainer.addView(railScroll)
        return railContainer
    }

    private fun buildMediaCard(
        card: ContentCardSpec,
        sectionCards: List<ContentCardSpec> = emptyList(),
        railScroll: HorizontalScrollView? = null
    ): View {
        val cardWidth = dp(260)
        val cardHeight = dp(250)
        val artworkHeight = dp(156)

        val cardRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setBackgroundResource(R.drawable.tv_media_card_background)
            clipToPadding = false
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                rightMargin = dp(22)
            }
            setOnClickListener {
                cardSelectionListener?.onCardSelectedFromSection(card, sectionCards)
            }
        }

        val artworkContainer = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.artwork_placeholder)
            clipToPadding = false
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                artworkHeight
            )
        }

        val artworkView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.92f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        artworkContainer.addView(artworkView)
        ArtworkLoader.load(card.artworkUrl, artworkView)

        val orangeWash = View(context).apply {
            alpha = 0f
            setBackgroundColor(0x33FF5500)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        artworkContainer.addView(orangeWash)

        val eyebrowBadge = TextView(context).apply {
            text = card.eyebrow.uppercase().ifBlank { "TRACK" }
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

        val cursorBadge = TextView(context).apply {
            text = ">"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            alpha = 0f
            visibility = View.INVISIBLE
            background = ovalDrawable(0xFFFF5500.toInt())
            layoutParams = FrameLayout.LayoutParams(dp(42), dp(42)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(12)
                marginEnd = dp(12)
            }
            elevation = dp(12).toFloat()
        }
        artworkContainer.addView(cursorBadge)
        cardRoot.addView(artworkContainer)

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val titleView = TextView(context).apply {
            text = card.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(titleView)

        val subtitleView = TextView(context).apply {
            text = card.subtitle
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }
        textContainer.addView(subtitleView)

        if (!card.metadata.isNullOrBlank()) {
            val metaView = TextView(context).apply {
                text = card.metadata
                setTextColor(0xFFFF5500.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                setPadding(0, dp(4), 0, 0)
            }
            textContainer.addView(metaView)
        }

        cardRoot.addView(textContainer)

        TvFocusStyler.apply(cardRoot, focusedScale = 1.08f, onFocusChanged = { hasFocus ->
            if (hasFocus) {
                cursorBadge.visibility = View.VISIBLE
                cursorBadge.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(120L)
                    .start()
                orangeWash.animate()
                    .alpha(1f)
                    .setDuration(120L)
                    .start()
                railScroll?.post {
                    val viewportPadding = dp(8)
                    val targetX = (cardRoot.left - viewportPadding).coerceAtLeast(0)
                    if (kotlin.math.abs(railScroll.scrollX - targetX) > dp(12)) {
                        railScroll.smoothScrollTo(targetX, 0)
                    }
                }
            } else {
                cursorBadge.animate()
                    .alpha(0f)
                    .translationY(dp(4).toFloat())
                    .setDuration(90L)
                    .withEndAction { cursorBadge.visibility = View.INVISIBLE }
                    .start()
                orangeWash.animate()
                    .alpha(0f)
                    .setDuration(90L)
                    .start()
            }
        })

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
                isClickable = true
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setBackgroundResource(R.drawable.tv_focusable_background)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(40)
                ).apply {
                    marginEnd = dp(12)
                }
                setPadding(dp(16), 0, dp(16), 0)
                minWidth = dp(100)
                minHeight = dp(40)
            }
            TvFocusStyler.apply(button, focusedScale = 1.08f)
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

    private fun ovalDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), 0xFFFFFFFF.toInt())
    }
}

private object ArtworkLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun load(url: String?, target: ImageView) {
        target.setImageDrawable(null)
        target.tag = null
        val normalizedUrl = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return
        cache[normalizedUrl]?.let { cached ->
            target.setImageBitmap(cached)
            return
        }

        target.tag = normalizedUrl
        executor.execute {
            val bitmap = runCatching {
                val connection = URL(normalizedUrl).openConnection()
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull() ?: return@execute

            cache[normalizedUrl] = bitmap
            mainHandler.post {
                if (target.tag == normalizedUrl) {
                    target.setImageBitmap(bitmap)
                }
            }
        }
    }
}
