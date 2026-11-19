import UIKit

enum SkeletonType { case post, comment, notification }

/// Reuses the feed placeholders during session restoration and campus loading.
final class StartupSkeletonView: UIView {
    private let cards = SkeletonCardView(type: .post, rows: 4)
    private var cardsHeight: NSLayoutConstraint?
    private let header = HomeHeader { _ in }
    private var headerHeight: NSLayoutConstraint?
    init() {
        super.init(frame: .zero)
        backgroundColor = AxooTheme.background
        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = false
        scroll.isUserInteractionEnabled = false
        scroll.backgroundColor = AxooTheme.surface
        [header, scroll].forEach { addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        header.configure(selected: .hot, newUnreadCount: 0)
        header.isUserInteractionEnabled = false
        headerHeight = header.heightAnchor.constraint(equalToConstant: HomeHeader.minimumHeight)
        headerHeight?.isActive = true
        let safe = safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: safe.topAnchor),
            header.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            header.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: header.bottomAnchor),
            scroll.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
        scroll.addSubview(cards)
        cards.pinEdges(to: scroll.contentLayoutGuide)
        cards.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor).isActive = true
        cardsHeight = cards.heightAnchor.constraint(equalToConstant: 1)
        cardsHeight?.isActive = true
        accessibilityIdentifier = "startup-skeleton"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override func layoutSubviews() {
        super.layoutSubviews()
        headerHeight?.constant = header.sizeThatFits(bounds.size).height
        let height = cards.sizeThatFits(CGSize(width: max(1, safeAreaLayoutGuide.layoutFrame.width), height: .greatestFiniteMagnitude)).height
        if cardsHeight?.constant != height { cardsHeight?.constant = height }
    }
}

/// One CoreGraphics renderer for feed and comment placeholders, matching NativeSkeletonCardView.
final class SkeletonCardView: AxooAnimatedDrawingView {
    let type: SkeletonType
    private let rows: Int
    private var progress: CGFloat = 0

    init(type: SkeletonType, rows: Int = 1) {
        self.type = type
        self.rows = max(1, rows)
        super.init(frame: .zero)
        isUserInteractionEnabled = false
    }

    override var intrinsicContentSize: CGSize { sizeThatFits(CGSize(width: UIView.noIntrinsicMetric, height: 0)) }

    private func postCardHeight(widthPx: CGFloat, hasMedia: Bool) -> CGFloat {
        if !hasMedia { return 184 }
        let mediaWidth = max(160, widthPx - 96)
        return 200 + mediaWidth * 0.75
    }

    private var commentActionHeight: CGFloat {
        max(44, max(AxooTypography.postBody.lineHeight + 24,
                    AxooTypography.replyTitle.lineHeight + 24))
    }

    /// An ordinary root comment with two text lines, using CommentView's spacing and fonts.
    private var commentRowHeight: CGFloat {
        let headerHeight = max(26, AxooTypography.postAuthor.lineHeight)
        let bodyHeight = ceil(AxooTypography.postBody.lineHeight * 2)
        return ceil(16 + headerHeight + 8 + bodyHeight + 8 + commentActionHeight + 8 + 4)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        if type == .notification { return CGSize(width: size.width, height: 8 + CGFloat(rows) * 114) }
        if type == .comment {
            return CGSize(width: size.width, height: CGFloat(rows) * commentRowHeight)
        }
        let gap: CGFloat = 8
        var height: CGFloat = gap
        for index in 0..<rows {
            let hasMedia = index % 2 == 0
            height += postCardHeight(widthPx: size.width, hasMedia: hasMedia)
            height += gap
        }
        return CGSize(width: size.width, height: height)
    }

    override func animationFrame(_ time: CFTimeInterval) {
        progress = CGFloat(time.truncatingRemainder(dividingBy: 1.1) / 1.1)
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext(), bounds.width > 0, bounds.height > 0 else { return }
        let mask = CGMutablePath()

        func bar(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat) {
            let width = max(1, w)
            let radius = min(r, width / 2, h / 2)
            mask.addRoundedRect(in: CGRect(x: x, y: y, width: width, height: h), cornerWidth: radius, cornerHeight: radius)
        }

        let horizontalMargin: CGFloat = 16
        var y: CGFloat = type == .comment ? 0 : 8

        for index in 0..<rows {
            if type == .notification {
                let left: CGFloat = 12, right = bounds.width - 12
                let top = 8 + CGFloat(index) * 114
                let card = UIBezierPath(roundedRect: CGRect(x: left, y: top, width: max(1, right - left), height: 104), cornerRadius: 20)
                AxooTheme.cardBackground.setFill(); card.fill()
                AxooTheme.cardBorder.setStroke(); card.lineWidth = 0.7; card.stroke()
                bar(left + 16, top + 38, 28, 28, 8)
                let text = left + 58
                bar(text, top + 20, right - text - 30, 12, 6)
                bar(text, top + 42, right - text - 54, 12, 6)
                bar(text, top + 72, 54, 8, 4)
            } else if type == .post {
                let hasMedia = index % 2 == 0
                let cardHeight = postCardHeight(widthPx: bounds.width, hasMedia: hasMedia)
                let cardTop = y
                let cardBottom = y + cardHeight
                let cardRadius: CGFloat = 14

                let cardRect = CGRect(x: horizontalMargin, y: cardTop, width: bounds.width - horizontalMargin * 2, height: cardHeight)
                let cardPath = UIBezierPath(roundedRect: cardRect, cornerRadius: cardRadius)
                AxooTheme.cardBackground.setFill(); cardPath.fill()
                AxooTheme.cardBorder.setStroke(); cardPath.lineWidth = 1; cardPath.stroke()

                let contentLeft = horizontalMargin + 16
                let avatarSize: CGFloat = 32
                let itemRadius: CGFloat = 7

                let avatarTop = cardTop + 14
                bar(contentLeft, avatarTop, avatarSize, avatarSize, avatarSize / 2)

                let authorLeft = contentLeft + avatarSize + 12
                bar(authorLeft, cardTop + 18, 86, 12, itemRadius)
                bar(authorLeft, cardTop + 36, 20, 10, 5)
                bar(contentLeft, cardTop + 54, 72, 21, 10.5)

                let voteIconSize: CGFloat = 24
                let voteCenterX = bounds.width - horizontalMargin - 28
                let voteUpCenterY = cardTop + 34
                let voteDownCenterY = cardTop + 100
                bar(voteCenterX - voteIconSize / 2, voteUpCenterY - voteIconSize / 2, voteIconSize, voteIconSize, 4)
                bar(voteCenterX - voteIconSize / 2, voteDownCenterY - voteIconSize / 2, voteIconSize, voteIconSize, 4)

                let bodyRight = voteCenterX - voteIconSize / 2 - 8
                let bodyY = cardTop + 88
                bar(contentLeft, bodyY, bodyRight - contentLeft, itemRadius * 2, itemRadius)
                bar(contentLeft, bodyY + 16, bodyRight - 40 - contentLeft, itemRadius * 2, itemRadius)
                bar(contentLeft, bodyY + 32, bodyRight - 92 - contentLeft, itemRadius * 2, itemRadius)

                if hasMedia {
                    bar(contentLeft, bodyY + 56, bodyRight - contentLeft, cardBottom - 56 - (bodyY + 56), 12)
                }

                let actionsCenterY = cardBottom - 22
                let actionIconSize: CGFloat = 20
                bar(contentLeft - 4, actionsCenterY - actionIconSize / 2, actionIconSize, actionIconSize, 3)
                bar(contentLeft + actionIconSize + 8, actionsCenterY - 7, 22, 14, 5)
                
                let menuLeft = bodyRight - actionIconSize
                bar(menuLeft, actionsCenterY - actionIconSize / 2, actionIconSize, actionIconSize, 3)

                y += cardHeight + 8
            } else {
                let rowHeight = commentRowHeight
                let cardTop = y + 4
                let cardBottom = y + rowHeight - 4
                let cardRadius: CGFloat = 8

                let cardRect = CGRect(x: horizontalMargin, y: cardTop, width: max(0, bounds.width - horizontalMargin * 2), height: cardBottom - cardTop)
                let cardPath = UIBezierPath(roundedRect: cardRect, cornerRadius: cardRadius)
                AxooTheme.cardBackground.setFill(); cardPath.fill()
                AxooTheme.cardBorder.setStroke(); cardPath.lineWidth = 0.7; cardPath.stroke()

                let contentLeft = horizontalMargin + 14
                let avatarSize: CGFloat = 26
                let itemRadius: CGFloat = 6

                let headerTop = y + 16
                bar(contentLeft, headerTop, avatarSize, avatarSize, avatarSize / 2)

                let headerLeft = contentLeft + 34
                let headerRight = bounds.width - 30 - 44
                bar(headerLeft, headerTop + (AxooTypography.postAuthor.lineHeight - 12) / 2,
                    min(92, max(1, headerRight - headerLeft)), 12, itemRadius)
                if headerRight > headerLeft + 98 {
                    bar(headerLeft + 98, headerTop + (AxooTypography.postMetadata.lineHeight - 8) / 2,
                        min(44, headerRight - headerLeft - 98), 8, 4)
                }
                let menuCenterX = bounds.width - 30 - 22
                bar(menuCenterX - 12, headerTop + 20, 24, 8, 4)

                let bodyRight = bounds.width - horizontalMargin - 14
                let bodyTop = headerTop + max(26, AxooTypography.postAuthor.lineHeight) + 8
                let lineHeight = AxooTypography.postBody.lineHeight
                let bodyBarTop = bodyTop + (lineHeight - itemRadius * 2) / 2
                bar(contentLeft, bodyBarTop, bodyRight - contentLeft, itemRadius * 2, itemRadius)
                bar(contentLeft, bodyBarTop + lineHeight, bodyRight - 58 - contentLeft, itemRadius * 2, itemRadius)

                let actionsCenterY = cardBottom - 8 - commentActionHeight / 2
                let iconSize: CGFloat = 24
                bar(contentLeft, actionsCenterY - 6, 62, 12, itemRadius)
                
                let downLeft = bodyRight - 22 - iconSize / 2
                bar(downLeft, actionsCenterY - iconSize / 2, iconSize, iconSize, 3)
                let scoreCenterX = bodyRight - 44 - 24
                bar(scoreCenterX - 12, actionsCenterY - 7, 24, 14, 5)
                let upCenterX = bodyRight - 136 + 22
                bar(upCenterX - iconSize / 2, actionsCenterY - iconSize / 2, iconSize, iconSize, 3)

                y += rowHeight
            }
        }

        context.saveGState()
        context.addPath(mask); context.clip()
        context.setFillColor(UIColor.secondaryLabel.withAlphaComponent(0.13).cgColor); context.fill(bounds)
        
        if animationEnabled {
            let colors = [UIColor.clear.cgColor, UIColor.label.withAlphaComponent(0.08).cgColor, UIColor.clear.cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.5, 1]) {
                let width = bounds.width * 0.65
                let x = -width + (bounds.width + width) * progress
                context.drawLinearGradient(gradient, start: CGPoint(x: x, y: 0), end: CGPoint(x: x + width, y: bounds.height), options: [])
            }
        }
        context.restoreGState()
    }
}
