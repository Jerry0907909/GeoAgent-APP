package com.geoagent.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.chat.ChatActivity
import com.geoagent.ui.motion.MotionTokens
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository

    private var isLoginMode = true
    private var usePasswordLogin = false
    private var loginCooldown = false
    private var registerCooldown = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var layoutLogin: View
    private lateinit var layoutRegister: View

    private lateinit var loginMethodSwitch: View
    private lateinit var loginMethodIndicator: View
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
    private lateinit var btnRegister: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        bindViews()
        setupAgreementText()
        setupListeners()
        setLoginMethod(false)
        MotionUtils.show(layoutLogin, MotionTokens.ENTER_MILLIS)

        lifecycleScope.launch {
            if (authRepository.isLoggedIn()) { navigateToChat() }
        }
    }

    private fun bindViews() {
        layoutLogin = findViewById(R.id.layout_login)
        layoutRegister = findViewById(R.id.layout_register)

        loginMethodSwitch = findViewById(R.id.login_method_switch)
        loginMethodIndicator = findViewById(R.id.login_method_indicator)
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
        btnRegister = findViewById(R.id.btn_register)
    }

    private fun setupAgreementText() {
        val full = "已阅读并同意《服务协议》和《隐私政策》"
        val spannable = SpannableString(full)
        val linkColor = ContextCompat.getColor(this, R.color.deepseek_primary)

        fun addLink(label: String, type: String) {
            val start = full.indexOf(label)
            if (start < 0) return
            val end = start + label.length
            spannable.setSpan(ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(LegalDocumentActivity.intent(this@LoginActivity, type))
                        TransitionHelper.forward(this@LoginActivity)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = linkColor
                        ds.isUnderlineText = false
                    }
                },
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        addLink("《服务协议》", LegalDocumentActivity.TYPE_SERVICE)
        addLink("《隐私政策》", LegalDocumentActivity.TYPE_PRIVACY)

        tvAgreement.text = spannable
        tvAgreement.movementMethod = LinkMovementMethod.getInstance()
        tvAgreement.highlightColor = Color.TRANSPARENT
    }

    private fun setupListeners() {
        tvCodeLogin.setOnClickListener {
            setLoginMethod(false, animate = true)
        }
        tvPasswordLogin.setOnClickListener {
            setLoginMethod(true, animate = true)
        }
        btnSendCode.setOnClickListener {
            MotionUtils.press(it)
            sendLoginCode()
        }
        btnRegSendCode.setOnClickListener {
            MotionUtils.press(it)
            sendRegisterCode()
        }
        btnSubmit.setOnClickListener {
            MotionUtils.press(it)
            doLogin()
        }
        btnRegister.setOnClickListener {
            MotionUtils.press(it)
            doRegister()
        }
        tvForgot.setOnClickListener {
            MotionUtils.press(it)
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
            TransitionHelper.forward(this)
        }
        findViewById<TextView>(R.id.tv_to_register).setOnClickListener {
            MotionUtils.press(it)
            setMode(false)
        }
        findViewById<TextView>(R.id.tv_back_login).setOnClickListener {
            MotionUtils.press(it)
            setMode(true)
        }
    }

    private fun setMode(login: Boolean) {
        if (isLoginMode == login) return
        isLoginMode = login

        val showing = if (login) layoutLogin else layoutRegister
        val hiding = if (login) layoutRegister else layoutLogin
        MotionUtils.switchVisibility(showing, hiding)
    }

    private fun setLoginMethod(usePassword: Boolean, animate: Boolean = false) {
        if (animate && usePasswordLogin == usePassword) return
        moveLoginMethodIndicator(usePassword, animate)
        usePasswordLogin = usePassword
        val p = ContextCompat.getColor(this, R.color.deepseek_primary)
        val m = ContextCompat.getColor(this, R.color.text_muted)
        if (usePassword) {
            tvPasswordLogin.setTextColor(p); tvPasswordLogin.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tvCodeLogin.setTextColor(m); tvCodeLogin.typeface = android.graphics.Typeface.DEFAULT
            crossfadeLoginField(cardAccount, cardEmail, animate)
            crossfadeLoginField(cardPassword, cardCode, animate)
        } else {
            tvCodeLogin.setTextColor(p); tvCodeLogin.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tvPasswordLogin.setTextColor(m); tvPasswordLogin.typeface = android.graphics.Typeface.DEFAULT
            crossfadeLoginField(cardEmail, cardAccount, animate)
            crossfadeLoginField(cardCode, cardPassword, animate)
        }
    }

    private fun crossfadeLoginField(showing: View, hiding: View, animate: Boolean) {
        if (showing.visibility == View.VISIBLE && hiding.visibility != View.VISIBLE) return

        showing.animate().cancel()
        hiding.animate().cancel()
        showing.translationX = 0f
        showing.translationY = 0f
        hiding.translationX = 0f
        hiding.translationY = 0f

        if (!animate || !MotionUtils.animationsEnabled()) {
            showing.visibility = View.VISIBLE
            showing.alpha = 1f
            showing.scaleX = 1f
            showing.scaleY = 1f
            hiding.visibility = View.INVISIBLE
            hiding.alpha = 1f
            hiding.scaleX = 1f
            hiding.scaleY = 1f
            return
        }

        showing.alpha = 0f
        showing.scaleX = 0.99f
        showing.scaleY = 0.99f
        showing.visibility = View.VISIBLE
        showing.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(MotionTokens.MICRO_MILLIS)
            .setInterpolator(MotionUtils.easeOut)
            .start()

        if (hiding.visibility == View.VISIBLE) {
            hiding.animate()
                .alpha(0f)
                .scaleX(0.99f)
                .scaleY(0.99f)
                .setDuration(120L)
                .setInterpolator(MotionUtils.easeOutSoft)
                .withEndAction {
                    hiding.visibility = View.INVISIBLE
                    hiding.alpha = 1f
                    hiding.scaleX = 1f
                    hiding.scaleY = 1f
                }
                .start()
        } else {
            hiding.visibility = View.INVISIBLE
            hiding.alpha = 1f
            hiding.scaleX = 1f
            hiding.scaleY = 1f
        }
    }

    private fun moveLoginMethodIndicator(usePassword: Boolean, animate: Boolean) {
        loginMethodSwitch.post {
            val inset = (2 * resources.displayMetrics.density).toInt()
            val contentWidth = loginMethodSwitch.width - inset * 2
            if (contentWidth <= 0) return@post
            val tabWidth = contentWidth / 2
            val lp = loginMethodIndicator.layoutParams as FrameLayout.LayoutParams
            if (lp.width != tabWidth) {
                lp.width = tabWidth
                loginMethodIndicator.layoutParams = lp
            }
            val targetX = if (usePassword) {
                (inset + tabWidth).toFloat()
            } else {
                inset.toFloat()
            }
            loginMethodIndicator.animate().cancel()
            if (animate && MotionUtils.animationsEnabled()) {
                loginMethodIndicator.animate()
                    .translationX(targetX)
                    .setDuration(MotionTokens.STATE_MILLIS)
                    .setInterpolator(MotionUtils.easeOut)
                    .start()
            } else {
                loginMethodIndicator.translationX = targetX
            }
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
                        toast(msg)
                        cd(btnSendCode as TextView) { loginCooldown = false }
                    } else {
                        loginCooldown = false
                        resetSendBtn(btnSendCode as TextView)
                        toast(msg.ifBlank { "验证码发送失败" })
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
                        toast(msg)
                        cd(btnRegSendCode as TextView) { registerCooldown = false }
                    } else {
                        registerCooldown = false
                        resetSendBtn(btnRegSendCode as TextView)
                        toast(msg.ifBlank { "验证码发送失败" })
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
                    btnRegister.isEnabled = true; btnRegister.text = "注册"
                    toast(e.message ?: "注册失败")
                }
            )
        }
    }

    private fun navigateToChat() {
        startActivity(Intent(this, ChatActivity::class.java))
        TransitionHelper.forward(this); finish()
    }

    private fun resetBtn() { btnSubmit.isEnabled = true; btnSubmit.text = "登录" }
    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
