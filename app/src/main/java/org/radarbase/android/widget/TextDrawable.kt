/*
 * Copyright 2016 Ali Muzaffar
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.android.widget

import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView

/**
 * Create a TextDrawable using the given paint object and string
 *
 * @param paint
 * @param s
 */
class TextDrawable(textView: TextView, mText: String) : Drawable(), TextWatcher {
    private val heightBounds = Rect()

    //Since this can change the font used, we need to recalculate bounds.
    private val paint = Paint(textView.paint)

    //Since this can change the bounds of the text, we need to recalculate.
    var text: String = mText
        set(value) {
            field = value
            calculateBounds()
            invalidateSelf()
        }

    init {
        calculateBounds()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawText(text, 0f, bounds.height().toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Super getOpacity is deprecated")
    override fun getOpacity(): Int = when (paint.alpha) {
        0 -> PixelFormat.TRANSPARENT
        255 -> PixelFormat.OPAQUE
        else -> PixelFormat.TRANSLUCENT
    }

    private fun calculateBounds() {
        paint.getTextBounds("1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", 0, 1, heightBounds)

        //We want to use some character to determine the max height of the text.
        //Otherwise if we draw something like "..." they will appear centered
        //Here I'm just going to use the entire alphabet to determine max height.
        //This doesn't account for leading or training white spaces.
        //mPaint.getTextBounds(mText, 0, mText.length(), bounds);
        bounds.apply {
            top = heightBounds.top
            bottom = heightBounds.bottom
            right = paint.measureText(text).toInt()
            left = 0
        }
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable) {
        text = s.toString()
    }
}
