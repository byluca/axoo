import UIKit

final class PostDetailCommentsHeader: UIView {
    private let label = UILabel.axoo("Commenti", font: AxooTypography.statusTitle)
    init() { super.init(frame: .zero); addSubview(label) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: label.font.lineHeight + 16)
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        label.frame = bounds.insetBy(dx: 16, dy: 8)
    }
}

final class PostDetailStatusView: AxooDrawingView {
    private let label = UILabel.axoo()
    private let popcorn = AnimationView(name: "popcorn-icon")
    private lazy var retry = UIButton.axoo("Riprova", constrainsHeight: false) { [weak self] in self?.onRetry() }
    var onRetry: () -> Void = {}
    private var loading = false
    let skeleton = SkeletonCardView(type: .comment, rows: 3)
    init() {
        super.init(frame: .zero)
        label.textAlignment = .center
        addSubview(label)
        addSubview(retry)
        addSubview(skeleton)
        popcorn.contentMode = .scaleAspectFit
        addSubview(popcorn)
        retry.accessibilityIdentifier = "detail-retry"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(loading: Bool, error: String?) {
        self.loading = loading
        skeleton.isHidden = !loading
        skeleton.animationEnabled = loading
        skeleton.accessibilityIdentifier = "detail-loading"
        label.isHidden = loading
        label.font = error == nil ? AxooTypography.statusTitle.withSize(20) : AxooTypography.emptyStateBody
        label.textColor = error == nil ? AxooTheme.textSecondary : AxooTheme.textPrimary
        label.text = error ?? "Scrivi il primo commento…"
        popcorn.isHidden = loading || error != nil
        updateAnimationVisibility()
        label.accessibilityIdentifier = error == nil ? "detail-empty" : "detail-error"
        retry.isHidden = loading || error == nil
        setNeedsLayout()
        setNeedsDisplay()
    }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        if loading { return skeleton.sizeThatFits(size) }
        let textHeight = label.sizeThatFits(CGSize(width: max(1, size.width - 40), height: .greatestFiniteMagnitude)).height
        return CGSize(width: size.width, height: textHeight + (popcorn.isHidden ? (retry.isHidden ? 64 : 100) : 22 + 196 + 10 + 42))
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let height = label.sizeThatFits(CGSize(width: max(1, bounds.width - 40), height: .greatestFiniteMagnitude)).height
        popcorn.frame = CGRect(x: (bounds.width - 196) / 2, y: 22, width: 196, height: 196)
        label.frame = CGRect(x: 20, y: popcorn.isHidden ? 20 : popcorn.frame.maxY + 10, width: bounds.width - 40, height: height)
        retry.frame = CGRect(x: 20, y: label.frame.maxY + 12, width: bounds.width - 40, height: 44)
        let skeletonSize = skeleton.sizeThatFits(CGSize(width: max(1, bounds.width), height: .greatestFiniteMagnitude))
        skeleton.frame = CGRect(origin: .zero, size: skeletonSize)
        updateAnimationVisibility()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateAnimationVisibility()
    }

    func updateAnimationVisibility() {
        popcorn.updateAnimationVisibility()
    }

}

final class PostDetailHostCell: UITableViewCell {
    var hostedView: UIView? {
        didSet {
            if oldValue !== hostedView { oldValue?.removeFromSuperview() }
            if let hostedView = hostedView { contentView.addSubview(hostedView) }
            setNeedsLayout()
        }
    }
    var horizontalInset: CGFloat = 0
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func layoutSubviews() {
        super.layoutSubviews()
        hostedView?.frame = contentView.bounds.insetBy(dx: horizontalInset, dy: 0)
    }
}
