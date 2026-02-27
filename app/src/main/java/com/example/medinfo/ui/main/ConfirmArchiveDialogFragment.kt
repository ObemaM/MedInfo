package com.example.medinfo.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.medinfo.R
import com.example.medinfo.databinding.DialogConfirmArchiveBinding

class ConfirmArchiveDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogConfirmArchiveBinding.inflate(requireActivity().layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        // Прозрачный фон диалога
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Кнопки принять и отказать
        binding.buttonConfirm.setOnClickListener {
            dismiss()
        }

        binding.buttonCancel.setOnClickListener {
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
