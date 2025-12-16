package com.example.neuroinfo.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.neuroinfo.R
import com.google.android.material.button.MaterialButton

class ConfirmArchiveDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_confirm_archive, null)

        val confirmButton = view.findViewById<MaterialButton>(R.id.button_confirm)
        val cancelButton = view.findViewById<MaterialButton>(R.id.button_cancel)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        // Make the background of the dialog transparent
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        confirmButton.setOnClickListener {
            dismiss()
        }

        cancelButton.setOnClickListener {
            // Just close the dialog
            dismiss()
        }

        return dialog
    }

    companion object {
        const val TAG = "ConfirmArchiveDialog"
        const val REQUEST_KEY = "request_confirm_archive"
        const val KEY_CONFIRMED = "confirmed"
    }
}
