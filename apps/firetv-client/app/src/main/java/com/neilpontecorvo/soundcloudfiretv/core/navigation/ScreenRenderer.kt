package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
        val root = LayoutInflater.from(context).inflate(R.layout.view_panel, null)
        root.findViewById<TextView>(R.id.panelTitle).text = model.title
        root.findViewById<TextView>(R.id.panelBody).apply {
            text = model.body
            visibility = if (model.body.isBlank()) View.GONE else View.VISIBLE
        }
        var firstFocusable: View? = null

        val contentContainer = root.findViewById<LinearLayout>(R.id.panelContentSections)
        model.contentSections.forEach { section ->
            val sectionTitle = TextView(context).apply {
                text = section.title
                setTextColor(context.getColor(android.R.color.white))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(6)
                }
            }
            contentContainer.addView(sectionTitle)

            val rail = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipToPadding = false
                setPadding(dp(4), dp(4), dp(18), dp(10))
            }
            val railScroll = HorizontalScrollView(context).apply {
                isFillViewport = true
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                addView(rail)
            }

            val cards = mutableListOf<View>()
            section.cards.forEach { card ->
                val cardView = buildContentCard(card, cards)
                if (firstFocusable == null) {
                    firstFocusable = cardView
                    cardView.isSelected = true
                }
                cards.add(cardView)
                rail.addView(cardView)
            }
            contentContainer.addView(railScroll)
        }

        val actionsContainer = root.findViewById<LinearLayout>(R.id.panelActions)
        model.actions.forEach { spec ->
            val button = Button(context).apply {
                text = spec.label
                setOnClickListener { spec.onClick.invoke() }
                isFocusable = true
                isFocusableInTouchMode = true
                setTextColor(context.getColor(android.R.color.white))
                setBackgroundResource(R.drawable.tv_focusable_background)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(42)
                ).apply { bottomMargin = dp(6) }
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

    private fun buildContentCard(card: ContentCardSpec, siblingCards: List<View>): View {
        val cardRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setBackgroundResource(R.drawable.tv_content_card_background)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                dp(292),
                dp(136)
            ).apply { rightMargin = dp(10) }
        }

        val eyebrow = TextView(context).apply {
            text = card.eyebrow.uppercase()
            setTextColor(0xFF5CE1E6.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }

        val title = TextView(context).apply {
            text = card.title
            setTextColor(context.getColor(android.R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
        }

        val subtitle = TextView(context).apply {
            text = card.subtitle
            setTextColor(0xFFD4D4D4.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
        }

        cardRoot.addView(eyebrow)
        cardRoot.addView(title)
        cardRoot.addView(subtitle)

        if (!card.metadata.isNullOrBlank()) {
            val metadata = TextView(context).apply {
                text = card.metadata
                setTextColor(0xFFA8A8A8.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
            }
            cardRoot.addView(metadata)
        }

        TvFocusStyler.apply(cardRoot, focusedScale = 1.065f)
        cardRoot.setOnClickListener {
            siblingCards.forEach { it.isSelected = false }
            cardRoot.isSelected = true
            cardRoot.requestFocus()
        }

        return cardRoot
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
