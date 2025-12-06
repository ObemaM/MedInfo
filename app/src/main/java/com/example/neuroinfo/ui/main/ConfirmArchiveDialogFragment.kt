package com.example.neuroinfo.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
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

        // Get the item ID from the arguments passed from MainActivity
        val itemId = requireArguments().getInt("ITEM_ID", -1)

        confirmButton.setOnClickListener {
            // Send a signal back to MainActivity, including the item ID
            setFragmentResult(REQUEST_KEY, Bundle().apply {
                putBoolean(KEY_CONFIRMED, true)
                putInt("ITEM_ID", itemId)
            })
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
