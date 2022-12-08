package org.radarcns.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import org.radarbase.android.RadarApplication.Companion.radarConfig
import org.radarbase.android.RadarConfiguration.Companion.PROJECT_ID_KEY
import org.radarbase.android.RadarConfiguration.Companion.USER_ID_KEY
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthService.Companion.BASE_URL_PROPERTY
import org.radarbase.android.auth.AuthService.Companion.PRIVACY_POLICY_URL_PROPERTY
import org.radarcns.detail.InfoActivity.Companion.PRIVACY_POLICY
import org.radarcns.detail.databinding.FragmentPrivacyPolicyBinding
import org.slf4j.LoggerFactory

class PrivacyPolicyFragment : Fragment() {
    private var mListener: OnFragmentInteractionListener? = null
    private var privacyPolicyUrl: String? = null
    private var projectId: String? = null
    private var userId: String? = null
    private var baseUrl: String? = null
    private var binding: FragmentPrivacyPolicyBinding? = null

    private var dataCollectionUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = checkNotNull(arguments) { "Cannot start without AppAuthState arguments" }

        projectId = args.getString(PROJECT_ID_KEY)
        userId = args.getString(USER_ID_KEY)
        baseUrl = args.getString(BASE_URL_PROPERTY)
        privacyPolicyUrl = args.getString(PRIVACY_POLICY_URL_PROPERTY)
        dataCollectionUrl = args.getString(PRIVACY_POLICY)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentPrivacyPolicyBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.apply {
            dataCollectionDescriptionStatement.setOnClickListener { openUrl(updatePrivacyStatement()) }

            generalPrivacyPolicyStatement.apply {
                visibility = if (privacyPolicyUrl != null) {
                    setOnClickListener { openUrl(privacyPolicyUrl) }
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
            }

            acceptPrivacyPolicyButton.setOnClickListener { acceptPrivacyPolicy() }
            rejectPrivacyPolicyButton.setOnClickListener { rejectPrivacyPolicy() }

            inputProjectId.text = projectId
            inputUserId.text = userId

            val baseUrl = baseUrl ?: "Unknown server"
            consentPrivacyPolicy.apply {
                text = fromHtml(R.string.consent_privacy_policy, Html.FROM_HTML_MODE_LEGACY)
                setOnCheckedChangeListener { _, isChecked ->
                    acceptPrivacyPolicyButton.isEnabled = consentCollectedData.isChecked && consentServer.isChecked && isChecked
                }
            }
            consentCollectedData.apply{
                text = fromHtml(R.string.consent_collected_data, Html.FROM_HTML_MODE_LEGACY)
                setOnCheckedChangeListener { _, isChecked ->
                    acceptPrivacyPolicyButton.isEnabled = consentPrivacyPolicy.isChecked && consentServer.isChecked && isChecked
                }
            }
            consentServer.apply{
                text = fromHtml(R.string.consent_server, baseUrl, Html.FROM_HTML_MODE_LEGACY)
                setOnCheckedChangeListener { _, isChecked ->
                    acceptPrivacyPolicyButton.isEnabled = consentPrivacyPolicy.isChecked && consentCollectedData.isChecked && isChecked
                }
            }
            inputDestinationUrl.text = baseUrl
        }
    }


    @Suppress("DEPRECATION")
    private fun fromHtml(@StringRes resourceId: Int, vararg parameters: Any): Spanned {
        val s = getString(resourceId, *parameters)
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    private fun updatePrivacyStatement(): String? {
        val url = dataCollectionUrl
        logger.info("Setting privacy policy {}", url)

        binding?.dataCollectionDescriptionStatement?.apply {
            if (url != null) {
                visibility = View.VISIBLE
                setText(R.string.collected_data_description)
            } else {
                visibility = View.INVISIBLE
            }
        }
        return url
    }


    private fun openUrl(url: String?) {
        url?.also {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
        }
    }

    private fun acceptPrivacyPolicy() {
        logger.info("Policy accepted. Redirecting to DetailedMainView...")
        mListener?.onAcceptPrivacyPolicy()
    }

    private fun rejectPrivacyPolicy() {
        logger.info("Policy rejected. Redirecting to LoginActivity...")
        mListener?.onRejectPrivacyPolicy()
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments](http://developer.android.com/training/basics/fragments/communicating.html) for more information.
     */
    interface OnFragmentInteractionListener {
        fun onAcceptPrivacyPolicy()
        fun onRejectPrivacyPolicy()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PrivacyPolicyFragment::class.java)

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment PrivacyPolicyFragment.
         */
        fun newInstance(context: Context, state: AppAuthState): PrivacyPolicyFragment {
            return PrivacyPolicyFragment().apply {
                arguments = Bundle().apply {
                    putString(PROJECT_ID_KEY, state.projectId)
                    putString(USER_ID_KEY, state.userId)
                    putString(BASE_URL_PROPERTY, state.getAttribute(BASE_URL_PROPERTY))
                    putString(PRIVACY_POLICY_URL_PROPERTY, state.getAttribute(PRIVACY_POLICY_URL_PROPERTY))
                    putString(PRIVACY_POLICY, context.radarConfig.latestConfig.optString(PRIVACY_POLICY))
                }
            }
        }
    }
}
