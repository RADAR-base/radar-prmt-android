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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import org.json.JSONException;
import org.json.JSONObject;
import org.radarcns.android.RadarConfiguration;
import org.radarcns.android.auth.*;
import org.radarcns.android.auth.oauth2.Jwt;
import org.radarcns.android.auth.oauth2.OAuth2LoginManager;

import java.util.Arrays;
import java.util.List;

import static org.radarcns.android.auth.LoginManager.AUTH_TYPE_BEARER;
import static org.radarcns.android.auth.oauth2.OAuth2LoginManager.LOGIN_REFRESH_TOKEN;

public class RadarLoginActivity extends LoginActivity {
    private OAuth2LoginManager oauthManager;
    private QrLoginManager qrManager;
    private boolean canLogin;

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        super.onCreate(savedBundleInstance);
        setContentView(R.layout.activity_login);
    }

    @Override
    protected void onResume() {
        super.onResume();
        canLogin = true;
    }

    @NonNull
    @Override
    protected List<LoginManager> createLoginManagers(AppAuthState state) {
        this.oauthManager = new OAuth2LoginManager(this, null, "sub", state);
        this.qrManager = new QrLoginManager(this, new AuthStringParser() {
            @Override
            public AppAuthState parse(@NonNull String s) {
                try {
                    JSONObject object = new JSONObject(s);
                    if (!object.has("refreshToken")) {
                        throw new IllegalArgumentException("No valid refresh token found");
                    }
                    String refreshToken = object.getString("refreshToken");
                    Jwt jwt = Jwt.parse(refreshToken);
                    JSONObject jwtBody = jwt.getBody();
                    oauthManager.update(new AppAuthState.Builder()
                            .tokenType(AUTH_TYPE_BEARER)
                            .property(LOGIN_REFRESH_TOKEN, refreshToken)
                            .userId(jwtBody.getString("sub"))
                            .expiration(jwtBody.getLong("exp") * 1_000L)
                            .build());
                    oauthManager.refresh();
                    return null;
                } catch (JSONException e) {
                    throw new IllegalArgumentException("QR code does not contain valid JSON.", e);
                }
            }
        });
        return Arrays.asList(this.qrManager, this.oauthManager);
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

    public void login(View view) {
        if (canLogin) {
            canLogin = false;
            final RadarConfiguration config = RadarConfiguration.getInstance();
            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setIndeterminate(true);
            progressDialog.setTitle(R.string.firebase_fetching);
            progressDialog.show();
            config.fetch().addOnCompleteListener(this, new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    progressDialog.cancel();
                    config.activateFetched();
                    oauthManager.start();
                }
            });
        }
    }
}
