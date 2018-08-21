package org.radarcns.detail;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.radarcns.android.MainActivityView;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.auth.AppAuthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.radarcns.detail.DetailInfoActivity.PRIVACY_POLICY;

public class PrivacyPolicyFragment extends Fragment implements MainActivityView, View.OnClickListener {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyPolicyFragment.class);
    private TextView policyLink;
    private Button acceptedButton;
    private String policyUrl = null;
    private OnFragmentInteractionListener mListener;
    private AppAuthState authState;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PrivacyPolicyFragment.
     */
    public static PrivacyPolicyFragment newInstance(AppAuthState state) {
        PrivacyPolicyFragment fragment = new PrivacyPolicyFragment();
        Bundle args = new Bundle();
        args = state.addToBundle(args);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            authState = AppAuthState.Builder.from(args).build();
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_privacy_policy, container, false);
        policyLink = (TextView) view.findViewById(R.id.loginPrivacyStatement);
        policyUrl = RadarConfiguration.getInstance().getString(PRIVACY_POLICY);
        policyLink.setText(policyUrl);
        policyLink.setVisibility(View.VISIBLE);
        policyLink.setOnClickListener(this);

        acceptedButton = view.findViewById(R.id.accept_privacy_policy_button);
        acceptedButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }
    private void updatePrivacyStatement(boolean runInUiThread) {
        final String newPolicyUrl = RadarConfiguration.getInstance().getString(PRIVACY_POLICY);
        synchronized (this) {
            policyUrl = newPolicyUrl;
        }
        logger.info("Setting privacy policy {}", newPolicyUrl);
        if (runInUiThread) {
//            runOnUiThread(() -> {
//                if (newPolicyUrl == null) {
//                    policyLink.setVisibility(View.INVISIBLE);
//                } else {
//                    policyLink.setVisibility(View.VISIBLE);
//                }
//            });
        } else {
            if (newPolicyUrl == null) {
                policyLink.setVisibility(View.INVISIBLE);
            } else {
                policyLink.setVisibility(View.VISIBLE);
                policyLink.setText(policyUrl);
            }
        }
    }


    public void openPrivacyPolicy(View view) {
        updatePrivacyStatement(true);
        String localPolicyUrl;
        synchronized (this) {
            localPolicyUrl = policyUrl;
        }
        if (localPolicyUrl != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(localPolicyUrl)));
        }
    }

    public void acceptPrivacyPolicy(View view) {
        logger.info("Policy accepted. Redirecting to DetailedMainView...");
        mListener.onAcceptPrivacyPolicy(authState);
    }

    @Override
    public void update() {
        updatePrivacyStatement(true);
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        switch (viewId) {
            case R.id.accept_privacy_policy_button:
                acceptPrivacyPolicy(view);
                return;
            case R.id.loginPrivacyStatement:
                openPrivacyPolicy(view);
                return;

        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {

        void onAcceptPrivacyPolicy(AppAuthState state);
    }
}
