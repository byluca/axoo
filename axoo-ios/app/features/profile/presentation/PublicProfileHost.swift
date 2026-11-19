import UIKit

final class PublicProfileHost: MyPostsHost {
    private let profileModel: PublicProfileViewModel
    private let postsModel: MyPostsViewModel
    private let stack = UIStackView()
    private let makeBioEditor: () -> UIViewController
    private let skeleton = ProfileSkeletonView()
    private let profileCard = UIStackView()
    private let avatar = AnonAvatarView(vector: true)
    private let nickname = UILabel.axoo(font: AxooTypography.profileNickname)
    private let university = UILabel.axoo(font: AxooTypography.profileUniversity, color: AxooTheme.textSecondary)
    private let status = UIButton(type: .system)
    private lazy var bio = ProfileBioView(onEdit: profileModel.isOwn ? { [weak self] in self?.editBio() } : nil)
    private lazy var menu = UIButton.icon(.menu) { [weak self] in self?.showOptions() }

    init(viewModel: PublicProfileViewModel, postsModel: MyPostsViewModel, editorService: PostEditorService,
         makeDetail: @escaping (Post) -> PostDetailHost,
         makeBioEditor: @escaping () -> UIViewController) {
        self.profileModel = viewModel
        self.postsModel = postsModel
        self.makeBioEditor = makeBioEditor
        super.init(viewModel: postsModel, editorService: editorService, makeDetail: makeDetail)
        title = "Profilo"
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.largeTitleDisplayMode = .never
        navigationItem.titleView = UILabel.axoo("Profilo", font: AxooTypography.statusTitle)
        installArrowBackButton(testID: "public-profile-back")
        menu.accessibilityIdentifier = "public-profile-menu"
        let item = UIBarButtonItem(customView: menu)
        item.hideSharedBackground()
        navigationItem.rightBarButtonItem = item
        stack.axis = .vertical
        stack.spacing = 16
        stack.isLayoutMarginsRelativeArrangement = true
        stack.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 20, leading: 20, bottom: 16, trailing: 20)
        profileCard.axis = .vertical
        profileCard.alignment = .center
        profileCard.spacing = 4
        profileCard.isLayoutMarginsRelativeArrangement = true
        profileCard.layoutMargins = UIEdgeInsets(top: 28, left: 20, bottom: 20, right: 20)
        profileCard.backgroundColor = AxooTheme.cardBackground
        profileCard.layer.cornerRadius = 24
        profileCard.layer.borderWidth = 0.7
        [avatar, nickname, university].forEach { profileCard.addArrangedSubview($0) }
        profileCard.setCustomSpacing(14, after: avatar)
        avatar.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            avatar.widthAnchor.constraint(equalToConstant: 132),
            avatar.heightAnchor.constraint(equalToConstant: 132),
            nickname.widthAnchor.constraint(lessThanOrEqualTo: profileCard.widthAnchor, constant: -40),
            university.widthAnchor.constraint(lessThanOrEqualTo: profileCard.widthAnchor, constant: -40),
        ])
        nickname.textAlignment = .center
        university.textAlignment = .center
        status.titleLabel?.numberOfLines = 0
        status.titleLabel?.font = AxooTypography.button
        status.setTitleColor(AxooTheme.textSecondary, for: .normal)
        status.addAction(UIAction { [weak self] _ in self?.profileModel.load() }, for: .touchUpInside)
        profileModel.onChange = { [weak self] in
            guard let self else { return }
            if profileModel.canShowPosts { postsModel.refresh() }
            renderProfile()
        }
        renderProfile()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(false, animated: animated)
        setProfileHeader(stack, showsPosts: profileModel.canShowPosts)
        profileModel.load()
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        avatar.loadIfNeeded()
        bio.setActive(true)
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        bio.setActive(false)
        profileModel.cancelLoad()
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isMovingFromParent || navigationController?.isBeingDismissed == true { profileModel.stop() }
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        profileCard.layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }
    private func renderProfile() {
        guard isViewLoaded else { return }
        stack.arrangedSubviews.forEach { stack.removeArrangedSubview($0); $0.removeFromSuperview() }
        status.setTitle(profileModel.error, for: .normal)
        status.isEnabled = !profileModel.unavailable
        menu.isHidden = profileModel.isOwn || profileModel.isLoading || profileModel.user == nil
        menu.isEnabled = !profileModel.isBlocking
        if profileModel.isLoading && profileModel.user == nil {
            stack.addArrangedSubview(skeleton)
        } else if let user = profileModel.user {
            nickname.setVerifiedName(user.nickname, configuration: user.avatarConfig)
            university.text = user.universityName ?? ""
            university.isHidden = university.text?.isEmpty != false
            avatar.configure(AvatarIdentity(seed: user.id, configuration: user.avatarConfig))
            avatar.loadIfNeeded()
            bio.configure(user.bioDocument)
            bio.setActive(viewIfLoaded?.window != nil)
            stack.addArrangedSubview(profileCard)
            if !bio.isHidden { stack.addArrangedSubview(bio) }
            if profileModel.canShowPosts {
                let heading = UILabel.axoo("Post pubblicati", font: AxooTypography.statusTitle.withSize(20))
                heading.accessibilityTraits.insert(.header)
                stack.addArrangedSubview(heading)
            }
        } else {
            stack.addArrangedSubview(status)
        }
        setProfileHeader(stack, showsPosts: profileModel.canShowPosts)
        if let notice = profileModel.notice, presentedViewController == nil {
            profileModel.clearNotice()
            presentError(notice)
        }
    }
    private func editBio() { navigationController?.pushViewController(makeBioEditor(), animated: true) }
    private func showOptions() {
        guard !profileModel.isOwn, profileModel.user != nil, !profileModel.isBlocking else { return }
        let sheet = AxooBottomSheet(title: profileModel.user?.nickname ?? "Profilo", showsCancel: false)
        sheet.addButton("Segnala profilo", icon: .flag) { [weak self] in
            guard let self else { return }
            present(AxooReportSheet(submit: { [profileModel] reason in try await profileModel.report(reason) }), animated: true)
        }
        sheet.addButton(AxooOverlayCopy.blockUser, destructive: true, icon: .block) { [weak self] in
            guard let self else { return }
            let confirmation = AxooConfirmationCard(title: AxooOverlayCopy.blockTitle, message: AxooOverlayCopy.blockMessage,
                actionTitle: AxooOverlayCopy.block, destructive: true) { [weak self] in
                    self?.profileModel.block { [weak self] in self?.navigationController?.popViewController(animated: true) }
                }
            present(confirmation, animated: true)
        }
        present(sheet, animated: true)
    }
}
