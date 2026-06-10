package com.geoagent.data.api

import com.geoagent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailSender {

    suspend fun sendVerificationCode(toEmail: String, code: String): Result<Unit> {
        return sendPlainText(
            toEmail = toEmail,
            subject = "GeoAgent 验证码",
            content = "您的 GeoAgent 验证码是：$code\n验证码 5 分钟内有效。"
        )
    }

    suspend fun sendPlainText(toEmail: String, subject: String, content: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val config = smtpConfig().getOrElse { return@withContext Result.failure(it) }
            var lastError: Throwable? = null
            for (attempt in 1..3) {
                val result = runCatching {
                    sendInternal(config, toEmail, subject, content)
                }
                if (result.isSuccess) return@withContext Result.success(Unit)
                lastError = result.exceptionOrNull()
                if (attempt < 3) kotlinx.coroutines.delay(1000L * attempt)
            }
            Result.failure(Exception("邮件发送失败: ${lastError?.message}"))
        }
    }

    private fun sendInternal(config: SmtpConfig, toEmail: String, subject: String, content: String) {
        val properties = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.host", config.host)
                    put("mail.smtp.port", config.port.toString())
                    put("mail.smtp.ssl.enable", (config.port == 465).toString())
                    put("mail.smtp.starttls.enable", (config.port != 465).toString())
                    put("mail.smtp.connectiontimeout", "15000")
                    put("mail.smtp.timeout", "15000")
                    put("mail.smtp.writetimeout", "15000")
                }
                val session = Session.getInstance(
                    properties,
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(config.user, config.password)
                    }
                )
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(config.from, "GeoAgent", "UTF-8"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail.trim(), false))
                    setSubject(subject.ifBlank { "GeoAgent" }, "UTF-8")
                    setText(content, "UTF-8")
                    sentDate = Date()
                }
                Transport.send(message)
            }

    fun generateCode(): String = (100000..999999).random().toString()

    private fun smtpConfig(): Result<SmtpConfig> {
        val host = BuildConfig.SMTP_HOST.ifBlank { "smtp.qq.com" }
        val port = if (BuildConfig.SMTP_PORT > 0) BuildConfig.SMTP_PORT else 465
        val user = BuildConfig.SMTP_USER.trim()
        val password = BuildConfig.SMTP_PASSWORD.trim()
        val from = BuildConfig.EMAIL_FROM.trim().ifBlank { user }
        if (user.isBlank() || password.isBlank()) {
            return Result.failure(
                IllegalStateException("未配置 SMTP 账号，请在项目根目录 .env 中填写 SMTP_USER / SMTP_PASSWORD")
            )
        }
        return Result.success(SmtpConfig(host, port, user, password, from))
    }

    private data class SmtpConfig(
        val host: String,
        val port: Int,
        val user: String,
        val password: String,
        val from: String
    )
}
