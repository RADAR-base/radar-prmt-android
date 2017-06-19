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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.radarcns.android.util.Boast;

public class LoginActivity extends Activity {
    public static final String EXTRA_USERNAME = "org.radarcns.android.userId";
    public static final String EXTRA_REMOVE_USERNAME = "org.radarcns.android.userId.remove";
    private TextView passwordView;
    private TextView loginView;

    @Override
    protected void onCreate(Bundle savedBundleInstance) {
        super.onCreate(savedBundleInstance);
        if (getIntent() != null && getIntent().hasExtra(Intent.EXTRA_DATA_REMOVED) &&
                getIntent().getStringExtra(Intent.EXTRA_DATA_REMOVED).equals("username")) {
            getPreferences(MODE_PRIVATE).edit().remove("username").apply();
        }
        startActivityWithStoredUsername();

        setContentView(R.layout.activity_login);
        loginView = (EditText) findViewById(R.id.inputUserId);
        passwordView = (EditText) findViewById(R.id.inputPassword);
    }

    public void login(View view) {
        if (passwordView.getText().toString().equals("radarcns")) {
            getPreferences(MODE_PRIVATE).edit().putString("username", loginView.getText().toString()).apply();
            startActivityWithStoredUsername();
        } else {
            Boast.makeText(this, "Invalid user/password combination").show();
        }
    }

    private void startActivityWithStoredUsername() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.contains("username")) {
            Intent intent = new Intent(this, DetailMainActivity.class);
            intent.putExtra(EXTRA_USERNAME, prefs.getString("username", "radarcns"));
            startActivity(intent);
            finish();
        }
    }
}
