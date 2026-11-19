import UIKit

private struct DetailListState: Equatable {
    let post: Post
    let items: [CommentItem]
    var rows: [CommentRow] { CommentThread.flatten(items) }
    let loading: Bool
    let refreshing: Bool
    let error: String?
    let followID: String?
    init(_ state: PostDetailState) {
        post = state.post; items = state.items
        loading = state.isLoading; refreshing = state.isRefreshing; error = state.loadError
        followID = state.followCommentID
    }
}

final class PostDetailHost: AxooViewController, UITableViewDelegate, UIGestureRecognizerDelegate {
    private enum RowID: Hashable { case post, header, status; case comment(String) }
    private let editorService: PostEditorService?
    private let detailTitle: String
    let viewModel: CommentsViewModel
    var onOpenProfile: (String) -> Void = { _ in }
    private var state: PostDetailState { viewModel.state }

    let card: PostCardView
    private lazy var backButton = ArrowBackButton { [weak self] in self?.back() }
    private let table = UITableView(frame: .zero, style: .plain)
    private lazy var backSwipe = UISwipeGestureRecognizer(target: self, action: #selector(swipeBack))
    private var axooRefresh: AxooRefresh?
    private let composer = CommentComposerView()
    private let bottomFade = BottomFadeView()
    private let header = PostDetailCommentsHeader()
    private let status = PostDetailStatusView()
    private let measureComment = CommentView()
    private let editingScrim = UIView()
    private var composerHeight: NSLayoutConstraint?
    private var composerBottom: NSLayoutConstraint?
    private var preserveInputViewport = false
    private var keyboardTransition: UUID?
    private var dataSource: UITableViewDiffableDataSource<Int, RowID>!
    private var rendered: DetailListState?
    private var pending: DetailListState?
    private var applying = false
    private var rowsByID: [String: CommentRow] = [:]
    private var sizes: [String: CGFloat] = [:]
    private var lastWidth: CGFloat = 0
    private var lastTableHeight: CGFloat = 0
    private var anchor: (id: RowID, offset: CGFloat, fromBottom: Bool)?
    private var notificationCommentID: String?
    private var initialComments: Bool
    private var followedID: String?
    private var previousEditing = false
    private var returningFromDetail = false
    private var mediaPlaybackActive = false
    private var renderTask: Task<Void, Never>?

    init(post: Post, repository: CommentRepository, title: String = "Post", focusComments: Bool = false, editorService: PostEditorService? = nil) {
        self.editorService = editorService
        self.detailTitle = title == "Post" ? "" : title
        let model = CommentsViewModel(post: post, repository: repository)
        self.viewModel = model
        initialComments = focusComments
        followedID = model.state.followCommentID
        card = PostCardView(post: model.state.post, presentation: .detail) { _ in }
        super.init()
        self.title = detailTitle
        viewModel.onChange = { [weak self] in self?.scheduleRender() }
        viewModel.onEvent = { [weak self] event in
            guard let self else { return }
            switch event {
            case .focusComposer: self.requestComposerFocus()
            case .commentSent:
                Haptics.commentSent()
                AppSounds.commentSent()
            case .editSubmitted: Haptics.vibrate()
            case .sendFailed: self.followedID = nil
            }
        }
    }
    private func openMediaPicker(sticker: Bool) {
        guard state.editingID == nil, !state.isPostDeleted, presentedViewController == nil else { return }
        composer.textView.resignFirstResponder()
        let picker = RemoteMediaPickerScreen(api: editorService?.mediaAPI, kind: sticker ? .sticker : .gif)
        picker.onSelect = { [weak self, weak picker] media in
            picker?.onSelect = nil
            picker?.dismiss(animated: true) { [weak self] in self?.viewModel.sendMedia(media) }
        }
        picker.modalPresentationStyle = .pageSheet
        if let sheet = picker.sheetPresentationController {
            sheet.preferredCornerRadius = AxooSheetStyle.cornerRadius
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
            sheet.prefersScrollingExpandsWhenScrolledToEdge = true
        }
        present(picker, animated: true)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        AppSounds.prepare()
        backSwipe.direction = .right
        backSwipe.delegate = self
        view.addGestureRecognizer(backSwipe)
        if let interactivePop = navigationController?.interactivePopGestureRecognizer {
            backSwipe.require(toFail: interactivePop)
        }
        view.backgroundColor = AxooTheme.surface
        navigationItem.largeTitleDisplayMode = .never
        let barAppearance = UINavigationBarAppearance()
        barAppearance.configureWithOpaqueBackground()
        barAppearance.backgroundColor = AxooTheme.cardBackground
        barAppearance.shadowColor = AxooTheme.cardBorder
        navigationItem.standardAppearance = barAppearance
        navigationItem.scrollEdgeAppearance = barAppearance
        navigationItem.compactAppearance = barAppearance
        backButton.accessibilityIdentifier = "detail-back"
        navigationItem.leftBarButtonItem = UIBarButtonItem(customView: backButton)
        navigationItem.leftBarButtonItem?.hideSharedBackground()
        table.backgroundColor = AxooTheme.surface
        table.contentInsetAdjustmentBehavior = .never
        table.contentInset.top = 4
        table.separatorStyle = .none
        table.estimatedRowHeight = 180
        table.keyboardDismissMode = .onDrag
        table.showsVerticalScrollIndicator = false
        table.accessibilityIdentifier = "detail-list"
        table.delegate = self
        table.register(PostDetailHostCell.self, forCellReuseIdentifier: "post")
        table.register(PostDetailHostCell.self, forCellReuseIdentifier: "header")
        table.register(PostDetailHostCell.self, forCellReuseIdentifier: "status")
        table.register(CommentCell.self, forCellReuseIdentifier: "comment")
        axooRefresh = AxooRefresh(scrollView: table) { [weak self] in self?.viewModel.refresh() }
        // The preview is identified independently of its content and survives scrolling.
        let label = UILabel.axoo("Anteprima · dati di esempio", font: AxooTypography.postMetadata, color: .secondaryLabel)
        label.textAlignment = .center
        label.isHidden = !viewModel.isPreview
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        view.addSubview(table)
        view.addSubview(bottomFade)
        view.addSubview(editingScrim)
        view.addSubview(composer)
        [table, bottomFade, editingScrim, composer].forEach { $0.translatesAutoresizingMaskIntoConstraints = false }
        editingScrim.backgroundColor = UIColor.black.withAlphaComponent(0.18)
        editingScrim.isHidden = true
        composerHeight = composer.heightAnchor.constraint(equalToConstant: 84)
        composerHeight?.isActive = true
        let safe = view.safeAreaLayoutGuide
        composerBottom = composer.bottomAnchor.constraint(equalTo: safe.bottomAnchor)
        composerBottom?.isActive = true
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: safe.topAnchor), label.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            label.trailingAnchor.constraint(equalTo: safe.trailingAnchor), label.heightAnchor.constraint(equalToConstant: viewModel.isPreview ? 24 : 0),
            table.topAnchor.constraint(equalTo: label.bottomAnchor), table.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            table.trailingAnchor.constraint(equalTo: safe.trailingAnchor), table.bottomAnchor.constraint(equalTo: composer.bottomAnchor),
            composer.leadingAnchor.constraint(equalTo: safe.leadingAnchor), composer.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            bottomFade.leadingAnchor.constraint(equalTo: composer.leadingAnchor), bottomFade.trailingAnchor.constraint(equalTo: composer.trailingAnchor),
            bottomFade.bottomAnchor.constraint(equalTo: composer.bottomAnchor), bottomFade.heightAnchor.constraint(equalToConstant: BottomFadeView.height),
            editingScrim.topAnchor.constraint(equalTo: table.topAnchor), editingScrim.bottomAnchor.constraint(equalTo: composer.topAnchor),
            editingScrim.leadingAnchor.constraint(equalTo: table.leadingAnchor), editingScrim.trailingAnchor.constraint(equalTo: table.trailingAnchor)
        ])
        measureComment.isHidden = true
        view.addSubview(measureComment)
        dataSource = UITableViewDiffableDataSource<Int, RowID>(tableView: table) { [weak self] table, path, id in
            self?.cell(table, path: path, id: id)
        }
        status.onRetry = { [weak self] in self?.viewModel.refresh() }
        card.onAction = { [weak self] action in
            switch action {
            case .author:
                if let self { self.onOpenProfile(self.state.post.userID) }
            case .vote(let direction): self?.viewModel.votePost(direction)
            case .pollVote(let optionID): self?.viewModel.votePoll(optionID)
            case .comments: self?.showComments()
            case .saveSticker:
                if let self { StickerSaveSheet.present(from: self, sticker: state.post.personalSticker, api: editorService?.mediaAPI) }
            case .copyText: UIPasteboard.general.string = self?.state.post.body
            case .open: break
            case .options: break // The button presents its options sheet.
            }
        }
        card.setOptionsSheet { [weak self] sheet in self?.postOptions(in: sheet) }
        composer.onChange = { [weak self] in self?.viewModel.updateDraft(text: $0.text, selection: $1, entities: $0.entities) }
        composer.onMediaRequested = { [weak self] in self?.openMediaPicker(sticker: $0) }
        composer.onSend = { [weak self] in self?.viewModel.send() }
        composer.onCancelReply = { [weak self] in self?.viewModel.cancelReply() }
        composer.onActivate = { [weak self] in self?.prepareInputAnchor() }
        composer.onHeightChange = { [weak self] in
            self?.captureAnchor()
            self?.view.setNeedsLayout()
        }
        NotificationCenter.default.addObserver(self, selector: #selector(keyboardWillChange(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification, object: nil)
        enqueue(DetailListState(state))
        renderChrome(state)
    }
    deinit { NotificationCenter.default.removeObserver(self) }
    @objc private func keyboardWillChange(_ note: Notification) {
        guard view.window != nil,
              let info = note.userInfo,
              let frame = info[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
        view.layoutIfNeeded()
        captureAnchor()
        let converted = view.convert(frame, from: nil)
        let docked = converted.intersects(view.bounds) && converted.maxY >= view.bounds.maxY
        let overlap = docked ? max(0, view.bounds.maxY - converted.minY - view.safeAreaInsets.bottom) : 0
        composerBottom?.constant = -overlap
        let duration = info[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? 0.25
        let curve = info[UIResponder.keyboardAnimationCurveUserInfoKey] as? UInt ?? 0
        let transition = UUID()
        keyboardTransition = transition
        UIView.animate(withDuration: duration,
                       delay: 0, options: [UIView.AnimationOptions(rawValue: curve << 16), .beginFromCurrentState]) {
            self.view.layoutIfNeeded()
            self.restoreAnchor()
        } completion: { _ in
            if self.keyboardTransition == transition { self.keyboardTransition = nil }
        }
    }
    private func scheduleRender() {
        guard isViewLoaded, renderTask == nil else { return }
        renderTask = Task { [weak self] in
            await Task.yield()
            guard let self else { return }
            renderTask = nil
            enqueue(DetailListState(state))
            renderChrome(state)
        }
    }
    private func requestComposerFocus() {
        guard isViewLoaded else { return }
        Task { [weak self] in
            guard let self else { return }
            await finishPresentedTransition()
            guard navigationController?.topViewController === self, viewIfLoaded?.window != nil else { return }
            composer.render(state)
            composer.focus(selection: state.draft.selection)
        }
    }
    func setNotificationComment(_ id: String) {
        notificationCommentID = id
        initialComments = false
        if isViewLoaded { scheduleRender() }
    }
    func setFocusComments(_ value: Bool) {
        initialComments = value
        if value, isViewLoaded, view.window != nil, !state.isLoading {
            initialComments = false
            showComments()
        }
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        returningFromDetail = false
        navigationController?.setNavigationBarHidden(false, animated: animated)
        viewModel.start()
        table.setNeedsLayout()
        status.skeleton.setNeedsLayout()
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        mediaPlaybackActive = true
        updateMediaPlayback()
        renderChrome(state)
        if notificationCommentID != nil { scheduleRender() }
        if initialComments && !state.isLoading {
            initialComments = false
            showComments()
        }
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if returningFromDetail {
            returningFromDetail = false
            viewModel.stop()
        } else if navigationController?.isBeingDismissed == true { viewModel.stop() }
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        mediaPlaybackActive = false
        updateMediaPlayback()
        returningFromDetail = isMovingFromParent
        navigationController?.interactivePopGestureRecognizer?.isEnabled = true
    }
    func configure(_ post: Post) { viewModel.synchronize(post: post) }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        if composer.bounds.width > 0 {
            let height = composer.sizeThatFits(CGSize(width: composer.bounds.width, height: .greatestFiniteMagnitude)).height
            if composerHeight?.constant != height { captureAnchor(); composerHeight?.constant = height }
        }
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let bottomInset = composer.bounds.height + 8
        let insetChanged = table.contentInset.bottom != bottomInset
        if insetChanged {
            table.contentInset.bottom = bottomInset
            table.verticalScrollIndicatorInsets.bottom = bottomInset
        }
        if lastWidth != table.bounds.width {
            lastWidth = table.bounds.width
            sizes.removeAll()
            table.reloadData()
        }
        if lastTableHeight != table.bounds.height || insetChanged {
            lastTableHeight = table.bounds.height
            restoreAnchor()
        }
        updateMediaPlayback()
    }

    private func renderChrome(_ state: PostDetailState) {
        let editing = state.editingID != nil
        title = editing ? AxooOverlayCopy.editComment : detailTitle
        backButton.setImage(AxooIcon.back.image(pointSize: 24), for: .normal)
        backButton.tintColor = AxooTheme.textPrimary
        backButton.isEnabled = !state.isSaving && !state.isPerformingAction
        if navigationController?.topViewController === self {
            navigationController?.interactivePopGestureRecognizer?.isEnabled = !editing && !state.isPerformingAction
        }
        editingScrim.isHidden = !editing
        axooRefresh?.isEnabled = !editing && !state.isPerformingAction
        composer.render(state)
        if previousEditing && !editing { composer.textView.resignFirstResponder() }
        previousEditing = editing
        // Comment requests may finish while the post editor owns navigation.
        guard navigationController?.topViewController === self, viewIfLoaded?.window != nil else { return }
        if state.isPostDeleted { navigationController?.popViewController(animated: true); return }
        if let notice = state.notice {
            let alert = AxooConfirmationCard(title: nil, message: notice, cancelTitle: nil, actionTitle: AxooOverlayCopy.ok)
            viewModel.clearNotice()
            presentAfterMenu(alert)
        }
    }
    private func enqueue(_ state: DetailListState) {
        pending = state
        guard !applying else { return }
        applying = true
        Task { [weak self] in
            guard let self = self else { return }
            while let next = self.pending {
                self.pending = nil
                await self.apply(next)
            }
            self.applying = false
        }
    }
    private func apply(_ state: DetailListState) async {
        captureAnchor()
        let old = rendered
        let oldRows = rowsByID
        let rows = state.rows
        rowsByID = Dictionary(uniqueKeysWithValues: rows.map { ($0.stableID, $0) })
        let voteOnly = Set(rows.compactMap { row -> String? in
            guard let previous = oldRows[row.stableID], previous != row,
                  isVoteOnlyChange(previous, row) else { return nil }
            return row.stableID
        })
        for row in rows where oldRows[row.stableID] != row && !voteOnly.contains(row.stableID) {
            sizes[row.stableID] = nil
        }
        rendered = state
        card.configure(state.post)
        status.configure(loading: state.loading, error: state.error)
        var snapshot = NSDiffableDataSourceSnapshot<Int, RowID>()
        snapshot.appendSections([0])
        var ids: [RowID] = [.post, .header]
        ids += rows.map { .comment($0.stableID) }
        if rows.isEmpty || state.error != nil { ids.append(.status) }
        snapshot.appendItems(ids)
        let existing = Set(dataSource.snapshot().itemIdentifiers)
        let changed = ids.filter { id in
            guard existing.contains(id) else { return false }
            switch id {
            case .post: return old?.post != state.post
            case .header: return false
            case .status: return true
            case .comment(let key): return oldRows[key] != rowsByID[key] && !voteOnly.contains(key)
            }
        }
        // Reload changed rows so delegate heights follow their current content.
        snapshot.reloadItems(changed)
        await dataSource.apply(snapshot, animatingDifferences: false)
        for key in voteOnly {
            guard let path = dataSource.indexPath(for: .comment(key)),
                  let cell = table.cellForRow(at: path) as? CommentCell,
                  let row = rowsByID[key] else { continue }
            cell.commentView.updateVote(row)
        }
        table.layoutIfNeeded()
        if !state.refreshing && !state.loading { axooRefresh?.endRefreshing() }
        if let id = notificationCommentID, !state.loading, !state.refreshing, state.error == nil, table.window != nil {
            notificationCommentID = nil
            if let row = rows.first(where: { $0.item.comment.id == id }),
               let path = dataSource.indexPath(for: .comment(row.stableID)) {
                anchor = nil
                table.scrollToRow(at: path, at: .top, animated: false)
            }
        } else if initialComments && !state.loading && table.window != nil {
            initialComments = false
            showComments()
        } else if let id = state.followID, id != followedID,
                  let last = dataSource.snapshot().itemIdentifiers.last, let path = dataSource.indexPath(for: last) {
            followedID = id
            anchor = nil
            preserveInputViewport = false
            table.scrollToRow(at: path, at: .bottom, animated: false)
        } else { restoreAnchor() }
        table.layoutIfNeeded()
        updateMediaPlayback()
    }
    private func isVoteOnlyChange(_ previous: CommentRow, _ current: CommentRow) -> Bool {
        var item = current.item
        item.comment.score = previous.item.comment.score
        item.comment.vote = previous.item.comment.vote
        item.isBusy = previous.item.isBusy
        item.isVoting = previous.item.isVoting
        return CommentRow(item: item, depth: current.depth, rootID: current.rootID,
                          isFirst: current.isFirst, isLast: current.isLast,
                          linesAbove: current.linesAbove, linesBelow: current.linesBelow) == previous
    }
    private func cell(_ table: UITableView, path: IndexPath, id: RowID) -> UITableViewCell {
        switch id {
        case .post, .header, .status:
            let name = id == .post ? "post" : id == .header ? "header" : "status"
            let cell = table.dequeueReusableCell(withIdentifier: name, for: path) as! PostDetailHostCell
            cell.horizontalInset = id == .post ? 12 : 0
            cell.hostedView = id == .post ? card : id == .header ? header : status
            return cell
        case .comment(let key):
            let cell = table.dequeueReusableCell(withIdentifier: "comment", for: path) as! CommentCell
            if let row = rowsByID[key] {
                cell.commentView.configure(row)
                cell.commentView.onAction = { [weak self] in self?.handle($0, row: row) }
                cell.commentView.setOptionsSheet { [weak self] sheet in self?.commentOptions(row.item.comment.id, in: sheet) }
            }
            return cell
        }
    }
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        guard let id = dataSource.itemIdentifier(for: indexPath) else { return 100 }
        let size = CGSize(width: max(1, table.bounds.width), height: .greatestFiniteMagnitude)
        switch id {
        case .post: return card.sizeThatFits(CGSize(width: max(1, size.width - 24), height: size.height)).height
        case .header: return header.sizeThatFits(size).height
        case .status: return status.sizeThatFits(size).height
        case .comment(let key):
            if let height = sizes[key] { return height }
            guard let row = rowsByID[key] else { return 0 }
            measureComment.configure(row)
            let height = measureComment.sizeThatFits(size).height
            sizes[key] = height
            return height
        }
    }
    private func prepareInputAnchor() {
        preserveInputViewport = true
        table.setContentOffset(table.contentOffset, animated: false)
        captureAnchor()
    }
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        axooRefresh?.update()
        updateMediaPlayback()
        status.skeleton.updateAnimationVisibility()
        status.updateAnimationVisibility()
    }
    private func updateMediaPlayback() {
        card.updateMediaPlayback(isActive: mediaPlaybackActive)
        for case let cell as CommentCell in table.visibleCells {
            cell.commentView.updateMediaPlayback(isActive: mediaPlaybackActive)
        }
    }
    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        (cell as? CommentCell)?.commentView.updateMediaPlayback(isActive: mediaPlaybackActive)
    }
    func tableView(_ tableView: UITableView, didEndDisplaying cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        (cell as? CommentCell)?.commentView.updateMediaPlayback(isActive: false)
    }
    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        preserveInputViewport = false
        view.endEditing(true)
    }
    private func captureAnchor() {
        guard keyboardTransition == nil else { return }
        guard axooRefresh?.isAdjustingScroll != true else { anchor = nil; return }
        if preserveInputViewport {
            let visibleBottom = table.contentOffset.y + visibleListHeight
            if let path = table.indexPathsForVisibleRows?.last(where: { table.rectForRow(at: $0).minY < visibleBottom }),
               let id = dataSource?.itemIdentifier(for: path) {
                anchor = (id, visibleBottom - table.rectForRow(at: path).maxY, true)
                return
            }
        }
        guard let path = table.indexPathsForVisibleRows?.first, let id = dataSource?.itemIdentifier(for: path) else { return }
        anchor = (id, table.rectForRow(at: path).minY - table.contentOffset.y, false)
    }
    private var visibleListHeight: CGFloat { max(0, table.bounds.height - table.adjustedContentInset.bottom) }
    private func restoreAnchor() {
        guard !table.isDragging, !table.isDecelerating, axooRefresh?.isAdjustingScroll != true,
              let anchor = anchor, let path = dataSource.indexPath(for: anchor.id) else { return }
        let frame = table.rectForRow(at: path)
        // Android's commentAnchorOffset keeps the last visible row and its signed bottom gap.
        let offset = max(0, visibleListHeight - frame.height - anchor.offset)
        let desired = anchor.fromBottom ? frame.minY - offset : frame.minY - anchor.offset
        let maxY = max(-table.adjustedContentInset.top, table.contentSize.height - table.bounds.height + table.adjustedContentInset.bottom)
        table.setContentOffset(CGPoint(x: 0, y: min(maxY, max(-table.adjustedContentInset.top, desired))), animated: false)
    }
    private func showComments() {
        guard let path = dataSource.indexPath(for: .header) else { return }
        anchor = nil
        table.scrollToRow(at: path, at: .top, animated: false)
    }
    @objc private func back() {
        if state.editingID != nil { viewModel.cancelEditing() }
        else { view.endEditing(true); navigationController?.popViewController(animated: true) }
    }
    override var allowsBackSwipe: Bool {
        guard let navigation = navigationController else { return false }
        return navigation.topViewController === self && navigation.viewControllers.count > 1
            && navigation.transitionCoordinator == nil && presentedViewController == nil
            && state.editingID == nil && !state.isSaving && !state.isPerformingAction
    }
    @objc private func swipeBack() {
        guard allowsBackSwipe else { return }
        back()
    }
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        gestureRecognizer !== backSwipe || allowsBackSwipe
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        // Horizontal return must also work over the vertically scrolling comments.
        gestureRecognizer === backSwipe && other === table.panGestureRecognizer
    }
    private func handle(_ action: CommentView.Action, row: CommentRow) {
        switch action {
        case .author: onOpenProfile(row.item.comment.userID)
        case .saveSticker: StickerSaveSheet.present(from: self, sticker: row.item.comment.media, api: editorService?.mediaAPI)
        case .reply: viewModel.reply(to: row.item.comment.id)
        case .vote(let direction): viewModel.voteComment(row.item.comment.id, direction: direction)
        case .menu: break // The button presents its options sheet.
        }
    }
    private func commentOptions(_ id: String, in sheet: AxooBottomSheet) {
        guard let item = viewModel.item(id), item.canInteract, !state.isPerformingAction else { return }
        if item.comment.userID == viewModel.access.userID {
            if item.comment.allowsEditing { sheet.addButton(AxooOverlayCopy.editComment, icon: .edit) { [weak self] in self?.viewModel.beginEditing(id) } }
            sheet.addButton(AxooOverlayCopy.deleteComment, destructive: true, icon: .delete) { [weak self] in
                self?.confirm(AxooOverlayCopy.deleteCommentTitle, message: AxooOverlayCopy.deleteCommentMessage) { [weak self] in self?.viewModel.deleteComment(id) }
            }
        } else {
            sheet.addButton(AxooOverlayCopy.reportComment, icon: .flag) { [weak self] in self?.report(.comment(id)) }
            sheet.addButton(AxooOverlayCopy.blockUser, destructive: true, icon: .block) { [weak self] in self?.confirmBlock(item.comment.userID) }
        }
    }
    private func postOptions(in sheet: AxooBottomSheet) {
        guard state.editingID == nil, !state.isPerformingAction else { return }
        let access = viewModel.access
        if access.userID == access.postAuthorID {
            if editorService != nil && state.post.poll == nil && state.post.canEdit { sheet.addButton(AxooOverlayCopy.editPost, icon: .edit) { [weak self] in self?.editPost() } }
            sheet.addButton(AxooOverlayCopy.deletePost, destructive: true, icon: .delete) { [weak self] in
                self?.confirm(AxooOverlayCopy.deletePostTitle, message: self?.viewModel.isPreview == true ? "Il post verrà rimosso dall'anteprima." : AxooOverlayCopy.deleteDetailPostMessage) { [weak self] in self?.viewModel.deletePost() }
            }
        } else {
            sheet.addButton("Nascondi per me", icon: .close) { [weak self] in self?.viewModel.hidePost() }
            sheet.addButton(AxooOverlayCopy.reportPost, icon: .flag) { [weak self] in self?.report(.post) }
            sheet.addButton(AxooOverlayCopy.blockAuthor, destructive: true, icon: .block) { [weak self] in self?.confirmBlock(access.postAuthorID) }
        }
    }
    private func editPost() {
        guard let service = editorService, viewModel.ownsPost, state.post.poll == nil, state.post.canEdit else { return }
        let model = PostEditorViewModel(service: service, post: state.post)
        let screen = PostEditorHost(viewModel: model) { [weak viewModel] in viewModel?.applyEditorResult($0) }
        navigationController?.pushViewController(screen, animated: true)
    }
    private func confirmBlock(_ id: String) {
        let alert = AxooConfirmationCard(title: AxooOverlayCopy.blockTitle,
            message: AxooOverlayCopy.blockMessage, actionTitle: AxooOverlayCopy.block, destructive: true) { [weak self] in
            self?.viewModel.block(id)
        }
        presentAfterMenu(alert)
    }
    private func report(_ target: PostReportTarget) {
        let sheet = AxooReportSheet(submit: { [weak self] reason in
            guard let self else { throw CancellationError() }
            try await self.viewModel.submitReport(target, reason: reason.rawValue)
        })
        presentAfterMenu(sheet)
    }
    private func confirm(_ title: String, message: String, action: @escaping () -> Void) {
        let alert = AxooConfirmationCard(title: title, message: message, actionTitle: AxooOverlayCopy.delete, destructive: true, action: action)
        presentAfterMenu(alert)
    }
    private func presentAfterMenu(_ controller: UIViewController, onPresented: (() -> Void)? = nil) {
        Task { [weak self] in
            guard let self = self else { return }
            await self.finishPresentedTransition()
            guard self.navigationController?.topViewController === self, self.viewIfLoaded?.window != nil else { return }
            self.view.endEditing(true)
            self.present(controller, animated: true, completion: onPresented)
        }
    }
    private func finishPresentedTransition() async {
        guard let current = presentedViewController else { return }
        // UIKit exposes dismissal through a lifecycle callback; keep the caller async.
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            if current.isBeingDismissed, let transition = current.transitionCoordinator {
                let scheduled = transition.animate(alongsideTransition: nil) { _ in continuation.resume() }
                if !scheduled { continuation.resume() }
            } else {
                current.dismiss(animated: true) { continuation.resume() }
            }
        }
    }

}
