package com.example.medinfo.ui.main

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.setFragmentResult
import com.example.medinfo.R
import com.example.medinfo.databinding.BottomSheetCallFiltersBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CallFiltersBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    private var dateFromMillis: Long? = null

    private var binding: BottomSheetCallFiltersBinding? = null
    private var dateToMillis: Long? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val color = ContextCompat.getColor(requireContext(), R.color.background_1)
        dialog.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(w, w.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.navigationBarColor = Color.TRANSPARENT
        dialog.window?.statusBarColor = Color.TRANSPARENT
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            dimAmount = 0.0f // Disable system dimming
        }
        val dimColor = Color.parseColor("#80000000") // Semi-transparent black
        dialog.window?.setBackgroundDrawable(ColorDrawable(dimColor))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            dialog.window?.isNavigationBarContrastEnforced = false
            dialog.window?.isStatusBarContrastEnforced = false
        }
        return dialog
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetCallFiltersBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val initialFilters =
                (arguments?.getSerializable(ARG_FILTERS) as? CallFilters) ?: CallFilters()

        binding?.let { b ->
            b.closeButton.setOnClickListener { dismiss() }

            b.urgencyFromEdit.setText(initialFilters.urgencyFrom?.toString().orEmpty())
            b.urgencyToEdit.setText(initialFilters.urgencyTo?.toString().orEmpty())

            when (initialFilters.sex) {
                SexFilter.ANY -> b.sexToggleGroup.check(R.id.sex_any_button)
                SexFilter.MALE -> b.sexToggleGroup.check(R.id.sex_male_button)
                SexFilter.FEMALE -> b.sexToggleGroup.check(R.id.sex_female_button)
            }

            b.ageFromEdit.setText(initialFilters.ageFrom?.toString().orEmpty())
            b.ageToEdit.setText(initialFilters.ageTo?.toString().orEmpty())

            dateFromMillis = initialFilters.dateFromMillis
            dateToMillis = initialFilters.dateToMillis

            b.dateFromEdit.setText(dateFromMillis?.let { dateFormat.format(it) }.orEmpty())
            b.dateToEdit.setText(dateToMillis?.let { dateFormat.format(it) }.orEmpty())

            b.callDayFromEdit.setText(initialFilters.callDayFrom?.toString().orEmpty())
            b.callDayToEdit.setText(initialFilters.callDayTo?.toString().orEmpty())
            b.callYearFromEdit.setText(initialFilters.callYearFrom?.toString().orEmpty())
            b.callYearToEdit.setText(initialFilters.callYearTo?.toString().orEmpty())

            b.dateFromEdit.setOnClickListener {
                pickDateTime(dateFromMillis) { millis ->
                    dateFromMillis = millis
                    b.dateFromEdit.setText(dateFormat.format(millis))
                }
            }

            b.dateToEdit.setOnClickListener {
                pickDateTime(dateToMillis) { millis ->
                    dateToMillis = millis
                    b.dateToEdit.setText(dateFormat.format(millis))
                }
            }

            // Кнопки вне let, если они не находятся в binding
            binding?.resetFiltersButton?.setOnClickListener {
                binding?.urgencyFromEdit?.setText("")
                binding?.urgencyToEdit?.setText("")
                binding?.sexToggleGroup?.check(R.id.sex_any_button)
                binding?.ageFromEdit?.setText("")
                binding?.ageToEdit?.setText("")
                dateFromMillis = null
                dateToMillis = null
                binding?.dateFromEdit?.setText("")
                binding?.dateToEdit?.setText("")
                binding?.callDayFromEdit?.setText("")
                binding?.callDayToEdit?.setText("")
                binding?.callYearFromEdit?.setText("")
                binding?.callYearToEdit?.setText("")
            }

            binding?.applyFiltersButton?.setOnClickListener {
                val urgencyFrom = binding?.urgencyFromEdit?.text?.toString()?.trim()?.toIntOrNull()
                val urgencyTo = binding?.urgencyToEdit?.text?.toString()?.trim()?.toIntOrNull()

                val ageFrom = binding?.ageFromEdit?.text?.toString()?.trim()?.toIntOrNull()
                val ageTo = binding?.ageToEdit?.text?.toString()?.trim()?.toIntOrNull()

                val sex =
                        when (binding?.sexToggleGroup?.checkedButtonId) {
                            R.id.sex_male_button -> SexFilter.MALE
                            R.id.sex_female_button -> SexFilter.FEMALE
                            else -> SexFilter.ANY
                        }

                val callDayFrom = binding?.callDayFromEdit?.text?.toString()?.trim()?.toIntOrNull()
                val callDayTo = binding?.callDayToEdit?.text?.toString()?.trim()?.toIntOrNull()
                val callYearFrom = binding?.callYearFromEdit?.text?.toString()?.trim()?.toIntOrNull()
                val callYearTo = binding?.callYearToEdit?.text?.toString()?.trim()?.toIntOrNull()

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

                var normCallDayFrom = callDayFrom
                var normCallDayTo = callDayTo
                if (normCallDayFrom != null && normCallDayTo != null && normCallDayFrom > normCallDayTo) {
                    val tmp = normCallDayFrom
                    normCallDayFrom = normCallDayTo
                    normCallDayTo = tmp
                }

                var normCallYearFrom = callYearFrom
                var normCallYearTo = callYearTo
                if (normCallYearFrom != null && normCallYearTo != null && normCallYearFrom > normCallYearTo) {
                    val tmp = normCallYearFrom
                    normCallYearFrom = normCallYearTo
                    normCallYearTo = tmp
                }

                val filters =
                        CallFilters(
                                urgencyFrom = normUrgencyFrom,
                                urgencyTo = normUrgencyTo,
                                sex = sex,
                                ageFrom = normAgeFrom,
                                ageTo = normAgeTo,
                                dateFromMillis = normDateFrom,
                                dateToMillis = normDateTo,
                                callDayFrom = normCallDayFrom,
                                callDayTo = normCallDayTo,
                                callYearFrom = normCallYearFrom,
                                callYearTo = normCallYearTo
                        )

                setFragmentResult(REQUEST_KEY, bundleOf(KEY_FILTERS to filters))
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
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

        val dateDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val timeDialog = TimePickerDialog(
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
        const val TAG = "CallFiltersBottomSheet"
        const val REQUEST_KEY = "request_call_filters"
        const val KEY_FILTERS = "filters"
        const val ARG_FILTERS = "arg_filters"

        fun newInstance(current: CallFilters): CallFiltersBottomSheetDialogFragment {
            return CallFiltersBottomSheetDialogFragment().apply {
                arguments = bundleOf(ARG_FILTERS to current)
            }
        }
    }
}
