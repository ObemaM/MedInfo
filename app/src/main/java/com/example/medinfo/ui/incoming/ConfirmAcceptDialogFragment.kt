package com.example.medinfo.ui.incoming

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.medinfo.databinding.DialogConfirmAcceptBinding

class ConfirmAcceptDialogFragment(private val onConfirm: (Boolean) -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogConfirmAcceptBinding.inflate(requireActivity().layoutInflater)

        // Создаем диалоговое окно
        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.buttonAcceptConfirm.setOnClickListener {
            onConfirm(true)
            dismiss() // Закрывает диалог
        }

        binding.buttonAcceptCancel.setOnClickListener {
            dismiss() // Закрывает диалог
        }

        return dialog
    }
}
