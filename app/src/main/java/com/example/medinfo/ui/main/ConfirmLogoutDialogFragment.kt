package com.example.medinfo.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.medinfo.R
import com.google.android.material.button.MaterialButton

class ConfirmLogoutDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_confirm_logout, null)

        val confirmButton = view.findViewById<MaterialButton>(R.id.button_logout_confirm)
        val cancelButton = view.findViewById<MaterialButton>(R.id.button_logout_cancel)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        // Make the background of the dialog transparent
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        confirmButton.setOnClickListener {
            // Send a signal back to MainActivity that logout was confirmed
            setFragmentResult(REQUEST_KEY, Bundle().apply { putBoolean(KEY_CONFIRMED_LOGOUT, true) })
            dismiss()
        }

        cancelButton.setOnClickListener {
            // Just close the dialog
            dismiss()
        }

        return dialog
    }

    companion object {
        const val TAG = "ConfirmLogoutDialog"
        const val REQUEST_KEY = "request_confirm_logout"
        const val KEY_CONFIRMED_LOGOUT = "confirmed_logout"
    }
}
