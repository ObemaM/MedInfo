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
import android.widget.ImageButton
import androidx.core.os.bundleOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.setFragmentResult
import com.example.medinfo.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CallFiltersBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    private var dateFromMillis: Long? = null
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
        // Add custom dimming without pink color
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            dimAmount = 0.0f // Disable system dimming
        }
        // Create custom dim overlay
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
        return inflater.inflate(R.layout.bottom_sheet_call_filters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val initialFilters =
                (arguments?.getSerializable(ARG_FILTERS) as? CallFilters) ?: CallFilters()

        val closeButton = view.findViewById<ImageButton>(R.id.close_button)

        val urgencyFromEdit = view.findViewById<TextInputEditText>(R.id.urgency_from_edit)
        val urgencyToEdit = view.findViewById<TextInputEditText>(R.id.urgency_to_edit)

        val sexToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.sex_toggle_group)

        val ageFromEdit = view.findViewById<TextInputEditText>(R.id.age_from_edit)
        val ageToEdit = view.findViewById<TextInputEditText>(R.id.age_to_edit)

        val dateFromEdit = view.findViewById<TextInputEditText>(R.id.date_from_edit)
        val dateToEdit = view.findViewById<TextInputEditText>(R.id.date_to_edit)

        val callDayFromEdit = view.findViewById<TextInputEditText>(R.id.call_day_from_edit)
        val callDayToEdit = view.findViewById<TextInputEditText>(R.id.call_day_to_edit)
        val callYearFromEdit = view.findViewById<TextInputEditText>(R.id.call_year_from_edit)
        val callYearToEdit = view.findViewById<TextInputEditText>(R.id.call_year_to_edit)

        val resetButton = view.findViewById<View>(R.id.reset_filters_button)
        val applyButton = view.findViewById<View>(R.id.apply_filters_button)

        urgencyFromEdit.setText(initialFilters.urgencyFrom?.toString().orEmpty())
        urgencyToEdit.setText(initialFilters.urgencyTo?.toString().orEmpty())

        when (initialFilters.sex) {
            SexFilter.ANY -> sexToggleGroup.check(R.id.sex_any_button)
            SexFilter.MALE -> sexToggleGroup.check(R.id.sex_male_button)
            SexFilter.FEMALE -> sexToggleGroup.check(R.id.sex_female_button)
        }

        ageFromEdit.setText(initialFilters.ageFrom?.toString().orEmpty())
        ageToEdit.setText(initialFilters.ageTo?.toString().orEmpty())

        dateFromMillis = initialFilters.dateFromMillis
        dateToMillis = initialFilters.dateToMillis

        dateFromEdit.setText(dateFromMillis?.let { dateFormat.format(it) }.orEmpty())
        dateToEdit.setText(dateToMillis?.let { dateFormat.format(it) }.orEmpty())

        callDayFromEdit.setText(initialFilters.callDayFrom?.toString().orEmpty())
        callDayToEdit.setText(initialFilters.callDayTo?.toString().orEmpty())
        callYearFromEdit.setText(initialFilters.callYearFrom?.toString().orEmpty())
        callYearToEdit.setText(initialFilters.callYearTo?.toString().orEmpty())

        closeButton.setOnClickListener { dismiss() }

        dateFromEdit.setOnClickListener {
            pickDateTime(dateFromMillis) { millis ->
                dateFromMillis = millis
                dateFromEdit.setText(dateFormat.format(millis))
            }
        }

        dateToEdit.setOnClickListener {
            pickDateTime(dateToMillis) { millis ->
                dateToMillis = millis
                dateToEdit.setText(dateFormat.format(millis))
            }
        }

        resetButton.setOnClickListener {
            urgencyFromEdit.setText("")
            urgencyToEdit.setText("")
            sexToggleGroup.check(R.id.sex_any_button)
            ageFromEdit.setText("")
            ageToEdit.setText("")
            dateFromMillis = null
            dateToMillis = null
            dateFromEdit.setText("")
            dateToEdit.setText("")
            callDayFromEdit.setText("")
            callDayToEdit.setText("")
            callYearFromEdit.setText("")
            callYearToEdit.setText("")
        }

        applyButton.setOnClickListener {
            val urgencyFrom = urgencyFromEdit.text?.toString()?.trim()?.toIntOrNull()
            val urgencyTo = urgencyToEdit.text?.toString()?.trim()?.toIntOrNull()

            val ageFrom = ageFromEdit.text?.toString()?.trim()?.toIntOrNull()
            val ageTo = ageToEdit.text?.toString()?.trim()?.toIntOrNull()

            val sex =
                    when (sexToggleGroup.checkedButtonId) {
                        R.id.sex_male_button -> SexFilter.MALE
                        R.id.sex_female_button -> SexFilter.FEMALE
                        else -> SexFilter.ANY
                    }

            val callDayFrom = callDayFromEdit.text?.toString()?.trim()?.toIntOrNull()
            val callDayTo = callDayToEdit.text?.toString()?.trim()?.toIntOrNull()
            val callYearFrom = callYearFromEdit.text?.toString()?.trim()?.toIntOrNull()
            val callYearTo = callYearToEdit.text?.toString()?.trim()?.toIntOrNull()

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
