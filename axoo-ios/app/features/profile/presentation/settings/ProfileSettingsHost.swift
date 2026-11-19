import UIKit
import SafariServices

/// Sezioni in card bianche sulla superficie grigia condivisa con feed e dettaglio.
final class ProfileSettingsHost: UITableViewController {
    private struct Row {
        enum Action {
            case publicProfile, theme, blockedUsers, notifications, moderation, signOut, deleteAccount
            case page(String)
        }
        let title: String
        let icon: AxooIcon
        let action: Action
        var color: UIColor {
            switch action {
            case .signOut, .deleteAccount: return .systemRed
            default: return AxooTheme.textPrimary
            }
        }
    }
    private var sections: [[Row]] {
        var result = standardSections
        if viewModel.user?.isModerator == true {
            result.insert([.init(title: "Moderazione", icon: .flag, action: .moderation)], at: 0)
        }
        return result
    }
    private let standardSections: [[Row]] = [
        [.init(title: "Profilo", icon: .privacy, action: .publicProfile)],
        [
            .init(title: "Tema", icon: .theme, action: .theme),
            .init(title: "Notifiche", icon: .notifications, action: .notifications),
            .init(title: "Utenti bloccati", icon: .blockedUsers, action: .blockedUsers)
        ],
        [
            .init(title: "Contattaci · axoocampus@gmail.com", icon: .contact, action: .page("contact.html")),
            .init(title: "Informativa sulla privacy", icon: .privacy, action: .page("privacy.html")),
            .init(title: "Termini d’uso", icon: .terms, action: .page("terms.html"))
        ],
        [
            .init(title: "Esci dall'account", icon: .logout, action: .signOut),
            .init(title: AxooOverlayCopy.deleteAccount, icon: .delete, action: .deleteAccount)
        ]
    ]
    let viewModel: ProfileViewModel
    private let onSignOut: () -> Void
    private let versionLabel = UILabel.axoo(
        "Axoo · Versione \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.52")",
        font: AxooTypography.settingsVersion,
        color: AxooTheme.textSecondary)
    init(viewModel: ProfileViewModel, onSignOut: @escaping () -> Void) {
        self.viewModel = viewModel
        self.onSignOut = onSignOut
        super.init(style: .insetGrouped)
        title = "Impostazioni"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func viewDidLoad() {
        super.viewDidLoad()
        installArrowBackButton(testID: "settings-back")
        tableView.hideTopEdgeEffect()
        tableView.showsVerticalScrollIndicator = false
        tableView.backgroundColor = AxooTheme.surface
        tableView.separatorStyle = .none
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 52
        tableView.sectionHeaderHeight = 16
        tableView.sectionFooterHeight = .leastNormalMagnitude
        versionLabel.textAlignment = .center
        versionLabel.accessibilityIdentifier = "settings-version"
        tableView.tableFooterView = versionLabel
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        viewModel.onChange = { [weak self] in self?.render() }
        render()
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let width = tableView.bounds.width
        let height = ceil(versionLabel.sizeThatFits(CGSize(width: max(1, width - 40), height: .greatestFiniteMagnitude)).height) + 24
        let size = CGSize(width: width, height: height)
        if versionLabel.frame.size != size {
            versionLabel.frame.size = size
            tableView.tableFooterView = versionLabel
        }
    }
    override func numberOfSections(in tableView: UITableView) -> Int { sections.count }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { sections[section].count }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let row = sections[indexPath.section][indexPath.row]
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.backgroundColor = AxooTheme.cardBackground
        cell.textLabel?.text = row.title
        cell.textLabel?.numberOfLines = 0
        cell.textLabel?.font = AxooTypography.sheetOption
        cell.textLabel?.textColor = row.color
        cell.imageView?.image = row.icon.image(pointSize: 23)
        cell.imageView?.tintColor = row.color
        cell.accessoryType = .none
        if case .publicProfile = row.action {
            let isPublic = viewModel.state.pendingProfilePublic ?? (viewModel.user?.isProfilePublic == true)
            cell.textLabel?.text = isPublic ? "Profilo: Pubblico" : "Profilo: Privato"
            cell.isUserInteractionEnabled = viewModel.user != nil && viewModel.state.pendingProfilePublic == nil
            cell.contentView.alpha = cell.isUserInteractionEnabled ? 1 : 0.5
            cell.detailTextLabel?.text = "Mostra i tuoi post a chi visita il profilo"
            cell.detailTextLabel?.numberOfLines = 0
            cell.detailTextLabel?.font = AxooTypography.postMetadata
            cell.detailTextLabel?.textColor = AxooTheme.textSecondary
        }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let row = sections[indexPath.section][indexPath.row]
        switch row.action {
        case .publicProfile: chooseProfilePrivacy()
        case .theme:
            chooseTheme()
        case .notifications:
            if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                UIApplication.shared.open(url)
            }
        case .moderation:
            guard viewModel.user?.isModerator == true else { return }
            navigationController?.pushViewController(ModerationHost(viewModel: viewModel.makeModerationViewModel()), animated: true)
        case .blockedUsers:
            navigationController?.pushViewController(BlockedUsersHost(viewModel: viewModel), animated: true)
        case .signOut:
            confirmSignOut()
        case .deleteAccount:
            confirmDelete()
        case .page(let path):
            if let url = URL(string: "https://axoocampus.it/" + path) {
                present(SFSafariViewController(url: url), animated: true)
            }
        }
    }
    private func chooseProfilePrivacy() {
        guard let user = viewModel.user, viewModel.state.pendingProfilePublic == nil else { return }
        let choices = SlidingCategoryControl(items: ["Privato", "Pubblico"])
        let wasPublic = user.isProfilePublic == true
        choices.selectedSegmentIndex = wasPublic ? 1 : 0
        let card = AxooConfirmationCard(title: "Visibilità del profilo",
            message: "Scegli se mostrare i tuoi post a chi visita il profilo.",
            actionTitle: "Salva", contentView: choices, action: { [weak self] in
                let isPublic = choices.selectedSegmentIndex == 1
                if isPublic != wasPublic { self?.viewModel.setProfilePublic(isPublic) }
            })
        present(card, animated: true)
    }

    private func chooseTheme() {
        let choices = SlidingCategoryControl(items: ["Chiaro", "Scuro"])
        choices.selectedSegmentIndex = AxooTheme.isDark ? 1 : 0
        let card = AxooConfirmationCard(title: "Tema", message: "Scegli l’aspetto dell’app.",
            actionTitle: "OK", contentView: choices) { [weak self] in
                AxooTheme.setDark(choices.selectedSegmentIndex == 1)
                self?.tableView.reloadData()
            }
        present(card, animated: true)
    }

    private func confirmSignOut() {
        let alert = AxooConfirmationCard(title: AxooOverlayCopy.signOutTitle, message: AxooOverlayCopy.signOutMessage,
            actionTitle: AxooOverlayCopy.signOut, destructive: true) { [weak self] in self?.onSignOut() }
        present(alert, animated: true)
    }
    private func confirmDelete() {
        guard !viewModel.state.isDeletingAccount else { return }
        let confirmation = UITextField()
        confirmation.font = AxooTypography.postBody
        confirmation.textColor = AxooTheme.textPrimary
        confirmation.tintColor = AxooTheme.accent
        confirmation.borderStyle = .roundedRect
        confirmation.autocapitalizationType = .allCharacters
        confirmation.autocorrectionType = .no
        confirmation.spellCheckingType = .no
        confirmation.placeholder = "ELIMINA"
        confirmation.accessibilityIdentifier = "delete-account-confirmation"
        confirmation.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        let alert = AxooConfirmationCard(title: AxooOverlayCopy.deleteAccountTitle,
            message: AxooOverlayCopy.deleteAccountMessage,
            actionTitle: AxooOverlayCopy.deleteAccount, destructive: true, contentView: confirmation) { [weak self, weak confirmation] in
            guard let self else { return }
            self.viewModel.deleteAccount(confirmation: confirmation?.text ?? "") { [weak self] in self?.onSignOut() }
        }
        present(alert, animated: true)
    }
    private func render() {
        guard isViewLoaded else { return }
        tableView.reloadData()
        if let notice = viewModel.state.notice, presentedViewController == nil {
            viewModel.clearNotice()
            presentError(notice)
        }
    }
}
