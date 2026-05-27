package com.geoagent.server

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.security.SecureRandom

class GeoAgentServer(port: Int = 8080) : NanoHTTPD("127.0.0.1", port) {

    // ---------- session storage ----------

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: Int,
        val username: String,
        val email: String,
        val fullName: String?,
        val createdAt: Long = System.currentTimeMillis()
    )

    companion object {
        private const val SESSION_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private val secureRandom = SecureRandom()
    }

    private val sessions = mutableMapOf<String, Session>()

    // ---------- helpers ----------

    private fun jsonOk(json: String): Response =
        NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", json)

    private fun jsonErr(status: Response.Status, msg: String): Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json", """{"detail":"$msg"}""")

    private fun sseBody(events: List<String>): String =
        events.joinToString("\n\n") { "data: $it" } + "\n\n"

    private fun readBody(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        try {
            session.parseBody(map)
        } catch (_: Exception) {
        }
        return map["postData"] ?: ""
    }

    private fun parseJson(session: IHTTPSession): Map<String, String> {
        val raw = readBody(session).trim()
        if (raw.isBlank() || !raw.startsWith("{")) return emptyMap()
        return raw.removeSurrounding("{", "}")
            .split(",")
            .filter { it.contains(":") }
            .associate { entry ->
                val parts = entry.split(":", limit = 2)
                val k = parts[0].trim().removeSurrounding("\"")
                val v = parts[1].trim().removeSurrounding("\"")
                k to v
            }
    }

    private fun requireAuth(session: IHTTPSession): Session {
        val auth = session.headers["authorization"]
            ?: throw StopProcessingException("未登录")
        val token = auth.removePrefix("Bearer ").trim()
        return sessions[token] ?: throw StopProcessingException("Token 无效或已过期")
    }

    class StopProcessingException(msg: String) : Exception(msg)

    // ---------- route dispatch ----------

    override fun serve(session: IHTTPSession): Response {
        return try {
            dispatch(session)
        } catch (e: StopProcessingException) {
            jsonErr(Response.Status.UNAUTHORIZED, e.message ?: "auth error")
        } catch (e: Exception) {
            jsonErr(Response.Status.INTERNAL_ERROR, e.message ?: "server error")
        }
    }

    private fun dispatch(session: IHTTPSession): Response {
        val uri = session.uri.removePrefix("/api")
        val m = session.method?.name ?: return jsonErr(Response.Status.BAD_REQUEST, "no method")

        return when {
            // Auth
            m == "POST" && uri == "/auth/login"                   -> authLogin(session)
            m == "POST" && uri == "/auth/register"                -> authRegister(session)
            m == "POST" && uri == "/auth/send-verification-code"  -> jsonOk("""{"success":true,"message":"验证码已发送"}""")
            m == "POST" && uri == "/auth/refresh"                 -> authRefresh(session)
            m == "GET"  && uri == "/auth/me"                      -> authGetMe(session)
            m == "PUT"  && uri == "/auth/me"                      -> authUpdateMe(session)
            m == "POST" && uri == "/auth/change-password"         -> jsonOk("""{"success":true,"message":"密码修改成功"}""")
            m == "GET"  && uri == "/auth/preferences"             -> authGetPrefs(session)
            m == "PUT"  && uri == "/auth/preferences"             -> authUpdatePrefs(session)

            // Chat
            m == "POST" && uri == "/chat"                         -> chatSync(session)
            m == "POST" && uri == "/chat/stream"                  -> chatStream(session)
            m == "POST" && uri == "/chat/follow-up"               -> chatFollowUp(session)

            // Documents
            m == "GET"  && uri == "/documents/list"               -> docList(session)
            m == "POST" && uri == "/documents/upload-file"        -> jsonOk("""{"document_id":"doc-new","name":"上传文档.pdf","message":"上传成功"}""")
            m == "POST" && uri == "/documents/upload-batch"       -> jsonOk("""{"message":"上传成功"}""")
            m == "DELETE" && uri.startsWith("/documents/")         -> jsonOk("""{"success":true,"message":"删除成功"}""")
            m == "GET"  && uri == "/documents/collections"        -> docCollections(session)

            // Search
            m == "POST" && uri == "/search/deep"                  -> searchDeep(session)

            else -> jsonErr(Response.Status.NOT_FOUND, "Not found: $uri")
        }
    }

    // ==================== Auth ====================

    private fun genTokens(): Pair<String, String> {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val a = "tok_" + bytes.joinToString("") { "%02x".format(it) }
        secureRandom.nextBytes(bytes)
        val r = "ref_" + bytes.joinToString("") { "%02x".format(it) }
        return a to r
    }

    private fun cleanExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeAll { (_, s) -> now - s.createdAt > SESSION_TTL_MS }
    }

    private fun authLogin(session: IHTTPSession): Response {
        cleanExpiredSessions()
        val body = parseJson(session)
        val (access, refresh) = genTokens()
        sessions[access] = Session(access, refresh, 1, "地质学家", body["email"] ?: "user@example.com", "张工")
        return jsonOk("""{"access_token":"$access","refresh_token":"$refresh","token_type":"bearer","expires_in":3600}""")
    }

    private fun authRegister(session: IHTTPSession): Response {
        cleanExpiredSessions()
        val body = parseJson(session)
        val (access, refresh) = genTokens()
        val uname = body["username"] ?: "user"
        sessions[access] = Session(access, refresh, 1, uname, body["email"] ?: "", null)
        return jsonOk("""{"id":1,"username":"$uname","email":"${body["email"]}","access_token":"$access","refresh_token":"$refresh","token_type":"bearer"}""")
    }

    private fun authRefresh(session: IHTTPSession): Response {
        val (access, refresh) = genTokens()
        sessions[access] = Session(access, refresh, 1, "user", "user@example.com", null)
        return jsonOk("""{"access_token":"$access","refresh_token":"$refresh","token_type":"bearer","expires_in":3600}""")
    }

    private fun authGetMe(session: IHTTPSession): Response {
        requireAuth(session)
        return jsonOk("""{"id":1,"username":"地质学家","email":"geologist@example.com","full_name":"张工","avatar_url":null,"is_active":true,"is_superuser":false}""")
    }

    private fun authUpdateMe(session: IHTTPSession): Response {
        requireAuth(session)
        return jsonOk("""{"id":1,"username":"地质学家","email":"geologist@example.com","full_name":"张工","avatar_url":null,"is_active":true}""")
    }

    private fun authGetPrefs(session: IHTTPSession): Response {
        requireAuth(session)
        return jsonOk("""{"language":"zh-CN","theme":"light","default_model":null,"max_context_messages":10,"enable_memory":true}""")
    }

    private fun authUpdatePrefs(session: IHTTPSession): Response {
        requireAuth(session)
        return jsonOk("""{"language":"zh-CN","theme":"light","default_model":null,"max_context_messages":10,"enable_memory":true}""")
    }

    // ==================== Chat ====================

    private val chatAnswerText = """根据地质资料分析，该区域构造特征如下：

1. **地层**：出露震旦系至志留系浅变质岩系，以浅变质的碎屑岩和碳酸盐岩为主。

2. **构造**：受秦岭-大别造山带影响，发育NW-SE向褶皱和逆冲断层。

3. **岩浆活动**：以中生代中酸性侵入岩为主，分布在区域东南部。"""

    private fun chatSync(session: IHTTPSession): Response {
        requireAuth(session)
        val escaped = chatAnswerText
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return jsonOk("""{"answer":"$escaped","sources":[{"content":"研究区出露地层以震旦系至志留系为主...","source":"中国区域地质志·湖北卷","url":"","type":"document","relevance_score":0.92}],"conversation_id":1}""")
    }

    private fun chatStream(session: IHTTPSession): Response {
        requireAuth(session)
        val body = sseBody(listOf(
            """{"type":"info","conversation_id":1}""",
            """{"type":"status","message":"正在检索地质文献..."}""",
            """{"type":"content","content":"根据地质""",
            """{"type":"content","content":"资料分析""",
            """{"type":"content","content":"，该区域构造特征如下：\n\n""",
            """{"type":"content","content":"**1. 地层**：出露震旦系至志留系浅变质岩系\n""",
            """{"type":"content","content":"**2. 构造**：NW-SE向褶皱和逆冲断层\n""",
            """{"type":"content","content":"**3. 岩浆活动**：中生代中酸性侵入岩为主""",
            """{"type":"sources","sources":[{"content":"研究区出露地层以震旦系至志留系为主...","source":"中国区域地质志·湖北卷","url":"","type":"document","relevance_score":0.92}]}""",
            """{"type":"done","message":"回答完成"}""",
        ))
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/event-stream", body)
    }

    private fun chatFollowUp(session: IHTTPSession): Response {
        requireAuth(session)
        return jsonOk("""{"questions":["该区域的成矿条件如何？","能否详细介绍秦岭-大别造山带的构造演化？","该地区有哪些典型矿床？"]}""")
    }

    // ==================== Documents ====================

    private fun docList(session: IHTTPSession): Response {
        requireAuth(session)
        return jsonOk("""{"documents":[
            {"id":"doc-001","name":"中国区域地质志.pdf","source":"中国区域地质志.pdf","type":"pdf","size":24500000,"created_at":"2025-12-15","collection":"default"},
            {"id":"doc-002","name":"矿床学概论.docx","source":"矿床学概论.docx","type":"docx","size":3800000,"created_at":"2026-01-20","collection":"default"},
            {"id":"doc-003","name":"地质构造分析笔记.txt","source":"地质构造分析笔记.txt","type":"txt","size":150000,"created_at":"2026-03-08","collection":"default"}
        ]}""")
    }

    private fun docCollections(session: IHTTPSession): Response {
        requireAuth(session)
        return jsonOk("""{"collections":[{"name":"default","document_count":3,"created_at":"2025-12-01"}]}""")
    }

    // ==================== Search ====================

    private fun searchDeep(session: IHTTPSession): Response {
        requireAuth(session)
        val body = sseBody(listOf(
            """{"type":"plan","queries":["华南板块北缘 构造特征","震旦系 志留系 浅变质岩","秦岭大别造山带 逆冲断层"]}""",
            """{"type":"search","results":[{"title":"华南板块构造演化","url":"","snippet":"华南板块北缘经历了多期构造叠加..."},{"title":"秦岭造山带研究进展","url":"","snippet":"秦岭-大别造山带是华北板块与扬子板块碰撞的产物..."}]}""",
            """{"type":"extract","content":"华南板块北缘出露地层以震旦系至志留系为主，岩性为浅变质的碎屑岩和碳酸盐岩，受秦岭-大别造山带影响发育NW-SE向褶皱和逆冲断层。"}""",
            """{"type":"answer","content":"华南板块北缘的构造特征主要表现为：\n\n1. **地层**：出露震旦系至志留系浅变质碎屑岩和碳酸盐岩\n2. **构造样式**：NW-SE向褶皱和逆冲断层，受秦岭-大别造山带控制\n3. **岩浆活动**：中生代中酸性侵入岩为主"}""",
            """{"type":"citation","citations":[{"title":"中国区域地质志·湖北卷","url":""},{"title":"秦岭造山带构造演化综述","url":""}]}""",
            """{"type":"done"}""",
        ))
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/event-stream", body)
    }
}