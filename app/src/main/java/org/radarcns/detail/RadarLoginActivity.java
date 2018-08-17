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
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static org.radarcns.android.RadarConfiguration.PROJECT_ID_KEY;
import static org.radarcns.android.RadarConfiguration.RADAR_CONFIGURATION_CHANGED;
import static org.radarcns.android.RadarConfiguration.USER_ID_KEY;
import static org.radarcns.detail.DetailInfoActivity.PRIVACY_POLICY;

public class RadarLoginActivity extends LoginActivity implements NetworkConnectedReceiver.NetworkConnectedListener {
    private static final Logger logger = LoggerFactory.getLogger(RadarLoginActivity.class);

    private QrLoginManager qrManager;
    private ManagementPortalLoginManager mpManager;
    private boolean canLogin;
    private ProgressDialog progressDialog;
    private TextView messageBox;
    private Button scanButton;
    private NetworkConnectedReceiver networkReceiver;
    private String policyUrl = null;
    private TextView policyLink;
    private List<String> VALID_PROTOCOLS = Arrays.asList("http", "https");

    private final BroadcastReceiver configBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onDoneProcessing();
            if (BuildConfig.DEBUG && RadarConfiguration.getInstance().has("mp_refresh_token")) {
                mpManager.setRefreshToken(RadarConfiguration.getInstance().getString("mp_refresh_token"));
            }
            mpManager.refresh();
            updatePrivacyStatement(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        super.onCreate(savedBundleInstance);
        setContentView(R.layout.activity_login);
        messageBox = findViewById(R.id.messageText);
        scanButton = findViewById(R.id.scanButton);
        networkReceiver = new NetworkConnectedReceiver(this, this);
        policyLink = findViewById(R.id.loginPrivacyStatement);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (RadarConfiguration.getInstance().getStatus() == RadarConfiguration.FirebaseStatus.READY) {
            onProcessing(R.string.retrieving_configuration);
        } else if (BuildConfig.DEBUG && RadarConfiguration.getInstance().has("mp_refresh_token")) {
            mpManager.setRefreshToken(RadarConfiguration.getInstance().getString("mp_refresh_token"));
        }
        updatePrivacyStatement(false);
        canLogin = true;
        registerReceiver(configBroadcastReceiver, new IntentFilter(RADAR_CONFIGURATION_CHANGED));
        networkReceiver.register();
    }

    private void updatePrivacyStatement(boolean runInUiThread) {
        final String newPolicyUrl = RadarConfiguration.getInstance().getString(PRIVACY_POLICY, null);
        synchronized (this) {
            policyUrl = newPolicyUrl;
        }
        logger.info("Setting privacy policy {}", newPolicyUrl);
        if (runInUiThread) {
            runOnUiThread(() -> {
                if (newPolicyUrl == null) {
                    policyLink.setVisibility(View.INVISIBLE);
                } else {
                    policyLink.setVisibility(View.VISIBLE);
                }
            });
        } else {
            if (newPolicyUrl == null) {
                policyLink.setVisibility(View.INVISIBLE);
            } else {
                policyLink.setVisibility(View.VISIBLE);
            }
        }
    }

    public void openPrivacyPolicy(View view) {
        String localPolicyUrl;
        synchronized (this) {
            localPolicyUrl = policyUrl;
        }
        if (localPolicyUrl != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(localPolicyUrl)));
        }
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
        this.qrManager = new QrLoginManager(this, s -> {
            onProcessing(R.string.logging_in);
            logger.info("Read tokenUrl: {}", s);

            if (s.isEmpty()) {
                throw new QrException("Please scan the correct QR code.");
            }

            try {
                // validate scanned url
                URL tokenUrl = URI.create(s).toURL();
                if (VALID_PROTOCOLS.contains(tokenUrl.getProtocol())) {
                    mpManager.setTokenFromUrl(s);
                    return mpManager.refresh();
                } else {
                    throw new QrException("Unsupported protocol");
                }

            } catch (MalformedURLException e) {
                throw new QrException("Please scan your QR code again.", e);
            }
        });
        return Arrays.asList(this.qrManager, this.mpManager);
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
        onDoneProcessing();
        FirebaseAnalytics firebase = FirebaseAnalytics.getInstance(this);
        firebase.setUserProperty(USER_ID_KEY, state.getUserId());
        firebase.setUserProperty(PROJECT_ID_KEY, state.getProjectId());
        super.loginSucceeded(manager, state);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
