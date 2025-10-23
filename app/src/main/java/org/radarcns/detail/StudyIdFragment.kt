package org.radarcns.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.radarcns.detail.databinding.FragmentStudyIdBinding
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class StudyIdFragment : Fragment() {
    private var listener: FragmentInteractionListener? = null
    private var binding: FragmentStudyIdBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentStudyIdBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.apply {
            okButton.setOnClickListener {
                val enteredId = studyIdInput.editText?.text.toString()
                if (enteredId.isBlank() || enteredId == "null") {
                    logger.error("Study ID cannot be null or blank")
                    Toast.makeText(context, R.string.blank_study_id, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                callListenerValidated {
                    it.onStudyIdEntered(enteredId.trim())
                }
            }

            cancelButton.setOnClickListener {
                callListenerValidated {
                    it.onStudyIdCancelled()
                }
            }
        }
    }

    fun callListenerValidated(callback: (FragmentInteractionListener) -> Unit) {
        val listener = listener ?: run {
            logger.warn("Listener is null, cannot perform callback")
            return
        }

        logger.debug("Calling listener")
        callback(listener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement FragmentInteractionListener")
        }
    }

    interface FragmentInteractionListener {
        fun onStudyIdEntered(studyId: String)
        fun onStudyIdCancelled()
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StudyIdFragment::class.java)
    }
}