/*
 * Copyright 2017 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarcns.detail;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;

import org.json.JSONException;
import org.json.JSONObject;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.auth.AppAuthState;
import org.radarcns.android.auth.LoginActivity;
import org.radarcns.android.auth.LoginManager;
import org.radarcns.android.auth.QrLoginManager;
import org.radarcns.android.auth.portal.ManagementPortalLoginManager;
import org.radarcns.android.util.Boast;
import org.radarcns.android.util.NetworkConnectedReceiver;
import org.radarcns.producer.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.radarcns.android.RadarConfiguration.PROJECT_ID_KEY;
import static org.radarcns.android.RadarConfiguration.RADAR_CONFIGURATION_CHANGED;
import static org.radarcns.android.RadarConfiguration.USER_ID_KEY;

public class RadarLoginActivity extends LoginActivity implements NetworkConnectedReceiver.NetworkConnectedListener, PrivacyPolicyFragment.OnFragmentInteractionListener {
    private static final Logger logger = LoggerFactory.getLogger(RadarLoginActivity.class);
    private static final String BASE_URL_KEY = "radar_base_url";

    private QrLoginManager qrManager;
    private ManagementPortalLoginManager mpManager;
    private boolean canLogin;
    private ProgressDialog progressDialog;
    private TextView messageBox;
    private Button scanButton;
    private NetworkConnectedReceiver networkReceiver;
    private boolean didLogin;
    private boolean didCreate;

    private final BroadcastReceiver configBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onDoneProcessing();
            mpManager.refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        didLogin = false;
        didCreate = false;
        super.onCreate(savedBundleInstance);
        if (!didLogin) {
            setContentView(R.layout.activity_login);
            messageBox = findViewById(R.id.messageText);
            scanButton = findViewById(R.id.scanButton);
            networkReceiver = new NetworkConnectedReceiver(this, this);
        }
        didCreate = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RadarConfiguration.getInstance().getStatus() == RadarConfiguration.FirebaseStatus.READY) {
            onProcessing(R.string.retrieving_configuration);
        }
        canLogin = true;
        registerReceiver(configBroadcastReceiver, new IntentFilter(RADAR_CONFIGURATION_CHANGED));
        networkReceiver.register();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(configBroadcastReceiver);
        networkReceiver.unregister();
    }

    @NonNull
    @Override
    protected List<LoginManager> createLoginManagers(AppAuthState state) {
        logger.info("Creating mpManager");
        this.mpManager = new ManagementPortalLoginManager(this, state);
        this.qrManager = new QrLoginManager(this, this::parseQrCode);
        return Arrays.asList(this.qrManager, this.mpManager);
    }

    private AppAuthState parseQrCode(String qrCode) {
        onProcessing(R.string.logging_in);
        logger.info("Read tokenUrl: {}", qrCode);

        if (qrCode.isEmpty()) {
            throw new QrException("Please scan the correct QR code.");
        }

        qrCode = qrCode.trim();

        if (qrCode.charAt(0) == '{') {
            // parse as JSON with embedded refresh token
            try {
                String refreshToken = new JSONObject(qrCode).get("refreshToken").toString();
                mpManager.setRefreshToken(refreshToken);
            } catch (JSONException e) {
                throw new QrException("Failed to parse JSON refresh token", e);
            }
        } else if (qrCode.startsWith("http://") || qrCode.startsWith("https://")) {
            // parse as URL containing refresh token
            try {
                // validate scanned url
                mpManager.setTokenFromUrl(URI.create(qrCode).toURL().toString());
            } catch (MalformedURLException | IllegalArgumentException e) {
                throw new QrException("Please scan your QR code again.", e);
            }
        } else {
            // unknown QR code format
            throw new QrException("Please scan your QR code again.");
        }
        return mpManager.refresh();
    }

    private void onProcessing(int titleResource) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setTitle(titleResource);
        progressDialog.show();
    }

    private void onDoneProcessing() {
        if (progressDialog != null) {
            logger.info("Closing progress window");
            progressDialog.cancel();
            progressDialog = null;
        }
    }

    @Override
    public void onNetworkConnectionChanged(boolean isConnected, boolean isWifiOrEthernet) {
        logger.info("Network change: {}", isConnected);
        if (isConnected) {
            scanButton.setEnabled(true);
            messageBox.setText("");
        } else {
            scanButton.setEnabled(false);
            messageBox.setText(R.string.no_connection);
        }
    }

    @NonNull
    @Override
    protected Class<? extends Activity> nextActivity() {
        return DetailMainActivity.class;
    }

    public void scan(View view) {
        if (canLogin) {
            canLogin = false;
            this.qrManager.start();
        }
    }

    @Override
    public void loginFailed(LoginManager manager, final Exception ex) {
        canLogin = true;
        onDoneProcessing();
        logger.error("Failed to log in with {}", manager, ex);
        runOnUiThread(() -> {
            int res;
            if (ex instanceof QrException) {
                res = R.string.login_failed_qr;
            } else if (ex instanceof AuthenticationException) {
                res = R.string.login_failed_authentication;
            } else if (ex instanceof FirebaseRemoteConfigException) {
                res = R.string.login_failed_firebase;
            } else if (ex instanceof ConnectException) {
                res = R.string.login_failed_connection;
            } else if (ex instanceof IOException) {
                res = R.string.login_failed_mp;
            } else {
                res = R.string.login_failed;
            }
            Boast.makeText(RadarLoginActivity.this, res, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void loginSucceeded(LoginManager manager, @NonNull AppAuthState state) {
        didLogin = true;
        onDoneProcessing();
        if (!state.isPrivacyPolicyAccepted()) {
            logger.info("Login succeeded. Calling privacy-policy fragment");
            startPrivacyPolicyFragment(state);
        } else {
            FirebaseAnalytics firebase = FirebaseAnalytics.getInstance(this);
            firebase.setUserProperty(USER_ID_KEY, state.getUserId());
            firebase.setUserProperty(PROJECT_ID_KEY, state.getProjectId());
            super.loginSucceeded(manager, state);
            if (!didCreate) {
                overridePendingTransition(0, 0);
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void startPrivacyPolicyFragment(AppAuthState state) {
        logger.info("Starting privacy policy fragment");

        try {
            PrivacyPolicyFragment fragment = PrivacyPolicyFragment.newInstance(state);
            createFragmentLayout(R.id.privacy_policy_fragment, fragment);
        } catch (IllegalStateException ex) {
            logger.error("Failed to start privacy policy fragment:" +
                    " is LoginActivity is already closed?", ex);
            Crashlytics.logException(ex);
        }
    }

    private void createFragmentLayout(int id, Fragment fragment) {
        FrameLayout fragmentLayout = new FrameLayout(this);
        fragmentLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fragmentLayout.setId(id);
        setContentView(fragmentLayout);
        FragmentTransaction transaction = getFragmentManager().beginTransaction().add(id, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void onAcceptPrivacyPolicy(AppAuthState state) {
        // set privacyPolicyAccepted to true.
        final AppAuthState updated = state.newBuilder()
                .privacyPolicyAccepted(true)
                .build();
        logger.info("Updating privacyPolicyAccepted {}" , updated.toString());
        super.loginSucceeded(null, updated);
    }
}
