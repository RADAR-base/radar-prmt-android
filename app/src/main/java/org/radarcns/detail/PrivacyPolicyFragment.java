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

import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.auth.AppAuthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

import static org.radarcns.android.auth.portal.ManagementPortalClient.BASE_URL_PROPERTY;
import static org.radarcns.android.auth.portal.ManagementPortalClient.PRIVACY_POLICY_URL_PROPERTY;
import static org.radarcns.detail.DetailInfoActivity.PRIVACY_POLICY;
import static org.radarcns.detail.DetailMainActivityView.MAX_USERNAME_LENGTH;
import static org.radarcns.detail.DetailMainActivityView.truncate;

public class PrivacyPolicyFragment extends Fragment implements View.OnClickListener {
    private static final Logger logger = LoggerFactory.getLogger(PrivacyPolicyFragment.class);
    private TextView dataCollectionPolicyLink;
    private OnFragmentInteractionListener mListener;
    private AppAuthState authState;
    private String privacyPolicyUrl;

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

        dataCollectionPolicyLink = view.findViewById(R.id.dataCollectionDescriptionStatement);
        dataCollectionPolicyLink.setOnClickListener(this);

        TextView generalPrivacyPolicyLink = view.findViewById(R.id.generalPrivacyPolicyStatement);
        Serializable privacyPolicy = authState.getProperty(PRIVACY_POLICY_URL_PROPERTY);
        if (privacyPolicy != null) {
            privacyPolicyUrl = privacyPolicy.toString();
            generalPrivacyPolicyLink.setOnClickListener(this);
            generalPrivacyPolicyLink.setVisibility(View.VISIBLE);
            generalPrivacyPolicyLink.setText(getString(R.string.general_privacy_policy));
        } else {
            generalPrivacyPolicyLink.setVisibility(View.INVISIBLE);
        }

        Button acceptedButton = view.findViewById(R.id.accept_privacy_policy_button);
        acceptedButton.setOnClickListener(this);

        String projectId = getString(R.string.study_id_message, authState.getProjectId());
        TextView mProjectId = view.findViewById(R.id.inputProjectId);
        mProjectId.setText(projectId);

        String userId = getString(R.string.user_id_message, truncate(authState.getUserId(), MAX_USERNAME_LENGTH));
        TextView mUserId = view.findViewById(R.id.inputUserId);
        mUserId.setText(userId);

        String baseUrl = (String) authState.getProperties().get(BASE_URL_PROPERTY);
        TextView acceptanceStatement = view.findViewById(R.id.policyAcceptanceStatement);
        acceptanceStatement.setText(getString(R.string.policy_acceptance_message, baseUrl));
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

    private synchronized String getDataCollectionUrl() {
        return RadarConfiguration.getInstance().getString(PRIVACY_POLICY);
    }

    private String updatePrivacyStatement() {
        final String url = getDataCollectionUrl();
        logger.info("Setting privacy policy {}", url);

        if (url != null) {
            dataCollectionPolicyLink.setVisibility(View.VISIBLE);
            dataCollectionPolicyLink.setText(R.string.collected_data_description);
        } else {
            dataCollectionPolicyLink.setVisibility(View.INVISIBLE);
        }
        return url;
    }


    public void openUrl(String url) {
        if (url != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    public void acceptPrivacyPolicy() {
        logger.info("Policy accepted. Redirecting to DetailedMainView...");
        mListener.onAcceptPrivacyPolicy(authState);
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        switch (viewId) {
            case R.id.accept_privacy_policy_button:
                acceptPrivacyPolicy();
                break;
            case R.id.dataCollectionDescriptionStatement:
                openUrl(updatePrivacyStatement());
                break;
            case R.id.generalPrivacyPolicyStatement:
                openUrl(privacyPolicyUrl);
                break;
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
