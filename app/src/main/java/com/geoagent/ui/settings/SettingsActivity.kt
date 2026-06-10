package com.geoagent.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.geoagent.BuildConfig
import com.geoagent.R
import com.geoagent.data.api.dto.UserSettingsRequest
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.data.repository.SettingsRepository
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.auth.LegalDocumentActivity
import com.geoagent.ui.auth.LoginActivity
import com.geoagent.ui.documents.DocumentListActivity
import com.geoagent.ui.motion.MotionUtils
import com.geoagent.ui.theme.AppThemeHelper
import com.geoagent.ui.theme.AppThemeMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var userPrefsDataStore: UserPrefsDataStore
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            MotionUtils.press(it)
            finish(); TransitionHelper.backward(this)
        }

        // Bind labels & click listeners
        bindRow(R.id.row_account, getString(R.string.account_security)) {
            startActivity(Intent(this, AccountSecurityActivity::class.java))
            TransitionHelper.forward(this)
        }
        bindRow(R.id.row_appearance, getString(R.string.appearance)) { showAppearanceDialog() }
        bindRow(R.id.row_documents, getString(R.string.knowledge_base)) {
            startActivity(Intent(this, DocumentListActivity::class.java))
            TransitionHelper.forward(this)
        }

        // Privacy
        bindSwitch(
            R.id.sw_data_improve,
            getString(R.string.privacy_data_improve),
            getString(R.string.privacy_data_improve_desc),
            userPrefsDataStore.dataImproveEnabled
        ) {
            userPrefsDataStore.setDataImproveEnabled(it)
            syncSettings(UserSettingsRequest(data_improve_enabled = it))
        }
        bindSwitch(
            R.id.sw_incognito,
            getString(R.string.privacy_incognito),
            getString(R.string.privacy_incognito_desc),
            userPrefsDataStore.incognitoEnabled
        ) {
            userPrefsDataStore.setIncognitoEnabled(it)
            syncSettings(UserSettingsRequest(incognito_enabled = it))
        }
        bindRow(R.id.row_export_data, getString(R.string.privacy_export_data)) { exportData() }
        bindRow(R.id.row_delete_data, getString(R.string.privacy_delete_data)) { confirmDeleteAllData() }

        // Personalization
        bindRow(R.id.row_custom_instruction, getString(R.string.pers_custom_instruction)) { showCustomInstructionDialog() }
        bindSwitch(
            R.id.sw_memory,
            getString(R.string.pers_memory_toggle),
            getString(R.string.pers_memory_desc),
            userPrefsDataStore.memoryEnabled
        ) {
            userPrefsDataStore.setMemoryEnabled(it)
            syncSettings(UserSettingsRequest(memory_enabled = it, enable_memory = it))
        }
        bindRow(R.id.row_ai_profile, getString(R.string.pers_ai_profile)) { showAiProfile() }

        // Notifications
        bindSwitch(R.id.sw_push, getString(R.string.notif_push), state = userPrefsDataStore.pushEnabled) {
            userPrefsDataStore.setPushEnabled(it)
            syncSettings(UserSettingsRequest(push_enabled = it))
        }
        bindSwitch(R.id.sw_email_alerts, getString(R.string.notif_email_alerts), state = userPrefsDataStore.emailAlertsEnabled) {
            userPrefsDataStore.setEmailAlertsEnabled(it)
            syncSettings(UserSettingsRequest(email_alerts_enabled = it))
        }

        // Usage
        bindRow(R.id.row_usage_stats, getString(R.string.usage_stats)) { showUsageStats() }
        bindRow(R.id.row_chat_history, getString(R.string.usage_chat_history)) { showChatHistoryInfo() }

        // About
        bindRow(R.id.row_help, getString(R.string.about_help)) { showHelp() }
        bindRow(R.id.row_feedback, getString(R.string.about_feedback)) { sendFeedback() }
        bindRow(R.id.row_version, getString(R.string.version), "v${BuildConfig.VERSION_NAME}", showChevron = false)
        bindRow(R.id.row_terms, getString(R.string.about_terms)) {
            startActivity(LegalDocumentActivity.intent(this, LegalDocumentActivity.TYPE_SERVICE))
            TransitionHelper.forward(this)
        }
        bindRow(R.id.row_privacy_policy, getString(R.string.about_privacy_policy)) {
            startActivity(LegalDocumentActivity.intent(this, LegalDocumentActivity.TYPE_PRIVACY))
            TransitionHelper.forward(this)
        }
        bindRow(R.id.row_license, getString(R.string.about_license)) { showLicense() }

        // Logout
        findViewById<TextView>(R.id.btn_logout).setOnClickListener {
            MotionUtils.press(it)
            lifecycleScope.launch {
                authRepository.logout()
                startActivity(Intent(this@SettingsActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                TransitionHelper.fade(this@SettingsActivity)
                finish()
            }
        }

        loadUser()
        loadSettingSummaries()
    }

    private fun bindRow(rowId: Int, label: String, value: String? = null, showChevron: Boolean = true, onClick: (() -> Unit)? = null) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.tv_row_label).text = label
        val valueView = row.findViewById<TextView>(R.id.tv_row_value)
        if (!value.isNullOrBlank()) { valueView.text = value; valueView.visibility = View.VISIBLE }
        if (!showChevron) row.findViewById<View>(R.id.iv_chevron)?.visibility = View.GONE
        if (onClick != null) row.setOnClickListener { MotionUtils.press(it); onClick() }
    }

    private fun bindSwitch(
        switchId: Int,
        label: String,
        desc: String? = null,
        state: Flow<Boolean>,
        onChanged: suspend (Boolean) -> Unit
    ) {
        val row = findViewById<View>(switchId)
        row.findViewById<TextView>(R.id.tv_row_label).text = label
        if (desc != null) {
            val descView = row.findViewById<TextView>(R.id.tv_row_desc)
            descView.text = desc; descView.visibility = View.VISIBLE
        }
        val toggle = row.findViewById<SwitchMaterial>(R.id.sw_toggle)
        lifecycleScope.launch {
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = state.first()
            toggle.setOnCheckedChangeListener { _, checked ->
                lifecycleScope.launch {
                    onChanged(checked)
                    toast("已保存")
                }
            }
        }
        row.setOnClickListener {
            MotionUtils.press(it)
            toggle.isChecked = !toggle.isChecked
        }
        toggle.setOnClickListener { MotionUtils.press(it) }
    }

    private fun loadUser() {
        lifecycleScope.launch {
            authRepository.getMe().fold(
                onSuccess = { user ->
                    findViewById<TextView>(R.id.tv_user_name).text = user.full_name?.takeIf { it.isNotBlank() } ?: user.username
                    findViewById<TextView>(R.id.tv_user_email).text = user.email
                },
                onFailure = { Toast.makeText(this@SettingsActivity, it.message, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun loadSettingSummaries() {
        lifecycleScope.launch {
            findViewById<View>(R.id.row_custom_instruction)
                .findViewById<TextView>(R.id.tv_row_value)
                .apply {
                    val hasInstruction = userPrefsDataStore.customInstruction.first().isNotBlank()
                    text = if (hasInstruction) "已设置" else "未设置"
                    visibility = View.VISIBLE
                }
        }
    }

    private fun syncSettings(request: UserSettingsRequest) {
        lifecycleScope.launch {
            settingsRepository.syncSettings(request).onFailure {
                toast("本地已保存，后端同步失败")
            }
        }
    }

    private fun exportData() {
        lifecycleScope.launch {
            toast("正在导出数据...")
            settingsRepository.exportUserData().fold(
                onSuccess = { result ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, result.uri)
                        putExtra(Intent.EXTRA_SUBJECT, result.file.name)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "分享导出的 GeoAgent 数据"))
                    toast("已生成${result.source}导出文件")
                },
                onFailure = { toast(it.message ?: "导出失败") }
            )
        }
    }

    private fun confirmDeleteAllData() {
        showConfirmDialog(
            title = "删除所有数据",
            body = "这会清空本机对话、知识库文档、长期记忆和个人偏好。账号登录状态会保留。",
            confirmText = "删除"
        ) {
            lifecycleScope.launch {
                settingsRepository.clearAllUserData().fold(
                    onSuccess = {
                        toast("已删除所有本地数据")
                        recreate()
                    },
                    onFailure = { toast(it.message ?: "删除失败") }
                )
            }
        }
    }

    private fun showCustomInstructionDialog() {
        lifecycleScope.launch {
            val current = userPrefsDataStore.customInstruction.first()
            val content = LayoutInflater.from(this@SettingsActivity).inflate(R.layout.dialog_settings_text_input, null)
            content.findViewById<TextView>(R.id.tv_dialog_title).text = "自定义指令"
            content.findViewById<TextView>(R.id.tv_dialog_subtitle).text = "告诉 GeoAgent 你希望它如何回答、称呼你，或优先关注哪些信息。"
            val input = content.findViewById<EditText>(R.id.et_settings_text)
            input.hint = "例如：回答保持简洁，重点解释地质学概念。"
            input.setText(current)
            input.setSelection(input.text.length)
            bindInstructionPresets(content, input)
            val dialog = AlertDialog.Builder(this@SettingsActivity).setView(content).create()
            content.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
                MotionUtils.press(it)
                dialog.dismiss()
            }
            content.findViewById<TextView>(R.id.btn_save).setOnClickListener {
                MotionUtils.press(it)
                lifecycleScope.launch {
                    userPrefsDataStore.setCustomInstruction(input.text?.toString().orEmpty())
                    syncSettings(UserSettingsRequest(custom_instruction = input.text?.toString().orEmpty().trim()))
                    loadSettingSummaries()
                    toast("已保存")
                    dialog.dismiss()
                }
            }
            dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
            dialog.show()
            dialog.window?.applyDialogWindow()
        }
    }

    private fun showAiProfile() {
        lifecycleScope.launch {
            val user = authRepository.getMe().getOrNull()
            val body = buildString {
                appendLine("账号：${user?.email ?: "未登录"}")
                appendLine("名称：${user?.full_name?.takeIf { it.isNotBlank() } ?: user?.username?.takeIf { it.isNotBlank() } ?: "未设置"}")
                appendLine("长期记忆：${if (userPrefsDataStore.memoryEnabled.first()) "开启" else "关闭"}")
                appendLine("隐身模式：${if (userPrefsDataStore.incognitoEnabled.first()) "开启" else "关闭"}")
                appendLine("自定义指令：${if (userPrefsDataStore.customInstruction.first().isBlank()) "未设置" else "已设置"}")
            }
            showInfoDialog("AI 个人档案", body.trim())
        }
    }

    private fun showUsageStats() {
        lifecycleScope.launch {
            val stats = settingsRepository.usageStats()
            showInfoDialog(
                "用量统计",
                "对话数量：${stats.conversationCount}\n知识库文档：${stats.documentCount}\n长期记忆：${stats.memoryCount}\n待办任务：${stats.taskCount}"
            )
        }
    }

    private fun showChatHistoryInfo() {
        showInfoDialog(
            "聊天历史管理",
            "聊天记录会显示在对话页侧边栏。你可以在对话列表中修改名称；如需彻底清空，请使用“删除所有数据”。"
        )
    }

    private fun showHelp() {
        showInfoDialog(
            "帮助中心",
            "快速模式适合直接提问。\n思考模式会展示推理过程。\n智能搜索会在回答完成后展示来源。\n知识库模式会优先检索你上传的文档。"
        )
    }

    private fun sendFeedback() {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_settings_text_input, null)
        content.findViewById<TextView>(R.id.tv_dialog_title).text = "反馈与建议"
        content.findViewById<TextView>(R.id.tv_dialog_subtitle).text =
            "直接发送给开发者。请描述你遇到的问题、建议或希望新增的功能。"
        val input = content.findViewById<EditText>(R.id.et_settings_text)
        input.hint = "例如：智能搜索结果希望增加更多来源说明。"
        val dialog = AlertDialog.Builder(this).setView(content).create()
        content.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            MotionUtils.press(it)
            dialog.dismiss()
        }
        content.findViewById<TextView>(R.id.btn_save).apply {
            text = "发送"
            setOnClickListener {
                MotionUtils.press(it)
                val feedback = input.text?.toString().orEmpty().trim()
                val recipient = feedbackRecipient()
                when {
                    feedback.isBlank() -> toast("请先填写反馈内容")
                    recipient.isBlank() -> toast("未配置反馈邮箱，请在 .env 中填写 EMAIL_FROM 或 SMTP_USER")
                    else -> {
                        isEnabled = false
                        text = "发送中"
                        lifecycleScope.launch {
                            val user = authRepository.getMe().getOrNull()
                            authRepository.sendEmail(
                                toAddr = recipient,
                                subject = "GeoAgent 反馈与建议",
                                content = buildFeedbackEmailBody(feedback, user?.email)
                            ).fold(
                                onSuccess = {
                                    toast("反馈已发送")
                                    dialog.dismiss()
                                },
                                onFailure = { error ->
                                    isEnabled = true
                                    text = "发送"
                                    toast(error.message ?: "发送失败")
                                }
                            )
                        }
                    }
                }
            }
        }
        dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
        dialog.show()
        dialog.window?.applyDialogWindow()
    }

    private fun showLicense() {
        showInfoDialog(
            "开源许可",
            "GeoAgent 使用 AndroidX、Material Components、OkHttp、Retrofit、Room、DataStore、Gson、Coil、Markwon 等开源组件。各组件许可请以其官方仓库为准。"
        )
    }

    private fun showInfoDialog(title: String, body: String) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_settings_info, null)
        content.findViewById<TextView>(R.id.tv_dialog_title).text = title
        content.findViewById<TextView>(R.id.tv_dialog_body).text = body
        val dialog = AlertDialog.Builder(this).setView(content).create()
        content.findViewById<TextView>(R.id.btn_done).setOnClickListener {
            MotionUtils.press(it)
            dialog.dismiss()
        }
        dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
        dialog.show()
        dialog.window?.applyDialogWindow()
    }

    private fun showConfirmDialog(
        title: String,
        body: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_settings_confirm, null)
        content.findViewById<TextView>(R.id.tv_dialog_title).text = title
        content.findViewById<TextView>(R.id.tv_dialog_body).text = body
        val dialog = AlertDialog.Builder(this).setView(content).create()
        content.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            MotionUtils.press(it)
            dialog.dismiss()
        }
        content.findViewById<TextView>(R.id.btn_confirm).apply {
            text = confirmText
            setOnClickListener {
                MotionUtils.press(it)
                dialog.dismiss()
                onConfirm()
            }
        }
        dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
        dialog.show()
        dialog.window?.applyDialogWindow()
    }

    private fun showAppearanceDialog() {
        lifecycleScope.launch {
            val currentMode = AppThemeHelper.fromStored(userPrefsDataStore.themeMode.first())
            val content = LayoutInflater.from(this@SettingsActivity).inflate(R.layout.dialog_appearance, null)
            var selectedMode = currentMode
            val optionLight = content.findViewById<LinearLayout>(R.id.option_light)
            val optionDark = content.findViewById<LinearLayout>(R.id.option_dark)
            val optionSystem = content.findViewById<LinearLayout>(R.id.option_system)
            val indicatorLight = content.findViewById<View>(R.id.indicator_light)
            val indicatorDark = content.findViewById<View>(R.id.indicator_dark)
            val indicatorSystem = content.findViewById<View>(R.id.indicator_system)

            fun render(mode: AppThemeMode) {
                selectedMode = mode
                listOf(
                    AppThemeMode.LIGHT to (optionLight to indicatorLight),
                    AppThemeMode.DARK to (optionDark to indicatorDark),
                    AppThemeMode.SYSTEM to (optionSystem to indicatorSystem)
                ).forEach { (m, views) ->
                    val sel = m == mode
                    views.first.setBackgroundResource(if (sel) R.drawable.bg_appearance_option_selected else R.drawable.bg_appearance_option_default)
                    views.second.visibility = if (sel) View.VISIBLE else View.INVISIBLE
                }
            }
            render(currentMode)
            optionLight.setOnClickListener { MotionUtils.press(it); render(AppThemeMode.LIGHT) }
            optionDark.setOnClickListener { MotionUtils.press(it); render(AppThemeMode.DARK) }
            optionSystem.setOnClickListener { MotionUtils.press(it); render(AppThemeMode.SYSTEM) }

            val dialog = AlertDialog.Builder(this@SettingsActivity).setView(content).create()
            content.findViewById<TextView>(R.id.btn_cancel).setOnClickListener { MotionUtils.press(it); dialog.dismiss() }
            content.findViewById<TextView>(R.id.btn_save).setOnClickListener {
                MotionUtils.press(it)
                lifecycleScope.launch {
                    userPrefsDataStore.setThemeMode(selectedMode.name.lowercase())
                    syncSettings(UserSettingsRequest(theme = selectedMode.name.lowercase()))
                    AppThemeHelper.apply(selectedMode); dialog.dismiss()
                }
            }
            dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
            dialog.show(); dialog.window?.applyDialogWindow()
        }
    }

    private fun bindInstructionPresets(content: View, input: EditText) {
        val scroll = content.findViewById<HorizontalScrollView>(R.id.scroll_instruction_presets)
        val container = content.findViewById<LinearLayout>(R.id.layout_instruction_presets)
        scroll.visibility = View.VISIBLE
        container.removeAllViews()
        instructionPresets.forEach { preset ->
            val chip = TextView(this).apply {
                text = preset.name
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.chip_text))
                textSize = 14f
                setBackgroundResource(R.drawable.bg_chip_outline_gray)
                gravity = android.view.Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(16), 0, dp(16), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(36)
                ).apply {
                    marginEnd = dp(8)
                }
                setOnClickListener {
                    MotionUtils.press(it)
                    input.setText(preset.prompt)
                    input.setSelection(input.text.length)
                }
            }
            container.addView(chip)
        }
    }

    private fun feedbackRecipient(): String =
        BuildConfig.EMAIL_FROM.trim().ifBlank { BuildConfig.SMTP_USER.trim() }

    private fun buildFeedbackEmailBody(feedback: String, userEmail: String?): String = buildString {
        appendLine("用户反馈：")
        appendLine(feedback)
        appendLine()
        appendLine("账号：${userEmail?.takeIf { it.isNotBlank() } ?: "未知"}")
        appendLine("版本：v${BuildConfig.VERSION_NAME}")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class InstructionPreset(
        val name: String,
        val prompt: String
    )

    private val instructionPresets = listOf(
        InstructionPreset(
            "严谨",
            "请用严谨、准确、可验证的方式回答。优先说明依据和不确定性，避免夸张表述；涉及地质学、文献或数据时，请先给结论，再列出关键依据。"
        ),
        InstructionPreset(
            "冷静",
            "请保持冷静、克制、清晰的表达。回答不要情绪化，先解决问题，再补充必要背景；如果信息不足，请直接说明需要哪些补充信息。"
        ),
        InstructionPreset(
            "活泼",
            "请用更自然、轻松、有亲和力的语气回答。保持专业准确，但表达可以更有节奏感；复杂内容请拆成容易理解的小段。"
        ),
        InstructionPreset(
            "简洁",
            "请尽量简洁回答。优先给出直接结论和可执行步骤，减少铺垫；除非我要求展开，否则不要输出过长解释。"
        ),
        InstructionPreset(
            "学术",
            "请采用学术化表达。回答应结构清晰、概念准确，必要时指出研究背景、方法、局限与参考方向，避免口语化和未经证实的判断。"
        )
    )

    private fun Window.applyDialogWindow() {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setDimAmount(0.38f)
    }

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}
