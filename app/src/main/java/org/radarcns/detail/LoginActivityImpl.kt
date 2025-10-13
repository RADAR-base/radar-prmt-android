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

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONException
import org.json.JSONObject
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration.Companion.BASE_URL_KEY
import org.radarbase.android.auth.*
import org.radarbase.android.auth.AuthService.Companion.BASE_URL_PROPERTY
import org.radarbase.android.auth.oauth2.OAuth2LoginManager
import org.radarbase.android.auth.oauth2.OAuth2StateManager.Companion.MissingConfigurationException
import org.radarbase.android.auth.portal.ManagementPortalLoginManager
import org.radarbase.android.auth.sep.SEPLoginManager
import org.radarbase.android.util.Boast
import org.radarbase.android.util.NetworkConnectedReceiver
import org.radarbase.android.util.takeTrimmedIfNotEmpty
import org.radarbase.android.widget.addPrivacyPolicy
import org.radarbase.producer.AuthenticationException
import org.radarcns.detail.databinding.ActivityLoginBinding
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException

class LoginActivityImpl :
    LoginActivity(),
    NetworkConnectedReceiver.NetworkConnectedListener,
    PrivacyPolicyFragment.OnFragmentInteractionListener,
    StudyIdFragment.FragmentInteractionListener {
    private var startActivityFuture: Runnable? = null
    private var didModifyBaseUrl: Boolean = false
    private var canLogin: Boolean = false

    private lateinit var networkReceiver: NetworkConnectedReceiver
    private var networkIsConnected = false
    private var didCreate: Boolean = false
    private var authAttempt: Int = 0

    private lateinit var binding: ActivityLoginBinding

    private lateinit var qrCodeScanner: QrCodeScanner
    private lateinit var credentialsDialog: Dialog
    private var studyIdFragment: StudyIdFragment? = null

    private lateinit var mainHandler: Handler

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val resultIntent = result.data
        if (resultIntent != null) {
            intent = resultIntent
        }

        val authEx = AuthorizationException.fromIntent(resultIntent)
        if ((authEx != null) && authEx.code == USER_CANCELLED_OAUTH_FLOW) {
            loginFailed(null, OAuthFlowAbortException("Oauth flow cancelled by user"))
            startLoginActivity()
            return@registerForActivityResult
        }

        val authResp = resultIntent?.let(AuthorizationResponse::fromIntent)
        if (authResp != null && ++authAttempt == 1) {
            secondAuthAttempt()
            return@registerForActivityResult
        }

        authConnection.applyBinder {
            managers.find { it.onActivityCreate(this@LoginActivityImpl, this@applyBinder) }
                ?.let { this@applyBinder.update(it) }
        }
    }

    private fun secondAuthAttempt() {
        applyOAuth2Manager { binder, manager, state ->
            try {
                manager.start(state, activityResultLauncher)
            } catch (ex: MissingConfigurationException) {
                loginFailed(manager, ex)
                startLoginActivity()
            }
        }
    }

    override fun onCreate(savedInstanceBundle: Bundle?) {
        didCreate = false
        didModifyBaseUrl = false
        mainHandler = Handler(Looper.getMainLooper())
        super.onCreate(savedInstanceBundle)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkReceiver = NetworkConnectedReceiver(this, this)
        didCreate = true
        qrCodeScanner = QrCodeScanner(this) { value ->
            value?.takeTrimmedIfNotEmpty()
                ?.also { parseQrCode(it) }
        }

        with(binding) {
            scanButton.setOnClickListener { v -> scan(v) }
            enterCredentialsButton.setOnClickListener { v -> enterCredentials(v) }
            enterStudyIdButton.setOnClickListener { v -> startStudyIdFragment() }
            loader.visibility = View.GONE
        }
        checkNetworkConnection()

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
        onProcessing()
        logger.info("Read tokenUrl: {}", qrCode)

        if (qrCode.isEmpty()) {
            loginFailed(null, QrException("Please scan the correct QR code."))
            return
        }

        if (qrCode.contains(PRMT_CUSTOM_SCHEME)) {
            sepLoginFlow(qrCode)
        } else {
            mpLoginFlow(qrCode)
        }
    }

    private fun mpLoginFlow(qrCode: String) {
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

    private fun sepLoginFlow(qrCode: String) {
        applySepManager { binder, sepManager, authState ->
            sepManager.sepQrFlow(authState, qrCode)
            binder.update(sepManager)
        }
    }

    private fun oAuthFlow(baseUrl: String) {
        applyOAuth2Manager { binder, oAuthManager, authState ->
            val updatedAuth = authState.alter {
                attributes[BASE_URL_PROPERTY] = baseUrl
            }

            binder.updateState {
                attributes.putAll(updatedAuth.attributes)
            }

            radarConfig.updateWithAuthState(this, updatedAuth)
            logger.debug("AppAuthDebug: {}", updatedAuth)
            try {
                oAuthManager.start(updatedAuth, activityResultLauncher)
            } catch (ex: MissingConfigurationException) {
                loginFailed(oAuthManager, ex)
                startLoginActivity()
            }
        }
    }

    private fun applyMpManager(callback: (AuthService.AuthServiceBinder, ManagementPortalLoginManager, AppAuthState) -> Unit) {
        authConnection.applyBinder {
            val mpManager = managers.find { it is ManagementPortalLoginManager }
                    as? ManagementPortalLoginManager ?: return@applyBinder

            applyState {
                callback(this@applyBinder, mpManager, this)
            }
        }
    }

    private fun applySepManager(callback: (AuthService.AuthServiceBinder, SEPLoginManager, AppAuthState) -> Unit) {
        authConnection.applyBinder {
            val sepManager = managers.find { it is SEPLoginManager }
                    as? SEPLoginManager ?: return@applyBinder

            applyState {
                callback(this@applyBinder, sepManager, this)
            }
        }
    }

    private fun applyOAuth2Manager(callback: (AuthService.AuthServiceBinder, OAuth2LoginManager, AppAuthState) -> Unit) {
        authConnection.applyBinder {
            val oauthManager = managers.find { it is OAuth2LoginManager }
                    as? OAuth2LoginManager ?: return@applyBinder

            applyState {
                callback(this@applyBinder, oauthManager, this)
            }
        }
    }

    private fun onProcessing() {
        setLoader(true)
    }

    private fun onDoneProcessing() {
        setLoader(false)
        logger.info("Closing progress window")
    }

    override fun onNetworkConnectionChanged(state: NetworkConnectedReceiver.NetworkState) {
        logger.info("Network change: {}", state.isConnected)
        networkIsConnected = state.isConnected
        mainHandler.post {
            checkNetworkConnection()
        }
    }

    private fun checkNetworkConnection() = with(binding) {
        if (networkIsConnected) {
            scanButton.isEnabled = true
            enterCredentialsButton.isClickable = true
            enterStudyIdButton.isEnabled = true
            messageText.text = ""
        } else {
            scanButton.isEnabled = false
            enterCredentialsButton.isClickable = false
            enterStudyIdButton.isEnabled = false
            messageText.setText(R.string.no_connection)
        }
    }

    private fun scan(@Suppress("UNUSED_PARAMETER") view: View) {
        if (canLogin) {
            canLogin = false
            qrCodeScanner.start()
        }
    }

    override fun loginFailed(manager: LoginManager?, ex: Exception?) {
        canLogin = true

        logger.error("Failed to log in with {}", manager, ex)
        mainHandler.post {
            onDoneProcessing()
            val res: Int = when (ex) {
                is QrException -> R.string.login_failed_qr
                is AuthenticationException -> R.string.login_failed_authentication
                is InvalidStudyIdException -> R.string.invalid_study_id
                is FirebaseRemoteConfigException -> R.string.login_failed_firebase
                is ConnectException -> R.string.login_failed_connection
                is MissingConfigurationException -> R.string.missing_config_exception
                is OAuthFlowAbortException -> R.string.oauth_flow_aborted
                is IOException -> R.string.login_failed_mp
                else -> R.string.login_failed
            }
            Boast.makeText(this@LoginActivityImpl, res, Toast.LENGTH_LONG).show()
        }
    }

    override fun loginSucceeded(manager: LoginManager?, authState: AppAuthState) {
        mainHandler.post {
            startActivityFuture?.let {
                mainHandler.removeCallbacks(it)
                startActivityFuture = null
            }
            val runnable = Runnable {
                onDoneProcessing()
                if (supportFragmentManager.fragments.any { it.id == R.id.privacy_policy_fragment }) {
                    logger.info("Privacy policy fragment already started.")
                } else if (authState.isPrivacyPolicyAccepted) {
                    super.loginSucceeded(manager, authState)
                    if (!didCreate) {
                        overridePendingTransition(0, 0)
                    }
                } else {
                    logger.info("Login succeeded. Calling privacy-policy fragment")
                    startPrivacyPolicyFragment(authState)
                }
                startActivityFuture = null
            }
            startActivityFuture = runnable
            mainHandler.postDelayed(runnable, 100L)
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
        supportFragmentManager.commit {
            add(id, fragment)
        }
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

    override fun onRejectPrivacyPolicy() {
        authConnection.applyBinder {
            updateState {
                isPrivacyPolicyAccepted = false
            }
            invalidate(null, true)
        }
        startLoginActivity()
    }

    override fun onStudyIdEntered(studyId: String) {
        if (canLogin) {
            canLogin = false
            closeStudyIdFragment()
            try {
                val studyUrl = urlJsonFromRemoteConfig(studyId) ?: run {
                    logger.error("No study id with name {} is present. Aborting login.", studyId)
                    startLoginActivity()
                    return
                }

                val uri = URI(studyUrl)
                val defaultPort = when (uri.scheme.lowercase()) {
                    "http" -> 80
                    "https" -> 443
                    else -> -1
                }
                val sepBaseUrl = if (uri.port != -1 && uri.port != defaultPort) {
                    "${uri.scheme}://${uri.host}:${uri.port}"
                } else {
                    "${uri.scheme}://${uri.host}"
                }
                logger.debug("Starting oauth flow at {}", uri.toString())
                oAuthFlow(sepBaseUrl)
            } catch (ex: URISyntaxException) {
                logger.error("Invalid url scanned from portal")
                loginFailed(null, ex)
            }
        }
    }

    override fun onStudyIdCancelled() {
        logger.info("Study ID cancelled")
        startLoginActivity()
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivityImpl::class.java)
        finish()
        startActivity(intent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    private fun enterCredentials(@Suppress("UNUSED_PARAMETER") view: View) {
        credentialsDialog = Dialog(this)
        credentialsDialog.setContentView(R.layout.dialog_login_token)

        val baseUrlInput = credentialsDialog.findViewById<TextInputLayout>(R.id.baseUrl)
        radarConfig.config.observe(this) { config ->
            if (!didModifyBaseUrl) {
                val baseUrl = config.getString(BASE_URL_KEY, "").toHttpUrlOrNull() ?: return@observe
                var urlString = baseUrl.toString().substring(baseUrl.scheme.length + 3)
                if (urlString.endsWith("/")) {
                    urlString = urlString.substring(0, urlString.length - 1)
                }
                baseUrlInput.editText?.setText(urlString)
            }
        }

        val tokenInput = credentialsDialog.findViewById<TextInputLayout>(R.id.token)

        credentialsDialog.findViewById<Button>(R.id.ok_button).setOnClickListener {
            if (canLogin) {
                canLogin = false
                applyMpManager { _, mpManager, authState ->
                    var baseUrl = baseUrlInput.editText?.text.toString()
                        .replace(baseUrlPrefixRegex, "")
                        .replace(baseUrlPostfixRegex, "")
                    if (baseUrl.endsWith("/")) {
                        baseUrl = baseUrl.substring(0, baseUrl.length - 1)
                    }
                    val url = "https://$baseUrl/managementportal/api/meta-token/${tokenInput.editText?.text}"
                    try {
                        mpManager.setTokenFromUrl(authState, url)
                        credentialsDialog.dismiss()
                    } catch (ex: MalformedURLException) {
                        loginFailed(mpManager, IllegalArgumentException("Cannot parse URL $url"))
                    }
                }
            }
        }

        credentialsDialog.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            credentialsDialog.cancel()
        }
        credentialsDialog.show()
    }

    private fun urlJsonFromRemoteConfig(studyId: String): String? {
        try {
            val json = radarConfig.latestConfig.optString(STUDY_ID_CONSTANT)?.let(::JSONObject)
            return json?.optString(studyId).let { url ->
                if (url.isNullOrBlank()) {
                    throw InvalidStudyIdException(
                        "Study with id (${studyId}) doesn't exists"
                    )
                }
                url
            }
        } catch (e: JSONException) {
            loginFailed(null, e)
            return null
        } catch (ex: InvalidStudyIdException) {
            loginFailed(null, ex)
            return null
        }
    }

    private fun startStudyIdFragment() {
        logger.info("Starting study id fragment...")
        try {
            studyIdFragment = StudyIdFragment().also {
                createFragmentLayout(R.id.study_id_fragment, it)
            }
        } catch (ex: IllegalStateException) {
            logger.error(
                "Failed to start study id fragment -- is login activity already closed?",
                ex
            )
        }
    }

    private fun closeStudyIdFragment() {
        supportFragmentManager.commit {
            studyIdFragment?.let { remove(it) }
            studyIdFragment = null
        }
    }

    private fun setLoader(show: Boolean) = with(binding) {
        if (show) {
            scanButton.visibility = View.GONE
            enterCredentialsButton.visibility = View.GONE
            enterStudyIdButton.visibility = View.GONE
            loader.visibility = View.VISIBLE
        } else {
            scanButton.visibility = View.VISIBLE
            enterCredentialsButton.visibility = View.VISIBLE
            enterStudyIdButton.visibility = View.GONE
            loader.visibility = View.GONE
        }
    }

    class InvalidStudyIdException(info: String): RuntimeException(info)

    companion object {
        private val logger = LoggerFactory.getLogger(LoginActivityImpl::class.java)

        private const val PRMT_CUSTOM_SCHEME = "org.radarbase.prmt://"
        private const val STUDY_ID_CONSTANT = "study_id_url_map"
        private const val USER_CANCELLED_OAUTH_FLOW = 1
        private val baseUrlPrefixRegex = "^https?:?/?/?".toRegex()
        // Allow for typos in managementportal.
        private val baseUrlPostfixRegex = "/[mangetporl]+/?$".toRegex()

        class OAuthFlowAbortException(message: String): RuntimeException(message)
    }
}
