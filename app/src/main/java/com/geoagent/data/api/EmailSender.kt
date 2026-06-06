package com.geoagent.data.api

import com.geoagent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter

object EmailSender {

    suspend fun sendVerificationCode(toEmail: String, code: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val host = BuildConfig.SMTP_HOST.ifBlank { "smtp.qq.com" }
            val port = if (BuildConfig.SMTP_PORT > 0) BuildConfig.SMTP_PORT else 465
            val user = BuildConfig.SMTP_USER.trim()
            val password = BuildConfig.SMTP_PASSWORD.trim()
            val from = BuildConfig.EMAIL_FROM.trim().ifBlank { user }

            if (user.isBlank() || password.isBlank()) {
                return@withContext Result.failure(
                    Exception("未配置 SMTP 账号，请在项目根目录 .env 中填写 SMTP_USER / SMTP_PASSWORD")
                )
            }

            runCatching {
                val socket = javax.net.ssl.SSLSocketFactory.getDefault()
                    .createSocket(host, port) as javax.net.ssl.SSLSocket
                socket.use { sslSocket ->
                    sslSocket.startHandshake()
                    val writer = sslSocket.outputStream.bufferedWriter()
                    val reader = sslSocket.inputStream.bufferedReader()

                    expectOk(readResponse(reader), "连接")
                    sendCommand(writer, reader, "EHLO geoagent")
                    sendCommand(writer, reader, "AUTH LOGIN")
                    sendCommand(writer, reader, encodeBase64(user))
                    sendCommand(writer, reader, encodeBase64(password))
                    sendCommand(writer, reader, "MAIL FROM:<$from>")
                    sendCommand(writer, reader, "RCPT TO:<$toEmail>")
                    sendCommand(writer, reader, "DATA")
                    writer.write("From: GeoAgent <$from>\r\n")
                    writer.write("To: <$toEmail>\r\n")
                    writer.write(
                        "Subject: =?UTF-8?B?${encodeBase64("GeoAgent 验证码")}?=\r\n"
                    )
                    writer.write("Content-Type: text/plain; charset=utf-8\r\n")
                    writer.write("\r\n")
                    writer.write("您的 GeoAgent 验证码是：$code\r\n验证码 5 分钟内有效。\r\n")
                    writer.write(".\r\n")
                    writer.flush()
                    expectOk(readResponse(reader), "发送")
                    sendCommand(writer, reader, "QUIT")
                }
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(Exception("邮件发送失败: ${it.message}")) }
            )
        }
    }

    fun generateCode(): String = (100000..999999).random().toString()

    private fun encodeBase64(value: String): String =
        java.util.Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun sendCommand(writer: BufferedWriter, reader: BufferedReader, command: String) {
        writer.write("$command\r\n")
        writer.flush()
        expectOk(readResponse(reader), command)
    }

    private fun readResponse(reader: BufferedReader): String {
        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break
            lines.add(line)
            if (line.length >= 4 && line[3] == ' ') break
        }
        return lines.lastOrNull().orEmpty()
    }

    private fun expectOk(response: String, step: String) {
        if (response.isBlank()) throw Exception("$step 无响应")
        val code = response.take(3)
        if (code.startsWith("4") || code.startsWith("5")) {
            throw Exception("$step 失败: $response")
        }
    }
}
