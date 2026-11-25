import UIKit

final class HomeHeader: UIView {
    static let minimumHeight: CGFloat = 52
    private let logo = UILabel.axoo("Axoo", font: AxooTypography.homeLogo, color: AxooTheme.accent)
    private let badge = UnreadCountBadge()
    private let tabs = UISegmentedControl.axoo(items: HomeFeedTab.allCases.map(\.title), showsSelectionIndicator: false)

    init(onSelect: @escaping (HomeFeedTab) -> Void) {
        super.init(frame: .zero)
        backgroundColor = AxooTheme.cardBackground
        layer.zPosition = 1
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.08
        layer.shadowRadius = 3
        layer.shadowOffset = CGSize(width: 0, height: 2)
        logo.numberOfLines = 1
        tabs.accessibilityIdentifier = "feed-tabs"
        tabs.addAction(UIAction { [weak tabs] _ in
            guard let tabs else { return }
            onSelect(HomeFeedTab.allCases[tabs.selectedSegmentIndex])
        }, for: .valueChanged)
        [logo, tabs, badge].forEach { addSubview($0) }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    func configure(selected: HomeFeedTab, newUnreadCount: Int) {
        tabs.selectedSegmentIndex = HomeFeedTab.allCases.firstIndex(of: selected) ?? 0
        badge.count = newUnreadCount
        setNeedsLayout()
    }
    private func stacksTabs(at width: CGFloat) -> Bool {
        let logoWidth = ("Axoo" as NSString).size(withAttributes: [.font: AxooTypography.homeLogo]).width * 1.06
        return logoWidth + 154 + 48 > width
    }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let stacked = stacksTabs(at: size.width)
        let logoHeight = AxooTypography.homeLogo.lineHeight
        let tabHeight = max(44, AxooTypography.feedTab.lineHeight + 12)
        return CGSize(width: size.width, height: stacked ? logoHeight + tabHeight + 8 : max(Self.minimumHeight, max(logoHeight, tabHeight)))
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.shadowPath = UIBezierPath(rect: bounds).cgPath
        let stacked = stacksTabs(at: bounds.width)
        logo.font = AxooTypography.homeLogo
        logo.transform = .identity
        let logoSize = logo.sizeThatFits(bounds.size)
        let tabHeight = max(44, AxooTypography.feedTab.lineHeight + 12)
        logo.frame = CGRect(x: 16, y: stacked ? 0 : (bounds.height - logoSize.height) / 2,
                            width: min(logoSize.width, (bounds.width - 32) / 1.06), height: logoSize.height)
        logo.transform = CGAffineTransform(scaleX: 1.06, y: 1)
        logo.center.x = 16 + logo.bounds.width * 1.06 / 2
        tabs.setTitleTextAttributes([.font: AxooTypography.feedTab, .foregroundColor: AxooTheme.textSecondary], for: .normal)
        tabs.setTitleTextAttributes([.font: AxooTypography.feedTab, .foregroundColor: AxooTheme.accent], for: .selected)
        let tabWidth = stacked ? bounds.width - 32 : CGFloat(154)
        tabs.frame = CGRect(x: stacked ? 16 : bounds.width - 16 - tabWidth,
                            y: stacked ? logoSize.height : (bounds.height - tabHeight) / 2,
                            width: tabWidth, height: tabHeight)
        let size = badge.sizeThatFits(bounds.size)
        let labelWidth = ("New" as NSString).size(withAttributes: [.font: AxooTypography.feedTab]).width
        badge.frame = CGRect(x: min(bounds.width - 16 - size.width, tabs.frame.minX + tabWidth * 0.75 + labelWidth / 2 - 3),
                             y: tabs.frame.midY - size.height, width: size.width, height: size.height)
    }
}
