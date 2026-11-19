import UIKit

final class NativeHomeFeedView: UITableView, UITableViewDataSource, UITableViewDelegate {
    private let feed: FeedViewModel
    let tab: HomeFeedTab
    private let onPostOptions: (Post, AxooBottomSheet) -> Void
    private let onPostAction: (String, PostCardView.Action) -> Void
    private var axooRefresh: AxooRefresh?
    private var refreshTask: Task<Void, Never>?
    private var ids: [String] = []
    private var renderedPosts: [String: Post] = [:]
    private var measuringCard: PostCardView?
    private let footer = UIStackView()
    private let loadingSkeleton = SkeletonCardView(type: .post, rows: 4)
    private lazy var skeletonHeight = loadingSkeleton.heightAnchor.constraint(equalToConstant: 1)
    private var lastWidth: CGFloat = 0
    private var sizes: [String: CGFloat] = [:]
    var isActive = false { didSet { axooRefresh?.isVisible = isActive; updateSkeletons(); updateMediaPlayback() } }

    init(feed: FeedViewModel, tab: HomeFeedTab, onPostAction: @escaping (String, PostCardView.Action) -> Void,
         onPostOptions: @escaping (Post, AxooBottomSheet) -> Void) {
        self.feed = feed
        self.onPostOptions = onPostOptions
        self.tab = tab
        self.onPostAction = onPostAction
        super.init(frame: .zero, style: .plain)
        backgroundColor = AxooTheme.surface
        contentInsetAdjustmentBehavior = .never
        separatorStyle = .none
        showsVerticalScrollIndicator = false
        dataSource = self
        delegate = self
        estimatedRowHeight = 270
        contentInset = UIEdgeInsets(top: 4, left: 0, bottom: 16, right: 0)
        accessibilityIdentifier = "feed-scroll-\(tab.rawValue)"
        register(PostCardCell.self, forCellReuseIdentifier: "post")
        axooRefresh = AxooRefresh(scrollView: self) { [weak self] in self?.refreshFeed() }
        footer.axis = .vertical
        footer.spacing = 12
        footer.isLayoutMarginsRelativeArrangement = true
        footer.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 24, leading: 16, bottom: 24, trailing: 16)
        loadingSkeleton.accessibilityIdentifier = "feed-loading-\(tab.rawValue)"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    deinit { refreshTask?.cancel() }

    func render() {
        let nextIDs = feed.state(for: tab).postIDs
        let changed = nextIDs.filter { renderedPosts[$0] != feed.posts[$0] }
        renderedPosts = Dictionary(uniqueKeysWithValues: nextIDs.compactMap { id in
            feed.posts[id].map { (id, $0) }
        })
        if ids != nextIDs {
            ids = nextIDs
            sizes.removeAll()
            reloadData()
        } else if !changed.isEmpty {
            changed.forEach { sizes.removeValue(forKey: $0) }
            let rows = ids.enumerated().compactMap { changed.contains($0.element) ? IndexPath(row: $0.offset, section: 0) : nil }
            for path in rows {
                if let cell = cellForRow(at: path) as? PostCardCell, let post = feed.posts[ids[path.row]] {
                    let id = post.id
                    cell.configure(post: post) { [weak self] in self?.onPostAction(id, $0) }
                    cell.card?.setOptionsSheet { [weak self] sheet in
                        guard let self, let current = self.feed.posts[id] else { return }
                        self.onPostOptions(current, sheet)
                    }
                }
            }
            UIView.performWithoutAnimation { self.beginUpdates(); self.endUpdates() }
        }
        footer.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let state = feed.state(for: tab)
        let loading = state.isLoading && ids.isEmpty
        footer.spacing = loading ? 0 : 12
        footer.directionalLayoutMargins = loading
            ? NSDirectionalEdgeInsets(top: 4, leading: 0, bottom: 16, trailing: 0)
            : NSDirectionalEdgeInsets(top: 24, leading: 16, bottom: 24, trailing: 16)
        if loading {
            footer.addArrangedSubview(loadingSkeleton)
            skeletonHeight.isActive = true
        } else if ids.isEmpty && (state.nextCursor == nil || state.errorMessage != nil) {
            status(state.errorMessage == nil ? "Qui è ancora tutto vuoto." : "Feed non trovato", title: true)
            status(state.errorMessage == nil ? "I nuovi post del tuo campus appariranno qui." : "C'è stato un problema di connessione.\nTira verso il basso per riprovare.")
            addRetry(title: state.errorMessage == nil ? "Aggiorna" : "Riprova")
        } else if let error = state.errorMessage {
            status(error)
            addRetry(title: "Riprova")
        } else if state.isLoadingMore {
            let spinner = UIActivityIndicatorView(style: .medium)
            spinner.startAnimating()
            footer.addArrangedSubview(spinner)
            status("Altri post…")
        } else if state.nextCursor != nil {
            footer.addArrangedSubview(UIButton.axoo("Carica altri post") { [weak self] in
                guard let self = self else { return }
                self.feed.loadMore(self.tab)
            })
        } else { status("Hai visto tutti i post") }
        fitFooter()
        updateSkeletons()
        updateMediaPlayback()
    }
    @discardableResult private func status(_ text: String, title: Bool = false) -> UILabel {
        let label = UILabel.axoo(text, font: title ? AxooTypography.statusTitle : AxooTypography.statusBody, color: title ? AxooTheme.textPrimary : AxooTheme.textSecondary)
        label.textAlignment = .center
        footer.addArrangedSubview(label)
        return label
    }
    private func addRetry(title: String) {
        let retry = UIButton.axoo(title) { [weak self] in
            guard let self = self else { return }
            self.feed.retry(self.tab)
        }
        retry.accessibilityIdentifier = "feed-retry"
        footer.addArrangedSubview(retry)
    }
    private func fitFooter() {
        guard bounds.width > 0 else { return }
        if loadingSkeleton.superview === footer {
            skeletonHeight.constant = loadingSkeleton.sizeThatFits(CGSize(width: max(1, bounds.width), height: .greatestFiniteMagnitude)).height
        }
        let height = footer.systemLayoutSizeFitting(CGSize(width: bounds.width, height: 0),
                        withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height
        if tableFooterView !== footer || footer.frame.width != bounds.width || footer.frame.height != height {
            footer.frame = CGRect(x: 0, y: 0, width: bounds.width, height: height)
            tableFooterView = footer
        }
    }
    override func layoutSubviews() {
        if lastWidth != bounds.width {
            lastWidth = bounds.width
            sizes.removeAll()
            reloadData()
        }
        super.layoutSubviews()
        fitFooter()
        updateSkeletons()
    }

    private func updateSkeletons() {
        for case let skeleton as SkeletonCardView in footer.arrangedSubviews {
            skeleton.animationEnabled = isActive
            skeleton.updateAnimationVisibility()
        }
    }
    private func updateMediaPlayback() {
        for case let cell as PostCardCell in visibleCells {
            cell.card?.updateMediaPlayback(isActive: isActive)
        }
    }
    @objc private func refreshFeed() {
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            guard let self = self else { return }
            await self.feed.refresh(self.tab)
            self.axooRefresh?.endRefreshing()
        }
    }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { ids.count }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = dequeueReusableCell(withIdentifier: "post", for: indexPath) as! PostCardCell
        let id = ids[indexPath.row]
        if let post = feed.posts[id] {
            cell.configure(post: post) { [weak self] action in self?.onPostAction(id, action) }
            cell.card?.setOptionsSheet { [weak self] sheet in
                guard let self, let current = self.feed.posts[id] else { return }
                self.onPostOptions(current, sheet)
            }
            cell.card?.updateMediaPlayback(isActive: isActive)
        }
        return cell
    }
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        let id = ids[indexPath.row]
        if let height = sizes[id] { return height }
        guard let post = feed.posts[id] else { return 0 }
        let measuring: PostCardView
        if let existing = measuringCard {
            measuring = existing
            measuring.configure(post)
        } else {
            measuring = PostCardView(post: post, loadsMedia: false) { _ in }
            measuring.isHidden = true
            // Keep one measurement view with this window's traits instead of allocating one per row.
            addSubview(measuring)
            measuringCard = measuring
        }
        let height = measuring.sizeThatFits(CGSize(width: max(1, bounds.width - 24), height: .greatestFiniteMagnitude)).height
        sizes[id] = height
        return height
    }
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        axooRefresh?.update()
        updateSkeletons()
        updateMediaPlayback()
        guard isActive, !ids.isEmpty, isDragging || isDecelerating,
              contentOffset.y + bounds.height > contentSize.height - 250 else { return }
        feed.loadMore(tab)
    }
}
