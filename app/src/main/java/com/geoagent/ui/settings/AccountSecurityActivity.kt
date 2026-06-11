package com.geoagent.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.data.local.AvatarLocalStore
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.auth.LoginActivity
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountSecurityActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var userPrefsDataStore: UserPrefsDataStore
    @Inject lateinit var avatarLocalStore: AvatarLocalStore

    private lateinit var avatarFrame: FrameLayout
    private lateinit var avatarImage: ImageView
    private lateinit var avatarInitial: TextView
    private lateinit var avatarStatus: TextView
    private lateinit var avatarSizeContainer: LinearLayout
    private var displayInitial: String = "G"
    private var currentAvatarUri: String? = null
    private var currentAvatarSizeDp: Int = UserPrefsDataStore.DEFAULT_AVATAR_SIZE_DP

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val path = avatarLocalStore.persistFromPickerUri(uri.toString())
            if (path.isNullOrBlank()) {
                Toast.makeText(this@AccountSecurityActivity, "头像保存失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            currentAvatarUri = avatarLocalStore.pathToModel(path)
            userPrefsDataStore.setLocalAvatarUri(currentAvatarUri)
            renderAvatar()
            Toast.makeText(this@AccountSecurityActivity, "头像已更新", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_security)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            MotionUtils.press(it)
            finish()
            TransitionHelper.backward(this)
        }

        val etDisplayName = findViewById<TextInputEditText>(R.id.et_display_name)
        avatarFrame = findViewById(R.id.layout_avatar_frame)
        avatarImage = findViewById(R.id.iv_avatar)
        avatarInitial = findViewById(R.id.tv_avatar_initial)
        avatarStatus = findViewById(R.id.tv_avatar_status)
        avatarSizeContainer = findViewById(R.id.layout_avatar_size)
        findViewById<View>(R.id.row_change_password).findViewById<TextView>(R.id.tv_row_label)
            .setText(R.string.change_password)
        findViewById<View>(R.id.row_switch_account).findViewById<TextView>(R.id.tv_row_label)
            .setText(R.string.switch_account)
        bindAvatarSizeOptions()

        lifecycleScope.launch {
            authRepository.getMe().fold(
                onSuccess = { user ->
                    val displayName = user.full_name?.takeIf { it.isNotBlank() } ?: user.username
                    etDisplayName.setText(displayName)
                    displayInitial = displayName.firstInitial()
                },
                onFailure = { }
            )
            currentAvatarUri = userPrefsDataStore.localAvatarUri.first()
            currentAvatarSizeDp = userPrefsDataStore.avatarSizeDp.first()
            renderAvatar()
        }

        findViewById<MaterialButton>(R.id.btn_choose_avatar).setOnClickListener {
            MotionUtils.press(it)
            avatarPicker.launch("image/*")
        }
        findViewById<MaterialButton>(R.id.btn_save_name).setOnClickListener {
            MotionUtils.press(it)
            val name = etDisplayName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                authRepository.updateMe(name).fold(
                    onSuccess = {
                        displayInitial = name.firstInitial()
                        renderAvatar()
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

    private fun bindAvatarSizeOptions() {
        avatarSizeContainer.removeAllViews()
        AVATAR_SIZES.forEachIndexed { index, option ->
            val chip = TextView(this).apply {
                text = option.label
                gravity = android.view.Gravity.CENTER
                textSize = 14f
                isClickable = true
                isFocusable = true
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener {
                    MotionUtils.press(it)
                    currentAvatarSizeDp = option.sizeDp
                    lifecycleScope.launch { userPrefsDataStore.setAvatarSizeDp(option.sizeDp) }
                    renderAvatar()
                }
            }
            avatarSizeContainer.addView(chip, LinearLayout.LayoutParams(
                0,
                dp(36),
                1f
            ).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }

    private fun renderAvatar() {
        val sizePx = dp(currentAvatarSizeDp)
        avatarFrame.layoutParams = avatarFrame.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        avatarInitial.text = displayInitial
        avatarInitial.textSize = when {
            currentAvatarSizeDp <= 48 -> 20f
            currentAvatarSizeDp >= 80 -> 28f
            else -> 24f
        }
        val avatarUri = currentAvatarUri?.takeIf { it.isNotBlank() }
        if (avatarUri == null) {
            avatarImage.setImageDrawable(null)
            avatarImage.visibility = View.INVISIBLE
            avatarInitial.visibility = View.VISIBLE
        } else {
            avatarImage.setImageURI(Uri.parse(avatarUri))
            avatarImage.visibility = View.VISIBLE
            avatarInitial.visibility = View.GONE
        }
        avatarStatus.text = if (avatarUri == null) {
            "未设置 · ${currentAvatarSizeDp}dp"
        } else {
            "已选择 · ${currentAvatarSizeDp}dp"
        }
        renderSizeChips()
    }

    private fun renderSizeChips() {
        for (i in 0 until avatarSizeContainer.childCount) {
            val chip = avatarSizeContainer.getChildAt(i) as TextView
            val selected = AVATAR_SIZES[i].sizeDp == currentAvatarSizeDp
            chip.setBackgroundResource(if (selected) R.drawable.bg_chip_active_blue else R.drawable.bg_chip_outline_gray)
            chip.setTextColor(ContextCompat.getColor(this, if (selected) R.color.primary else R.color.chip_text))
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

    private fun String.firstInitial(): String =
        trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class AvatarSizeOption(
        val label: String,
        val sizeDp: Int
    )

    companion object {
        private val AVATAR_SIZES = listOf(
            AvatarSizeOption("小 48", 48),
            AvatarSizeOption("中 64", 64),
            AvatarSizeOption("大 80", 80)
        )
    }
}
