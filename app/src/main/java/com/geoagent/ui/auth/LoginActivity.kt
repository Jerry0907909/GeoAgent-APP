package com.geoagent.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import com.geoagent.R
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.chat.ChatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class LoginActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()

    private var isLoginMode = true
    private var usePasswordLogin = false
    private var loginCooldown = false
    private var registerCooldown = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tabLogin: TextView
    private lateinit var tabRegister: TextView
    private lateinit var tabIndicator: View
    private lateinit var layoutLogin: View
    private lateinit var layoutRegister: View

    private lateinit var tvCodeLogin: TextView
    private lateinit var tvPasswordLogin: TextView
    private lateinit var etEmail: TextInputEditText
    private lateinit var etAccount: TextInputEditText
    private lateinit var etCode: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var cardEmail: View
    private lateinit var cardAccount: View
    private lateinit var cardCode: View
    private lateinit var cardPassword: View
    private lateinit var tvForgot: TextView
    private lateinit var btnSendCode: View
    private lateinit var btnSubmit: MaterialButton

    private lateinit var etRegUsername: TextInputEditText
    private lateinit var etRegEmail: TextInputEditText
    private lateinit var etRegPassword: TextInputEditText
    private lateinit var etRegCode: TextInputEditText
    private lateinit var btnRegSendCode: View
    private lateinit var cbAgree: MaterialCheckBox
    private lateinit var tvAgreement: TextView
    private lateinit var layoutAgreement: View
    private lateinit var btnRegister: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        bindViews()
        setupAgreementText()
        setupListeners()

        lifecycleScope.launch {
            if (authRepository.isLoggedIn()) { navigateToChat() }
        }
    }

    private fun bindViews() {
        tabLogin = findViewById(R.id.tab_login)
        tabRegister = findViewById(R.id.tab_register)
        tabIndicator = findViewById(R.id.tab_indicator)
        layoutLogin = findViewById(R.id.layout_login)
        layoutRegister = findViewById(R.id.layout_register)

        tvCodeLogin = findViewById(R.id.pill_code_login)
        tvPasswordLogin = findViewById(R.id.pill_password_login)
        etEmail = findViewById(R.id.et_email)
        etAccount = findViewById(R.id.et_account)
        etCode = findViewById(R.id.et_code)
        etPassword = findViewById(R.id.et_password)
        cardEmail = findViewById(R.id.card_email)
        cardAccount = findViewById(R.id.card_account)
        cardCode = findViewById(R.id.card_code)
        cardPassword = findViewById(R.id.card_password)
        tvForgot = findViewById(R.id.tv_forgot)
        btnSendCode = findViewById(R.id.btn_send_code)
        btnSubmit = findViewById(R.id.btn_submit)

        etRegUsername = findViewById(R.id.et_reg_username)
        etRegEmail = findViewById(R.id.et_reg_email)
        etRegPassword = findViewById(R.id.et_reg_password)
        etRegCode = findViewById(R.id.et_reg_code)
        btnRegSendCode = findViewById(R.id.btn_reg_send_code)
        cbAgree = findViewById(R.id.cb_agree)
        tvAgreement = findViewById(R.id.tv_agreement)
        layoutAgreement = findViewById(R.id.layout_agreement)
        btnRegister = findViewById(R.id.btn_register)
    }

    private fun setupAgreementText() {
        val full = "已阅读并同意 《服务协议》 和 《隐私政策》"
        val spannable = SpannableString(full)
        val linkColor = ContextCompat.getColor(this, R.color.deepseek_primary)
        listOf("《服务协议》", "《隐私政策》").forEach { label ->
            val start = full.indexOf(label)
            if (start >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(linkColor),
                    start,
                    start + label.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        tvAgreement.text = spannable
        layoutAgreement.setOnClickListener { cbAgree.toggle() }
    }

    private fun setupListeners() {
        tabLogin.setOnClickListener { setMode(true) }
        tabRegister.setOnClickListener { setMode(false) }
        tvCodeLogin.setOnClickListener { setLoginMethod(false) }
        tvPasswordLogin.setOnClickListener { setLoginMethod(true) }
        btnSendCode.setOnClickListener { sendLoginCode() }
        btnRegSendCode.setOnClickListener { sendRegisterCode() }
        btnSubmit.setOnClickListener { doLogin() }
        btnRegister.setOnClickListener { doRegister() }
        tvForgot.setOnClickListener {
            Toast.makeText(this, "请使用验证码登录或切换到注册页重新设置密码。", Toast.LENGTH_LONG).show()
        }
        findViewById<TextView>(R.id.tv_back_login).setOnClickListener { setMode(true) }
    }

    private fun setMode(login: Boolean) {
        if (isLoginMode == login) return
        isLoginMode = login

        val on = ContextCompat.getColor(this, R.color.on_background)
        val off = ContextCompat.getColor(this, R.color.text_muted)

        val parent = tabIndicator.parent as ConstraintLayout
        TransitionManager.beginDelayedTransition(parent)

        val lp = tabIndicator.layoutParams as ConstraintLayout.LayoutParams
        if (login) {
            lp.startToStart = R.id.tab_login
            lp.endToEnd = R.id.tab_login
        } else {
            lp.startToStart = R.id.tab_register
            lp.endToEnd = R.id.tab_register
        }
        tabIndicator.layoutParams = lp

        val showing = if (login) layoutLogin else layoutRegister
        val hiding = if (login) layoutRegister else layoutLogin
        hiding.animate().alpha(0f).setDuration(180).withEndAction {
            hiding.visibility = View.GONE
            showing.alpha = 0f
            showing.visibility = View.VISIBLE
            showing.animate().alpha(1f).setDuration(200).start()
        }.start()

        if (login) {
            tabLogin.setTextColor(on); tabLogin.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tabRegister.setTextColor(off); tabRegister.typeface = android.graphics.Typeface.DEFAULT
        } else {
            tabRegister.setTextColor(on); tabRegister.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tabLogin.setTextColor(off); tabLogin.typeface = android.graphics.Typeface.DEFAULT
        }
    }

    private fun setLoginMethod(usePassword: Boolean) {
        usePasswordLogin = usePassword
        val p = ContextCompat.getColor(this, R.color.deepseek_primary)
        val m = ContextCompat.getColor(this, R.color.text_muted)
        if (usePassword) {
            tvPasswordLogin.setTextColor(p); tvPasswordLogin.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tvCodeLogin.setTextColor(m); tvCodeLogin.typeface = android.graphics.Typeface.DEFAULT
            cardEmail.visibility = View.GONE
            cardCode.visibility = View.GONE
            cardAccount.visibility = View.VISIBLE
            cardPassword.visibility = View.VISIBLE
        } else {
            tvCodeLogin.setTextColor(p); tvCodeLogin.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tvPasswordLogin.setTextColor(m); tvPasswordLogin.typeface = android.graphics.Typeface.DEFAULT
            cardEmail.visibility = View.VISIBLE
            cardCode.visibility = View.VISIBLE
            cardAccount.visibility = View.GONE
            cardPassword.visibility = View.GONE
        }
    }

    private fun sendLoginCode() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        if (!email.contains("@")) { toast("请输入邮箱地址"); return }
        if (loginCooldown) return
        loginCooldown = true
        (btnSendCode as TextView).setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        lifecycleScope.launch {
            authRepository.sendVerificationCode(email).fold(
                onSuccess = { toast("验证码已发送"); cd(btnSendCode as TextView) { loginCooldown = false } },
                onFailure = { e ->
                    val msg = e.message.orEmpty()
                    val hasLocalCode = msg.contains("本地验证码")
                    if (hasLocalCode) {
                        toastLong(msg)
                        cd(btnSendCode as TextView) { loginCooldown = false }
                    } else {
                        loginCooldown = false
                        resetSendBtn(btnSendCode as TextView)
                        toastLong(msg.ifBlank { "验证码发送失败" })
                    }
                }
            )
        }
    }

    private fun sendRegisterCode() {
        val email = etRegEmail.text?.toString()?.trim().orEmpty()
        if (!email.contains("@")) { toast("请输入邮箱地址"); return }
        if (registerCooldown) return
        registerCooldown = true
        (btnRegSendCode as TextView).setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        lifecycleScope.launch {
            authRepository.sendVerificationCode(email).fold(
                onSuccess = { toast("验证码已发送"); cd(btnRegSendCode as TextView) { registerCooldown = false } },
                onFailure = { e ->
                    val msg = e.message.orEmpty()
                    val hasLocalCode = msg.contains("本地验证码")
                    if (hasLocalCode) {
                        toastLong(msg)
                        cd(btnRegSendCode as TextView) { registerCooldown = false }
                    } else {
                        registerCooldown = false
                        resetSendBtn(btnRegSendCode as TextView)
                        toastLong(msg.ifBlank { "验证码发送失败" })
                    }
                }
            )
        }
    }

    private fun cd(view: TextView, done: () -> Unit) {
        view.isEnabled = false; var r = 60
        val run = object : Runnable {
            override fun run() {
                if (r <= 0) {
                    view.text = "重新发送"
                    view.isEnabled = true
                    view.setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.deepseek_primary))
                    done()
                } else {
                    view.text = "${r}s"
                    r--
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(run, 0)
    }

    private fun resetSendBtn(v: TextView) {
        v.text = "获取验证码"
        v.isEnabled = true
        v.setTextColor(ContextCompat.getColor(this, R.color.deepseek_primary))
    }

    private fun doLogin() {
        if (usePasswordLogin) {
            val account = etAccount.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            if (account.isEmpty()) { toast("请输入邮箱地址或用户名"); return }
            if (password.length < 6) { toast("密码长度至少 6 位"); return }
            btnSubmit.isEnabled = false; btnSubmit.text = "登录中…"

            lifecycleScope.launch {
                authRepository.loginWithPassword(account, password).fold(
                    onSuccess = { navigateToChat() },
                    onFailure = { e ->
                        resetBtn()
                        toast(e.message ?: "登录失败")
                    }
                )
            }
        } else {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val code = etCode.text?.toString()?.trim().orEmpty()
            if (!email.contains("@")) { toast("请输入邮箱地址"); return }
            if (code.isEmpty()) { toast("请输入验证码"); return }

            btnSubmit.isEnabled = false; btnSubmit.text = "登录中…"
            lifecycleScope.launch {
                authRepository.loginWithEmailCode(email, code).fold(
                    onSuccess = { navigateToChat() },
                    onFailure = { e ->
                        resetBtn()
                        toast(e.message ?: "登录失败")
                    }
                )
            }
        }
    }

    private fun doRegister() {
        val username = etRegUsername.text?.toString()?.trim().orEmpty()
        val email = etRegEmail.text?.toString()?.trim().orEmpty()
        val password = etRegPassword.text?.toString().orEmpty()
        val code = etRegCode.text?.toString()?.trim().orEmpty()

        if (username.isEmpty()) { toast("请输入用户名"); return }
        if (!email.contains("@")) { toast("请输入有效的邮箱地址"); return }
        if (password.length < 6) { toast("密码长度至少 6 位"); return }
        if (code.isEmpty()) { toast("请输入验证码"); return }
        if (!cbAgree.isChecked) { toast("请先同意服务协议和隐私政策"); return }

        btnRegister.isEnabled = false; btnRegister.text = "注册中…"

        lifecycleScope.launch {
            authRepository.register(username, email, password, code).fold(
                onSuccess = { navigateToChat() },
                onFailure = { e ->
                    btnRegister.isEnabled = true
                    btnRegister.text = "注 册"
                    toast(e.message ?: "注册失败")
                }
            )
        }
    }

    private fun navigateToChat() {
        startActivity(Intent(this, ChatActivity::class.java))
        TransitionHelper.forward(this); finish()
    }

    private fun resetBtn() { btnSubmit.isEnabled = true; btnSubmit.text = "登 录" }
    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    private fun toastLong(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
}
