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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import org.radarcns.android.auth.AppAuthState;
import org.radarcns.android.auth.LoginActivity;
import org.radarcns.android.auth.LoginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class RadarLoginActivity extends LoginActivity {
    private LoginManager trivialLoginManager;

    private final Logger logger = LoggerFactory.getLogger(RadarLoginActivity.class);

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        super.onCreate(savedBundleInstance);
        setContentView(R.layout.activity_login);
        String userId = getAuthState().getUserId();
        if (userId != null) {
            TextView userIdText = (TextView) findViewById(R.id.inputUserId);
            userIdText.setText(userId);
        }
    }

    @NonNull
    @Override
    protected List<LoginManager> createLoginManagers(final AppAuthState state) {
        this.trivialLoginManager = new LoginManager() {
            private final AppAuthState authState = state;

            @Override
            public AppAuthState refresh() {
                if (authState.isValid()) {
                    return authState;
                }
                return null;
            }

            @Override
            public void start() {
                TextView userIdText = (TextView) findViewById(R.id.inputUserId);
                String userId = userIdText.getText().toString();
                if (!userId.isEmpty()) {
                    SharedPreferences preferences = RadarLoginActivity.this.getSharedPreferences("main", Context.MODE_PRIVATE);
                    preferences.edit().putString("userId", userId).apply();
                    loginSucceeded(this, new AppAuthState.Builder().projectId("0").userId(userId).expiration(Long.MAX_VALUE).build());
                } else {
                    loginFailed(this, null);
                }
            }

            @Override
            public void onActivityCreate() {
                // noop
            }

            @Override
            public void onActivityResult(int requestCode, int resultCode, Intent data) {
                // noop
            }
        };
        return Arrays.asList(trivialLoginManager);
    }

    @NonNull
    @Override
    protected Class<? extends Activity> nextActivity() {
        return DetailMainActivity.class;
    }

    public void scan(View view) {
        logger.warn("Scan functionality not available!");
    }

    public void login(View view) {
        this.trivialLoginManager.start();
    }
}
