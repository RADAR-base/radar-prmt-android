package org.radarbase.garmin.ui;

import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import org.radarbase.garmin.R;
import org.radarbase.garmin.util.ConfirmationDialog;

public abstract class BaseFragment extends Fragment {
    protected final String TAG = getClass().getSimpleName();

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        Log.d(TAG, "onAttach()");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        Log.d(TAG, "onCreateView()");
        return inflater.inflate(getLayoutId(), container, false);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Log.d(TAG, "onResume()");

        showBackInActionBar(shouldShowBack());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected abstract
    @LayoutRes
    int getLayoutId();

    protected void showErrorDialog(String message)
    {
        Log.d(TAG, String.format("showErrorDialog(message = %s)", message));

        ConfirmationDialog dialog = new ConfirmationDialog(getContext(), getString(R.string.alert_error_title), message, getString(R.string.button_ok));
        dialog.show();
    }

    protected void setTitleInActionBar(@StringRes int titleInActionBar)
    {
        Log.d(TAG, String.format("setTitleInActionBar(titleInActionBar = %s)", getString(titleInActionBar)));

        ActionBar actionBar = getActivity().getActionBar();

        if(actionBar != null)
        {
            actionBar.setTitle(titleInActionBar);
        }
    }

    protected void setTitleInActionBar(String titleInActionBar)
    {
        Log.d(TAG, String.format("setTitleInActionBar(titleInActionBar = %s)", titleInActionBar));

        ActionBar actionBar = getActivity().getActionBar();

        if(actionBar != null)
        {
            actionBar.setTitle(titleInActionBar);
        }
    }

    private void showBackInActionBar(boolean enabled)
    {
        Log.d(TAG, "showBackInActionBar()");

        ActionBar actionBar = getActivity().getActionBar();

        if(actionBar != null)
        {
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
    }

    protected boolean shouldShowBack()
    {
        return true;
    }
}
