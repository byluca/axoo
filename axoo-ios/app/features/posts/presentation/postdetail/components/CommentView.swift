import UIKit

final class CommentView: AxooDrawingView {
    enum Action { case reply, author, menu, saveSticker; case vote(Comment.Vote) }
    var onAction: (Action) -> Void = { _ in }
    private(set) var row: CommentRow?
    private let avatar = AnonAvatarView(frame: .zero)
    private let author = UILabel.axoo()
    private let metadata = UILabel.axoo(color: .secondaryLabel)
    private let body = AnimatedEmojiTextView()
    private let mediaImage = PostMediaImageView()
    private let score = RollingNumberView(frame: .zero)
    private lazy var reply = UIButton.axoo("Rispondi", constrainsHeight: false) { [weak self] in self?.onAction(.reply) }
    private let menu = UIButton(type: .system)
    func setOptionsSheet(_ configure: @escaping (AxooBottomSheet) -> Void) { menu.setOptionsSheet(configure) }
    private lazy var up = UIButton.icon(.commentUp) { [weak self] in Haptics.vibrate(); self?.onAction(.vote(.up)) }
    private lazy var down = UIButton.icon(.commentDown) { [weak self] in Haptics.vibrate(); self?.onAction(.vote(.down)) }
    private var connectorY: CGFloat = 26

    init() {
        super.init(frame: .zero)
        [avatar, author, metadata, body, mediaImage, score, reply, menu, up, down].forEach { addSubview($0) }
        [avatar, author].forEach {
            $0.isUserInteractionEnabled = true
            $0.accessibilityTraits.insert(.button)
            $0.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(openAuthor)))
        }
        menu.setImage(AxooIcon.menu.image(pointSize: 24), for: .normal)
        menu.addAction(UIAction(identifier: UIAction.Identifier("axoo-options")) { [weak self] _ in
            self?.onAction(.menu)
        }, for: .touchUpInside)
        // Bring the icons 4pt toward the score while keeping their full touch targets.
        up.imageEdgeInsets = UIEdgeInsets(top: 0, left: 4, bottom: 0, right: -4)
        down.imageEdgeInsets = UIEdgeInsets(top: 0, left: -4, bottom: 0, right: 4)
        mediaImage.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(saveSticker)))
        mediaImage.contentMode = .scaleAspectFit
        mediaImage.clipsToBounds = true
        mediaImage.isHidden = true
        score.textAlignment = .center
        score.adjustsFontSizeToFitWidth = true
        score.minimumScaleFactor = 0.65
        reply.titleLabel?.numberOfLines = 0
        reply.contentHorizontalAlignment = .left
        // The sheet's scrim blocks touches; keep the label's normal color behind it.
        reply.tintAdjustmentMode = .normal
        reply.configuration?.baseForegroundColor = AxooTheme.textSecondary
        reply.configuration?.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 0, bottom: 12, trailing: 0)
        reply.configuration?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var attributes = incoming
            attributes.font = AxooTypography.replyTitle
            return attributes
        }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    @objc private func openAuthor() {
        guard let comment = row?.item.comment, !comment.isDeleted, !comment.userID.isEmpty else { return }
        onAction(.author)
    }

    @objc private func saveSticker() {
        guard let comment = row?.item.comment, !comment.isDeleted,
              comment.media?.kind == .sticker, comment.media?.provider == "axoo" else { return }
        onAction(.saveSticker)
    }

    func configure(_ row: CommentRow) {
        self.row = row
        let media = row.item.comment.media
        let isPersonalSticker = media?.kind == .sticker && media?.provider == "axoo"
        mediaImage.layer.cornerRadius = (media?.kind == .gif || isPersonalSticker) ? 12 : 0
        let url = row.item.comment.isDeleted ? nil : media?.url
        mediaImage.isHidden = url == nil
        mediaImage.isUserInteractionEnabled = !row.item.comment.isDeleted && media?.kind == .sticker && media?.provider == "axoo"
        mediaImage.accessibilityLabel = mediaImage.isUserInteractionEnabled ? "Salva sticker" : nil
        let preview = media?.kind == .gif ? media?.previewURL : nil
        mediaImage.load(url, previewURL: preview)
        let item = row.item
        let comment = item.comment
        avatar.configure(comment.isDeleted ? nil : AvatarIdentity(seed: comment.userID, configuration: comment.avatarConfig))
        author.textColor = AxooTheme.textPrimary
        metadata.textColor = AxooTheme.textSecondary
        menu.tintColor = AxooTheme.textSecondary
        author.font = AxooTypography.postAuthor
        metadata.font = AxooTypography.postMetadata
        body.font = AxooTypography.postBody
        score.font = AxooTypography.postScore
        author.setVerifiedName(comment.authorLabel, configuration: comment.isDeleted ? nil : comment.avatarConfig)
        
        let campus = (comment.universityName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let timeLabel = TimeAgo.format(comment.createdAt)
        let meta = [campus, timeLabel, comment.isEdited ? "Modificato" : ""]
            .filter { !$0.isEmpty }.joined(separator: " · ")
        metadata.text = meta.isEmpty ? "" : "· " + meta
        
        body.textColor = comment.isDeleted ? AxooTheme.textSecondary : AxooTheme.textPrimary
        body.setDocument(EmojiDocument(text: comment.isDeleted ? "Commento non disponibile" : comment.body, entities: comment.isDeleted ? [] : comment.entities), enlargeEmojiOnly: true)
        body.accessibilityIdentifier = "comment-body-\(comment.id)"
        score.configure(value: comment.score, identity: row.stableID)
        score.textColor = comment.vote == .none ? AxooTheme.interactionInactive : (comment.vote == .up ? AxooTheme.accent : AxooTheme.downVote)
        score.accessibilityIdentifier = "comment-score-\(comment.id)"
        [avatar, author, metadata, score, reply, menu, up, down].forEach { $0.isHidden = comment.isDeleted }
        reply.isEnabled = item.canReply
        [menu, up, down].forEach { $0.isEnabled = item.canInteract }
        reply.accessibilityIdentifier = "comment-reply-\(comment.id)"
        menu.accessibilityIdentifier = "comment-menu-\(comment.id)"
        for (button, direction, name) in [(up, Comment.Vote.up, "up"), (down, .down, "down")] {
            button.isSelected = comment.vote == direction
            button.tintColor = button.isSelected ? score.textColor : AxooTheme.interactionInactive
            button.accessibilityIdentifier = "comment-\(name)-\(comment.id)"
        }
        accessibilityIdentifier = "comment-\(comment.id)"
        // Reused cells can expose newly visible actions before the next layout
        // pass. Give them their current row's frames before publishing the view.
        if bounds.width > 0 { _ = arrange(bounds.width, apply: true) }
        setNeedsLayout()
        setNeedsDisplay()
    }
    // A vote keeps the existing cell, menu and SVG views in place.
    func updateVote(_ updated: CommentRow) {
        let previous = row?.item.comment
        let comment = updated.item.comment
        row = updated
        if previous?.score != comment.score {
            score.configure(value: comment.score, identity: updated.stableID)
        }
        if previous?.vote != comment.vote {
            score.textColor = comment.vote == .none ? AxooTheme.interactionInactive : (comment.vote == .up ? AxooTheme.accent : AxooTheme.downVote)
            for (button, direction) in [(up, Comment.Vote.up), (down, .down)] {
                button.isSelected = comment.vote == direction
                button.tintColor = button.isSelected ? score.textColor : AxooTheme.interactionInactive
            }
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize { CGSize(width: size.width, height: arrange(size.width, apply: false)) }
    override func layoutSubviews() { super.layoutSubviews(); _ = arrange(bounds.width, apply: true) }
    func updateMediaPlayback(isActive: Bool) {
        mediaImage.playbackEnabled = isActive
        mediaImage.updatePlayback()
    }

    private func arrange(_ width: CGFloat, apply: Bool) -> CGFloat {
        guard let row = row else { return 0 }
        let indent = CGFloat(min(row.depth, CommentThread.maximumIndent)) * 14
        let left: CGFloat = 30 + indent
        let available = max(1, width - left - 30)
        var y: CGFloat = row.isFirst ? 16 : 12
        func place(_ view: UIView, _ rect: CGRect) { if apply { view.frame = rect } }
        func label(_ view: UIView, x: CGFloat, width: CGFloat) {
            let height = ceil(view.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height)
            place(view, CGRect(x: x, y: y, width: width, height: height))
            y += height
        }
        if !row.item.comment.isDeleted {
            let top = y
            place(menu, CGRect(x: width - 30 - 44, y: top + (author.intrinsicContentSize.height - 44) / 2, width: 44, height: 44))
            let avatarSize: CGFloat = 28
            let nameLeft = left + avatarSize + 8
            let nameWidth = max(1, available - avatarSize - 8 - 44)
            
            let metaFull = metadata.intrinsicContentSize.width
            let authorMax = max(32, nameWidth - metaFull - 6)
            let authorWidth = min(author.intrinsicContentSize.width, authorMax)
            
            place(author, CGRect(x: nameLeft, y: top, width: authorWidth, height: author.intrinsicContentSize.height))
            place(metadata, CGRect(x: nameLeft + authorWidth + 6, y: top, width: max(1, nameWidth - authorWidth - 6), height: metadata.intrinsicContentSize.height))
            
            place(avatar, CGRect(x: left, y: top, width: avatarSize, height: avatarSize))
            if apply { connectorY = top + avatarSize / 2 }
            y = top + max(avatarSize, author.intrinsicContentSize.height) + 8
        } else if apply { connectorY = y + body.font.lineHeight / 2 }
        if row.item.comment.isDeleted || !row.item.comment.body.isEmpty { label(body, x: left, width: available) }
        else { place(body, .zero) }
        if !row.item.comment.isDeleted, let media = row.item.comment.media {
            if !row.item.comment.body.isEmpty { y += 8 }
            let source = CGSize(width: CGFloat(max(1, media.width)), height: CGFloat(max(1, media.height)))
            let scale = min(available / source.width, 200 / source.height)
            let size = CGSize(width: source.width * scale, height: source.height * scale)
            place(mediaImage, CGRect(x: left + (available - size.width) / 2, y: y, width: size.width, height: size.height))
            y += size.height
        } else { place(mediaImage, .zero) }
        y += 8
        if !row.item.comment.isDeleted {
            // Keep the action row stable while the score rolls after a vote.
            let scoreWidth: CGFloat = 48
            let voteWidth = 88 + scoreWidth
            let replyWidth = min(available - voteWidth, max(90, reply.sizeThatFits(CGSize(width: available, height: 100)).width))
            let actionHeight = max(44, max(AxooTypography.postBody.lineHeight + 24, reply.sizeThatFits(CGSize(width: replyWidth, height: .greatestFiniteMagnitude)).height))
            let separate = available < replyWidth + voteWidth + 8
            place(reply, CGRect(x: left, y: y, width: replyWidth, height: actionHeight))
            if separate { y += actionHeight }
            let voteX = width - 30 - voteWidth
            place(up, CGRect(x: voteX, y: y, width: 44, height: actionHeight))
            place(score, CGRect(x: voteX + 44, y: y, width: scoreWidth, height: actionHeight))
            place(down, CGRect(x: voteX + 44 + scoreWidth, y: y, width: 44, height: actionHeight))
            y += actionHeight
        }
        return ceil(y + 8 + (row.isLast ? 4 : 0))
    }
    override func draw(_ rect: CGRect) {
        guard let row = row, let context = UIGraphicsGetCurrentContext() else { return }
        let top: CGFloat = row.isFirst ? 4 : 0
        let bottom = bounds.height - (row.isLast ? 4 : 0)
        let surface = CGRect(x: 16, y: top, width: max(0, bounds.width - 32), height: max(0, bottom - top))
        var corners: UIRectCorner = []
        if row.isFirst { corners.formUnion([.topLeft, .topRight]) }
        if row.isLast { corners.formUnion([.bottomLeft, .bottomRight]) }
        let path = UIBezierPath(roundedRect: surface, byRoundingCorners: corners, cornerRadii: CGSize(width: 8, height: 8))
        AxooTheme.cardBackground.setFill()
        path.fill()
        // Clip the horizontal strokes on middle rows so the thread reads as one continuous surface.
        context.saveGState()
        context.clip(to: CGRect(x: 0, y: row.isFirst ? 0 : 0.7, width: bounds.width,
                               height: bounds.height - (row.isFirst ? 0 : 0.7) - (row.isLast ? 0 : 0.7)))
        AxooTheme.cardBorder.setStroke()
        path.lineWidth = 0.7
        path.stroke()
        context.restoreGState()
        context.setStrokeColor(AxooTheme.textSecondary.withAlphaComponent(1.0 / 3.0).cgColor)
        context.setLineWidth(1)
        for depth in 1...CommentThread.maximumIndent {
            let x = CGFloat(34 + (depth - 1) * 14)
            let above = row.linesAbove.contains(depth)
            let below = row.linesBelow.contains(depth)
            if above {
                context.move(to: CGPoint(x: x, y: 0))
                if depth == row.depth {
                    context.addLine(to: CGPoint(x: x, y: connectorY - 6))
                    context.addQuadCurve(to: CGPoint(x: x + 6, y: connectorY), control: CGPoint(x: x, y: connectorY))
                    context.addLine(to: CGPoint(x: CGFloat(27 + min(row.depth, CommentThread.maximumIndent) * 14), y: connectorY))
                } else { context.addLine(to: CGPoint(x: x, y: bounds.height)) }
                context.strokePath()
            }
            if below && (!above || depth == row.depth) {
                let start = above ? connectorY - 6 : bottom - 8
                context.move(to: CGPoint(x: x, y: start))
                context.addLine(to: CGPoint(x: x, y: bounds.height))
                context.strokePath()
            }
        }
    }
}

final class CommentCell: UITableViewCell {
    let commentView = CommentView()
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
        contentView.addSubview(commentView)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func layoutSubviews() {
        super.layoutSubviews()
        commentView.frame = contentView.bounds
        commentView.layoutIfNeeded()
    }
    override func prepareForReuse() { super.prepareForReuse(); commentView.onAction = { _ in } }
}
