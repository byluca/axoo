import UIKit

/// Card compatta per conferme e avvisi, con spaziature condivise con Android.
final class AxooConfirmationCard: AxooViewController {
    private let heading: String?
    private let message: String
    private let cancelTitle: String?
    private let actionTitle: String
    private let destructive: Bool
    private let action: () -> Void
    private let dismissOnAction: Bool
    private var busy = false
    private let contentView: UIView?
    private let allowsDismissal: Bool
    private let headerView: UIView?
    private let cancelAction: (() -> Void)?
    private let lineSpacing: CGFloat
    private var finishing = false
    private let headingLabel = UILabel.axoo(font: AxooTypography.confirmationTitle)
    private let bodyLabel = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    private let buttons = UIStackView()

    init(title: String?, message: String, cancelTitle: String? = AxooOverlayCopy.cancel,
         actionTitle: String, destructive: Bool = false, contentView: UIView? = nil,
         allowsDismissal: Bool = true, headerView: UIView? = nil, lineSpacing: CGFloat = 2,
         cancelAction: (() -> Void)? = nil, dismissOnAction: Bool = true, action: @escaping () -> Void = {}) {
        heading = title
        self.message = message
        self.cancelTitle = cancelTitle
        self.actionTitle = actionTitle
        self.destructive = destructive
        self.action = action
        self.dismissOnAction = dismissOnAction
        self.contentView = contentView
        self.allowsDismissal = allowsDismissal
        self.headerView = headerView
        self.cancelAction = cancelAction
        self.lineSpacing = lineSpacing
        super.init()
        modalPresentationStyle = .overFullScreen
        modalTransitionStyle = .crossDissolve
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.black.withAlphaComponent(0.25)

        let backdrop = UIControl()
        backdrop.addAction(UIAction { [weak self] _ in self?.close(confirmed: false) }, for: .touchUpInside)
        view.addSubview(backdrop)
        backdrop.pinEdges(to: view)

        let card = UIView()
        card.backgroundColor = AxooTheme.panel
        card.layer.cornerRadius = 16
        card.clipsToBounds = true
        card.accessibilityIdentifier = "confirmation-card"
        view.addSubview(card)
        card.translatesAutoresizingMaskIntoConstraints = false

        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = false
        card.addSubview(scroll)
        scroll.pinEdges(to: card)
        let content = UIStackView()
        content.axis = .vertical
        content.spacing = 8
        scroll.addSubview(content)
        content.pinEdges(to: scroll.contentLayoutGuide, insets: UIEdgeInsets(top: 20, left: 20, bottom: 20, right: 20))
        headingLabel.text = heading
        headingLabel.textAlignment = .left
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = lineSpacing
        bodyLabel.attributedText = NSAttributedString(string: message, attributes: [.paragraphStyle: paragraph])
        bodyLabel.textAlignment = .left
        if let headerView { content.addArrangedSubview(headerView); content.setCustomSpacing(12, after: headerView) }
        if heading != nil { content.addArrangedSubview(headingLabel) }
        content.addArrangedSubview(bodyLabel)
        if let contentView { content.addArrangedSubview(contentView) }

        buttons.axis = .horizontal
        buttons.distribution = .fillEqually
        buttons.spacing = 8
        if let cancelTitle {
            buttons.addArrangedSubview(textButton(cancelTitle, color: AxooTheme.textSecondary,
                identifier: "confirmation-cancel", confirmed: false))
        }
        buttons.addArrangedSubview(textButton(actionTitle, color: destructive ? .systemRed : AxooTheme.textPrimary,
            identifier: "confirmation-action", confirmed: true))
        content.setCustomSpacing(16, after: contentView ?? bodyLabel)
        content.addArrangedSubview(buttons)

        let preferredWidth = card.widthAnchor.constraint(equalToConstant: 320)
        preferredWidth.priority = .defaultHigh
        let contentHeight = card.heightAnchor.constraint(equalTo: content.heightAnchor, constant: 40)
        contentHeight.priority = .defaultHigh
        let safe = view.safeAreaLayoutGuide
        let preferredCenter = card.centerYAnchor.constraint(equalTo: safe.centerYAnchor)
        preferredCenter.priority = .defaultHigh
        NSLayoutConstraint.activate([
            card.centerXAnchor.constraint(equalTo: safe.centerXAnchor),
            preferredCenter,
            card.topAnchor.constraint(greaterThanOrEqualTo: safe.topAnchor, constant: 24),
            card.bottomAnchor.constraint(lessThanOrEqualTo: view.keyboardLayoutGuide.topAnchor, constant: -24),
            card.leadingAnchor.constraint(greaterThanOrEqualTo: safe.leadingAnchor, constant: 24),
            card.trailingAnchor.constraint(lessThanOrEqualTo: safe.trailingAnchor, constant: -24),
            card.heightAnchor.constraint(lessThanOrEqualTo: safe.heightAnchor, constant: -48),
            card.widthAnchor.constraint(lessThanOrEqualToConstant: 320),
            content.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -40),
            preferredWidth, contentHeight
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard buttons.bounds.width > 0 else { return }
        let availableWidth = (buttons.bounds.width - buttons.spacing) / 2
        let stacked = buttons.arrangedSubviews.count > 1 && buttons.arrangedSubviews.contains { view in
            guard let button = view as? UIButton, let label = button.titleLabel else { return false }
            return label.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)).width + 16 > availableWidth
        }
        let axis: NSLayoutConstraint.Axis = stacked ? .vertical : .horizontal
        if buttons.axis != axis { buttons.axis = axis }
    }

    private func textButton(_ title: String, color: UIColor, identifier: String, confirmed: Bool) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.setTitleColor(color, for: .normal)
        button.titleLabel?.font = AxooTypography.confirmationAction
        button.titleLabel?.numberOfLines = 0
        button.titleLabel?.textAlignment = .center
        button.contentEdgeInsets = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        button.accessibilityIdentifier = identifier
        button.addAction(UIAction { [weak self] _ in self?.close(confirmed: confirmed, explicitCancel: !confirmed) }, for: .touchUpInside)
        return button
    }

    func setBusy(_ value: Bool) {
        busy = value
        buttons.arrangedSubviews.forEach { ($0 as? UIButton)?.isEnabled = !value }
        isModalInPresentation = value || !allowsDismissal
    }

    private func close(confirmed: Bool, explicitCancel: Bool = false) {
        guard !busy, (confirmed || allowsDismissal || (explicitCancel && cancelAction != nil)), !finishing, !isBeingDismissed else { return }
        if confirmed && !dismissOnAction { action(); return }
        finishing = true

        dismiss(animated: true) { [self] in
            if confirmed { action() } else if explicitCancel { cancelAction?() }
        }
    }

}

extension UIViewController {
    func presentError(_ message: String) {
        present(AxooConfirmationCard(title: AxooOverlayCopy.errorTitle, message: message,
            cancelTitle: nil, actionTitle: AxooOverlayCopy.ok), animated: true)
    }
}
