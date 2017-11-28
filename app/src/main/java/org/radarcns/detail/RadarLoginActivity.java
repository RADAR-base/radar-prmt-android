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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import org.json.JSONException;
import org.json.JSONObject;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.auth.AppAuthState;
import org.radarcns.android.auth.AuthStringParser;
import org.radarcns.android.auth.LoginActivity;
import org.radarcns.android.auth.LoginManager;
import org.radarcns.android.auth.ManagementPortalClient;
import org.radarcns.android.auth.ManagementPortalLoginManager;
import org.radarcns.android.auth.QrLoginManager;
import org.radarcns.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import static org.radarcns.android.RadarConfiguration.MANAGEMENT_PORTAL_URL_KEY;
import static org.radarcns.android.RadarConfiguration.RADAR_CONFIGURATION_CHANGED;
import static org.radarcns.android.RadarConfiguration.UNSAFE_KAFKA_CONNECTION;

public class RadarLoginActivity extends LoginActivity {
    private static final Logger logger = LoggerFactory.getLogger(RadarLoginActivity.class);

    private QrLoginManager qrManager;
    private ManagementPortalLoginManager mpManager;
    private boolean canLogin;
    private ProgressDialog progressDialog;
    private final BroadcastReceiver configBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            RadarConfiguration config = RadarConfiguration.getInstance();

            String mpString = config.getString(MANAGEMENT_PORTAL_URL_KEY, null);
            if (mpClient == null && mpString != null && !mpString.isEmpty()) {
                try {
                    managementPortal = new ServerConfig(mpString);
                    managementPortal.setUnsafe(config.getBoolean(UNSAFE_KAFKA_CONNECTION, false));
                    mpClient = new ManagementPortalClient(managementPortal);
                } catch (MalformedURLException e) {
                    logger.error("Cannot create ManagementPortal client from url {}",
                            mpString);
                }
            }
            if (mpClient != null && mpManager != null) {
                logger.info("Refreshing mp manager");
                mpManager.setManagementPortal(mpClient, config.getString(RadarConfiguration.OAUTH2_CLIENT_ID, "pRMT"),
                        config.getString(RadarConfiguration.OAUTH2_CLIENT_SECRET, null));
                mpManager.refresh();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        super.onCreate(savedBundleInstance);
        setContentView(R.layout.activity_login);
        registerReceiver(configBroadcastReceiver, new IntentFilter(RADAR_CONFIGURATION_CHANGED));
    }

    @Override
    protected void onResume() {
        super.onResume();
        canLogin = true;
    }

    @NonNull
    @Override
    protected List<LoginManager> createLoginManagers(AppAuthState state) {
        getAuthState().invalidate(this);
        RadarConfiguration config = RadarConfiguration.getInstance();
        logger.info("Creating mpManager");
        this.mpManager = new ManagementPortalLoginManager(this, state, mpClient,
                config.getString(RadarConfiguration.OAUTH2_CLIENT_ID, "pRMT"),
                config.getString(RadarConfiguration.OAUTH2_CLIENT_SECRET, null));
        this.qrManager = new QrLoginManager(this, new AuthStringParser() {
            @Override
            public AppAuthState parse(@NonNull String s) {
                onProcessing(R.string.logging_in);
                try {
                    JSONObject object = new JSONObject(s);
                    if (!object.has("refreshToken")) {
                        throw new IllegalArgumentException("No valid refresh token found");
                    }
                    String refreshToken = object.getString("refreshToken");
                    mpManager.setRefreshToken(refreshToken);
                    return mpManager.refresh();
                } catch (JSONException e) {
                    throw new IllegalArgumentException("QR code does not contain valid JSON.", e);
                }
            }
        });
        config.fetch();
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
            logger.info("Closed progress window");
            progressDialog = null;
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
    protected AppAuthState updateMpInfo(LoginManager manager, @NonNull AppAuthState state) throws IOException {
        AppAuthState newAuthState = super.updateMpInfo(manager, state);
        onDoneProcessing();
        return newAuthState;
    }

    @Override
    public void loginFailed(LoginManager manager, Exception ex) {
        onDoneProcessing();
        super.loginFailed(manager, ex);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(configBroadcastReceiver);
    }
}
