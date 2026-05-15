package com.example.medinfo.util

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager

// Единая настройка окон диалогов: адаптивная ширина, центрирование и ограничение высоты
// с прокруткой внутреннего содержимого. Вызывать ДО Dialog.show() — иначе диалог
// успевает отрисоваться с дефолтными параметрами и видимо "прыгает".
object DialogSizing {

    private const val HORIZONTAL_MARGIN_DP = 32   // по 16dp с каждой стороны
    private const val MAX_WIDTH_DP = 360
    private const val MAX_HEIGHT_SCREEN_FRACTION = 0.8f

    // root — корневая View диалога; scrollView — внутренний скролл, который ужимается,
    // если содержимое не влезает в MAX_HEIGHT_SCREEN_FRACTION экрана.
    fun apply(window: Window?, root: View, scrollView: View) {
        window ?: return
        val metrics = root.resources.displayMetrics
        val density = metrics.density

        val targetWidth = (metrics.widthPixels - (HORIZONTAL_MARGIN_DP * density).toInt())
            .coerceAtMost((MAX_WIDTH_DP * density).toInt())

        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        capHeight(root, scrollView, targetWidth, metrics.heightPixels)
    }

    // Меряем естественную высоту диалога. Если она больше лимита — ужимаем ScrollView ровно
    // на размер переполнения, и внутренний список начинает прокручиваться.
    private fun capHeight(root: View, scrollView: View, targetWidth: Int, screenHeight: Int) {
        val maxHeight = (screenHeight * MAX_HEIGHT_SCREEN_FRACTION).toInt()

        root.measure(
            View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (root.measuredHeight <= maxHeight) return

        val overflow = root.measuredHeight - maxHeight
        val cappedHeight = (scrollView.measuredHeight - overflow).coerceAtLeast(0)
        scrollView.layoutParams = scrollView.layoutParams.apply { height = cappedHeight }
        scrollView.requestLayout()
    }
}
