import UIKit

final class HomeHost: AxooViewController, UIScrollViewDelegate {
    let feed: FeedViewModel
    var onOpenProfile: (String) -> Void = { _ in }
    private let editorService: PostEditorService?
    private let makeDetailScreen: (Post) -> PostDetailHost
    private var renderTask: Task<Void, Never>?

    private lazy var header = HomeHeader { [weak self] in self?.feed.select($0) }
    private let pager = UIScrollView()
    private let bottomFade = BottomFadeView()
    private var feeds: [NativeHomeFeedView] = []
    private var headerHeight: NSLayoutConstraint?
    private var lastWidth: CGFloat = 0

    init(feed: FeedViewModel, editorService: PostEditorService? = nil,
         makeDetailScreen: @escaping (Post) -> PostDetailHost) {
        self.editorService = editorService
        self.feed = feed
        self.makeDetailScreen = makeDetailScreen
        super.init()
        feed.onChange = { [weak self] in self?.renderState() }
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        // The feed paints its grey surface; the status bar shares the white header.
        view.backgroundColor = AxooTheme.background
        [header, pager, bottomFade].forEach { view.addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        headerHeight = header.heightAnchor.constraint(equalToConstant: HomeHeader.minimumHeight)
        headerHeight?.isActive = true
        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: safe.topAnchor),
            header.leadingAnchor.constraint(equalTo: safe.leadingAnchor), header.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            pager.topAnchor.constraint(equalTo: header.bottomAnchor), pager.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            pager.trailingAnchor.constraint(equalTo: safe.trailingAnchor), pager.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            bottomFade.leadingAnchor.constraint(equalTo: pager.leadingAnchor), bottomFade.trailingAnchor.constraint(equalTo: pager.trailingAnchor),
            bottomFade.topAnchor.constraint(equalTo: safe.bottomAnchor, constant: -BottomFadeView.height),
            bottomFade.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        pager.isPagingEnabled = true
        pager.showsHorizontalScrollIndicator = false
        pager.showsVerticalScrollIndicator = false
        pager.contentInsetAdjustmentBehavior = .never
        pager.delegate = self
        let pages = UIStackView()
        pages.axis = .horizontal
        pages.distribution = .fillEqually
        pager.addSubview(pages)
        pages.pinEdges(to: pager.contentLayoutGuide)
        pages.heightAnchor.constraint(equalTo: pager.frameLayoutGuide.heightAnchor).isActive = true
        for tab in HomeFeedTab.allCases {
            let feed = NativeHomeFeedView(feed: self.feed, tab: tab,
                onPostAction: { [weak self] in self?.handle($0, $1) },
                onPostOptions: { [weak self] in self?.postOptions($0, in: $1) })
            feeds.append(feed)
            pages.addArrangedSubview(feed)
            feed.widthAnchor.constraint(equalTo: pager.frameLayoutGuide.widthAnchor).isActive = true
        }
        renderSelection()
        feed.start()
        renderState()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(true, animated: animated)
        feeds.forEach { $0.setNeedsLayout() }
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        headerHeight?.constant = header.sizeThatFits(view.bounds.size).height
        let bottomInset = view.safeAreaInsets.bottom + 16
        for feed in feeds where feed.contentInset.bottom != bottomInset {
            feed.contentInset.bottom = bottomInset
            feed.verticalScrollIndicatorInsets.bottom = bottomInset
        }
        if lastWidth != pager.bounds.width {
            lastWidth = pager.bounds.width
            renderSelection()
        }
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        showNotice()
    }
    private func showNotice() {
        guard let notice = feed.notice, viewIfLoaded?.window != nil,
              navigationController?.topViewController === self || navigationController?.topViewController === parent,
              presentedViewController == nil else { return }
        feed.clearNotice()
        let alert = AxooConfirmationCard(title: nil, message: notice, cancelTitle: nil, actionTitle: AxooOverlayCopy.ok)
        present(alert, animated: true)
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        view.setNeedsLayout()
    }
    private func renderSelection() {
        header.configure(selected: feed.selectedFeed, newUnreadCount: feed.newUnreadCount)
        for feed in feeds {
            feed.isActive = feed.tab == self.feed.selectedFeed
        }
        setContentScrollView(feeds.first { $0.tab == feed.selectedFeed }, for: .bottom)
        guard !pager.isDragging, !pager.isDecelerating else { return }
        let index = HomeFeedTab.allCases.firstIndex(of: feed.selectedFeed) ?? 0
        pager.setContentOffset(CGPoint(x: CGFloat(index) * pager.bounds.width, y: 0), animated: false)
    }
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        guard pager.bounds.width > 0 else { return }
        let index = min(1, max(0, Int((pager.contentOffset.x / pager.bounds.width).rounded())))
        feed.select(HomeFeedTab.allCases[index])
    }
    private func handle(_ id: String, _ action: PostCardView.Action) {
        switch action {
        case .author:
            if let post = feed.posts[id] { onOpenProfile(post.userID) }
        case .open, .comments:
            guard let post = feed.posts[id] else { return }
            let focusComments: Bool
            if case .comments = action { focusComments = true } else { focusComments = false }
            let controller = makeDetailScreen(post)
            controller.setFocusComments(focusComments)
            navigationController?.pushViewController(controller, animated: true)
        case .options: break // The button presents its options sheet.
        case .saveSticker: StickerSaveSheet.present(from: self, sticker: feed.posts[id]?.personalSticker, api: editorService?.mediaAPI)
        case .copyText: UIPasteboard.general.string = feed.posts[id]?.body
        case .vote(let direction): feed.vote(postID: id, direction: direction)
        case .pollVote(let optionID): feed.votePoll(postID: id, optionID: optionID)
        }
    }
    func postOptions(_ post: Post, in sheet: AxooBottomSheet) {
        let detail = makeDetailScreen(post)
        let owner = detail.viewModel.ownsPost
        if owner {
            if post.poll == nil, post.canEdit, let service = editorService {
                sheet.addButton(AxooOverlayCopy.editPost, icon: .edit) { [weak self] in
                    guard let self else { return }
                    let model = PostEditorViewModel(service: service, post: post)
                    let editor = PostEditorHost(viewModel: model) { [weak self] result in
                        guard let self, let current = self.feed.posts[post.id] else { return }
                        self.feed.applyEditorResult(result, post: current)
                        if let updated = self.feed.posts[post.id] { detail.viewModel.synchronize(post: updated) }
                    }
                    self.navigationController?.pushViewController(editor, animated: true)
                }
            }
            sheet.addButton(AxooOverlayCopy.deletePost, destructive: true, icon: .delete) { [weak self] in
                self?.confirmPostAction(AxooOverlayCopy.deletePostTitle, message: AxooOverlayCopy.deletePostMessage) {
                    detail.viewModel.deletePost()
                }
            }
        } else {
            sheet.addButton("Nascondi per me", icon: .close) { detail.viewModel.hidePost() }
            sheet.addButton(AxooOverlayCopy.reportPost, icon: .flag) { [weak self] in
                guard let self else { return }
                let report = AxooReportSheet(submit: { reason in
                    try await detail.viewModel.submitReport(.post, reason: reason.rawValue)
                })
                self.presentAfterOptions(report)
            }
            sheet.addButton(AxooOverlayCopy.blockAuthor, destructive: true, icon: .block) { [weak self] in
                guard let self else { return }
                let alert = AxooConfirmationCard(title: AxooOverlayCopy.blockTitle,
                    message: AxooOverlayCopy.blockMessage, actionTitle: AxooOverlayCopy.block, destructive: true) {
                    detail.viewModel.block(post.userID)
                }
                self.presentAfterOptions(alert)
            }
        }
    }
    private func confirmPostAction(_ title: String, message: String, action: @escaping () -> Void) {
        let alert = AxooConfirmationCard(title: title, message: message, actionTitle: AxooOverlayCopy.delete, destructive: true, action: action)
        presentAfterOptions(alert)
    }
    private func presentAfterOptions(_ controller: UIViewController) {
        guard presentedViewController == nil, viewIfLoaded?.window != nil,
              navigationController?.topViewController === self || navigationController?.topViewController === parent else { return }
        present(controller, animated: true)
    }
    private func renderState() {
        guard isViewLoaded, renderTask == nil else { return }
        renderTask = Task { [weak self] in
            await Task.yield()
            guard let self else { return }
            renderTask = nil
            feeds.forEach { $0.render() }
            renderSelection()

            showNotice()
        }
    }

}
