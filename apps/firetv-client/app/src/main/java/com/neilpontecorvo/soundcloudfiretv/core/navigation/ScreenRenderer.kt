package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.neilpontecorvo.soundcloudfiretv.R

data class ActionSpec(val label: String, val onClick: () -> Unit)

data class ScreenViewModel(
    val title: String,
    val body: String,
    val actions: List<ActionSpec> = emptyList()
)

class ScreenRenderer(private val context: Context) {

    fun render(model: ScreenViewModel): View {
        val root = LayoutInflater.from(context).inflate(R.layout.view_panel, null)
        root.findViewById<TextView>(R.id.panelTitle).text = model.title
        root.findViewById<TextView>(R.id.panelBody).text = model.body

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
                    dp(64)
                ).apply { bottomMargin = dp(12) }
            }
            actionsContainer.addView(button)
        }

        return root
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
