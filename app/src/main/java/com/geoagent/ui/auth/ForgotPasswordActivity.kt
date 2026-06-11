package com.geoagent.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForgotPasswordActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository
    private var codeTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            MotionUtils.press(it)
            finish()
            TransitionHelper.backward(this)
        }

        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etCode = findViewById<TextInputEditText>(R.id.et_code)
        val etNewPassword = findViewById<TextInputEditText>(R.id.et_new_password)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password)
        val btnSendCode = findViewById<MaterialButton>(R.id.btn_send_code)
        val btnSubmit = findViewById<MaterialButton>(R.id.btn_submit)

        btnSendCode.setOnClickListener {
            MotionUtils.press(it)
            val email = etEmail.text?.toString()?.trim().orEmpty()
            if (!email.contains("@")) {
                Toast.makeText(this, "请输入有效邮箱", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSendCode.isEnabled = false
            lifecycleScope.launch {
                authRepository.sendVerificationCode(email).fold(
                    onSuccess = {
                        Toast.makeText(this@ForgotPasswordActivity, "验证码已发送", Toast.LENGTH_SHORT).show()
                        startCodeCountdown(btnSendCode)
                    },
                    onFailure = { e ->
                        val message = e.message.orEmpty()
                        if (message.contains("本地验证码")) {
                            Toast.makeText(this@ForgotPasswordActivity, message, Toast.LENGTH_LONG).show()
                            startCodeCountdown(btnSendCode)
                        } else {
                            btnSendCode.isEnabled = true
                            Toast.makeText(this@ForgotPasswordActivity, message.ifBlank { "发送失败" }, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        btnSubmit.setOnClickListener {
            MotionUtils.press(it)
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val code = etCode.text?.toString()?.trim().orEmpty()
            val newPassword = etNewPassword.text?.toString().orEmpty()
            val confirmPassword = etConfirmPassword.text?.toString().orEmpty()
            if (!email.contains("@")) {
                Toast.makeText(this, "请输入有效邮箱", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.isBlank()) {
                Toast.makeText(this, "请输入验证码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSubmit.isEnabled = false
            btnSubmit.text = "重置中..."
            lifecycleScope.launch {
                authRepository.resetPassword(email, code, newPassword, confirmPassword).fold(
                    onSuccess = {
                        Toast.makeText(this@ForgotPasswordActivity, "密码已重置，请登录", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@ForgotPasswordActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                        TransitionHelper.backward(this@ForgotPasswordActivity)
                    },
                    onFailure = { e ->
                        btnSubmit.isEnabled = true
                        btnSubmit.text = getString(R.string.reset_password)
                        Toast.makeText(this@ForgotPasswordActivity, e.message ?: "重置失败", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun startCodeCountdown(button: MaterialButton) {
        codeTimer?.cancel()
        codeTimer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                button.text = "${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                button.isEnabled = true
                button.text = getString(R.string.send_verification_code)
            }
        }.start()
    }

    override fun onDestroy() {
        codeTimer?.cancel()
        super.onDestroy()
    }
}
