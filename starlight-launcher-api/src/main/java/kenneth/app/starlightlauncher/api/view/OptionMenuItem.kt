package kenneth.app.starlightlauncher.api.view

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.LinearLayout
import kenneth.app.starlightlauncher.api.R
import kenneth.app.starlightlauncher.api.databinding.OptionMenuItemBinding

class OptionMenuItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val binding = OptionMenuItemBinding.inflate(LayoutInflater.from(context), this)

    var itemIcon: Drawable?
        get() = binding.itemIcon.drawable
        /**
         * Sets the icon drawable ([Drawable]) of this menu item.
         */
        set(drawable) {
            binding.itemIcon.apply {
                setImageDrawable(drawable)
                if (applyColorTint) {
                    applyItemIconColor()
                }
            }
        }

    var itemLabel: String
        get() = binding.itemLabel.text.toString()
        /**
         * Sets the label of this menu item.
         */
        set(label) {
            binding.itemLabel.text = label
        }

    var applyColorTint: Boolean = true
        get() = field
        set(value) {
            field = value
            if (value)
                applyItemIconColor()
            else
                removeItemIconColor()
        }

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.OptionMenuItem,
            0, 0
        ).apply {
            try {
                val label = getString(R.styleable.OptionMenuItem_itemLabel)
                val icon = getDrawable(R.styleable.OptionMenuItem_itemIcon)

                binding.apply {
                    itemLabel.text = label
                    itemIcon.setImageDrawable(icon)
                    applyItemIconColor()
                }
            } finally {
                recycle()
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        super.onTouchEvent(event)

        return when (event?.action) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                performClick()
                true
            }
            MotionEvent.ACTION_DOWN -> {
                showClickedEffect()
                true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                hideClickedEffect()
                true
            }
            else -> false
        }
    }

    private fun applyItemIconColor() {
        binding.itemIcon.setColorFilter(
            context.getColor(android.R.color.white),
            PorterDuff.Mode.SRC_ATOP
        )
    }

    private fun removeItemIconColor() {
        binding.itemIcon.clearColorFilter()
    }

    private fun showClickedEffect() {
        alpha = 0.5f
    }

    private fun hideClickedEffect() {
        alpha = 1f
    }
}