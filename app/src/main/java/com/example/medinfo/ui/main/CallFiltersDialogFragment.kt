package com.example.medinfo.ui.main

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.example.medinfo.R
import com.example.medinfo.databinding.DialogCallFiltersBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CallFiltersDialogFragment : DialogFragment() {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    private var dateFromMillis: Long? = null
    private var dateToMillis: Long? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val binding = DialogCallFiltersBinding.inflate(inflater)

        val initialFilters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_FILTERS, CallFilters::class.java)
        } else {
            arguments?.getSerializable(ARG_FILTERS) as? CallFilters
        } ?: CallFilters()

        binding.patientFullNameEdit.setText(initialFilters.patientFullName.orEmpty())
        binding.urgencyFromEdit.setText(initialFilters.urgencyFrom?.toString().orEmpty())
        binding.urgencyToEdit.setText(initialFilters.urgencyTo?.toString().orEmpty())

        when (initialFilters.sex) {
            SexFilter.ANY -> binding.sexToggleGroup.check(R.id.sex_any_button)
            SexFilter.MALE -> binding.sexToggleGroup.check(R.id.sex_male_button)
            SexFilter.FEMALE -> binding.sexToggleGroup.check(R.id.sex_female_button)
        }

        binding.ageFromEdit.setText(initialFilters.ageFrom?.toString().orEmpty())
        binding.ageToEdit.setText(initialFilters.ageTo?.toString().orEmpty())

        dateFromMillis = initialFilters.dateFromMillis
        dateToMillis = initialFilters.dateToMillis

        binding.dateFromEdit.setText(dateFromMillis?.let { dateFormat.format(it) }.orEmpty())
        binding.dateToEdit.setText(dateToMillis?.let { dateFormat.format(it) }.orEmpty())

        binding.callDayFromEdit.setText(initialFilters.dayNumber?.toString().orEmpty())
        binding.callYearFromEdit.setText(initialFilters.yearNumber?.toString().orEmpty())

        binding.closeButton.setOnClickListener { dismiss() }

        binding.dateFromLayout.setEndIconOnClickListener {
            dateFromMillis = null
            binding.dateFromEdit.setText("")
        }

        binding.dateToLayout.setEndIconOnClickListener {
            dateToMillis = null
            binding.dateToEdit.setText("")
        }

        binding.dateFromEdit.setOnClickListener {
            pickDateTime(dateFromMillis) { millis ->
                dateFromMillis = millis
                binding.dateFromEdit.setText(dateFormat.format(millis))
            }
        }

        binding.dateToEdit.setOnClickListener {
            pickDateTime(dateToMillis) { millis ->
                dateToMillis = millis
                binding.dateToEdit.setText(dateFormat.format(millis))
            }
        }

        binding.resetFiltersButton.setOnClickListener {
            binding.patientFullNameEdit.setText("")
            binding.urgencyFromEdit.setText("")
            binding.urgencyToEdit.setText("")
            binding.sexToggleGroup.check(R.id.sex_any_button)
            binding.ageFromEdit.setText("")
            binding.ageToEdit.setText("")
            dateFromMillis = null
            dateToMillis = null
            binding.dateFromEdit.setText("")
            binding.dateToEdit.setText("")
            binding.callDayFromEdit.setText("")
            binding.callYearFromEdit.setText("")
        }

        binding.applyFiltersButton.setOnClickListener {
            val patientFullName = binding.patientFullNameEdit.text
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val urgencyFrom = binding.urgencyFromEdit.text?.toString()?.trim()?.toIntOrNull()
            val urgencyTo = binding.urgencyToEdit.text?.toString()?.trim()?.toIntOrNull()

            val ageFrom = binding.ageFromEdit.text?.toString()?.trim()?.toIntOrNull()
            val ageTo = binding.ageToEdit.text?.toString()?.trim()?.toIntOrNull()

            val sex =
                when (binding.sexToggleGroup.checkedButtonId) {
                    R.id.sex_male_button -> SexFilter.MALE
                    R.id.sex_female_button -> SexFilter.FEMALE
                    else -> SexFilter.ANY
                }

            val dayNumber = binding.callDayFromEdit.text?.toString()?.trim()?.toIntOrNull()
            val yearNumber = binding.callYearFromEdit.text?.toString()?.trim()?.toIntOrNull()

            var normUrgencyFrom = urgencyFrom
            var normUrgencyTo = urgencyTo
            if (normUrgencyFrom != null && normUrgencyTo != null && normUrgencyFrom > normUrgencyTo) {
                val tmp = normUrgencyFrom
                normUrgencyFrom = normUrgencyTo
                normUrgencyTo = tmp
            }

            var normAgeFrom = ageFrom
            var normAgeTo = ageTo
            if (normAgeFrom != null && normAgeTo != null && normAgeFrom > normAgeTo) {
                val tmp = normAgeFrom
                normAgeFrom = normAgeTo
                normAgeTo = tmp
            }

            var normDateFrom = dateFromMillis
            var normDateTo = dateToMillis
            if (normDateFrom != null && normDateTo != null && normDateFrom > normDateTo) {
                val tmp = normDateFrom
                normDateFrom = normDateTo
                normDateTo = tmp
            }

            val filters =
                CallFilters(
                    patientFullName = patientFullName,
                    urgencyFrom = normUrgencyFrom,
                    urgencyTo = normUrgencyTo,
                    sex = sex,
                    ageFrom = normAgeFrom,
                    ageTo = normAgeTo,
                    dateFromMillis = normDateFrom,
                    dateToMillis = normDateTo,
                    dayNumber = dayNumber,
                    yearNumber = yearNumber
                )

            setFragmentResult(REQUEST_KEY, bundleOf(KEY_FILTERS to filters))
            dismiss()
        }

        val dialog =
            AlertDialog.Builder(requireContext())
                .setView(binding.root)
                .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val margin = (8 * resources.displayMetrics.density).toInt()
        dialog?.window?.setLayout(
            resources.displayMetrics.widthPixels - margin * 2,
            (resources.displayMetrics.heightPixels * 0.86f).toInt()
        )
    }

    private fun pickDateTime(initialMillis: Long?, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        if (initialMillis != null) {
            cal.timeInMillis = initialMillis
        }

        val initialYear = cal.get(Calendar.YEAR)
        val initialMonth = cal.get(Calendar.MONTH)
        val initialDay = cal.get(Calendar.DAY_OF_MONTH)

        val initialHour = cal.get(Calendar.HOUR_OF_DAY)
        val initialMinute = cal.get(Calendar.MINUTE)

        val dateDialog =
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val timeDialog =
                        TimePickerDialog(
                            requireContext(),
                            { _, hourOfDay, minute ->
                                val resultCal = Calendar.getInstance()
                                resultCal.set(Calendar.YEAR, year)
                                resultCal.set(Calendar.MONTH, month)
                                resultCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                resultCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                resultCal.set(Calendar.MINUTE, minute)
                                resultCal.set(Calendar.SECOND, 0)
                                resultCal.set(Calendar.MILLISECOND, 0)
                                onPicked(resultCal.timeInMillis)
                            },
                            initialHour,
                            initialMinute,
                            true
                        )
                    timeDialog.show()
                },
                initialYear,
                initialMonth,
                initialDay
            )

        dateDialog.show()
    }

    companion object {
        const val TAG = "CallFiltersDialog"
        const val REQUEST_KEY = "request_call_filters"
        const val KEY_FILTERS = "filters"
        const val ARG_FILTERS = "arg_filters"

        fun newInstance(current: CallFilters): CallFiltersDialogFragment {
            return CallFiltersDialogFragment().apply {
                arguments = bundleOf(ARG_FILTERS to current)
            }
        }
    }
}
