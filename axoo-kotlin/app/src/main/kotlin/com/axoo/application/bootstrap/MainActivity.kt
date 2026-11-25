/*
 * MainActivity.kt
 *
 * Schermata principale e gestore visivo.
 *
 * Cosa fa questa classe:
 * 1. Usa una scatola vuota (FrameLayout) al posto dei framework complessi.
 * 2. Gestisce direttamente le schermate dopo le operazioni di accesso.
 * 3. Ricorda dove eri se il telefono chiude l'app per fare spazio.
 * 4. Decide cosa fare quando premi il tasto indietro.
 */
package com.axoo.application.bootstrap

import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.AppTheme
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle /* dati salvati dal telefono */
import android.widget.FrameLayout /* una scatola vuota per lo schermo */
import androidx.activity.ComponentActivity /* la schermata base dell'app */
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.OnBackPressedCallback /* serve per il tasto indietro */
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge /* fa andare l'app a schermo intero */
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope /* serve per fare cose in background nello schermo */
import com.axoo.R
import com.axoo.BuildConfig
import com.axoo.core.push.PushRegistration
import com.axoo.core.push.PushTarget
import com.axoo.ui.theme.dp
import com.axoo.application.navigation.factory.DefaultScreenFactory
import com.axoo.application.navigation.host.NavigationHost
import com.axoo.application.navigation.route.NavigationSnapshot /* ricorda dove eravamo se l'app viene chiusa dal telefono */
import com.axoo.application.navigation.route.Route /* la pagina dove vogliamo andare */
import com.axoo.application.navigation.route.Tab /* i menu principali dell'app */
import com.axoo.application.navigation.route.rootRoute
import com.axoo.engine.lottie.LottieFrameClock
import com.axoo.features.auth.data.dto.ModerationNoticeResponse
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import com.axoo.features.auth.presentation.AccountRestrictionView
import com.axoo.features.auth.presentation.AuthController
import com.axoo.features.auth.presentation.AuthOperations
import com.axoo.features.auth.presentation.AuthStatusView
import com.axoo.features.auth.presentation.ComingSoonView
import com.axoo.features.moderation.domain.ModerationReason
import com.axoo.ui.components.AxooTextInput
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.sheets.NotificationPermissionSheet
import dagger.hilt.android.AndroidEntryPoint /* serve per caricare in automatico le dipendenze */
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch /* avvia una operazione in background */
import kotlinx.coroutines.CoroutineStart

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var container: FrameLayout /* la scatola principale dove disegniamo la UI */
    @Inject lateinit var authOperations: AuthOperations
    @Inject lateinit var pushRegistration: PushRegistration
    private lateinit var authController: AuthController
    private var navigationHost: NavigationHost? = null
    private var pendingNavigationSnapshot: NavigationSnapshot? = null
    private var lastAppliedDestination: Route? = null
    private var hasMountedAccount = false
    private var pushAccessAllowed = false
    private var pendingPush: PushTarget? = null
    private var notificationPermissionSheet: NotificationPermissionSheet? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (pushAccessAllowed) lifecycleScope.launch { pushRegistration.activate() }
    }

    // Variabili di stato per evitare che appaiano due volte di fila le schermate di ban e avvisi.
    private var mountedRestrictionKey: String? = null
    private var shownModerationNoticeId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    // Prepara la grafica e il controller che gestisce accesso e navigazione.
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            if (AppTheme.isDark(this)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )
        NativeColors.initialize(resources)
        //splash screen magenta
        setTheme(R.style.Theme_Axoo)
        super.onCreate(savedInstanceState)
        pendingPush = PushTarget.from(intent)
        hasMountedAccount = savedInstanceState?.getBoolean(KEY_MOUNTED_ACCOUNT) ?: false
        setupWindowEdges()
        pendingNavigationSnapshot = NavigationSnapshot.decode(
            savedInstanceState?.getString(KEY_NAVIGATION_SNAPSHOT),
        )

        container = FrameLayout(this).apply {
            setBackgroundColor(NativeColors.background)
        }
        setContentView(container)

        authController = AuthController(
            operations = authOperations,
            scope = lifecycleScope,
            onChecking = ::mountBlank,
            onLoggedOut = {
                pushAccessAllowed = false
                notificationPermissionSheet?.dismissImmediately()
                pushRegistration.stop()
                if (BuildConfig.PUSH_CONFIGURED) lifecycleScope.launch { pushRegistration.clearSignedOut() }
                clearPendingPush()
                mountedRestrictionKey = null
                pendingNavigationSnapshot = null
                if (hasMountedAccount) restartAfterLogout() else applyStartDestination(Route.Intro)
            },
            onAccount = ::applyAccount,
            onLogout = ::mountLogoutStatus,
            onRestoreFailed = ::mountAuthRecovery,
        )

        //trucchetto x evitare che utente se clicca il tasto indietro della navigation bar lo fa uscire dall app anche se non e'
        // nella home (tipo se e' nella sezione commenti e clicca indietro NON DEVE FARLO USCIRE dall app)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val handled = navigationHost?.onBackPressed() == true ||
                    (container.getChildAt(container.childCount - 1) as? com.axoo.application.navigation.host.BackHandler)?.onBackPressed() == true
                if (!handled) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    // Visibile: Fa un check del login col server, aggiorna le statistiche.
    override fun onStart() {
        super.onStart()
        authController.refreshSessionStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPush = PushTarget.from(intent)
        openPendingPush()
    }

    // In primo piano: Avvisa il motore grafico di far ripartire le animazioni (perché l'utente ci sta guardando).
    override fun onResume() {
        super.onResume()
        LottieFrameClock.setAppInForeground(true)
        if (pushAccessAllowed) androidx.lifecycle.ViewModelProvider(this)[com.axoo.features.notifications.presentation.NotificationsViewModel::class.java].foreground()
    }

    // Dietro le quinte: Ferma le animazioni per risparmiare batteria quando l'utente abbassa l'app.
    override fun onPause() {
        LottieFrameClock.setAppInForeground(false)
        super.onPause()
    }

    private fun applyAccount(account: SessionBootstrapResponse) {
        pushAccessAllowed = account.status == "active" && !account.termsRequired && !account.androidLocked
        if (!pushAccessAllowed) {
            pushRegistration.stop()
            notificationPermissionSheet?.dismissImmediately()
        }
        when (account.status) {
            "suspended" -> {
                hasMountedAccount = true
                mountRestriction(
                    key = "suspended:${account.reason}:${account.suspendedUntil}",
                    title = "Account sospeso",
                    message = suspensionMessage(account.reason, account.suspendedUntil),
                )
            }
            "banned" -> {
                hasMountedAccount = true
                mountRestriction(
                    key = "banned:${account.reason}",
                    title = "Account bannato",
                    message = "Non puoi più accedere ad Axoo con questo account.\nMotivo: ${ModerationReason.label(account.reason)}.",
                )
            }
            "active" -> {
                if (account.termsRequired) {
                    val key = "terms:${account.termsVersion}"
                    if (mountedRestrictionKey == key) return
                    mountedRestrictionKey = key
                    mountBlank()
                    val loginVm = androidx.lifecycle.ViewModelProvider(this)[com.axoo.features.auth.presentation.LoginViewModel::class.java]
                    loginVm.requireTerms(account)
                    val login = com.axoo.features.auth.presentation.LoginHost(this, loginVm, this,
                        onLoginSuccess = authController::onAuthenticated)
                    container.addView(login, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    return
                }
                if (account.androidLocked) {
                    mountedRestrictionKey = null
                    pendingNavigationSnapshot = null
                    mountBlank()
                    container.addView(ComingSoonView(this), FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                    ))
                    return
                }
                hasMountedAccount = true
                mountedRestrictionKey = null
                applyStartDestination(Route.Home)
                preparePush()
                account.moderationNotice?.let { notice ->
                    container.post { showModerationNotice(notice) }
                }
            }
            else -> mountAuthRecovery()
        }
    }

    private fun mountAuthRecovery() {
        mountedRestrictionKey = null
        mountBlank()
        container.addView(
            AuthStatusView(
                context = this,
                title = "Non riusciamo a caricare il campus",
                message = "Controlla la connessione e riprova.",
                onRetry = authController::refreshSessionStatus,
            ),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    private fun mountLogoutStatus(failed: Boolean) {
        pushAccessAllowed = false
        notificationPermissionSheet?.dismissImmediately()
        clearPendingPush()
        val key = "logout:$failed"
        if (mountedRestrictionKey == key) return
        mountedRestrictionKey = key
        hasMountedAccount = true // Successful retry must also discard this Activity's ViewModels.
        pendingNavigationSnapshot = null

        WindowCompat.getInsetsController(window, container).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        mountBlank()
        container.addView(
            AuthStatusView(
                context = this,
                title = if (failed) "Uscita non completata" else "Uscita in corso…",
                message = if (failed) "Non è stato possibile cancellare i dati dell'account da questo dispositivo. Riprova per completare l'uscita." else null,
                onRetry = if (failed) authController::logout else null,
            ),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    private fun mountRestriction(key: String, title: String, message: String) {
        if (mountedRestrictionKey == key) return
        mountedRestrictionKey = key
        pendingNavigationSnapshot = null

        navigationHost = null
        lastAppliedDestination = null
        container.removeAllViews()
        container.addView(
            AccountRestrictionView(
                context = this,
                title = title,
                message = message,
                onOpenTerms = ::openTerms,
                onLogout = authController::logout,
                onDeleteAccount = ::confirmRestrictedAccountDeletion,
            ),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun confirmRestrictedAccountDeletion() {
        if (authController.isDeletingAccount) return
        val confirmation = AxooTextInput(this).apply {
            hint = "ELIMINA"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine()
        }
        OverlayCard.present(
            anchor = container,
            title = OverlayCopy.deleteAccountTitle,
            message = OverlayCopy.deleteAccountMessage,
            contentView = confirmation,
            primaryAction = OverlayCardAction(OverlayCopy.deleteAccount, destructive = true) {
                authController.deleteRestrictedAccount(confirmation.text.toString()) { failure ->
                    OverlayCard.error(container, when (failure) {
                        AuthController.DeletionFailure.InvalidConfirmation -> OverlayCopy.deleteAccountInvalid
                        AuthController.DeletionFailure.RequestFailed -> OverlayCopy.deleteError
                    })
                }
            },
            secondaryAction = OverlayCardAction(OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun suspensionMessage(reason: String, suspendedUntil: String?): String {
        val deadline = suspendedUntil?.let(::formatRestrictionDeadline)
        val firstLine = if (deadline != null) {
            "Non puoi usare Axoo fino al $deadline."
        } else {
            "Il tuo account è temporaneamente sospeso."
        }
        return "$firstLine\nMotivo: ${ModerationReason.label(reason)}."
    }

    private fun formatRestrictionDeadline(value: String): String? = runCatching {
        RESTRICTION_DATE_FORMAT.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
    }.getOrNull()

    private fun showModerationNotice(notice: ModerationNoticeResponse) {
        if (shownModerationNoticeId == notice.id) return
        val anchor = navigationHost ?: return
        shownModerationNoticeId = notice.id
        val contentType = if (notice.targetType == "comment") "commento" else "post"
        OverlayCard.present(
            anchor = anchor,
            title = OverlayCopy.hiddenTitle,
            message = "Un tuo $contentType è stato nascosto per una violazione dei Termini di servizio. Motivo: ${ModerationReason.label(notice.reason)}.",
            primaryAction = OverlayCardAction(OverlayCopy.acknowledge) {
                authController.acknowledgeModerationNotice(notice.id)
            },
            secondaryAction = OverlayCardAction("Termini d’uso") {
                openTerms()
            },
            dismissOnBack = false,
        )
    }

    private fun openTerms() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL)))
    }

    // Salvataggio: Mette un segnalibro alla pagina corrente prima che Android ci chiuda per liberare RAM.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_MOUNTED_ACCOUNT, hasMountedAccount)
        navigationHost?.saveState()?.encode()?.let {
            outState.putString(KEY_NAVIGATION_SNAPSHOT, it)
        }
    }

    /* Capisce in quale pagina dobbiamo andare e la mostra a schermo. */
    private fun applyStartDestination(route: Route?) {
        if (route == null) {
            mountBlank()
            return
        }
        val (tab, target) = destinationToTabAndRoute(route)
        val host = navigationHost
        if (host == null) {
            mountRoot(initialTab = tab, postMountTarget = target)
        } else {
            if (lastAppliedDestination == route) return
            host.navigateTab(tab, target)
        }
        lastAppliedDestination = route
    }

    private fun restartAfterLogout() {
        hasMountedAccount = false
        pendingNavigationSnapshot = null

        // The new activity gets fresh ViewModels; no profile/feed from the old account.
        mountBlank()
        val intent = Intent.makeRestartActivityTask(componentName)
        startActivity(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /* Rimuove tutto e svuota lo schermo. */
    private fun mountBlank() {
        if (navigationHost == null && container.childCount == 0) return
        navigationHost = null
        lastAppliedDestination = null
        container.removeAllViews()
    }

    private fun preparePush() {
        if (!BuildConfig.PUSH_CONFIGURED) return
        lifecycleScope.launch {
            if (!pushAccessAllowed) return@launch
            // Activation sets the current-account guard before any Firebase/network wait.
            launch(start = CoroutineStart.UNDISPATCHED) { pushRegistration.activate() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                pushRegistration.shouldAskPermission()
            ) {
                pushRegistration.markPermissionAsked()
                if (pushAccessAllowed) {
                    val sheet = NotificationPermissionSheet(this@MainActivity) {
                        if (pushAccessAllowed) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    notificationPermissionSheet = sheet
                    sheet.addOnDismissedListener {
                        notificationPermissionSheet = null
                    }
                    sheet.presentOn(container)
                }
            }
            openPendingPush()
        }
    }

    private fun openPendingPush() {
        val target = pendingPush ?: return
        if (!pushAccessAllowed || navigationHost == null) return
        lifecycleScope.launch {
            val matches = pushRegistration.isCurrentRecipient(target.recipientId)
            if (pendingPush != target) return@launch
            clearPendingPush()
            if (matches && pushAccessAllowed) {
                navigationHost?.navigateTab(Tab.Home, Route.PostDetail(target.postId, commentId = target.commentId))
            }
        }
    }

    private fun clearPendingPush() {
        pendingPush = null
        listOf("type", "post_id", "comment_id", "recipient_id").forEach { intent.removeExtra(it) }
    }

    /* Carica la prima schermata dentro il contenitore principale. */
    private fun mountRoot(initialTab: Tab, postMountTarget: Route?) {
        container.removeAllViews()
        val snapshot = pendingNavigationSnapshot
        pendingNavigationSnapshot = null
        val view = NavigationHost(
            context = this,
            initialSnapshot = snapshot,
            initialTab = initialTab,
            screenFactory = DefaultScreenFactory(
                onAuthenticated = authController::onAuthenticated,
                onLogout = authController::logout,
            ),
        )
        navigationHost = view
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        // Se nessuno snapshot ci ha portato qui e abbiamo bisogno di una destinazione diversa da quella di base
        // nel tab iniziale (es. ripartire direttamente dal Login ignorando l'Intro), apriamola ora.
        if (snapshot == null && postMountTarget != null && postMountTarget != initialTab.rootRoute) {
            view.navigate(postMountTarget)
        }
    }

    /* Trasforma il nome della pagina nei dati necessari per andarci. */
    private fun destinationToTabAndRoute(route: Route): Pair<Tab, Route?> = when (route) {
        Route.Home -> Tab.Home to Route.Home
        Route.Login -> Tab.Auth to Route.Login
        Route.Intro -> Tab.Auth to Route.Intro
        else -> Tab.Auth to Route.Intro
    }

    private fun setupWindowEdges() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            run {
                window.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = Color.TRANSPARENT
                }
            }
        }
    }

    private companion object {
        const val KEY_NAVIGATION_SNAPSHOT = "axoo_navigation_snapshot"
        const val KEY_MOUNTED_ACCOUNT = "axoo_mounted_account"
        const val TERMS_URL = "https://axoocampus.it/terms.html"
        val RESTRICTION_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN)
    }
}
