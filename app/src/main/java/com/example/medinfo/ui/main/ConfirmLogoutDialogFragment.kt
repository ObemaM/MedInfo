package com.example.medinfo.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.medinfo.R
import com.example.medinfo.databinding.DialogConfirmLogoutBinding

class ConfirmLogoutDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogConfirmLogoutBinding.inflate(requireActivity().layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        // Делаем фон диалога прозрачным
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.buttonLogoutConfirm.setOnClickListener {
            // Отправляем сигнал в MainActivity, что выход подтвержден
            setFragmentResult(REQUEST_KEY, Bundle().apply { putBoolean(KEY_CONFIRMED_LOGOUT, true) })
            dismiss()
        }

        binding.buttonLogoutCancel.setOnClickListener {
            // Просто закрываем диалог
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
