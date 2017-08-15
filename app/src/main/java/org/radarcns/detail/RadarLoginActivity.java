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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;

import org.radarcns.android.auth.AppAuthState;
import org.radarcns.android.auth.LoginActivity;
import org.radarcns.android.auth.LoginManager;
import org.radarcns.android.auth.OAuth2LoginManager;

import java.util.Collections;
import java.util.List;

public class RadarLoginActivity extends LoginActivity {
    private OAuth2LoginManager oauthManager;

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        super.onCreate(savedBundleInstance);
        setContentView(R.layout.activity_login);
    }

    @NonNull
    @Override
    protected List<LoginManager> createLoginManagers(AppAuthState state) {
        this.oauthManager = new OAuth2LoginManager(this, "user_name", state);
        return Collections.<LoginManager>singletonList(this.oauthManager);
    }

    @NonNull
    @Override
    protected Class<? extends Activity> nextActivity() {
        return DetailMainActivity.class;
    }

    public void login(View view) {
        this.oauthManager.start();
    }
}
