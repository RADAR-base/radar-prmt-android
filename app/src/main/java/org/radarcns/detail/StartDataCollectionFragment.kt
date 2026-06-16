package org.radarcns.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.radarcns.detail.databinding.FragmentStartDataCollectionBinding
import org.slf4j.LoggerFactory

/**
 * Shown once during onboarding, right after the privacy policy is accepted. Data collection only
 * begins when the participant taps start here, which lets the main screen open. Nothing is persisted
 * by this fragment, so already onboarded participants who go straight to the main screen never see
 * it again.
 */
class StartDataCollectionFragment : Fragment() {
    private var listener: OnStartDataCollectionListener? = null
    private var binding: FragmentStartDataCollectionBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentStartDataCollectionBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.startDataCollectionButton?.setOnClickListener {
            logger.info("Participant tapped start data collection")
            listener?.onStartDataCollection()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? OnStartDataCollectionListener
            ?: throw RuntimeException("$context must implement OnStartDataCollectionListener")
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * Implemented by the hosting activity so the start action can move the onboarding flow on to the
     * main screen.
     */
    interface OnStartDataCollectionListener {
        fun onStartDataCollection()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StartDataCollectionFragment::class.java)
    }
}
