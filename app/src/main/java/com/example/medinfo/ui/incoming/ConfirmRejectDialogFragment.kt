package com.example.medinfo.ui.incoming

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.medinfo.R
import com.example.medinfo.databinding.DialogConfirmRejectBinding

class ConfirmRejectDialogFragment(private val onConfirm: (Boolean) -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogConfirmRejectBinding.inflate(requireActivity().layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.buttonRejectConfirm.setOnClickListener {
            onConfirm(true)
            dismiss()
        }

        binding.buttonRejectCancel.setOnClickListener {
            dismiss()
        }

        return dialog
    }
}
