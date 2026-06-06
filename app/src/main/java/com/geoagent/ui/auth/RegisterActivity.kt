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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class RegisterActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()
    private var codeTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etUsername = findViewById<TextInputEditText>(R.id.et_username)
        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etCode = findViewById<TextInputEditText>(R.id.et_code)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password)
        val btnSendCode = findViewById<MaterialButton>(R.id.btn_send_code)
        val btnRegister = findViewById<MaterialButton>(R.id.btn_register)

        btnSendCode.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                Toast.makeText(this, "请输入邮箱", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSendCode.isEnabled = false
            lifecycleScope.launch {
                authRepository.sendVerificationCode(email).fold(
                    onSuccess = {
                        Toast.makeText(this@RegisterActivity, "验证码已发送", Toast.LENGTH_SHORT).show()
                        startCodeCountdown(btnSendCode)
                    },
                    onFailure = { e ->
                        btnSendCode.isEnabled = true
                        Toast.makeText(this@RegisterActivity, e.message ?: "发送失败", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        btnRegister.setOnClickListener {
            val username = etUsername.text?.toString()?.trim().orEmpty()
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val code = etCode.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            val confirm = etConfirmPassword.text?.toString().orEmpty()
            if (username.isEmpty() || email.isEmpty() || code.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirm) {
                Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnRegister.isEnabled = false
            lifecycleScope.launch {
                authRepository.register(username, email, password, code).fold(
                    onSuccess = {
                        Toast.makeText(this@RegisterActivity, "注册成功，请登录", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        TransitionHelper.backward(this@RegisterActivity)
                        finish()
                    },
                    onFailure = { e ->
                        btnRegister.isEnabled = true
                        Toast.makeText(this@RegisterActivity, e.message ?: "注册失败", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        findViewById<android.widget.TextView>(R.id.tv_back_login).setOnClickListener {
            finish()
            TransitionHelper.backward(this)
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
