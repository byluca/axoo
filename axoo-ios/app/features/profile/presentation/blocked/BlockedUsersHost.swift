import UIKit

final class BlockedUsersHost: UITableViewController {
    let viewModel: ProfileViewModel
    private var blocked: [AccountProfile] { viewModel.state.blocked }
    private var isLoading: Bool { viewModel.state.isLoadingBlocked }
    private var isOperating: Bool { viewModel.state.isUnblocking }
    init(viewModel: ProfileViewModel) {
        self.viewModel = viewModel
        super.init(style: .insetGrouped)
        title = "Utenti bloccati"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func viewDidLoad() {
        super.viewDidLoad()
        installArrowBackButton(testID: "settings-page-back", animated: false)
        tableView.hideTopEdgeEffect()
        tableView.showsVerticalScrollIndicator = false
        tableView.backgroundColor = AxooTheme.surface
        refreshControl = UIRefreshControl()
        refreshControl?.addAction(UIAction { [weak self] _ in
            guard let self, !self.isOperating else { self?.refreshControl?.endRefreshing(); return }
            Haptics.vibrate()
            self.load()
        }, for: .valueChanged)
        bindViewModel()
        load()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        bindViewModel()
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isMovingFromParent || navigationController?.isBeingDismissed == true { viewModel.stopBlocked() }
    }
    private func bindViewModel() {
        viewModel.onChange = { [weak self] in self?.render() }
        render()
    }
    private func render() {
        guard isViewLoaded else { return }
        tableView.reloadData()
        if !isLoading { refreshControl?.endRefreshing() }
        if let notice = viewModel.state.notice, presentedViewController == nil {
            viewModel.clearNotice()
            presentError(notice)
        }
    }
    private func load() { viewModel.loadBlocked() }
    override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        max(1, blocked.count)
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.backgroundColor = AxooTheme.cardBackground
        if path.row < blocked.count {
            let item = blocked[path.row]
            cell.textLabel?.text = item.nickname
            cell.detailTextLabel?.text = item.universityName
            let menu = UIButton.icon(.menu) { [weak self] in self?.confirmUnblock(item) }
            menu.tintColor = AxooTheme.textSecondary
            menu.imageView?.transform = CGAffineTransform(rotationAngle: .pi / 2)
            menu.frame.size = CGSize(width: 44, height: 44)
            menu.accessibilityIdentifier = "blocked-menu-\(item.id)"
            cell.accessoryView = menu
        } else {
            cell.backgroundColor = .clear
            cell.selectionStyle = .none
            cell.textLabel?.font = AxooTypography.sheetOption
            cell.textLabel?.textColor = AxooTheme.textSecondary
            cell.textLabel?.text = isLoading ? "Caricamento…" : "Nessun utente bloccato"
        }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true)
    }
    override func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt path: IndexPath) -> UISwipeActionsConfiguration? {
        if path.row < blocked.count {
            let id = blocked[path.row].id
            return UISwipeActionsConfiguration(actions: [UIContextualAction(style: .normal, title: AxooOverlayCopy.unblock) { [weak self] _, _, done in
                guard let self else { done(false); return }
                self.unblock(id, completion: done)
            }])
        }
        return nil
    }
    private func confirmUnblock(_ user: AccountProfile) {
        guard !isOperating else { return }
        let alert = AxooConfirmationCard(title: "Sbloccare \(user.nickname)?",
            message: AxooOverlayCopy.unblockMessage, actionTitle: AxooOverlayCopy.unblock) { [weak self] in
            self?.unblock(user.id)
        }
        present(alert, animated: true)
    }
    func unblock(_ id: String, completion: @escaping (Bool) -> Void = { _ in }) {
        viewModel.unblock(id, completion: completion)
    }
}
