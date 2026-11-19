import UIKit

/// NativePostView equivalent: one drawn surface, manual layout and independent touch targets.
final class PostCardView: AxooDrawingView {
    enum Presentation: Equatable { case feed, detail }
    enum Action {
        case open, author, comments, copyText, options, saveSticker
        case vote(Post.Vote)
        case pollVote(String)
    }
    private(set) var post: Post
    let presentation: Presentation
    private let loadsMedia: Bool
    var onAction: (Action) -> Void
    private let content = UIControl()
    private let avatar = AnonAvatarView(frame: .zero)
    private let author = UILabel.axoo()
    private let metadata = UILabel.axoo(color: AxooTheme.textSecondary)
    private let bodyText = EmojiTextView(readOnly: true)
    private let category = PostCategoryLabel()
    private let mediaImage = PostMediaImageView()
    private let pollView = PostPollView(frame: .zero)
    var isShareMediaReady: Bool { post.mediaURL == nil || mediaImage.ready }
    private let score = RollingNumberView(frame: .zero)

    private lazy var up = UIButton.icon(.postUp, size: 32) { [weak self] in Haptics.vibrate(); self?.onAction(.vote(.up)) }
    private lazy var down = UIButton.icon(.postDown, size: 32) { [weak self] in Haptics.vibrate(); self?.onAction(.vote(.down)) }
    private lazy var comments = UIButton.icon(.comment, size: 22) { [weak self] in self?.onAction(.comments) }
    private lazy var share = UIButton.icon(.share, size: 22) { [weak self] in
        guard let self else { return }
        PostShare.present(card: self)
    }
    private let menu = UIButton(type: .custom)
    func setOptionsSheet(_ configure: @escaping (AxooBottomSheet) -> Void) { menu.setOptionsSheet(configure) }

    init(post: Post, presentation: Presentation = .feed, loadsMedia: Bool = true, onAction: @escaping (Action) -> Void) {
        self.post = post
        self.presentation = presentation
        self.loadsMedia = loadsMedia
        self.onAction = onAction
        super.init(frame: .zero)
        isOpaque = false
        backgroundColor = .clear
        content.addAction(UIAction { [weak self] _ in
            guard let self = self, self.presentation == .feed else { return }
            self.onAction(.open)
        }, for: .touchUpInside)
        addSubview(content)
        [avatar, author, metadata, category, bodyText, mediaImage, pollView].forEach { content.addSubview($0) }
        pollView.onVote = { [weak self] in self?.onAction(.pollVote($0)) }
        [avatar, author].forEach {
            $0.isUserInteractionEnabled = true
            $0.accessibilityTraits.insert(.button)
            $0.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(openAuthor)))
        }
        [up, score, down, comments, share, menu].forEach { addSubview($0) }
        score.textAlignment = .center
        bodyText.backgroundColor = .clear
        bodyText.isEditable = false
        bodyText.isSelectable = true
        bodyText.isUserInteractionEnabled = true
        bodyText.linksOnlyInteraction = presentation == .feed
        bodyText.isScrollEnabled = false
        bodyText.textContainerInset = .zero
        bodyText.textContainer.lineFragmentPadding = 0
        category.isUserInteractionEnabled = false
        mediaImage.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(saveSticker)))
        mediaImage.contentMode = .scaleAspectFit
        mediaImage.clipsToBounds = true
        mediaImage.layer.cornerRadius = 12
        menu.setImage(AxooIcon.menu.image(pointSize: 24), for: .normal)
        menu.addAction(UIAction(identifier: UIAction.Identifier("axoo-options")) { [weak self] _ in
            self?.onAction(.options)
        }, for: .touchUpInside)
        configure(post)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    @objc private func openAuthor() {
        guard !post.userID.isEmpty else { return }
        onAction(.author)
    }

    @objc private func saveSticker() { if post.personalSticker != nil { onAction(.saveSticker) } }

    func configure(_ post: Post) {
        pollView.configure(post.poll, samePost: self.post.id == post.id)
        self.post = post
        avatar.configure(AvatarIdentity(seed: post.userID, configuration: post.avatarConfig))
        author.font = presentation == .feed ? AxooTypography.feedPostAuthor : AxooTypography.postAuthor
        metadata.font = AxooTypography.postMetadata
        bodyText.font = AxooTypography.feedPostBody
        author.textColor = AxooTheme.textPrimary
        bodyText.textColor = AxooTheme.textPrimary
        score.font = AxooTypography.postScore
        comments.titleLabel?.font = AxooTypography.postAction
        author.numberOfLines = 1
        metadata.numberOfLines = 2
        bodyText.textContainer.maximumNumberOfLines = 0
        bodyText.textContainer.lineBreakMode = .byWordWrapping
        author.setVerifiedName(post.authorLabel, configuration: post.avatarConfig)
        metadata.text = post.metadata
        let document = EmojiDocument(text: post.body, entities: post.entities)
        bodyText.setDocument(document, enlargeEmojiOnly: presentation == .detail)
        mediaImage.isHidden = post.mediaURL == nil
        mediaImage.isUserInteractionEnabled = post.personalSticker != nil
        mediaImage.accessibilityLabel = post.personalSticker == nil ? nil : "Salva sticker"
        if loadsMedia {
            mediaImage.onReady = { [weak self] in self?.setNeedsLayout() }
            mediaImage.load(post.mediaURL, previewURL: post.mediaPreviewURL,
                            maximumPixelSize: presentation == .feed ? 480 : 1200)
        }
        category.category = post.category
        category.isHidden = post.category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        score.configure(value: post.score, identity: post.id)
        score.textColor = post.vote == .none ? AxooTheme.interactionInactive : (post.vote == .up ? AxooTheme.accent : AxooTheme.downVote)
        score.accessibilityIdentifier = "post-score-\(post.id)"
        for (button, direction, name) in [(up, Post.Vote.up, "up"), (down, .down, "down")] {
            let selected = post.vote == direction
            button.isSelected = selected

            button.tintColor = selected ? score.textColor : AxooTheme.interactionInactive
            button.accessibilityIdentifier = "post-\(name)-\(post.id)"
        }

        comments.setTitle(" \(post.commentsCount.formatted())", for: .normal)
        comments.tintColor = AxooTheme.interactionInactive
        comments.setTitleColor(AxooTheme.interactionInactive, for: .normal)
        comments.contentHorizontalAlignment = .left
        comments.accessibilityIdentifier = "post-comments-\(post.id)"
        share.tintColor = AxooTheme.interactionInactive
        share.accessibilityIdentifier = "post-share-\(post.id)"
        share.accessibilityLabel = "Condividi post"
        menu.tintColor = AxooTheme.interactionInactive
        menu.accessibilityIdentifier = "post-menu-\(post.id)"
        accessibilityIdentifier = "post-card-\(post.id)"
        content.accessibilityIdentifier = "post-open-\(post.id)"
        setNeedsLayout()
        setNeedsDisplay()
        invalidateIntrinsicContentSize()
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: arrange(width: size.width, apply: false))
    }
    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: arrange(width: max(1, bounds.width), apply: false))
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        _ = arrange(width: bounds.width, apply: true)
        mediaImage.updatePlayback()
    }

    func updateMediaPlayback(isActive: Bool = true) {
        bodyText.playbackEnabled = isActive
        pollView.setPlaybackEnabled(isActive)
        EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate()
        mediaImage.playbackEnabled = isActive
        mediaImage.updatePlayback()
    }

    /// Measurement and placement use the same calculation, as onMeasure/onLayout do on Android.
    private func arrange(width: CGFloat, apply: Bool) -> CGFloat {
        let scoreSize = score.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        let voteWidth = max(44, scoreSize.width + 4)
        let contentWidth = max(1, width - 40)
        let textWidth = max(1, contentWidth - (voteWidth + 8))
        let verticalVotesHeight = 92 + scoreSize.height
        func place(_ view: UIView, _ rect: CGRect) { if apply { view.frame = rect } }
        let avatarSize: CGFloat = presentation == .feed ? 36 : 34
        let nameLeft = avatarSize + 10
        let nameWidth = max(1, textWidth - nameLeft)
        
        let headerHeight = max(avatarSize, max(author.intrinsicContentSize.height, metadata.intrinsicContentSize.height))
        place(avatar, CGRect(x: 0, y: (headerHeight - avatarSize) / 2, width: avatarSize, height: avatarSize))
        
        let metaFull = metadata.intrinsicContentSize.width
        let authorMax = max(48, nameWidth - metaFull - 6)
        let authorWidth = min(author.intrinsicContentSize.width, authorMax)
        
        place(author, CGRect(x: nameLeft, y: (headerHeight - author.intrinsicContentSize.height) / 2, width: authorWidth, height: author.intrinsicContentSize.height))
        place(metadata, CGRect(x: nameLeft + authorWidth + 6, y: (headerHeight - metadata.intrinsicContentSize.height) / 2, width: max(1, nameWidth - authorWidth - 6), height: metadata.intrinsicContentSize.height))
        var y = headerHeight + 12
        if !category.isHidden {
            let size = category.sizeThatFits(CGSize(width: textWidth, height: .greatestFiniteMagnitude))
            place(category, CGRect(origin: CGPoint(x: 0, y: y), size: size))
            y += size.height + 12
        }
        let body: UIView = bodyText
        let bodySize = body.sizeThatFits(CGSize(width: textWidth, height: .greatestFiniteMagnitude))
        place(body, CGRect(x: 0, y: y, width: textWidth, height: ceil(bodySize.height)))
        y += ceil(bodySize.height)
        if !mediaImage.isHidden {
            // Media starts below the vote column and uses the entire inner card width.
            y = max(y, verticalVotesHeight) + 12
            // Fit the media rectangle so the 12pt mask rounds the visible image.
            let sourceWidth = CGFloat(post.mediaWidth > 0 ? post.mediaWidth : 4)
            let sourceHeight = CGFloat(post.mediaHeight > 0 ? post.mediaHeight : 3)
            let limit: CGFloat = presentation == .feed ? (sourceHeight > sourceWidth ? 480 : 240) : 560
            let scale = min(contentWidth / sourceWidth, limit / sourceHeight)
            let mediaSize = CGSize(width: sourceWidth * scale, height: sourceHeight * scale)
            let decodedSize = mediaImage.image?.size ?? mediaSize
            let fit = min(mediaSize.width / max(1, decodedSize.width), mediaSize.height / max(1, decodedSize.height))
            let visibleSize = CGSize(width: decodedSize.width * fit, height: decodedSize.height * fit)
            place(mediaImage, CGRect(x: (contentWidth - visibleSize.width) / 2, y: y + (mediaSize.height - visibleSize.height) / 2,
                                     width: visibleSize.width, height: visibleSize.height))
            y += mediaSize.height
        }
        if !pollView.isHidden {
            y = max(y, verticalVotesHeight) + 12
            let height = pollView.sizeThatFits(CGSize(width: contentWidth, height: .greatestFiniteMagnitude)).height
            place(pollView, CGRect(x: 0, y: y, width: contentWidth, height: height))
            y += height
        }
        let contentHeight = max(106, y)
        place(content, CGRect(x: 20, y: 18, width: contentWidth, height: contentHeight))
        let voteX = width - 20 - voteWidth
        let voteY = CGFloat(18)
        place(up, CGRect(x: voteX, y: voteY, width: 44, height: 44))
        place(score, CGRect(x: voteX,
                            y: voteY + 44,
                            width: voteWidth, height: scoreSize.height + 4))
        place(down, CGRect(x: voteX,
                           y: voteY + 48 + scoreSize.height, width: 44, height: 44))

        let votesBottom = 18 + verticalVotesHeight
        let footerY = max(18 + contentHeight, votesBottom) + 8
        let commentsWidth = min(max(44, width - 156), max(64, comments.sizeThatFits(CGSize(width: width, height: 44)).width))
        place(comments, CGRect(x: 20, y: footerY, width: commentsWidth, height: max(44, AxooTypography.postAction.lineHeight + 12)))
        place(share, CGRect(x: 20 + commentsWidth + 4, y: footerY, width: 44, height: 44))
        place(menu, CGRect(x: width - 64, y: footerY, width: 44, height: 44))
        return ceil(footerY + max(44, AxooTypography.postAction.lineHeight + 12) + 8)
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let surface = bounds.insetBy(dx: 4, dy: 4)
        let path = UIBezierPath(roundedRect: surface, cornerRadius: 14)
        AxooTheme.cardBackground.setFill()
        path.fill()
        AxooTheme.cardBorder.setStroke()
        path.lineWidth = 0.7
        path.stroke()
    }
}
