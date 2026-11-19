import UIKit

/// Builds screens for one account and connects their updates to the feed.
@MainActor
final class ScreenFactory {
    let feed: FeedViewModel
    let profile: ProfileViewModel
    private let posts: PostRepository
    private let onSignOut: () -> Void
    private lazy var myPosts: MyPostsViewModel = {
        let model = MyPostsViewModel(source: posts)
        model.onPostChange = { [weak self] post in
            self?.feed.applyDetailPost(post)
            self?.synchronizeDetails(post)
        }
        return model
    }()
    private lazy var notifications = NotificationsViewModel(source: NotificationsRepository(api: posts.api))
    private lazy var events = EventsViewModel(source: EventsRepository(api: posts.api))
    private var notificationObserver: NSObjectProtocol?
    private var notificationOpenTask: Task<Void, Never>?
    private weak var creationEditor: PostEditorHost?
    private var details: [String: PostDetailHost] = [:]
    private var profilePosts: [String: MyPostsViewModel] = [:]

    init(posts: PostRepository, feed: FeedViewModel? = nil, onSignOut: @escaping () -> Void) {
        self.feed = feed ?? FeedViewModel(source: posts)
        self.profile = ProfileViewModel(repository: posts.profile)
        self.posts = posts
        self.onSignOut = onSignOut
        posts.onPollChanged = { [weak self] id, poll in
            guard let self else { return }
            self.feed.applyPoll(poll, postID: id)
            self.myPosts.applyPoll(poll, postID: id)
            self.profilePosts.values.forEach { $0.applyPoll(poll, postID: id) }
            self.details.values.forEach { $0.viewModel.applyPoll(poll, postID: id) }
        }
        notificationObserver = NotificationCenter.default.addObserver(forName: Notification.Name("axooNotificationsChanged"), object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.notifications.received() }
        }
    }

    deinit { if let notificationObserver { NotificationCenter.default.removeObserver(notificationObserver) } }

    func makeHome() -> HomeHost {
        let home = HomeHost(feed: feed, editorService: posts,
            makeDetailScreen: { [self] in detail($0) })
        home.onOpenProfile = { [weak self, weak home] id in
            if let home { self?.openProfile(id, from: home) }
        }
        posts.onHidden = { [weak self] id in
            self?.feed.removePost(id)
            self?.profilePosts.values.forEach { $0.remove(id) }
            self?.notifications.received()
        }
        posts.onBlocked = { [weak self] id in
            self?.feed.removeAuthor(id)
            self?.profilePosts.values.forEach { $0.removeAuthor(id) }
            self?.notifications.received()
        }
        // A feed vote may finish after the detail has opened.
        feed.onPostsChanged = { [weak self] cards in
            for card in cards.values {
                self?.synchronizeDetails(card)
                self?.myPosts.update(card)
            }
        }
        return home
    }

    func makeTabs(home: HomeHost, onCreate: @escaping () -> Void) -> HomeTabBarController {
        let homeNavigation = AxooNavigationController(rootViewController: home)
        homeNavigation.setNavigationBarHidden(true, animated: false)
        let profileHost = ProfileHost(viewModel: profile, onSignOut: onSignOut, makeMyPosts: { [self] in
            let host = MyPostsHost(viewModel: myPosts, editorService: posts, makeDetail: { [self] in detail($0, inProfile: true) })
            host.onOpenProfile = { [weak self, weak host] id in
                if let host { self?.openProfile(id, from: host) }
            }
            return host
        })
        profile.onUnblocked = { [weak posts, weak feed, weak self] id in
            posts?.unblockLocally(id)
            feed?.unblockAuthor(id)
            feed?.refreshAll()
            self?.notifications.received()
        }
        profile.onUserChanged = { [weak feed] in feed?.refreshAll() }
        let inbox = NotificationsHost(viewModel: notifications)
        inbox.onOpen = { [weak self, weak inbox] item in
            guard let self, let inbox else { return }
            openNotification(item, from: inbox)
        }
        let tabs = HomeTabBarController(home: homeNavigation,
            events: AxooNavigationController(rootViewController: EventsHost(model: events)),
            notifications: AxooNavigationController(rootViewController: inbox),
            profile: AxooNavigationController(rootViewController: profileHost), onCreate: onCreate)
        notifications.onUnreadChange = { [weak tabs] in tabs?.bindNotificationsCount($0) }
        notifications.refreshUnread()
        return tabs
    }

    func makePostEditor() -> UINavigationController {
        let posts = self.posts
        let editor = PostEditorHost(service: posts, mode: .create) { [weak self] result in
            guard let card = posts.records[result.postID]?.asPost() else { return }
            self?.feed.insertCreatedPost(card)
            self?.myPosts.insert(card)
        }
        creationEditor = editor
        let creation = AxooNavigationController(rootViewController: editor)
        creation.modalPresentationStyle = .fullScreen
        editor.onClose = { [weak creation] in creation?.dismiss(animated: true) }
        return creation
    }

    func stop() {
        notificationOpenTask?.cancel()
        notifications.stop()
        events.stop()
        myPosts.stop()
        profilePosts.values.forEach { $0.stop() }
        profile.stop()
        feed.stop()
        creationEditor?.viewModel.stop()
        details.values.forEach { $0.viewModel.stop() }
    }

    func resume() { feed.refreshUnread(); notifications.foreground() }

    private func openNotification(_ item: AppNotification, from host: NotificationsHost) {
        notificationOpenTask?.cancel()
        notificationOpenTask = Task { [weak self, weak host] in
            guard let self, let host else { return }
            do {
                let post = try await posts.post(item.postId)
                guard !Task.isCancelled, host.viewIfLoaded?.window != nil,
                      host.navigationController?.topViewController === host else { return }
                let detail = detail(post.asPost(), inNotifications: true)
                detail.setNotificationComment(item.commentId)
                host.navigationController?.pushViewController(detail, animated: true)
            } catch {
                guard !Task.isCancelled, host.viewIfLoaded?.window != nil else { return }
                host.presentError((error as? HTTPFailure)?.status == 404 ? "Il contenuto non è più disponibile." : "Non riusciamo ad aprire la notifica. Riprova.")
            }
        }
    }

    private func synchronizeDetails(_ post: Post) {
        profilePosts.values.forEach { $0.update(post) }
        details.values.forEach { $0.viewModel.synchronize(post: post) }
    }

    private func openProfile(_ id: String, from host: UIViewController) {
        guard !id.isEmpty, !posts.blockedUsers.contains(id),
              host.navigationController?.topViewController === host else { return }
        let model: MyPostsViewModel
        if let cached = profilePosts[id] { model = cached }
        else {
            model = MyPostsViewModel(source: posts, page: { [posts] in try await posts.userPostsPage(userID: id, cursor: $0) })
            model.onPostChange = { [weak self] post in
                self?.feed.applyDetailPost(post)
                self?.myPosts.update(post)
                self?.synchronizeDetails(post)
            }
            profilePosts[id] = model
        }
        let screen = PublicProfileHost(viewModel: PublicProfileViewModel(userID: id, posts: posts),
            postsModel: model, editorService: posts, makeDetail: { [self] in detail($0, inProfile: true) },
            makeBioEditor: { [profile] in BioEditorHost(viewModel: profile) })
        screen.onOpenProfile = { [weak self, weak screen] authorID in
            if let screen, authorID != id { self?.openProfile(authorID, from: screen) }
        }
        host.navigationController?.pushViewController(screen, animated: true)
    }

    func detail(_ post: Post, inProfile: Bool = false, inNotifications: Bool = false) -> PostDetailHost {
        let key = "\(inNotifications ? "notifications" : (inProfile ? "profile" : "home")):\(post.id)"
        if let screen = details[key] { screen.viewModel.synchronize(post: post); return screen }
        let screen = PostDetailHost(post: post,
            repository: RemoteCommentRepository(posts: posts, authorID: post.userID), editorService: posts)
        screen.onOpenProfile = { [weak self, weak screen] id in
            if let screen { self?.openProfile(id, from: screen) }
        }
        screen.viewModel.onPostChange = { [weak self] in
            self?.feed.applyDetailPost($0)
            self?.myPosts.update($0)
            self?.synchronizeDetails($0)
        }
        screen.viewModel.onPostDeleted = { [weak self] id in
            self?.feed.removePost(id)
            self?.myPosts.remove(id)
            self?.profilePosts.values.forEach { $0.remove(id) }
            self?.notifications.received()
        }
        details[key] = screen
        return screen
    }
}
