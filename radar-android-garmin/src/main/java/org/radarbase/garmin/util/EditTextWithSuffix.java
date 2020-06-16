package org.radarbase.garmin.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;

import com.google.android.material.textfield.TextInputEditText;

import org.radarbase.garmin.R;

public class EditTextWithSuffix extends TextInputEditText
{
    TextPaint textPaint = new TextPaint();
    private String suffix = "";
    private float suffixPadding;

    public EditTextWithSuffix(Context context)
    {
        super(context);
    }

    public EditTextWithSuffix(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        getAttributes(context, attrs, 0);
    }

    public EditTextWithSuffix(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        getAttributes(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        int suffixXPosition = (int) textPaint.measureText(getText().toString()) + getPaddingLeft();
        canvas.drawText(suffix, Math.max(suffixXPosition, suffixPadding), getBaseline(), textPaint);
    }

    @Override
    protected void onFinishInflate()
    {
        super.onFinishInflate();
        textPaint.setColor(getCurrentTextColor());
        textPaint.setTextSize(getTextSize());
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void getAttributes(Context context, AttributeSet attrs, int defStyleAttr)
    {
        TypedArray styledAttributes = context.obtainStyledAttributes(
                attrs,
                R.styleable.EditTextWithSuffix,
                defStyleAttr,
                0);

        suffix = styledAttributes.getString(R.styleable.EditTextWithSuffix_suffix);

        if(suffix == null)
        {
            suffix = "";
        }

        suffixPadding = styledAttributes.getDimension(R.styleable.EditTextWithSuffix_suffixPadding, 0);

        styledAttributes.recycle();
    }

    public String getSuffix()
    {
        return suffix;
    }

    public void setSuffix(String suffix)
    {
        this.suffix = suffix;
    }

    public float getSuffixPadding()
    {
        return suffixPadding;
    }

    public void setSuffixPadding(float suffixPadding)
    {
        this.suffixPadding = suffixPadding;
    }
}
