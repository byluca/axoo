import UIKit

class MyPostsHost: AxooViewController, UITableViewDataSource, UITableViewDelegate {
    var onOpenProfile: (String) -> Void = { _ in }
    private let viewModel: MyPostsViewModel
    private let editorService: PostEditorService
    private let makeDetail: (Post) -> PostDetailHost
    private let table = UITableView(frame: .zero, style: .plain)
    private let footer = UIStackView()
    private var measuring: PostCardView?
    private var active = false
    private var rendered: [Post] = []
    private var profileHeader: UIView?
    private var showsPosts = true

    func setProfileHeader(_ header: UIView, showsPosts: Bool) {
        profileHeader = header
        if table.tableHeaderView !== header { table.tableHeaderView = header }
        if self.showsPosts != showsPosts { self.showsPosts = showsPosts; table.reloadData() }
        render()
        fitHeader()
    }
    private func fitHeader() {
        guard let header = profileHeader, table.bounds.width > 0 else { return }
        let height = header.systemLayoutSizeFitting(CGSize(width: table.bounds.width, height: 0),
            withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height
        let size = CGSize(width: table.bounds.width, height: height)
        if table.tableHeaderView !== header || header.frame.size != size {
            header.frame = CGRect(origin: .zero, size: size)
            table.tableHeaderView = header
        }
    }

    init(viewModel: MyPostsViewModel, editorService: PostEditorService,
         makeDetail: @escaping (Post) -> PostDetailHost) {
        self.viewModel = viewModel
        self.editorService = editorService
        self.makeDetail = makeDetail
        super.init()
        title = "I miei post"
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AxooTheme.surface
        installArrowBackButton(testID: "my-posts-back")
        table.backgroundColor = AxooTheme.surface
        table.separatorStyle = .none
        table.showsVerticalScrollIndicator = false
        table.hideTopEdgeEffect()
        table.dataSource = self
        table.delegate = self
        table.estimatedRowHeight = 270
        table.contentInset = UIEdgeInsets(top: 4, left: 0, bottom: 16, right: 0)
        table.accessibilityIdentifier = "my-posts-list"
        table.register(PostCardCell.self, forCellReuseIdentifier: "post")
        view.addSubview(table)
        table.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            table.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            table.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            table.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            table.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        footer.axis = .vertical
        footer.spacing = 12
        footer.isLayoutMarginsRelativeArrangement = true
        footer.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 24, leading: 20, bottom: 24, trailing: 20)
        viewModel.onChange = { [weak self] in self?.render() }
        viewModel.refresh()
        render()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
        active = true
        viewModel.onChange = { [weak self] in self?.render() }
        render()
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        active = false
        updatePlayback()
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        fitHeader()
        fitFooter()
    }
    private func render() {
        guard isViewLoaded else { return }
        if rendered != viewModel.posts {
            let sameIDs = rendered.map(\.id) == viewModel.posts.map(\.id)
            let previous = Dictionary(uniqueKeysWithValues: rendered.map { ($0.id, $0) })
            rendered = viewModel.posts
            if sameIDs {
                for case let cell as PostCardCell in table.visibleCells {
                    guard let id = cell.card?.post.id, let post = rendered.first(where: { $0.id == id }), previous[id] != post else { continue }
                    cell.configure(post: post) { [weak self] in self?.handle(id, $0) }
                    cell.card?.setOptionsSheet { [weak self] sheet in self?.options(id, sheet: sheet) }
                }
                UIView.performWithoutAnimation { table.beginUpdates(); table.endUpdates() }
            } else { table.reloadData() }
        }
        footer.arrangedSubviews.forEach { $0.removeFromSuperview() }
        if !showsPosts {
            fitFooter()
            updatePlayback()
            return
        }
        if (viewModel.loading && viewModel.posts.isEmpty) || viewModel.loadingMore {
            let spinner = UIActivityIndicatorView(style: .medium)
            spinner.startAnimating()
            footer.addArrangedSubview(spinner)
        } else if viewModel.error {
            addStatus("Impossibile caricare i post.")
            footer.addArrangedSubview(UIButton.axoo("Riprova") { [weak self] in self?.viewModel.retry() })
        } else if viewModel.posts.isEmpty && profileHeader == nil {
            addStatus("Non hai ancora pubblicato post")
        } else if viewModel.nextCursor != nil {
            footer.addArrangedSubview(UIButton.axoo("Carica altri post") { [weak self] in self?.viewModel.loadMore() })
        }
        fitFooter()
        updatePlayback()
        if let notice = viewModel.notice, presentedViewController == nil, view.window != nil {
            viewModel.clearNotice()
            presentError(notice)
        }
    }
    private func addStatus(_ text: String) {
        let label = UILabel.axoo(text, color: AxooTheme.textSecondary)
        label.textAlignment = .center
        footer.addArrangedSubview(label)
    }
    private func fitFooter() {
        guard table.bounds.width > 0 else { return }
        let height = footer.systemLayoutSizeFitting(CGSize(width: table.bounds.width, height: 0),
            withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height
        if table.tableFooterView !== footer || footer.frame.size != CGSize(width: table.bounds.width, height: height) {
            footer.frame = CGRect(x: 0, y: 0, width: table.bounds.width, height: height)
            table.tableFooterView = footer
        }
    }
    private func updatePlayback() {
        for case let cell as PostCardCell in table.visibleCells { cell.card?.updateMediaPlayback(isActive: active) }
    }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { showsPosts ? viewModel.posts.count : 0 }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = table.dequeueReusableCell(withIdentifier: "post", for: indexPath) as! PostCardCell
        let post = viewModel.posts[indexPath.row]
        cell.configure(post: post) { [weak self] action in self?.handle(post.id, action) }
        cell.card?.setOptionsSheet { [weak self] sheet in self?.options(post.id, sheet: sheet) }
        cell.card?.updateMediaPlayback(isActive: active)
        return cell
    }
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        let post = viewModel.posts[indexPath.row]
        if measuring == nil {
            let card = PostCardView(post: post, loadsMedia: false) { _ in }
            card.isHidden = true
            table.addSubview(card)
            measuring = card
        }
        measuring?.configure(post)
        return measuring?.sizeThatFits(CGSize(width: max(1, table.bounds.width - 24), height: .greatestFiniteMagnitude)).height ?? 0
    }
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        updatePlayback()
        if active, showsPosts, !viewModel.error, table.isDragging || table.isDecelerating,
           table.contentOffset.y + table.bounds.height > table.contentSize.height - 250 { viewModel.loadMore() }
    }
    private func handle(_ id: String, _ action: PostCardView.Action) {
        guard let post = viewModel.posts.first(where: { $0.id == id }) else { return }
        switch action {
        case .author: onOpenProfile(post.userID)
        case .open, .comments:
            let detail = makeDetail(post)
            if case .comments = action { detail.setFocusComments(true) } else { detail.setFocusComments(false) }
            if let navigationController, navigationController.viewControllers.contains(where: { $0 === detail }) {
                navigationController.popToViewController(detail, animated: true)
            } else { navigationController?.pushViewController(detail, animated: true) }
        case .vote(let direction): viewModel.vote(id, direction: direction)
        case .pollVote(let optionID): viewModel.votePoll(postID: id, optionID: optionID)
        case .saveSticker: StickerSaveSheet.present(from: self, sticker: post.personalSticker, api: editorService.mediaAPI)
        case .copyText: UIPasteboard.general.string = post.body
        case .options: break
        }
    }
    private func options(_ id: String, sheet: AxooBottomSheet) {
        guard let post = viewModel.posts.first(where: { $0.id == id }) else { return }
        let detail = makeDetail(post)
        if !detail.viewModel.ownsPost {
            sheet.addButton("Nascondi per me", icon: .close) { [weak self] in
                self?.performPostAction(detail) { detail.viewModel.hidePost() }
            }
            sheet.addButton(AxooOverlayCopy.reportPost, icon: .flag) { [weak self] in
                guard let self else { return }
                present(AxooReportSheet(submit: { reason in
                    try await detail.viewModel.submitReport(.post, reason: reason.rawValue)
                }), animated: true)
            }
            sheet.addButton(AxooOverlayCopy.blockAuthor, destructive: true, icon: .block) { [weak self] in
                guard let self else { return }
                let confirmation = AxooConfirmationCard(title: AxooOverlayCopy.blockTitle,
                    message: AxooOverlayCopy.blockMessage, actionTitle: AxooOverlayCopy.block, destructive: true) {
                    self.performPostAction(detail) { detail.viewModel.block(post.userID) }
                }
                present(confirmation, animated: true)
            }
            return
        }
        if post.poll == nil, post.canEdit {
            sheet.addButton(AxooOverlayCopy.editPost, icon: .edit) { [weak self] in
                guard let self else { return }
                let editor = PostEditorHost(viewModel: PostEditorViewModel(service: editorService, post: post)) { [weak self] result in
                    guard let self else { return }
                    let updated = result.applying(to: viewModel.posts.first(where: { $0.id == id }) ?? post)
                    viewModel.update(updated)
                    viewModel.onPostChange(updated)
                    detail.viewModel.synchronize(post: updated)
                }
                navigationController?.pushViewController(editor, animated: true)
            }
        }
        sheet.addButton(AxooOverlayCopy.deletePost, destructive: true, icon: .delete) { [weak self] in
            guard let self, presentedViewController == nil else { return }
            let alert = AxooConfirmationCard(title: AxooOverlayCopy.deletePostTitle, message: AxooOverlayCopy.deletePostMessage,
                actionTitle: AxooOverlayCopy.delete, destructive: true) { [weak self] in
                    self?.performPostAction(detail) { detail.viewModel.deletePost() }
                }
            present(alert, animated: true)
        }
    }
    private func performPostAction(_ detail: PostDetailHost, _ action: () -> Void) {
        action()
        Task { [weak self] in
            await detail.viewModel.actionTask?.value
            guard let self, let notice = detail.viewModel.state.notice, active, presentedViewController == nil else { return }
            detail.viewModel.clearNotice()
            presentError(notice)
        }
    }

}
