package com.example.lucimanager.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Extension functions for Views and UI components
 */

/**
 * Hide software keyboard
 */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

/**
 * Show software keyboard for EditText
 */
fun View.showKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * Set view visibility with fade animation
 */
fun View.setVisibilityWithFade(visible: Boolean, duration: Long = 300) {
    if (visible) {
        alpha = 0f
        visibility = View.VISIBLE
        animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(null)
    } else {
        animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                visibility = View.GONE
            }
    }
}

/**
 * Show snackbar with action
 */
fun Fragment.showSnackbar(
    message: String,
    actionText: String? = null,
    action: (() -> Unit)? = null,
    duration: Int = Snackbar.LENGTH_SHORT
) {
    view?.let { view ->
        val snackbar = Snackbar.make(view, message, duration)
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
        }
        snackbar.show()
    }
}

/**
 * Show error snackbar
 */
fun Fragment.showErrorSnackbar(message: String) {
    showSnackbar(message, "Dismiss", duration = Snackbar.LENGTH_LONG)
}

/**
 * Show success snackbar
 */
fun Fragment.showSuccessSnackbar(message: String) {
    showSnackbar(message, duration = Snackbar.LENGTH_SHORT)
}