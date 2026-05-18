package com.example.medinfo.ui.config

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.medinfo.databinding.DialogRestartRequiredBinding

// Показывается после смены адреса сервера: новый адрес подхватят только заново созданные
// компоненты, поэтому предлагаем перезапуск. onClosed(true) — перезапустить, false — позже.
class RestartRequiredDialogFragment(
    private val onClosed: (restartNow: Boolean) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogRestartRequiredBinding.inflate(requireActivity().layoutInflater)

        // Пользователь обязан выбрать вариант — не закрываем по тапу мимо.
        isCancelable = false

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.buttonRestartNow.setOnClickListener {
            onClosed(true)
            dismiss()
        }
        binding.buttonRestartLater.setOnClickListener {
            onClosed(false)
            dismiss()
        }

        return dialog
    }
}
