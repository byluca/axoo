import UIKit

final class NotificationsHost: AxooViewController, UITableViewDataSource, UITableViewDelegate {
    private let viewModel: NotificationsViewModel
    var onOpen: (AppNotification) -> Void = { _ in }
    private let table = UITableView(frame: .zero, style: .plain)
    private let footer = UIStackView()
    private let bottomFade = BottomFadeView()
    private let skeleton = SkeletonCardView(type: .notification, rows: 5)
    private let moreSkeleton = SkeletonCardView(type: .notification)
    private lazy var skeletonHeight = skeleton.heightAnchor.constraint(equalToConstant: 1)
    private lazy var moreSkeletonHeight = moreSkeleton.heightAnchor.constraint(equalToConstant: 122)
    private let emptyLabel = UILabel.axoo("Non hai ancora notifiche", color: AxooTheme.textSecondary)
    private var rendered: [AppNotification] = []
    private var active = false

    init(viewModel: NotificationsViewModel) {
        self.viewModel = viewModel
        super.init()
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        let heading = UILabel.axoo("Notifiche", font: AxooTypography.statusTitle)
        heading.accessibilityTraits = .header
        navigationItem.leftBarButtonItem = UIBarButtonItem(customView: heading)
        navigationItem.leftBarButtonItem?.hideSharedBackground()
        table.backgroundColor = AxooTheme.surface
        table.separatorStyle = .none
        table.showsVerticalScrollIndicator = false
        table.hideTopEdgeEffect()
        table.dataSource = self
        table.delegate = self
        table.rowHeight = UITableView.automaticDimension
        table.estimatedRowHeight = 110
        table.contentInset = UIEdgeInsets(top: 6, left: 0, bottom: 12, right: 0)
        table.accessibilityIdentifier = "notifications-list"
        table.register(NotificationCell.self, forCellReuseIdentifier: "notification")
        view.addSubview(table)
        table.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            table.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            table.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            table.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            table.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        view.addSubview(bottomFade)
        bottomFade.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            bottomFade.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bottomFade.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bottomFade.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -BottomFadeView.height),
            bottomFade.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        emptyLabel.textAlignment = .center
        emptyLabel.isHidden = true
        view.addSubview(emptyLabel)
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            emptyLabel.centerXAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.safeAreaLayoutGuide.centerYAnchor),
            emptyLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
            emptyLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24)
        ])
        footer.axis = .vertical
        footer.spacing = 12
        footer.isLayoutMarginsRelativeArrangement = true
        footer.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 24, leading: 20, bottom: 24, trailing: 20)
        viewModel.onChange = { [weak self] in self?.render() }
        render()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
        active = true
        updatePlayback()
        viewModel.open()
        render()
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        viewModel.acknowledge()
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        active = false
        updatePlayback()
        updateSkeletons()
        viewModel.close()
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); fitFooter() }
    private func render() {
        guard isViewLoaded else { return }
        if rendered != viewModel.items { rendered = viewModel.items; table.reloadData() }
        emptyLabel.isHidden = viewModel.loading || viewModel.loadingMore || viewModel.error || !viewModel.items.isEmpty
        footer.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let initialLoading = viewModel.loading && viewModel.items.isEmpty
        footer.directionalLayoutMargins = initialLoading || viewModel.loadingMore
            ? NSDirectionalEdgeInsets(top: 0, leading: 0, bottom: 12, trailing: 0)
            : NSDirectionalEdgeInsets(top: 24, leading: 20, bottom: 24, trailing: 20)
        if initialLoading {
            footer.addArrangedSubview(skeleton)
            skeletonHeight.isActive = true
        } else if viewModel.loadingMore {
            footer.addArrangedSubview(moreSkeleton)
            moreSkeletonHeight.isActive = true
        } else if viewModel.loading {
            // The existing list stays visible during refresh.
        } else if viewModel.error {
            addStatus("Impossibile caricare le notifiche.")
            footer.addArrangedSubview(UIButton.axoo("Riprova") { [weak self] in self?.viewModel.retry() })
        } else if viewModel.items.isEmpty { /* The empty label is centered in the safe area. */ }
        else if viewModel.cursor != nil {
            footer.addArrangedSubview(UIButton.axoo("Carica altre notifiche") { [weak self] in self?.viewModel.loadMore() })
        }
        fitFooter()
        updateSkeletons()
        table.layoutIfNeeded()
        if active, view.window != nil { viewModel.acknowledge() }
    }
    private func updateSkeletons() {
        for placeholder in [skeleton, moreSkeleton] {
            placeholder.animationEnabled = active && placeholder.superview != nil
            placeholder.updateAnimationVisibility()
        }
    }
    private func addStatus(_ message: String) {
        let label = UILabel.axoo(message, color: AxooTheme.textSecondary)
        label.textAlignment = .center
        footer.addArrangedSubview(label)
    }
    private func fitFooter() {
        guard table.bounds.width > 0 else { return }
        if skeleton.superview === footer {
            skeletonHeight.constant = skeleton.sizeThatFits(CGSize(width: table.bounds.width, height: 0)).height
        }
        let height = footer.systemLayoutSizeFitting(CGSize(width: table.bounds.width, height: 0),
            withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height
        if table.tableFooterView !== footer || footer.frame.size != CGSize(width: table.bounds.width, height: height) {
            footer.frame = CGRect(x: 0, y: 0, width: table.bounds.width, height: height)
            table.tableFooterView = footer
        }
    }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { viewModel.items.count }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = table.dequeueReusableCell(withIdentifier: "notification", for: indexPath) as! NotificationCell
        cell.configure(viewModel.items[indexPath.row])
        cell.setPlaybackEnabled(active)
        return cell
    }
    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        (cell as? NotificationCell)?.setPlaybackEnabled(active)
    }
    func tableView(_ tableView: UITableView, didEndDisplaying cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        (cell as? NotificationCell)?.setPlaybackEnabled(false)
    }
    private func updatePlayback() {
        table.visibleCells.forEach { ($0 as? NotificationCell)?.setPlaybackEnabled(active) }
    }
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) { onOpen(viewModel.items[indexPath.row]) }
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate()
        if active, !viewModel.error, table.isDragging || table.isDecelerating,
           table.contentOffset.y + table.bounds.height > table.contentSize.height - 250 { viewModel.loadMore() }
    }
}

private final class NotificationCell: UITableViewCell {
    private let card = UIView()
    private let icon = UIImageView()
    private let message = UILabel.axoo()
    private let preview = EmojiTextView(readOnly: true)
    private let time = UILabel.axoo(color: AxooTheme.textSecondary)
    private let dot = UIView()

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        card.layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
        card.backgroundColor = AxooTheme.cardBackground
        card.layer.cornerRadius = 20
        card.layer.borderWidth = 1
        card.layer.borderColor = AxooTheme.cardBorder.cgColor
        contentView.addSubview(card)
        card.translatesAutoresizingMaskIntoConstraints = false
        let text = UIStackView(arrangedSubviews: [message, preview, time])
        text.axis = .vertical
        text.spacing = 4
        text.setCustomSpacing(6, after: preview)
        let row = UIStackView(arrangedSubviews: [icon, text, dot])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 14
        card.addSubview(row)
        row.translatesAutoresizingMaskIntoConstraints = false
        icon.contentMode = .scaleAspectFit
        message.font = UIFont(name: "Inter-SemiBold", size: 15) ?? .systemFont(ofSize: 15, weight: .semibold)
        preview.font = UIFont(name: "Inter-Regular", size: 14) ?? .systemFont(ofSize: 14)
        preview.textColor = AxooTheme.textSecondary
        preview.isUserInteractionEnabled = false
        preview.playbackEnabled = false
        preview.textContainer.maximumNumberOfLines = 2
        preview.textContainer.lineBreakMode = .byTruncatingTail
        time.font = AxooTypography.postMetadata
        dot.backgroundColor = AxooTheme.accent
        dot.layer.cornerRadius = 3
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 5),
            card.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -5),
            card.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            card.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            row.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            row.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14),
            row.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            row.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            icon.widthAnchor.constraint(equalToConstant: 28), icon.heightAnchor.constraint(equalToConstant: 28),
            dot.widthAnchor.constraint(equalToConstant: 6), dot.heightAnchor.constraint(equalToConstant: 6)
        ])
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(_ item: AppNotification) {
        message.text = item.message
        preview.setDocument(item.previewDocument)
        preview.isHidden = item.preview.isEmpty
        time.text = APIJSON.date(item.createdAt).map(TimeAgo.format) ?? ""
        let notificationIcon: AxooIcon
        switch item.kind {
        case "post_appreciation": notificationIcon = .postUp
        case "comment_appreciation": notificationIcon = .commentUp
        case "comment_reply": notificationIcon = .reply
        default: notificationIcon = .comment
        }
        icon.image = notificationIcon.image(pointSize: 28)
        icon.tintColor = item.seenAt == nil ? AxooTheme.accent : AxooTheme.textSecondary
        dot.isHidden = item.seenAt != nil
        accessibilityIdentifier = "notification-\(item.id)"
    }
    func setPlaybackEnabled(_ value: Bool) { preview.playbackEnabled = value }
    override func prepareForReuse() {
        super.prepareForReuse()
        preview.playbackEnabled = false
        preview.setDocument(EmojiDocument(text: ""))
    }
}
