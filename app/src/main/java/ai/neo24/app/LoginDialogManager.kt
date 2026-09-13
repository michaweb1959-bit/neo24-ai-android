package ai.neo24.app

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LoginDialogManager(
    private val activity: Activity,
    private val onGoogleLoginRequested: () -> Unit,
    private val onPasswordLoginRequested: () -> Unit
) {

    private var dialog: AlertDialog? = null

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show() {
        if (
            activity.isFinishing ||
            activity.isDestroyed ||
            isShowing
        ) {
            return
        }

        val newDialog =
            MaterialAlertDialogBuilder(activity)
                .setTitle(
                    activity.getString(
                        R.string.login_title
                    )
                )
                .setMessage(
                    activity.getString(
                        R.string.login_message
                    )
                )
                .setPositiveButton(
                    activity.getString(
                        R.string.login_google
                    ),
                    null
                )
                .setNegativeButton(
                    activity.getString(
                        R.string.login_password
                    )
                ) { _, _ ->
                    onPasswordLoginRequested()
                }
                .setCancelable(true)
                .create()

        newDialog.setOnShowListener {
            newDialog
                .getButton(
                    AlertDialog.BUTTON_POSITIVE
                )
                .setOnClickListener {
                    setLoading(true)
                    onGoogleLoginRequested()
                }
        }

        newDialog.setOnCancelListener {
            onPasswordLoginRequested()
        }

        newDialog.setOnDismissListener {
            if (dialog === newDialog) {
                dialog = null
            }
        }

        dialog = newDialog
        newDialog.show()
    }

    fun refreshLanguage() {
        if (!isShowing) {
            return
        }

        dismiss()
        show()
    }

    fun setLoading(loading: Boolean) {
        val currentDialog =
            dialog ?: return

        if (!currentDialog.isShowing) {
            return
        }

        currentDialog
            .getButton(
                AlertDialog.BUTTON_POSITIVE
            )
            .apply {
                isEnabled = !loading

                text =
                    activity.getString(
                        if (loading) {
                            R.string.login_google_opening
                        } else {
                            R.string.login_google
                        }
                    )
            }

        currentDialog
            .getButton(
                AlertDialog.BUTTON_NEGATIVE
            )
            .isEnabled = !loading
    }

    fun showGoogleError() {
        setLoading(false)
    }

    fun dismiss() {
        val currentDialog =
            dialog

        dialog = null

        if (currentDialog?.isShowing == true) {
            currentDialog.dismiss()
        }
    }

    fun destroy() {
        dismiss()
    }
}