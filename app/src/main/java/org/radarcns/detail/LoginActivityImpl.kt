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

package org.radarcns.detail

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.LinearLayout.VERTICAL
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONException
import org.json.JSONObject
import org.radarbase.android.RadarApplication.Companion.radarApp
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration.Companion.BASE_URL_KEY
import org.radarbase.android.auth.*
import org.radarbase.android.auth.portal.ManagementPortalLoginManager
import org.radarbase.android.util.Boast
import org.radarbase.android.util.NetworkConnectedReceiver
import org.radarbase.android.util.takeTrimmedIfNotEmpty
import org.radarbase.android.widget.TextDrawable
import org.radarbase.producer.AuthenticationException
import org.radarcns.detail.SplashActivityImpl.Companion.addPrivacyPolicy
import org.radarcns.detail.databinding.ActivityLoginBinding
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.URI

class LoginActivityImpl : LoginActivity(), NetworkConnectedReceiver.NetworkConnectedListener, PrivacyPolicyFragment.OnFragmentInteractionListener {
    private var didModifyBaseUrl: Boolean = false
    private var canLogin: Boolean = false
    private var progressDialog: ProgressDialog? = null
    private lateinit var networkReceiver: NetworkConnectedReceiver
    private var didCreate: Boolean = false

    private lateinit var binding: ActivityLoginBinding

    private lateinit var qrCodeScanner: QrCodeScanner

    override fun onCreate(savedInstanceBundle: Bundle?) {
        didCreate = false
        didModifyBaseUrl = false
        super.onCreate(savedInstanceBundle)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkReceiver = NetworkConnectedReceiver(this, this)
        didCreate = true
        qrCodeScanner = QrCodeScanner(this) { value ->
            value?.takeTrimmedIfNotEmpty()
                ?.also { parseQrCode(it) }
        }

        addPrivacyPolicy(binding.loginPrivacyPolicyUrl)
    }

    override fun onResume() {
        super.onResume()
        canLogin = true
        networkReceiver.run { register() }
    }

    override fun onPause() {
        super.onPause()
        networkReceiver.run { unregister() }
    }

    private fun parseQrCode(qrCode: String) {
        onProcessing(R.string.logging_in)
        logger.info("Read tokenUrl: {}", qrCode)

        if (qrCode.isEmpty()) {
            loginFailed(null, QrException("Please scan the correct QR code."))
            return
        }

        applyMpManager { auth, mpManager, authState ->
            if (qrCode[0] == '{') {
                // parse as JSON with embedded refresh token
                try {
                    val refreshToken = JSONObject(qrCode).get("refreshToken").toString()
                    mpManager.setRefreshToken(authState, refreshToken)
                } catch (e: JSONException) {
                    loginFailed(null, QrException("Failed to parse JSON refresh token", e))
                    return@applyMpManager
                }
            } else if (qrCode.startsWith("http://") || qrCode.startsWith("https://")) {
                // parse as URL containing refresh token
                try {
                    // validate scanned url
                    mpManager.setTokenFromUrl(authState, URI.create(qrCode).toURL().toString())
                } catch (e: MalformedURLException) {
                    loginFailed(null, QrException("Please scan your QR code again.", e))
                    return@applyMpManager
                } catch (e: IllegalArgumentException) {
                    loginFailed(null, QrException("Please scan your QR code again.", e))
                    return@applyMpManager
                }
            } else {
                // unknown QR code format
                loginFailed(null, QrException("Please scan your QR code again."))
                return@applyMpManager
            }
            auth.update(mpManager)
        }
    }

    private fun applyMpManager(callback: (AuthService.AuthServiceBinder, ManagementPortalLoginManager, AppAuthState) -> Unit) {
        authConnection.applyBinder {
            val manager = managers.find { it is ManagementPortalLoginManager }
                    as? ManagementPortalLoginManager ?: return@applyBinder
            applyState {
                callback(this@applyBinder, manager, this)
            }
        }
    }

    private fun onProcessing(titleResource: Int) {
        progressDialog = ProgressDialog(this).apply {
            isIndeterminate = true
            setTitle(titleResource)
            show()
        }
    }

    private fun onDoneProcessing() {
        progressDialog?.apply {
            logger.info("Closing progress window")
            cancel()
        }
        progressDialog = null
    }

    override fun onNetworkConnectionChanged(state: NetworkConnectedReceiver.NetworkState) {
        logger.info("Network change: {}", state.isConnected)
        runOnUiThread {
            binding.apply {
                if (state.isConnected) {
                    scanButton.isEnabled = true
                    messageText.text = ""
                } else {
                    scanButton.isEnabled = false
                    messageText.setText(R.string.no_connection)
                }
            }
        }
    }

    fun scan(@Suppress("UNUSED_PARAMETER") view: View) {
        if (canLogin) {
            canLogin = false
            qrCodeScanner.start()
        }
    }

    override fun loginFailed(manager: LoginManager?, ex: Exception?) {
        canLogin = true
        onDoneProcessing()
        logger.error("Failed to log in with {}", manager, ex)
        runOnUiThread {
            val res: Int = when (ex) {
                is QrException -> R.string.login_failed_qr
                is AuthenticationException -> R.string.login_failed_authentication
                is FirebaseRemoteConfigException -> R.string.login_failed_firebase
                is ConnectException -> R.string.login_failed_connection
                is IOException -> R.string.login_failed_mp
                else -> R.string.login_failed
            }
            Boast.makeText(this@LoginActivityImpl, res, Toast.LENGTH_LONG).show()
        }
    }

    override fun loginSucceeded(manager: LoginManager?, authState: AppAuthState) {
        onDoneProcessing()
        runOnUiThread {
            if (authState.isPrivacyPolicyAccepted) {
                super.loginSucceeded(manager, authState)
                if (!didCreate) {
                    overridePendingTransition(0, 0)
                }
            } else {
                if (supportFragmentManager.fragments.any { it.id == R.id.privacy_policy_fragment }) {
                    logger.info("Privacy policy fragment already started.")
                } else {
                    logger.info("Login succeeded. Calling privacy-policy fragment")
                    startPrivacyPolicyFragment(authState)
                }
            }
        }
    }

    private fun startPrivacyPolicyFragment(state: AppAuthState) {
        logger.info("Starting privacy policy fragment")

        try {
            val fragment = PrivacyPolicyFragment.newInstance(this, state)
            createFragmentLayout(R.id.privacy_policy_fragment, fragment)
        } catch (ex: IllegalStateException) {
            logger.error("Failed to start privacy policy fragment: is LoginActivity is already closed?", ex)
        }
    }

    private fun createFragmentLayout(id: Int, fragment: Fragment) {
        setContentView(FrameLayout(this).apply {
            this.id = id
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        supportFragmentManager.beginTransaction().add(id, fragment).apply {
            addToBackStack(null)
        }.commit()
    }

    override fun onAcceptPrivacyPolicy() {
        authConnection.applyBinder {
            updateState {
                isPrivacyPolicyAccepted = true
            }
            logger.debug("Enabling Firebase Analytics")
            FirebaseAnalytics.getInstance(this@LoginActivityImpl).setAnalyticsCollectionEnabled(true)
            applyState {
                logger.info("Updating privacyPolicyAccepted {}", this)
                super.loginSucceeded(null, this)
            }
        }
    }

    fun enterCredentials(@Suppress("UNUSED_PARAMETER") view: View) {
        val baseUrlInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setCompoundDrawables(TextDrawable(this, "https://"), null, null, null)
            setOnKeyListener { _, _, e ->
                if (e.action == KeyEvent.ACTION_UP) {
                    didModifyBaseUrl = true
                }
                true
            }
        }

        val config = radarConfig.config.value
        if (!didModifyBaseUrl) {
            val baseUrl = config?.getString(BASE_URL_KEY, "")?.toHttpUrlOrNull()
            if (baseUrl != null) {
                val urlString = baseUrl.toString().substring(baseUrl.scheme.length + 3)
                baseUrlInput.setText(urlString)
            }
        }

        val tokenInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }

        // Layout containing label and input
        val layout = LinearLayout(this).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(70, 0, 70, 0)

            addView(TextView(this@LoginActivityImpl).apply {
                setText(R.string.enter_credentials_description)
            })
            addView(TextView(this@LoginActivityImpl).apply {
                setText(R.string.label_meta_token)
            })
            addView(tokenInput)
            addView(TextView(this@LoginActivityImpl).apply {
                setText(R.string.label_base_url)
            })
            addView(baseUrlInput)
        }

        AlertDialog.Builder(this).apply {
            setTitle(R.string.enter_credentials_title)
            setView(layout)
            setPositiveButton(R.string.ok) { dialog, _ ->
                if (canLogin) {
                    canLogin = false
                    applyMpManager { _, mpManager, authState ->
                        val baseUrl = baseUrlInput.text.toString()
                                .replace(baseUrlPrefixRegex, "")
                                .replace(baseUrlPostfixRegex, "")
                        val url = "https://$baseUrl/managementportal/api/meta-token/${tokenInput.text}"
                        try {
                            mpManager.setTokenFromUrl(authState, url)
                            dialog.dismiss()
                        } catch (ex: MalformedURLException) {
                            loginFailed(mpManager, IllegalArgumentException("Cannot parse URL $url"))
                        }
                    }
                }
            }
            setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
        }.show()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoginActivityImpl::class.java)

        private val baseUrlPrefixRegex = "^https?:?/?/?".toRegex()
        // Allow for typos in managementportal.
        private val baseUrlPostfixRegex = "/[mangetporl]+/?$".toRegex()
    }
}
