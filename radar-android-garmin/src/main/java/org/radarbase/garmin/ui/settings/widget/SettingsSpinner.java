package org.radarbase.garmin.ui.settings.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import org.radarbase.garmin.R;

import java.util.List;
import java.util.Map;


/**
 * View which displays an element with title and selected option, as in Android Settings.
 *
 * @author ioana.morari on 7/22/16.
 */
public class SettingsSpinner<T> extends LinearLayout
{
    private static final String TAG = SettingsSpinner.class.getSimpleName();

    private final Context mContext;
    private Map<T, Integer> resourceMap;

    private TextView titleView;
    private TextView optionView;
    private List<T> configOptions;
    private T selectedOption;
    private String resourceIdPrefix;

    private SpinnerListener mEnumSpinnerListener;

    public SettingsSpinner(Context context)
    {
        super(context);
        mContext = context;
        setOrientation(VERTICAL);
    }

    public SettingsSpinner(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        mContext = context;
        setOrientation(VERTICAL);
    }

    public SettingsSpinner(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        mContext = context;
        setOrientation(VERTICAL);
    }

    public void setEnumSpinnerListener(SpinnerListener enumSpinnerListener)
    {
        this.mEnumSpinnerListener = enumSpinnerListener;
    }

    public void initialize(@StringRes int title, String resourceIdPrefix)
    {
        this.resourceIdPrefix = resourceIdPrefix;
        initialize(title);
    }

    public void initialize(@StringRes int title, Map<T, Integer> resourceMap)
    {
        this.resourceMap = resourceMap;
        initialize(title);
    }

    public void initialize(@StringRes int title)
    {
        this.setOnClickListener(v -> SettingsSpinner.this.showListDialog());

        if(title != -1)
        {
            titleView.setText(title);
        }
        else
        {
            titleView.setVisibility(GONE);
        }
    }

    private void setSelectedOption(int index)
    {
        selectedOption = configOptions.get(index);
        optionView.setText(getString(selectedOption));

        if(mEnumSpinnerListener != null)
        {
            mEnumSpinnerListener.onItemSelected(selectedOption);
        }
    }

    private String getString(T selectedOption)
    {
        if(selectedOption instanceof String)
        {
            return org.radarbase.garmin.ui.settings.util.TextUtils.toTitleCase((String)selectedOption);
        }

        if(selectedOption == null)
        {
            return null;
        }

        Integer resId = null;

        if(resourceMap != null)
        {
            resId = resourceMap.get(selectedOption);
        }
        else if(!TextUtils.isEmpty(resourceIdPrefix))
        {
            resId = -1;
        }

        try
        {
            if(resId != null && resId != -1)
            {
                return org.radarbase.garmin.ui.settings.util.TextUtils.toTitleCase(mContext.getString(resId));
            }
        }
        catch(Exception e)
        {
            Log.w(TAG, "Failed to find resource for: " + selectedOption, e);
        }

        return org.radarbase.garmin.ui.settings.util.TextUtils.toTitleCase(selectedOption.toString());
    }

    public void setSelectedOption(T selectedOption)
    {
        if(configOptions != null)
        {
            int currentIndex = configOptions.indexOf(selectedOption);

            if(currentIndex != -1)
            {
                setSelectedOption(currentIndex);
            }
        }
    }

    public void setOptions(List<T> options, T selectedOption)
    {
        configOptions = options;
        setSelectedOption(selectedOption);
    }

    public String[] getOptionsText()
    {
        String[] items = new String[configOptions.size()];

        for (int i = 0; i < configOptions.size(); i++)
        {
            items[i] = getString(configOptions.get(i));
        }
        return items;
    }

    public T getSelectedValue()
    {
        return selectedOption;
    }

    @Override
    protected void onFinishInflate()
    {
        super.onFinishInflate();
        inflate(getContext(), R.layout.view_enum_spinner, this);

        titleView = findViewById(R.id.config_text_title);
        optionView = findViewById(R.id.config_text_option);
    }

    private void showListDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setItems(getOptionsText(), (dialog, which) -> setSelectedOption(which));
        builder.create().show();
    }

    public interface SpinnerListener<T>
    {
        void onItemSelected(T item);
    }
}
