import UIKit

/// Mounts the authenticated UI from AppViewModel startup state.
final class AppRootView: AxooViewController {
    let viewModel: AppViewModel
    private let onSignOut: () -> Void
    private let errorContent = UIStackView()
    private let skeleton = StartupSkeletonView()
    private lazy var retry = UIButton.axoo("Riprova", primary: true) { [weak self] in self?.viewModel.load() }
    private var screens: ScreenFactory?
    private var tabs: HomeTabBarController?
    private let onReady: (String) -> Void

    init(api: APIClient, onReady: @escaping (String) -> Void = { _ in }, onSignOut: @escaping () -> Void) {
        self.viewModel = AppViewModel(api: api)
        self.onSignOut = onSignOut
        self.onReady = onReady
        super.init()
        viewModel.onChange = { [weak self] in self?.render() }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let heading = UILabel.axoo("Non riusciamo a caricare il campus",
            font: AxooTypography.statusTitle)
        let message = UILabel.axoo("Controlla la connessione e riprova.",
            font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
        heading.textAlignment = .center
        message.textAlignment = .center
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineSpacing = 3
        message.attributedText = NSAttributedString(string: message.text ?? "", attributes: [.paragraphStyle: paragraph])
        errorContent.axis = .vertical
        errorContent.spacing = 24
        [heading, message, retry].forEach { errorContent.addArrangedSubview($0) }
        errorContent.setCustomSpacing(12, after: heading)
        retry.configuration?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attributes in
            var attributes = attributes
            attributes.font = AxooTypography.primaryButton
            return attributes
        }
        retry.accessibilityIdentifier = "campus-retry"
        view.addSubview(errorContent)
        errorContent.translatesAutoresizingMaskIntoConstraints = false
        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            errorContent.centerXAnchor.constraint(equalTo: safe.centerXAnchor),
            errorContent.centerYAnchor.constraint(equalTo: safe.centerYAnchor),
            errorContent.widthAnchor.constraint(equalTo: safe.widthAnchor, constant: -48)
        ])
        view.addSubview(skeleton)
        skeleton.pinEdges(to: view)
        viewModel.load()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(true, animated: animated)
    }

    func stop() { screens?.stop(); viewModel.stop() }
    func resume() { screens?.resume() }

    private func render() {
        guard isViewLoaded else { return }
        switch viewModel.state {
        case .loading:
            screens?.stop(); screens = nil
            retry.isEnabled = false
            errorContent.isHidden = true
            skeleton.isHidden = false
        case .failed:
            skeleton.isHidden = true
            errorContent.isHidden = false
            retry.isEnabled = true
        case .ready:
            guard screens == nil, let posts = viewModel.posts, let feed = viewModel.feed else { return }
            let screens = ScreenFactory(posts: posts, feed: feed, onSignOut: onSignOut)
            self.screens = screens
            let tabs = screens.makeTabs(home: screens.makeHome()) { [weak self] in self?.createPost() }
            self.tabs = tabs
            addChild(tabs)
            view.addSubview(tabs.view)
            tabs.view.pinEdges(to: view)
            tabs.didMove(toParent: self)
            skeleton.isHidden = true
            if let userID = posts.profile.currentUser?.id { onReady(userID) }

        }
    }

    private func createPost() {
        guard let screens, presentedViewController == nil else { return }
        present(screens.makePostEditor(), animated: true)
    }

    func openPost(_ id: String, commentID: String? = nil) {
        screens?.resume()
        viewModel.openPost(id) { [weak self] post in
            guard let self, let screens = self.screens, let tabs = self.tabs,
                  let navigation = tabs.viewControllers?.first as? UINavigationController,
                  let home = navigation.viewControllers.first else { return }
            guard let post else {
                if presentedViewController == nil { presentError("Non riusciamo ad aprire il post. Riprova.") }
                return
            }
            let show = {
                guard self.view.window != nil else { return }
                tabs.selectedIndex = 0
                let detail = screens.detail(post)
                if let commentID { detail.setNotificationComment(commentID) }
                navigation.setViewControllers([home, detail], animated: true)
            }
            if let presented = presentedViewController { presented.dismiss(animated: false, completion: show) }
            else { show() }
        }
    }

}
