package com.geoagent.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.auth.LoginActivity
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountSecurityActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_security)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            MotionUtils.press(it)
            finish()
            TransitionHelper.backward(this)
        }

        val etDisplayName = findViewById<TextInputEditText>(R.id.et_display_name)
        findViewById<View>(R.id.row_change_password).findViewById<TextView>(R.id.tv_row_label)
            .setText(R.string.change_password)
        findViewById<View>(R.id.row_switch_account).findViewById<TextView>(R.id.tv_row_label)
            .setText(R.string.switch_account)

        lifecycleScope.launch {
            authRepository.getMe().fold(
                onSuccess = { user ->
                    etDisplayName.setText(user.full_name ?: user.username)
                },
                onFailure = { }
            )
        }

        findViewById<MaterialButton>(R.id.btn_save_name).setOnClickListener {
            MotionUtils.press(it)
            val name = etDisplayName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                authRepository.updateMe(name).fold(
                    onSuccess = {
                        Toast.makeText(this@AccountSecurityActivity, "保存成功", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(this@AccountSecurityActivity, e.message ?: "保存失败", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        findViewById<View>(R.id.row_change_password).setOnClickListener {
            MotionUtils.press(it)
            showChangePasswordDialog()
        }
        findViewById<View>(R.id.row_switch_account).setOnClickListener {
            MotionUtils.press(it)
            lifecycleScope.launch {
                authRepository.logout()
                startActivity(Intent(this@AccountSecurityActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                TransitionHelper.fade(this@AccountSecurityActivity)
                finish()
            }
        }
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrent = view.findViewById<EditText>(R.id.et_current_password)
        val etNew = view.findViewById<EditText>(R.id.et_new_password)
        val etConfirm = view.findViewById<EditText>(R.id.et_confirm_password)

        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            MotionUtils.press(it)
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.btn_confirm).setOnClickListener {
            MotionUtils.press(it)
            lifecycleScope.launch {
                authRepository.changePassword(
                    etCurrent.text?.toString().orEmpty(),
                    etNew.text?.toString().orEmpty(),
                    etConfirm.text?.toString().orEmpty()
                ).fold(
                    onSuccess = {
                        Toast.makeText(this@AccountSecurityActivity, "密码已更新", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    },
                    onFailure = { e ->
                        Toast.makeText(this@AccountSecurityActivity, e.message ?: "修改失败", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
        dialog.show()
        dialog.window?.applyDialogWindow()
    }

    private fun Window.applyDialogWindow() {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setDimAmount(0.38f)
    }
}
