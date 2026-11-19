import UIKit

/// Creates screens and presents authentication state supplied by LoginViewModel.
final class AuthRootView: UINavigationController {
    let viewModel: LoginViewModel
    private let api: APIClient?
    private var authenticated: AppRootView?
    private var shownModerationNoticeID: String?
    private let push: PushNotifications?
    private var authenticatedUserID: String?
    private weak var permissionSheet: NotificationPermissionSheet?

    init(operations: AuthOperations, api: APIClient? = nil, push: PushNotifications? = nil) {
        self.viewModel = LoginViewModel(operations: operations, api: api)
        self.api = api
        self.push = push
        super.init(nibName: nil, bundle: nil)
        viewModel.onChange = { [weak self] in
            guard let self else { return }
            route(viewModel.screen)
        }
        viewModel.onFeedback = { Haptics.vibrate() }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.tintColor = AxooTheme.accent
        route(viewModel.screen)
        viewModel.start()
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        showModerationNoticeIfNeeded()
        showPushPermissionIfNeeded()
    }
    private func renderCurrent() {
        (topViewController as? LoginView)?.render()
        (topViewController as? AuthStatusScreen)?.render()
    }
    private func route(_ next: AuthScreen) {
        if case .account(let access) = next, access.status == .active, !access.termsRequired, let api {
            guard authenticated == nil else { showModerationNoticeIfNeeded(); return }
            let root = AppRootView(api: api, onReady: { [weak self] userID in
                guard let self else { return }
                authenticatedUserID = userID
                push?.activate(userID: userID)
                openPendingPush()
                showPushPermissionIfNeeded()
            }) { [weak self] in self?.viewModel.signOut() }
            authenticated = root
            setNavigationBarHidden(true, animated: false)
            setViewControllers([root], animated: false)
            showModerationNoticeIfNeeded()
            return
        }
        shownModerationNoticeID = nil
        authenticatedUserID = nil
        permissionSheet?.dismiss(animated: false)
        push?.stop()
        if case .intro = next { push?.clearSignedOut(); push?.clearPendingTarget() }
        authenticated?.stop()
        authenticated = nil
        let controller: UIViewController
        switch next {
        case .intro:
            controller = IntroView { [weak self] in self?.viewModel.beginLogin() }
        case .email, .code:
            if topViewController is LoginView { renderCurrent(); return }
            controller = LoginView(viewModel: viewModel)
        default:
            if topViewController is AuthStatusScreen { renderCurrent(); return }
            controller = AuthStatusScreen(viewModel: viewModel)
        }
        setNavigationBarHidden(true, animated: false)
        setViewControllers([controller], animated: false)
    }
    private func showModerationNoticeIfNeeded() {
        guard view.window != nil, presentedViewController == nil,
              let notice = viewModel.moderationNotice, notice.id != shownModerationNoticeID else { return }
        shownModerationNoticeID = notice.id
        let kind = notice.targetType == "comment" ? "commento" : "post"
        let reasons = ["spam": "Spam", "harassment": "Offese o molestie", "hate": "Odio o discriminazione",
                       "sexual_content": "Contenuto sessuale", "violence": "Violenza",
                       "personal_information": "Dati personali"]
        let reason = reasons[notice.reason] ?? "Violazione dei Termini di servizio"
        let alert = AxooConfirmationCard(title: AxooOverlayCopy.hiddenTitle,
            message: "Un tuo \(kind) è stato nascosto per una violazione dei Termini di servizio. Motivo: \(reason).",
            cancelTitle: nil, actionTitle: AxooOverlayCopy.acknowledge, allowsDismissal: false) { [weak self] in
            self?.viewModel.acknowledgeNotice(notice.id) { [weak self] success in
                if !success { self?.shownModerationNoticeID = nil }
                else { self?.showPushPermissionIfNeeded() }
            }
        }
        present(alert, animated: true)
    }
    func resume() {
        if viewModel.screen == .signingOut && !viewModel.isBusy { viewModel.signOut() }
        authenticated?.resume()
        if let userID = authenticatedUserID { push?.activate(userID: userID) }
        showModerationNoticeIfNeeded()
        showPushPermissionIfNeeded()
    }
    func stop() { viewModel.stop(); authenticated?.stop(); push?.stop() }

    private func showPushPermissionIfNeeded() {
        guard let push, authenticatedUserID != nil, view.window != nil else { return }
        Task { [weak self] in
            guard await push.shouldShowPermission(), let self,
                  authenticatedUserID != nil, view.window != nil,
                  presentedViewController == nil, authenticated?.presentedViewController == nil,
                  viewModel.moderationNotice == nil else { return }
            push.markPermissionShown()
            let sheet = NotificationPermissionSheet { [weak self] in
                if self?.authenticatedUserID != nil { push.requestPermission() }
            }
            permissionSheet = sheet
            present(sheet, animated: true)
        }
    }

    func openPendingPush() {
        guard let push, let userID = authenticatedUserID, let root = authenticated,
              let target = push.pendingTarget else { return }
        push.clearPendingTarget()
        if target.recipientID == userID { root.openPost(target.postID, commentID: target.commentID) }
    }
}

private final class AuthStatusScreen: AxooFormController {
    private let viewModel: LoginViewModel
    private let heading = UILabel.axoo(font: AxooTypography.postBody)
    private let message = UILabel.axoo(color: .secondaryLabel)
    private let activity = UIActivityIndicatorView(style: .large)
    private let skeleton = StartupSkeletonView()
    private lazy var retry = UIButton.axoo("Riprova", primary: true) { [weak self] in
        guard let self else { return }
        if viewModel.screen == .signOutFailed { viewModel.signOut() }
        else { viewModel.restore() }
    }
    private lazy var logout = UIButton.axoo(AxooOverlayCopy.signOut) { [weak self] in self?.viewModel.signOut() }
    private lazy var support = UIButton.axoo("Contatta l'assistenza") {
        Task { await UIApplication.shared.open(URL(string: "https://axoocampus.it/contact.html")!) }
    }

    init(viewModel: LoginViewModel) { self.viewModel = viewModel; super.init() }
    override func viewDidLoad() {
        super.viewDidLoad()
        logout.accessibilityIdentifier = "sign-out"
        [heading, message, activity, retry, support, logout].forEach { stack.addArrangedSubview($0) }
        view.addSubview(skeleton)
        skeleton.pinEdges(to: view)
        render()
    }
    func render() {
        guard isViewLoaded else { return }
        let checking = viewModel.screen == .checking
        let signingOut = viewModel.screen == .signingOut || viewModel.screen == .signOutFailed
        heading.font = signingOut ? AxooTypography.statusTitle : AxooTypography.postBody
        heading.textAlignment = signingOut ? .center : .natural
        message.font = signingOut ? AxooTypography.statusBody : AxooTypography.postBody
        message.textAlignment = signingOut ? .center : .natural
        skeleton.isHidden = !checking
        scrollView.isHidden = checking
        retry.isHidden = true
        support.isHidden = true
        logout.isHidden = false
        message.accessibilityIdentifier = nil
        message.text = viewModel.errorMessage
        if viewModel.isBusy && !checking { activity.startAnimating() } else { activity.stopAnimating() }
        retry.isEnabled = !viewModel.isBusy
        switch viewModel.screen {
        case .checking:
            heading.text = nil
            message.text = nil
            logout.isHidden = true
        case .recovery:
            heading.text = "Completiamo l'accesso"
            message.text = viewModel.errorMessage ?? "Riprova per verificare il tuo account."
            retry.isHidden = false
        case .signingOut:
            heading.text = "Uscita in corso…"
            logout.isHidden = true
        case .signOutFailed:
            heading.text = "Uscita non completata"
            logout.isHidden = true
            retry.isHidden = false
        case .account(let access):
            switch access.status {
            case .active:
                heading.text = "Accesso completato"
                message.text = "La tua email universitaria è stata verificata."
                message.accessibilityIdentifier = "login-success"
            case .suspended, .banned:
                heading.text = access.status == .suspended ? "Account sospeso" : "Account bloccato"
                var parts = [access.reason]
                if let until = access.suspendedUntil, access.status == .suspended {
                    parts.append("Sospensione fino al \(formattedDate(until)).")
                }
                if let error = viewModel.errorMessage { parts.append(error) }
                message.text = parts.filter { !$0.isEmpty }.joined(separator: "\n")
                support.isHidden = false
            }
        default: break
        }
        message.isHidden = message.text == nil
    }
    private func formattedDate(_ value: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fractional = formatter.date(from: value)
        formatter.formatOptions = [.withInternetDateTime]
        return (fractional ?? formatter.date(from: value))?.formatted(date: .abbreviated, time: .shortened) ?? value
    }
}
