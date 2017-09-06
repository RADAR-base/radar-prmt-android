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
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import org.radarcns.android.auth.AppAuthState;
import org.radarcns.android.auth.LoginManager;

import java.util.Arrays;
import java.util.List;

public class LoginActivity extends org.radarcns.android.auth.LoginActivity {
    private TextView passwordView;
    private TextView loginView;
    private LoginManager localLoginManager;

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        super.onCreate(savedBundleInstance);
        setContentView(R.layout.activity_login);
        loginView = (EditText) findViewById(R.id.inputUserId);
        passwordView = (EditText) findViewById(R.id.inputPassword);
    }

    public void login(View view) {
        localLoginManager.start();
    }

    @NonNull
    @Override
    protected List<LoginManager> createLoginManagers(AppAuthState appAuthState) {
        localLoginManager = new LoginManager() {
            @Override
            public AppAuthState refresh() {
                return null;
            }

            @Override
            public void start() {
                if (passwordView.getText().toString().equals("radarcns")) {
                    loginSucceeded(this, new AppAuthState(loginView.getText().toString(), null, null, 3, Long.MAX_VALUE, null));
                } else {
                    loginFailed(this, null);
                }
            }

            @Override
            public void onActivityCreate() {

            }

            @Override
            public void onActivityResult(int i, int i1, Intent intent) {

            }
        };
        return Arrays.asList(localLoginManager);
    }

    @NonNull
    @Override
    protected Class<? extends Activity> nextActivity() {
        return DetailMainActivity.class;
    }
}
