import UIKit

final class ProfileHost: AxooViewController {
    let viewModel: ProfileViewModel
    private let onSignOut: () -> Void
    private let makeMyPosts: (() -> UIViewController)?

    private let scrollView = UIScrollView()
    private let header = UIView()
    private let bottomFade = BottomFadeView()
    private let card = UIView()
    private let myPosts = UIButton(type: .system)
    private let avatar = AnonAvatarView(vector: true, preloadsOffscreen: true)
    private let nickname = UILabel.axoo()
    private let university = UILabel.axoo(color: .secondaryLabel)
    private lazy var bioCard = ProfileBioView { [weak self] in self?.editBio() }
    private let skeleton = ProfileSkeletonView()
    private let bioSkeleton = ProfileSkeletonView(bio: true)
    init(viewModel: ProfileViewModel, onSignOut: @escaping () -> Void, makeMyPosts: (() -> UIViewController)? = nil) {
        self.viewModel = viewModel
        self.onSignOut = onSignOut
        self.makeMyPosts = makeMyPosts
        super.init()
        title = "Profilo"
        if let user = viewModel.user {
            avatar.configure(AvatarIdentity(seed: user.id, configuration: user.avatarConfig))
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let border = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
        card.layer.borderColor = border
        myPosts.layer.borderColor = border
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AxooTheme.background
        scrollView.hideTopEdgeEffect()
        scrollView.showsVerticalScrollIndicator = false
        scrollView.backgroundColor = AxooTheme.surface
        let heading = UILabel.axoo("Profilo", font: UIFont(name: "Baloo2-ExtraBold", size: 22) ?? .boldSystemFont(ofSize: 22))
        heading.accessibilityIdentifier = "profile-heading"
        heading.numberOfLines = 1
        let menu = UIButton.icon(.hamburger) { [weak self] in self?.showSettings() }
        menu.tintColor = AxooTheme.textPrimary
        menu.accessibilityIdentifier = "profile-menu"
        header.backgroundColor = AxooTheme.cardBackground
        header.translatesAutoresizingMaskIntoConstraints = false
        heading.translatesAutoresizingMaskIntoConstraints = false
        menu.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(header)
        header.addSubview(heading)
        header.addSubview(menu)
        
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        bottomFade.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(bottomFade)
        
        card.backgroundColor = AxooTheme.cardBackground
        card.layer.cornerRadius = 24
        card.layer.borderWidth = 1
        card.layer.borderColor = AxooTheme.cardBorder.cgColor
        card.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(card)
        
        avatar.translatesAutoresizingMaskIntoConstraints = false
        avatar.isUserInteractionEnabled = true
        avatar.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(editAvatar)))
        
        nickname.isUserInteractionEnabled = true
        nickname.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(editNickname)))
        nickname.font = AxooTypography.profileNickname
        nickname.textAlignment = .center
        nickname.translatesAutoresizingMaskIntoConstraints = false
        
        university.font = AxooTypography.profileUniversity
        university.textAlignment = .center
        university.translatesAutoresizingMaskIntoConstraints = false
        
        [avatar, nickname, university].forEach { card.addSubview($0) }
        card.addSubview(skeleton)
        skeleton.pinEdges(to: card)
        bioCard.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(bioCard)
        bioSkeleton.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(bioSkeleton)
        NSLayoutConstraint.activate([
            bioSkeleton.topAnchor.constraint(equalTo: bioCard.topAnchor),
            bioSkeleton.leadingAnchor.constraint(equalTo: bioCard.leadingAnchor),
            bioSkeleton.trailingAnchor.constraint(equalTo: bioCard.trailingAnchor),
            bioSkeleton.heightAnchor.constraint(equalToConstant: 58),
            bioCard.heightAnchor.constraint(greaterThanOrEqualToConstant: 58),
        ])
        
        myPosts.setTitle("I miei post", for: .normal)
        myPosts.setImage(AxooIcon.document.image(pointSize: 22), for: .normal)
        myPosts.tintColor = AxooTheme.textPrimary
        myPosts.imageEdgeInsets = UIEdgeInsets(top: 0, left: -4, bottom: 0, right: 4)
        myPosts.titleLabel?.font = UIFont(name: "Baloo2-Bold", size: 17) ?? .boldSystemFont(ofSize: 17)
        myPosts.setTitleColor(AxooTheme.textPrimary, for: .normal)
        myPosts.backgroundColor = AxooTheme.cardBackground
        myPosts.layer.cornerRadius = 28
        myPosts.layer.borderWidth = 1
        myPosts.layer.borderColor = AxooTheme.cardBorder.cgColor
        myPosts.accessibilityIdentifier = "profile-my-posts"
        myPosts.translatesAutoresizingMaskIntoConstraints = false
        myPosts.addAction(UIAction { [weak self] _ in
            guard let self, let screen = makeMyPosts?() else { return }
            navigationController?.pushViewController(screen, animated: true)
        }, for: .touchUpInside)
        scrollView.addSubview(myPosts)
        let safe = view.safeAreaLayoutGuide
        setContentScrollView(scrollView, for: .bottom)
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: safe.topAnchor),
            header.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            header.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            header.heightAnchor.constraint(equalToConstant: 44),
            heading.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 16),
            heading.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            heading.trailingAnchor.constraint(lessThanOrEqualTo: menu.leadingAnchor, constant: -8),
            menu.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -8),
            menu.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            menu.widthAnchor.constraint(equalToConstant: 44),
            menu.heightAnchor.constraint(equalToConstant: 44),
            scrollView.topAnchor.constraint(equalTo: header.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            bottomFade.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            bottomFade.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            bottomFade.topAnchor.constraint(equalTo: safe.bottomAnchor, constant: -BottomFadeView.height),
            bottomFade.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            
            card.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            card.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 20),
            card.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -20),
            bioCard.topAnchor.constraint(equalTo: card.bottomAnchor, constant: 16),
            bioCard.leadingAnchor.constraint(equalTo: card.leadingAnchor),
            bioCard.trailingAnchor.constraint(equalTo: card.trailingAnchor),
            myPosts.topAnchor.constraint(equalTo: bioCard.bottomAnchor, constant: 16),
            myPosts.leadingAnchor.constraint(equalTo: card.leadingAnchor),
            myPosts.trailingAnchor.constraint(equalTo: card.trailingAnchor),
            myPosts.heightAnchor.constraint(greaterThanOrEqualToConstant: 56),
            myPosts.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -40),
            
            avatar.topAnchor.constraint(equalTo: card.topAnchor, constant: 28),
            avatar.centerXAnchor.constraint(equalTo: card.centerXAnchor),
            avatar.widthAnchor.constraint(equalToConstant: 132),
            avatar.heightAnchor.constraint(equalToConstant: 132),
            card.heightAnchor.constraint(greaterThanOrEqualToConstant: 262),
            
            nickname.topAnchor.constraint(equalTo: avatar.bottomAnchor, constant: 14),
            nickname.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            nickname.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            
            university.topAnchor.constraint(equalTo: nickname.bottomAnchor, constant: 4),
            university.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            university.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            university.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -20)
        ])
        bindViewModel()
        viewModel.load()
    }

    private func bindViewModel() {
        viewModel.onChange = { [weak self] in self?.renderProfile() }
        renderProfile()
    }

    func renderProfile() {
        guard isViewLoaded else { return }
        skeleton.isHidden = viewModel.user != nil || !viewModel.state.isLoading
        bioCard.isHidden = viewModel.user == nil
        bioSkeleton.isHidden = skeleton.isHidden
        if let notice = viewModel.state.notice, presentedViewController == nil {
            viewModel.clearNotice()
            presentError(notice)
        }
        guard let profile = viewModel.user else { return }
        nickname.setVerifiedName(profile.nickname, configuration: profile.avatarConfig)
        let uniName = profile.universityName ?? profile.universityId
        university.text = uniName
        university.isHidden = uniName.isEmpty
        avatar.configure(AvatarIdentity(seed: profile.id, configuration: profile.avatarConfig))
        bioCard.configure(profile.bioDocument)
    }

    private func editBio() {
        guard viewModel.user != nil else { return }
        navigationController?.pushViewController(BioEditorHost(viewModel: viewModel), animated: true)
    }
    
    @objc private func showSettings() {
        let settings = ProfileSettingsHost(viewModel: viewModel, onSignOut: onSignOut)
        navigationController?.pushViewController(settings, animated: true)
    }
    
    @objc private func editNickname() {
        guard let profile = viewModel.user, profile.avatarConfig?.isVerified != true else { return }
        let editor = NicknameEditorHost(viewModel: viewModel)
        navigationController?.pushViewController(editor, animated: true)
    }
    @objc private func editAvatar() {
        guard let profile = viewModel.user else { return }
        guard profile.avatarConfig?.isVerified != true else { return }
        let editor = AvatarEditorSheet(identity: AvatarIdentity(seed: profile.id, configuration: profile.avatarConfig)) { [weak self] newConfig in
            guard let self = self else { return }
            self.viewModel.updateAvatar(newConfig) { [weak self] success in
                guard let editor = self?.presentedViewController as? AvatarEditorSheet else { return }
                if success {
                    editor.dismiss(animated: true)
                } else {
                    editor.showSaveError()
                }
            }
        }
        present(editor, animated: true)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        bindViewModel()
        navigationController?.setNavigationBarHidden(true, animated: animated)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        avatar.loadIfNeeded()
        bioCard.setActive(true)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        bioCard.setActive(false)
    }
}
