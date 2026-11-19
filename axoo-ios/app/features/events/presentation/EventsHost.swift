import UIKit

final class EventsHost: AxooViewController, UITableViewDataSource, UITableViewDelegate {
    private let model: EventsViewModel
    private let table = UITableView(frame: .zero, style: .plain)
    private let refresh = UIRefreshControl()
    private let skeleton = SkeletonCardView(type: .notification, rows: 3)
    private let status = UILabel.axoo(color: AxooTheme.textSecondary)
    private let footer = UIStackView()
    private let errorLabel = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    private lazy var more = UIButton.axoo("Altri eventi") { [weak self] in self?.loadMore() }
    private lazy var add: UIButton = {
        let button = UIButton(type: .system)
        button.backgroundColor = AxooTheme.accent; button.tintColor = .white
        button.setImage(AxooIcon.create.image(pointSize: 24), for: .normal)
        button.layer.cornerRadius = 28; button.layer.shadowColor = UIColor.black.cgColor
        button.layer.shadowOpacity = 0.16; button.layer.shadowRadius = 8
        button.layer.shadowOffset = CGSize(width: 0, height: 3)
        button.addTarget(self, action: #selector(create), for: .touchUpInside)
        button.isHidden = true
        return button
    }()
    init(model: EventsViewModel) { self.model = model; super.init() }
    override func viewDidLoad() {
        super.viewDidLoad()
        let heading = UILabel.axoo("Prossimi eventi", font: AxooTypography.statusTitle)
        heading.accessibilityTraits = .header
        navigationItem.leftBarButtonItem = UIBarButtonItem(customView: heading)
        navigationItem.leftBarButtonItem?.hideSharedBackground()
        table.backgroundColor = AxooTheme.surface; table.separatorStyle = .none
        table.dataSource = self; table.delegate = self
        table.rowHeight = UITableView.automaticDimension; table.estimatedRowHeight = 350
        table.contentInset = UIEdgeInsets(top: 6, left: 0, bottom: 12, right: 0)
        table.register(EventCell.self, forCellReuseIdentifier: "event")
        table.accessibilityIdentifier = "events-list"
        refresh.addTarget(self, action: #selector(reload), for: .valueChanged); table.refreshControl = refresh
        view.addSubview(table); table.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            table.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            table.leadingAnchor.constraint(equalTo: view.leadingAnchor), table.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            table.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
        status.textAlignment = .center; status.numberOfLines = 0
        status.accessibilityTraits = .staticText
        more.accessibilityIdentifier = "events-more"
        add.accessibilityIdentifier = "events-add"; add.accessibilityLabel = "Crea evento"
        view.addSubview(add); add.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            add.widthAnchor.constraint(equalToConstant: 56), add.heightAnchor.constraint(equalToConstant: 56),
            add.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
            add.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16)
        ])
        footer.axis = .vertical; footer.spacing = 12
        footer.isLayoutMarginsRelativeArrangement = true
        footer.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 16, leading: 20, bottom: 20, trailing: 20)
        errorLabel.numberOfLines = 0; errorLabel.textAlignment = .center
        footer.addArrangedSubview(errorLabel); footer.addArrangedSubview(more)
        model.onChange = { [weak self] in self?.render() }; render()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
        model.onChange = { [weak self] in self?.render() }; model.refresh()
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated); skeleton.animationEnabled = false; skeleton.updateAnimationVisibility()
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews(); fitFooter()
        add.layer.shadowPath = UIBezierPath(ovalIn: add.bounds).cgPath
        view.bringSubviewToFront(add)
    }
    private func render() {
        guard isViewLoaded else { return }
        add.isHidden = !model.canManage; add.isEnabled = !model.saving
        table.contentInset.bottom = model.canManage ? 88 : 16
        table.verticalScrollIndicatorInsets.bottom = model.canManage ? 88 : 16
        table.reloadData()
        if !model.loading { refresh.endRefreshing() }
        let initialLoading = model.loading && model.items.isEmpty
        skeleton.animationEnabled = initialLoading; skeleton.updateAnimationVisibility()
        if initialLoading { table.backgroundView = skeleton }
        else if model.items.isEmpty {
            status.text = model.error ? "Impossibile caricare gli eventi" : "Nessun evento in programma"
            table.backgroundView = status
        } else { table.backgroundView = nil }
        errorLabel.text = model.saveError ?? (model.error && !model.items.isEmpty ? "Impossibile caricare gli eventi." : nil)
        errorLabel.isHidden = errorLabel.text == nil
        more.configuration?.title = model.error || model.saveError != nil ? "Riprova" : "Altri eventi"
        more.isEnabled = !model.loading && !model.saving
        table.tableFooterView = model.cursor != nil || model.error || model.saveError != nil ? footer : nil
        fitFooter()
    }
    private func fitFooter() {
        guard table.tableFooterView === footer, table.bounds.width > 0 else { return }
        let size = footer.systemLayoutSizeFitting(CGSize(width: table.bounds.width, height: 0),
            withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
        if footer.frame.size != CGSize(width: table.bounds.width, height: size.height) {
            footer.frame = CGRect(x: 0, y: 0, width: table.bounds.width, height: size.height); table.tableFooterView = footer
        }
    }
    @objc private func reload() { model.refresh() }
    private func loadMore() { if model.error || model.saveError != nil { model.refresh() } else { model.loadMore() } }
    @objc private func create() {
        guard model.canManage, !model.saving else { return }
        showEditor()
    }
    private func showEditor(_ event: CampusEvent? = nil) {
        let editor = EventEditorHost(events: model, event: event?.cityEvent(canManage: model.canManage))
        let navigation = AxooNavigationController(rootViewController: editor)
        navigation.modalPresentationStyle = .fullScreen
        present(navigation, animated: true)
    }
    private func menu(_ event: CampusEvent) {
        guard model.canManage, !model.saving else { return }
        let sheet = AxooBottomSheet(title: event.title)
        sheet.addButton("Modifica evento", icon: .create) { [weak self] in self?.showEditor(event) }
        sheet.addButton("Elimina evento", destructive: true, icon: .delete) { [weak self] in
            guard let self else { return }
            present(AxooConfirmationCard(title: "Eliminare l'evento?", message: event.title,
                actionTitle: "Elimina", destructive: true) { [weak self] in self?.model.delete(id: event.id) }, animated: true)
        }
        present(sheet, animated: true)
    }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { model.items.count }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let event = model.items[indexPath.row]
        let cell = table.dequeueReusableCell(withIdentifier: "event", for: indexPath) as! EventCell
        cell.bind(event, canManage: model.canManage)
        cell.onMenu = { [weak self] in self?.menu(event) }
        return cell
    }
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let event = model.items[indexPath.row]
        navigationController?.pushViewController(EventDetailHost(eventID: event.id, viewModel: model), animated: true)
    }

}

private final class EventCell: UITableViewCell {
    private let card = EventCardView()
    var onMenu: () -> Void = {}
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear; selectionStyle = .none
        contentView.addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(longPress(_:))))
        contentView.addSubview(card)
        card.pinEdges(to: contentView, insets: UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16))
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func bind(_ event: CampusEvent, canManage: Bool) {
        card.configure(event.cityEvent(canManage: canManage))
        accessibilityIdentifier = "event-\(event.id)"
    }
    @objc private func longPress(_ gesture: UILongPressGestureRecognizer) {
        if gesture.state == .began { onMenu() }
    }
    override func prepareForReuse() { super.prepareForReuse(); onMenu = {} }
}
