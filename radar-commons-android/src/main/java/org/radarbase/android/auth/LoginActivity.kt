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

package org.radarbase.android.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.radarbase.android.R
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.util.Boast
import org.radarbase.android.util.send
import org.slf4j.LoggerFactory

/** Activity to log in using a variety of login managers.  */
@Keep
abstract class LoginActivity : AppCompatActivity(), LoginListener {
    private var startedFromActivity: Boolean = false
    private var refreshOnly: Boolean = false

    protected lateinit var authConnection: AuthServiceConnection

    override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)

        if (savedInstanceBundle != null) {
            refreshOnly = savedInstanceBundle.getBoolean(ACTION_REFRESH)
            startedFromActivity = savedInstanceBundle.getBoolean(ACTION_LOGIN)
        } else {
            val intent = intent
            val action = intent.action
            refreshOnly = action == ACTION_REFRESH
            startedFromActivity = action == ACTION_LOGIN
        }

        if (startedFromActivity) {
            Boast.makeText(this, R.string.login_failed, Toast.LENGTH_LONG).show()
        }

        authConnection = AuthServiceConnection(this, this)
        authConnection.onBoundListeners.add(0) { binder ->
            binder.isInLoginActivity = true
        }
        authConnection.onBoundListeners.add { binder ->
            binder.refresh()
            binder.managers
                    .find { it.onActivityCreate(this@LoginActivity)}
                    ?.let { binder.update(it) }
        }
        authConnection.onUnboundListeners.add { binder ->
            binder.isInLoginActivity = false
        }
        authConnection.bind()
    }

    override fun onDestroy() {
        super.onDestroy()
        authConnection.unbind()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(ACTION_REFRESH, refreshOnly)
        outState.putBoolean(ACTION_LOGIN, startedFromActivity)

        // call superclass to save any view hierarchy
        super.onSaveInstanceState(outState)
    }

    /** Call when part of the login procedure failed.  */
    override fun loginFailed(manager: LoginManager?, ex: Exception?) {
        logger.error("Failed to log in with {}", manager, ex)
        runOnUiThread {
            Boast.makeText(this@LoginActivity, R.string.login_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** Call when the entire login procedure succeeded.  */
    override fun loginSucceeded(manager: LoginManager?, authState: AppAuthState) {
        logger.info("Login succeeded")

        LocalBroadcastManager.getInstance(this).send(ACTION_LOGIN_SUCCESS)

        if (startedFromActivity) {
            logger.debug("Start next activity with result")
            setResult(RESULT_OK, intent)
        } else if (!refreshOnly) {
            logger.debug("Start next activity without result")
            startActivity(intent.apply {
                setClass(this@LoginActivity, radarApp.mainActivity)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
            })
        }
        finish()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoginActivity::class.java)
        const val ACTION_LOGIN = "org.radarcns.auth.LoginActivity.login"
        const val ACTION_REFRESH = "org.radarcns.auth.LoginActivity.refresh"
        const val ACTION_LOGIN_SUCCESS = "org.radarcns.auth.LoginActivity.success"
    }
}
