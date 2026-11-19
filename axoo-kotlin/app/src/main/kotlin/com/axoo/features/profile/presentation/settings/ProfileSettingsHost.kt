package com.axoo.features.profile.presentation.settings

import com.axoo.features.profile.presentation.AccountDeletionConfirmationError
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.axoo.BuildConfig
import com.axoo.R
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.features.profile.presentation.ProfileViewModel
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.NativePillCard
import com.axoo.ui.components.NativePillDefaults
import com.axoo.ui.components.AxooTextInput
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.theme.AppTheme
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

/** Impostazioni dell'account e preferenze in una schermata nativa. */
internal class ProfileSettingsHost(
    context: Context,
    private val onLogout: () -> Unit,
    private val profileViewModel: ProfileViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onBack: () -> Unit,
    private val onNavigate: (Route) -> Unit,
    private val onThemeChanged: () -> Unit,
) : FrameLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.ProfileSettings
    private val view = ProfileSettingsView(context)
    private var observer: Job? = null

    init {
        setBackgroundColor(NativeColors.surface)
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val user = profileViewModel.me.value.getOrNull()
        val pending = profileViewModel.pendingProfilePublic.value
        view.bindPrivacy(pending ?: (user?.isProfilePublic == true), user != null && pending == null)
        val isAdmin = user?.isAdmin == true
        val isModerator = user?.isModerator == true || isAdmin

        view.setCallbacks(
            onBack = onBack,
            isModerator = isModerator,
            onModerator = { onNavigate(Route.Moderation) },
            onBlockedUsers = { onNavigate(Route.BlockedUsers) },
            onProfilePrivacy = ::showProfilePrivacyChoice,
            onTheme = ::showThemeChoice,
            onNotifications = {
                val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }
                context.startActivity(settings)
            },
            onContactUs = { openExternalPage(context, "https://axoocampus.it/contact.html") },
            onPrivacy = { openExternalPage(context, "https://axoocampus.it/privacy.html") },
            onTerms = { openExternalPage(context, "https://axoocampus.it/terms.html") },
            onLogout = ::showLogoutConfirmation,
            onDeleteAccount = ::showAccountDeleteConfirmation,
        )

    }

    override fun onScreenFullyVisible() {
        if (observer != null) return
        observer = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(profileViewModel.me, profileViewModel.pendingProfilePublic) { me, pending -> me.getOrNull() to pending }
                    .collect { (user, pending) -> view.bindPrivacy(pending ?: (user?.isProfilePublic == true), user != null && pending == null) }
            }
        }
    }
    override fun onScreenHidden() { observer?.cancel(); observer = null }
    override fun onScreenDestroyed() = onScreenHidden()

    override fun onBackPressed(): Boolean {
        onBack()
        return true
    }

    private fun showProfilePrivacyChoice() {
        val current = profileViewModel.me.value.getOrNull() ?: return
        if (profileViewModel.pendingProfilePublic.value != null) return
        var isPublic = current.isProfilePublic
        AlertDialog.Builder(context, R.style.Theme_Axoo_ChoiceDialog)
            .setTitle("Visibilità del profilo")
            .setSingleChoiceItems(arrayOf("Privato", "Pubblico"), if (isPublic) 1 else 0) { _, selected ->
                isPublic = selected == 1
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Salva") { _, _ ->
                if (isPublic != current.isProfilePublic) profileViewModel.setProfilePublic(isPublic) {
                    if (isAttachedToWindow) OverlayCard.error(this, "Modifica non confermata. Riprova.")
                }
            }
            .show()
    }

    private fun showThemeChoice() {
        var dark = AppTheme.isDark(context)
        AlertDialog.Builder(context, R.style.Theme_Axoo_ChoiceDialog)
            .setTitle("Tema")
            .setSingleChoiceItems(arrayOf("Chiaro", "Scuro"), if (dark) 1 else 0) { _, selected ->
                dark = selected == 1
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                if (AppTheme.setDark(context, dark)) onThemeChanged()
            }
            .show()
    }

    private fun showLogoutConfirmation() {
        OverlayCard.present(
            anchor = this,
            title = OverlayCopy.signOutTitle,
            message = OverlayCopy.signOutMessage,
            primaryAction = OverlayCardAction(OverlayCopy.signOut, destructive = true, onClick = onLogout),
            secondaryAction = OverlayCardAction(OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun showAccountDeleteConfirmation() {
        val confirmation = AxooTextInput(context).apply {
            hint = "ELIMINA"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine()
        }
        OverlayCard.present(
            anchor = this,
            title = OverlayCopy.deleteAccountTitle,
            message = OverlayCopy.deleteAccountMessage,
            contentView = confirmation,
            primaryAction = OverlayCardAction(OverlayCopy.deleteAccount, destructive = true) {
                profileViewModel.deleteAccount(
                    confirmation = confirmation.text.toString(),
                    onSuccess = onLogout,
                    onFailure = ::showAccountDeleteError,
                )
            },
            secondaryAction = OverlayCardAction(OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun showAccountDeleteError(error: Throwable) {
        OverlayCard.error(this, if (error is AccountDeletionConfirmationError) OverlayCopy.deleteAccountInvalid else OverlayCopy.deleteError)
    }

    private fun openExternalPage(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "Impossibile aprire la pagina.", Toast.LENGTH_SHORT).show()
        }
    }
}

private class ProfileSettingsView(context: Context) : FrameLayout(context) {
    private var onBack: () -> Unit = {}
    private var statusTopInset = 0
    private var navigationBottomInset = 0

    private val privacyRow = TextView(context).apply {
        textSize = 17f
        typeface = context.baloo2Typeface(600)
        setTextColor(NativeColors.textPrimary)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        background = pressedBackground()
    }

    fun bindPrivacy(isPublic: Boolean, enabled: Boolean) {
        privacyRow.text = if (isPublic) "Profilo: Pubblico" else "Profilo: Privato"
        privacyRow.isEnabled = enabled
        privacyRow.alpha = if (enabled) 1f else 0.5f
    }

    private val topBar = FrameLayout(context).apply { setBackgroundColor(NativeColors.card) }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(20), dp(16), dp(32))
    }
    private val scroll = ScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        isFillViewport = true
        addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    init {
        topBar.addView(ArrowBackButton(context).apply { setOnClickListener { onBack() } },
            LayoutParams(dp(44), dp(44), Gravity.START or Gravity.BOTTOM).apply { leftMargin = dp(6) })
        topBar.addView(TextView(context).apply {
            text = "Impostazioni"
            textSize = 22f
            typeface = context.baloo2Typeface(700)
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(NativeColors.textPrimary)
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.BOTTOM).apply {
            leftMargin = dp(54)
            rightMargin = dp(54)
        })
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { topMargin = dp(44) })
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            statusTopInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            navigationBottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (topBar.layoutParams as LayoutParams).height = dp(44) + statusTopInset
            (scroll.layoutParams as LayoutParams).topMargin = dp(44) + statusTopInset
            content.setPadding(dp(16), dp(20), dp(16), dp(32) + navigationBottomInset)
            requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun setCallbacks(
        onBack: () -> Unit,
        isModerator: Boolean,
        onModerator: () -> Unit,
        onBlockedUsers: () -> Unit,
        onNotifications: () -> Unit,
        onProfilePrivacy: () -> Unit,
        onTheme: () -> Unit,
        onContactUs: () -> Unit,
        onPrivacy: () -> Unit,
        onTerms: () -> Unit,
        onLogout: () -> Unit,
        onDeleteAccount: () -> Unit,
    ) {
        this.onBack = onBack
        content.removeAllViews()

        // Gruppo 1: Amministrazione
        if (isModerator) {
            content.addView(pill(
                settingsRow(OverlayCopy.moderationTitle, "star.svg", onClick = onModerator)
            ), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) })
        }

        privacyRow.setOnClickListener { onProfilePrivacy() }
        content.addView(pill(privacyRow).apply {
            addView(TextView(context).apply {
                text = "Mostra i tuoi post a chi visita il profilo"
                textSize = 13f
                typeface = context.baloo2Typeface(400)
                setTextColor(NativeColors.textSecondary)
                setPadding(dp(16), 0, dp(16), dp(12))
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) })

        // Gruppo 2: Preferenze
        content.addView(pill(
            settingsRow("Tema", "moon.svg", onClick = onTheme),
            settingsRow("Notifiche", "bell.svg", onClick = onNotifications),
            settingsRow("Utenti bloccati", "user-delete.svg", onClick = onBlockedUsers)
        ), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) })

        // Gruppo 3: Informazioni e Supporto
        content.addView(pill(
            settingsRow("Contattaci · axoocampus@gmail.com", "Headphone.svg", onClick = onContactUs),
            settingsRow("Informativa sulla privacy", "lock.svg", onClick = onPrivacy),
            settingsRow("Termini d'uso", "paper.svg", onClick = onTerms),
        ), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) })

        // Gruppo 4: Account
        content.addView(pill(
            settingsRow("Esci dall'account", "logout.svg", destructive = true, onClick = onLogout),
            settingsRow(OverlayCopy.deleteAccount, "delete.svg", destructive = true, onClick = onDeleteAccount),
        ), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) })

        // Versione App
        content.addView(TextView(context).apply {
            text = "Axoo · Versione ${BuildConfig.VERSION_NAME}"
            textSize = 14f
            typeface = context.baloo2Typeface(600)
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(NativeColors.textSecondary)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })
    }

    private fun pill(vararg rows: View): NativePillCard = NativePillCard(context).apply {
        rows.forEach { row ->
            addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        }
    }

    private fun settingsRow(
        title: String,
        icon: String,
        destructive: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout {
        val color = if (destructive) NativeColors.destructive else NativeColors.textPrimary
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = pressedBackground()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList = ColorStateList.valueOf(color)
                load("file:///android_asset/icons/$icon")
            }, LinearLayout.LayoutParams(dp(23), dp(23)))
            addView(TextView(context).apply {
                text = title
                textSize = 17f
                typeface = context.baloo2Typeface(600)
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(color)
            }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(16) })
        }
    }

    private fun pressedBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(NativePillDefaults.pressedOverlayColor))
        addState(intArrayOf(), ColorDrawable(0x00000000))
    }
}
