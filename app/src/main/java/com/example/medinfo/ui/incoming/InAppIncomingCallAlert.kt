package com.example.medinfo.ui.incoming

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.example.medinfo.R
import com.example.medinfo.model.api.HospitalizationResponseDto
import java.lang.ref.WeakReference
import kotlin.math.abs

object InAppIncomingCallAlert {

    private val handler = Handler(Looper.getMainLooper())
    private var activePopupRef: WeakReference<PopupWindow>? = null

    // При программной замене алерта подавляем stopBrief в OnDismissListener старого popup'а,
    // иначе звон оборвётся, хотя должен продолжаться для нового вызова.
    private var isReplacing = false

    fun show(activity: Activity, hospitalization: HospitalizationResponseDto) {
        handler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post

            activePopupRef?.get()?.let { previous ->
                isReplacing = true
                try {
                    previous.dismiss()
                } finally {
                    isReplacing = false
                }
            }

            val root = activity.window?.decorView as? ViewGroup ?: return@post
            val view = LayoutInflater.from(activity)
                .inflate(R.layout.view_in_app_call_alert, root, false)

            view.findViewById<TextView>(R.id.alert_subtitle).text =
                buildSubtitle(hospitalization)

            val openClickListener = View.OnClickListener {
                activePopupRef?.get()?.dismiss()
                openIncomingCall(activity, hospitalization)
            }
            view.setOnClickListener(openClickListener)

            val horizontalMargin = activity.resources.getDimensionPixelSize(R.dimen.call_alert_horizontal_margin)
            val topMargin = activity.resources.getDimensionPixelSize(R.dimen.call_alert_top_margin)
            val screenWidth = root.width.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.widthPixels
            val popupWidth = screenWidth - horizontalMargin * 2

            val popup = PopupWindow(
                view,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
            ).apply {
                isOutsideTouchable = false
                elevation = activity.resources.getDimension(R.dimen.call_alert_elevation)
                // Глушим ringer только если dismiss от пользователя (свайп). При замене
                // алерта isReplacing=true — звон должен продолжаться для нового вызова.
                setOnDismissListener {
                    if (!isReplacing) {
                        IncomingCallRinger.stopBrief()
                    }
                }
            }

            attachSwipeToDismiss(view, popup)

            activePopupRef = WeakReference(popup)
            popup.showAtLocation(root, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, topMargin)
        }
    }

    private fun attachSwipeToDismiss(view: View, popup: PopupWindow) {
        var downX = 0f
        var lastTranslationX = 0f
        var isSwiping = false
        val dismissThresholdRatio = 0.35f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    lastTranslationX = 0f
                    isSwiping = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    if (abs(deltaX) > dp(v, SWIPE_SLOP_DP)) {
                        isSwiping = true
                        lastTranslationX = deltaX
                        v.translationX = deltaX

                        val width = v.width.takeIf { it > 0 } ?: 1
                        v.alpha = (1f - abs(deltaX) / width).coerceIn(0.45f, 1f)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)

                    val width = v.width.takeIf { it > 0 } ?: 1
                    val shouldDismiss = abs(lastTranslationX) > width * dismissThresholdRatio

                    if (shouldDismiss) {
                        val targetX =
                            if (lastTranslationX > 0) width.toFloat() else -width.toFloat()

                        v.animate()
                            .translationX(targetX)
                            .alpha(0f)
                            .setDuration(SWIPE_ANIMATION_DURATION_MS)
                            .withEndAction {
                                popup.dismiss()
                                v.translationX = 0f
                                v.alpha = 1f
                            }
                            .start()
                    } else {
                        v.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(SWIPE_ANIMATION_DURATION_MS)
                            .start()
                    }

                    isSwiping
                }

                else -> false
            }
        }
    }

    private fun openIncomingCall(
        activity: Activity,
        hospitalization: HospitalizationResponseDto
    ) {
        val intent = Intent(activity, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingCallActivity.EXTRA_HOSPITALIZATION, hospitalization)
            putExtra(IncomingCallActivity.EXTRA_START_ALERTS, false)
        }
        activity.startActivity(intent)
    }

    private fun buildSubtitle(hospitalization: HospitalizationResponseDto): String {
        val call = hospitalization.call
        val patientName = listOfNotNull(
            call.patientSurname,
            call.patientName,
            call.patientPatronymic
        ).joinToString(" ").trim()

        val reason = call.reason?.takeIf { it.isNotBlank() }

        return listOfNotNull(patientName, reason)
            .filter { it.isNotBlank() }
            .joinToString(separator = ", ")
            .ifBlank { "Откройте вызов, чтобы принять решение" }
    }

    private fun dp(view: View, value: Int): Float {
        return value * view.resources.displayMetrics.density
    }

    private const val SWIPE_SLOP_DP = 12
    private const val SWIPE_ANIMATION_DURATION_MS = 160L
}
