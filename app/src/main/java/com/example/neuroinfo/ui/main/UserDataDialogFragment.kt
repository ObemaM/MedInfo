package com.example.neuroinfo.ui.main

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.neuroinfo.R
import com.google.android.material.button.MaterialButton

class UserDataDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_user_data, null)

        val login = requireArguments().getString(ARG_LOGIN)

        val loginTextView = view.findViewById<TextView>(R.id.user_login_text)
        loginTextView.text = login

        val okButton = view.findViewById<MaterialButton>(R.id.button_ok)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        okButton.setOnClickListener {
            dismiss()
        }

        return dialog
    }

    companion object {
        const val TAG = "UserDataDialog"
        private const val ARG_LOGIN = "user_login"

        fun newInstance(login: String): UserDataDialogFragment {
            val fragment = UserDataDialogFragment()
            val args = Bundle()
            args.putString(ARG_LOGIN, login)
            fragment.arguments = args
            return fragment
        }
    }
}
