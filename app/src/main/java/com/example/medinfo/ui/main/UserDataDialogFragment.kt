package com.example.medinfo.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.medinfo.R
import com.example.medinfo.databinding.DialogUserDataBinding

class UserDataDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogUserDataBinding.inflate(requireActivity().layoutInflater)

        val login = requireArguments().getString(ARG_LOGIN)
        binding.userLoginText.text = login

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.buttonOk.setOnClickListener {
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
