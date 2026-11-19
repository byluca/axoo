import UIKit

final class ModerationHost: UITableViewController {
    let viewModel: ModerationViewModel

    init(viewModel: ModerationViewModel) {
        self.viewModel = viewModel
        super.init(style: .insetGrouped)
        title = "Moderazione"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override func viewDidLoad() {
        super.viewDidLoad()
        installArrowBackButton(testID: "moderation-back")
        tableView.hideTopEdgeEffect()
        tableView.showsVerticalScrollIndicator = false
        tableView.backgroundColor = AxooTheme.surface
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 140
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Aggiorna", style: .plain,
            target: self, action: #selector(refresh))
        navigationItem.rightBarButtonItem?.hideSharedBackground()
        refreshControl = UIRefreshControl()
        refreshControl?.addAction(UIAction { [weak self] _ in self?.refresh() }, for: .valueChanged)
        viewModel.onChange = { [weak self] in self?.render() }
        viewModel.load()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isMovingFromParent || navigationController?.isBeingDismissed == true { viewModel.stop() }
    }

    @objc private func refresh() {
        guard !viewModel.isBusy else { refreshControl?.endRefreshing(); return }
        viewModel.load()
    }

    private func render() {
        guard isViewLoaded else { return }
        navigationItem.rightBarButtonItem?.isEnabled = !viewModel.isBusy
        tableView.allowsSelection = !viewModel.isBusy
        if !viewModel.isLoading { refreshControl?.endRefreshing() }
        tableView.reloadData()
    }

    override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        max(1, viewModel.items.count)
    }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        viewModel.isDeciding ? "Salvataggio…" : "Segnalazioni aperte"
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        viewModel.items.isEmpty ? nil : (viewModel.error ?? "Segnalazioni della tua città. Tocca un contenuto per prendere una decisione.")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.backgroundColor = AxooTheme.cardBackground
        cell.textLabel?.font = AxooTypography.sheetOption
        cell.textLabel?.textColor = AxooTheme.textPrimary
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.font = AxooTypography.statusBody
        cell.detailTextLabel?.textColor = AxooTheme.textSecondary
        cell.detailTextLabel?.numberOfLines = 0
        guard path.row < viewModel.items.count else {
            cell.textLabel?.text = viewModel.isLoading ? "Caricamento…" : (viewModel.error ?? "Nessuna segnalazione aperta")
            cell.selectionStyle = .none
            return cell
        }
        let item = viewModel.items[path.row]
        let kind = item.targetType == .post ? "Post" : (item.targetType == .user ? "Profilo" : "Commento")
        cell.textLabel?.text = "\(kind) · @\(item.authorNickname)\n\(item.body.isEmpty ? "Contenuto senza testo" : item.body)"
        var details = ["\(item.reportCount) \(item.reportCount == 1 ? "segnalazione" : "segnalazioni")"]
        details.append(contentsOf: item.reasons.map { AxooReportReason(rawValue: $0)?.title ?? $0 })
        if item.isDeleted { details.append("Eliminato dall’autore") }
        else if item.isHidden { details.append("Già rimosso") }
        cell.detailTextLabel?.text = details.joined(separator: " · ")
        cell.accessoryType = .disclosureIndicator
        cell.accessibilityIdentifier = "moderation-item-\(item.id)"
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true)
        guard !viewModel.isBusy, presentedViewController == nil, path.row < viewModel.items.count else { return }
        let item = viewModel.items[path.row]
        let sheet = AxooBottomSheet(title: "Decidi sul contenuto", message: "\(item.reportCount) segnalazioni · @\(item.authorNickname)")
        if !item.isDeleted {
            sheet.addButton("Rimuovi contenuto", destructive: true, icon: .delete, testID: "moderation-hide") { [weak self] in
                self?.chooseRemovalReason(item)
            }
        }
        sheet.addButton(item.isDeleted || item.isHidden ? "Chiudi segnalazioni" : "Mantieni contenuto", icon: .flag, testID: "moderation-keep") { [weak self] in
            self?.confirm(.keep, item: item)
        }
        present(sheet, animated: true)
    }

    private func chooseRemovalReason(_ item: ModerationItem) {
        guard !viewModel.isBusy, presentedViewController == nil else { return }
        let sheet = AxooBottomSheet(title: "Motivo della rimozione")
        for reason in AxooReportReason.allCases {
            sheet.addButton(reason.title, testID: "moderation-reason-\(reason.rawValue)") { [weak self] in
                self?.confirm(.hide, item: item, reason: reason)
            }
        }
        present(sheet, animated: true)
    }

    private func confirm(_ decision: ModerationDecision, item: ModerationItem, reason: AxooReportReason? = nil) {
        guard !viewModel.isBusy, presentedViewController == nil else { return }
        let removing = decision == .hide
        let alert = AxooConfirmationCard(title: removing ? "Rimuovere il contenuto?" : "Chiudere le segnalazioni?",
            message: removing ? "Il contenuto non sarà più visibile. Motivo: \(reason?.title ?? "")."
                : "Le segnalazioni saranno chiuse senza modificare il contenuto.",
            actionTitle: removing ? "Rimuovi" : "Conferma", destructive: removing) { [weak self] in
                self?.viewModel.decide(decision, item: item, reason: reason)
            }
        present(alert, animated: true)
    }
}
