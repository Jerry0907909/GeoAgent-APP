package com.geoagent.data.repository

import com.geoagent.BuildConfig
import com.geoagent.data.api.EmailSender
import com.geoagent.data.api.dto.EmailHistoryItem
import com.geoagent.data.api.dto.EmailHistoryResponse
import com.geoagent.data.api.dto.EmailSendResponse
import com.geoagent.data.api.dto.UserResponse
import com.geoagent.data.local.AccountStore
import com.geoagent.data.local.ApiKeyStore
import com.geoagent.data.local.TokenDataStore
import com.geoagent.data.local.VerificationCodeStore
import com.geoagent.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

class AuthRepositoryImpl(
    private val accountStore: AccountStore,
    private val tokenDataStore: TokenDataStore,
    private val verificationCodeStore: VerificationCodeStore,
    private val apiKeyStore: ApiKeyStore
) : AuthRepository {

    private val emailLogs = mutableListOf<EmailHistoryItem>()
    private var cachedProfile: UserResponse? = null

    override suspend fun loginWithPassword(username: String, password: String): Result<Unit> {
        val account = username.trim()
        val hash = password.hashCode().toString()
        val user = if (account.contains("@")) {
            accountStore.getUserByEmail(account)
        } else {
            accountStore.getUserByUsername(account)
        } ?: return Result.failure(IllegalArgumentException("账号或密码错误"))

        if (user.passwordHash != hash) {
            return Result.failure(IllegalArgumentException("账号或密码错误"))
        }

        return establishSession(user.email, user.username ?: user.email)
    }

    override suspend fun loginWithEmailCode(email: String, code: String): Result<Unit> {
        val normalizedEmail = email.trim()
        if (!verificationCodeStore.verify(normalizedEmail, code)) {
            return Result.failure(IllegalArgumentException("验证码错误或已过期"))
        }

        var user = accountStore.getUserByEmail(normalizedEmail)
        if (user == null) {
            accountStore.saveUser(normalizedEmail, "code_user", null)
            user = accountStore.getUserByEmail(normalizedEmail)!!
        }

        verificationCodeStore.clear()
        return establishSession(user.email, user.username ?: normalizedEmail)
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String,
        code: String
    ): Result<Unit> {
        val normalizedEmail = email.trim()
        val normalizedUsername = username.trim()

        if (!verificationCodeStore.verify(normalizedEmail, code)) {
            return Result.failure(IllegalArgumentException("验证码错误或已过期"))
        }
        if (accountStore.getUserByEmail(normalizedEmail) != null) {
            return Result.failure(IllegalArgumentException("该邮箱已注册"))
        }
        if (accountStore.getUserByUsername(normalizedUsername) != null) {
            return Result.failure(IllegalArgumentException("该用户名已存在"))
        }

        accountStore.saveUser(normalizedEmail, password.hashCode().toString(), normalizedUsername)
        verificationCodeStore.clear()
        return establishSession(normalizedEmail, normalizedUsername)
    }

    override suspend fun sendVerificationCode(email: String): Result<Unit> {
        val normalizedEmail = email.trim()
        if (!normalizedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("请输入有效邮箱"))
        }

        val code = EmailSender.generateCode()
        verificationCodeStore.saveCode(normalizedEmail, code)

        val mailResult = EmailSender.sendVerificationCode(normalizedEmail, code)
        if (mailResult.isSuccess) return Result.success(Unit)

        val reason = mailResult.exceptionOrNull()?.message.orEmpty()
        return if (BuildConfig.DEBUG) {
            Result.failure(Exception("$reason\n本地调试验证码：$code"))
        } else {
            Result.failure(Exception(reason.ifBlank { "验证码发送失败" }))
        }
    }

    override suspend fun getMe(): Result<UserResponse> {
        if (tokenDataStore.accessToken.first().isNullOrBlank()) {
            return Result.failure(IllegalStateException("未登录"))
        }
        cachedProfile?.let { return Result.success(it) }
        val email = apiKeyStore.currentUserEmail.first()
            ?: return Result.failure(IllegalStateException("未登录"))
        val user = accountStore.getUserByEmail(email)
            ?: return Result.failure(IllegalStateException("用户不存在"))
        val displayName = apiKeyStore.displayName.first()
        return Result.success(
            UserResponse(
                id = email.hashCode(),
                username = user.username.orEmpty(),
                email = user.email,
                full_name = displayName ?: user.username
            ).also { cachedProfile = it }
        )
    }

    override suspend fun updateMe(fullName: String?, avatarUrl: String?): Result<UserResponse> {
        val current = getMe().getOrElse { return Result.failure(it) }
        val email = apiKeyStore.currentUserEmail.first()
            ?: return Result.failure(IllegalStateException("未登录"))
        val requestedUsername = fullName?.trim()?.takeIf { it.isNotEmpty() }
        if (requestedUsername != null) {
            val username = requestedUsername
            val updated = runCatching { accountStore.updateUsername(email, username) }
                .getOrElse { return Result.failure(it) }
            if (!updated) return Result.failure(IllegalStateException("用户不存在"))
            apiKeyStore.saveDisplayName(username)
        }
        val username = requestedUsername ?: current.username
        val updated = current.copy(
            username = username,
            full_name = username,
            avatar_url = avatarUrl ?: current.avatar_url
        )
        cachedProfile = updated
        return Result.success(updated)
    }

    override suspend fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Result<Unit> {
        if (newPassword != confirmPassword) {
            return Result.failure(IllegalArgumentException("两次输入的新密码不一致"))
        }
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("密码长度至少 6 位"))
        }

        val email = apiKeyStore.currentUserEmail.first()
            ?: return Result.failure(IllegalStateException("未登录"))
        val user = accountStore.getUserByEmail(email)
            ?: return Result.failure(IllegalStateException("用户不存在"))
        if (user.passwordHash != oldPassword.hashCode().toString()) {
            return Result.failure(IllegalArgumentException("原密码错误"))
        }

        accountStore.saveUser(email, newPassword.hashCode().toString(), user.username)
        return Result.success(Unit)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        confirmPassword: String
    ): Result<Unit> {
        val normalizedEmail = email.trim()
        if (!normalizedEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("请输入有效邮箱"))
        }
        if (!verificationCodeStore.verify(normalizedEmail, code)) {
            return Result.failure(IllegalArgumentException("验证码错误或已过期"))
        }
        if (newPassword != confirmPassword) {
            return Result.failure(IllegalArgumentException("两次输入的新密码不一致"))
        }
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("密码长度至少 6 位"))
        }
        if (accountStore.getUserByEmail(normalizedEmail) == null) {
            return Result.failure(IllegalArgumentException("该邮箱未注册"))
        }
        if (!accountStore.updatePassword(normalizedEmail, newPassword.hashCode().toString())) {
            return Result.failure(IllegalStateException("密码重置失败"))
        }
        verificationCodeStore.clear()
        return Result.success(Unit)
    }

    override suspend fun isLoggedIn(): Boolean = !tokenDataStore.accessToken.first().isNullOrBlank()

    override suspend fun sendEmail(toAddr: String, subject: String, content: String): Result<EmailSendResponse> {
        val normalizedTo = toAddr.trim()
        if (!normalizedTo.contains("@")) {
            return Result.failure(IllegalArgumentException("请输入有效收件人邮箱"))
        }
        val normalizedSubject = subject.trim().ifBlank { "来自 GeoAgent 的邮件" }
        val sendResult = EmailSender.sendPlainText(normalizedTo, normalizedSubject, content)
        if (sendResult.isFailure) {
            return Result.failure(sendResult.exceptionOrNull() ?: IllegalStateException("邮件发送失败"))
        }
        return runCatching {
            emailLogs.add(
                EmailHistoryItem(
                    to_addr = normalizedTo,
                    subject = normalizedSubject,
                    content = content,
                    sent_at = System.currentTimeMillis()
                )
            )
            EmailSendResponse(success = true, message = "邮件已通过 QQ SMTP 发送")
        }
    }

    override suspend fun getEmailHistory(limit: Int): Result<EmailHistoryResponse> {
        return runCatching {
            val items = emailLogs.sortedByDescending { it.sent_at }.take(limit)
            EmailHistoryResponse(items = items, total = emailLogs.size)
        }
    }

    override suspend fun logout() {
        cachedProfile = null
        tokenDataStore.clearTokens()
        apiKeyStore.clearSession()
    }

    private suspend fun establishSession(email: String, displayName: String): Result<Unit> {
        val access = "local-${UUID.randomUUID()}"
        val refresh = "local-${UUID.randomUUID()}"
        tokenDataStore.saveTokens(access, refresh)
        apiKeyStore.saveCurrentUser(email)
        apiKeyStore.saveDisplayName(displayName)
        cachedProfile = null
        return Result.success(Unit)
    }
}
