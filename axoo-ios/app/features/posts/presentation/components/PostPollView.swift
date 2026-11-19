import UIKit

/// Stable row geometry before/after a vote, with results revealed on confirmation.
final class PostPollView: UIView {
    var onVote: (String) -> Void = { _ in }
    private var poll: PostPoll?
    private var rows: [PollOptionView] = []
    private let total = UILabel.axoo(font: AxooTypography.statusBody.withSize(11), color: AxooTheme.textSecondary)

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(total)
        isHidden = true
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    func configure(_ value: PostPoll?, samePost: Bool) {
        guard !samePost || poll != value else { return }
        let reuse = samePost && poll?.options.map(\.id) == value?.options.map(\.id)
        if !reuse {
            rows.forEach { $0.removeFromSuperview() }
            rows = (value?.options ?? []).map { option in
                let row = PollOptionView(frame: .zero)
                row.addAction(UIAction { [weak self] _ in
                    guard let self, let poll = self.poll, !poll.hasVoted, !poll.isSending else { return }
                    Haptics.vibrate()
                    self.onVote(option.id)
                }, for: .touchUpInside)
                addSubview(row)
                return row
            }
        }
        poll = value
        isHidden = value == nil
        guard let value else { return }
        let percentages = value.percentages
        for (index, row) in rows.enumerated() {
            row.configure(value.options[index], selected: value.myOptionId == value.options[index].id,
                          hasVoted: value.hasVoted, percentage: percentages[index], totalVotes: value.totalVotes,
                          separator: index > 0, canVote: !value.hasVoted && !value.isSending,
                          animate: reuse && window != nil)
        }
        total.text = value.isSending ? "Invio del voto…" : value.totalVotes == 1 ? "1 voto" : "\(value.totalVotes) voti"
        setNeedsLayout()
    }
    func setPlaybackEnabled(_ active: Bool) { rows.forEach { $0.setPlaybackEnabled(active) } }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: rows.reduce(CGFloat(0)) { $0 + $1.sizeThatFits(size).height } + total.font.lineHeight + 18)
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        var y: CGFloat = 0
        for row in rows {
            let height = row.sizeThatFits(CGSize(width: bounds.width, height: .greatestFiniteMagnitude)).height
            row.frame = CGRect(x: 0, y: y, width: bounds.width, height: height)
            y += height
        }
        total.frame = CGRect(x: 6, y: y + 10, width: max(0, bounds.width - 12), height: total.font.lineHeight)
    }
}

private final class PollOptionView: UIControl {
    private let text = EmojiTextView(readOnly: true)
    private let percentage = UILabel.axoo(font: AxooTypography.statusBody.withSize(11))
    private let selectionClip = UIView()
    private let selectionFill = UIView()
    private let barTrack = UIView()
    private let barFill = UIView()
    private let overflow = UILabel.axoo(font: AxooTypography.statusBody.withSize(11), color: AxooTheme.textSecondary)
    private let avatars = (0..<3).map { _ in AnonAvatarView(frame: .zero) }
    private var showsSeparator = false
    private var fraction: CGFloat = 0
    private var animator: UIViewPropertyAnimator?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        isAccessibilityElement = true
        selectionClip.layer.cornerRadius = 10
        selectionClip.clipsToBounds = true
        selectionClip.addSubview(selectionFill)
        selectionFill.backgroundColor = AxooTheme.accent.withAlphaComponent(0.085)
        text.font = AxooTypography.editorBody
        text.textColor = AxooTheme.textPrimary
        text.isSelectable = false
        percentage.textAlignment = .center
        barTrack.backgroundColor = AxooTheme.accent.withAlphaComponent(0.12)
        barFill.backgroundColor = AxooTheme.accent
        barTrack.layer.cornerRadius = 1.5
        barFill.layer.cornerRadius = 1.5
        ([selectionClip, text, percentage, barTrack, barFill, overflow] + avatars).forEach {
            addSubview($0)
            $0.isUserInteractionEnabled = false
            $0.isAccessibilityElement = false
        }
        avatars.forEach { $0.clipsToBounds = true; $0.layer.cornerRadius = 10 }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    func configure(_ option: PostPollOption, selected: Bool, hasVoted: Bool, percentage value: Int,
                   totalVotes: Int, separator: Bool, canVote: Bool, animate: Bool) {
        layoutIfNeeded()
        animator?.stopAnimation(true)
        isSelected = selected
        showsSeparator = separator
        fraction = hasVoted && totalVotes > 0 ? min(1, max(0, CGFloat(option.votes) / CGFloat(totalVotes))) : 0
        isEnabled = canVote
        text.setDocument(option.document)
        percentage.text = "\(value)%"
        percentage.textColor = selected ? AxooTheme.accent : AxooTheme.textSecondary
        percentage.isHidden = !hasVoted
        barTrack.isHidden = !hasVoted
        barFill.isHidden = !hasVoted
        let visible = Array(option.voters.prefix(3))
        let remaining = max(0, option.votes - visible.count)
        overflow.text = remaining > 0 ? "+\(remaining)" : ""
        overflow.isHidden = !hasVoted || remaining == 0
        for (index, avatar) in avatars.enumerated() {
            avatar.isHidden = !hasVoted || index >= visible.count
            if index < visible.count {
                avatar.configure(AvatarIdentity(seed: visible[index].userId, configuration: visible[index].avatarConfig))
            }
        }
        accessibilityLabel = option.text
        accessibilityIdentifier = "poll-option-\(option.id)"
        accessibilityValue = hasVoted ? "\(value)%\(selected ? ", scelto" : "")" : selected ? "Invio del voto" : nil
        accessibilityTraits = selected ? [.button, .selected] : [.button]
        setNeedsDisplay()
        setNeedsLayout()
        if animate {
            let animator = UIViewPropertyAnimator(duration: 0.24, timingParameters: AxooInterpolators.decelerate)
            animator.addAnimations { [weak self] in self?.layoutIfNeeded() }
            self.animator = animator
            animator.startAnimation()
        } else { UIView.performWithoutAnimation { layoutIfNeeded() } }
    }
    func setPlaybackEnabled(_ active: Bool) { text.playbackEnabled = active }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let body = text.sizeThatFits(CGSize(width: max(1, size.width - 48), height: .greatestFiniteMagnitude))
        return CGSize(width: size.width, height: max(74, 23 + ceil(body.height) + 30))
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let textWidth = max(1, bounds.width - 48)
        let textHeight = ceil(text.sizeThatFits(CGSize(width: textWidth, height: .greatestFiniteMagnitude)).height)
        text.frame = CGRect(x: 36, y: 23, width: textWidth, height: textHeight)
        percentage.frame = CGRect(x: 0, y: 4, width: 30, height: 14)
        selectionClip.frame = bounds
        selectionFill.frame = CGRect(x: 0, y: 0, width: isSelected ? bounds.width : 0, height: bounds.height)
        let barY = text.frame.maxY + 12
        let visible = avatars.filter { !$0.isHidden }
        let avatarsWidth: CGFloat = visible.isEmpty ? 0 : 20 + 14 * CGFloat(visible.count - 1)
        let overflowWidth = overflow.isHidden ? 0 : min(60, overflow.intrinsicContentSize.width + 6)
        let avatarLeft = bounds.width - 6 - overflowWidth - avatarsWidth
        let barWidth = max(12, avatarLeft - 10 - 36)
        barTrack.frame = CGRect(x: 36, y: barY - 1.5, width: barWidth, height: 3)
        barFill.frame = CGRect(x: 36, y: barY - 1.5, width: barWidth * fraction, height: 3)
        for (index, avatar) in visible.enumerated() {
            avatar.frame = CGRect(x: avatarLeft + 14 * CGFloat(index), y: barY - 10, width: 20, height: 20)
        }
        overflow.frame = CGRect(x: bounds.width - 6 - overflowWidth, y: barY - 8, width: overflowWidth, height: 16)
    }
    override func draw(_ rect: CGRect) {
        if showsSeparator {
            let line = UIBezierPath()
            line.move(to: .zero); line.addLine(to: CGPoint(x: bounds.width, y: 0))
            AxooTheme.cardBorder.setStroke(); line.lineWidth = 0.7; line.stroke()
        }
        let box = CGRect(x: 6, y: 24, width: 18, height: 18)
        let square = UIBezierPath(roundedRect: box, cornerRadius: 4)
        (isSelected ? AxooTheme.accent : AxooTheme.textSecondary).setStroke()
        square.lineWidth = 1
        square.stroke()
        if isSelected {
            AxooTheme.accent.setFill(); square.fill()
            let check = UIBezierPath()
            check.move(to: CGPoint(x: 10, y: 33)); check.addLine(to: CGPoint(x: 14, y: 37)); check.addLine(to: CGPoint(x: 20, y: 29))
            UIColor.white.setStroke(); check.lineWidth = 1.8; check.lineCapStyle = .round; check.lineJoinStyle = .round; check.stroke()
        }
    }
}
