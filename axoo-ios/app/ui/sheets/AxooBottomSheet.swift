import UIKit

enum AxooSheetStyle {
    static let cornerRadius: CGFloat = 28
}

/// Shared container; sheet content is ordinary UIKit controls inside a bounded scroll view.
class AxooBottomSheet: AxooViewController, UISheetPresentationControllerDelegate {
    let stack = UIStackView()
    let scroll = UIScrollView()
    var allowsDismissal = true {
        didSet {
            isModalInPresentation = !allowsDismissal
            cancelButton?.isEnabled = allowsDismissal
        }
    }
    private var cancelButton: UIButton?
    private var finishing = false
    private var selectionTaken = false
    private var measuredWidth: CGFloat = 0

    init(title: String, message: String? = nil, dismissTitle: String = AxooOverlayCopy.cancel,
         showsHeading: Bool = true, showsCancel: Bool = true) {
        super.init()
        self.title = title
        modalPresentationStyle = .pageSheet
        loadViewIfNeeded()
        let heading = UILabel.axoo(title, font: AxooTypography.statusTitle)
        if showsHeading {
            stack.addArrangedSubview(heading)
            stack.setCustomSpacing(12, after: heading)
        }
        if let message = message {
            let label = UILabel.axoo(message, font: AxooTypography.statusBody, color: .secondaryLabel)
            stack.addArrangedSubview(label)
            stack.setCustomSpacing(12, after: label)
        }
        if showsCancel { cancelButton = addButton(dismissTitle, testID: "sheet-cancel") { } }
        if let sheet = sheetPresentationController {
            sheet.delegate = self
            sheet.preferredCornerRadius = AxooSheetStyle.cornerRadius
            sheet.prefersGrabberVisible = true
            sheet.prefersScrollingExpandsWhenScrolledToEdge = false
            sheet.prefersEdgeAttachedInCompactHeight = true
            sheet.detents = [.custom(identifier: .init("axoo-content")) { [weak self] context in
                guard let self else { return nil }
                let width = self.view.bounds.width
                return min(context.maximumDetentValue, self.fittingHeight(width: width))
            }]
        }
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AxooTheme.panel
        stack.axis = .vertical
        stack.spacing = 4
        scroll.showsVerticalScrollIndicator = false
        view.addSubview(scroll)
        scroll.addSubview(stack)
        scroll.translatesAutoresizingMaskIntoConstraints = false
        stack.pinEdges(to: scroll.contentLayoutGuide, insets: UIEdgeInsets(top: 10, left: 20, bottom: 20, right: 20))
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.topAnchor, constant: 23),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -40)
        ])
    }
    /// Inserts before the common cancel button, keeping content order predictable.
    @discardableResult
    func addButton(_ title: String, destructive: Bool = false, icon: AxooIcon? = nil,
                   testID: String? = nil, action: @escaping () -> Void) -> UIButton {
        let button = UIButton.axoo(title) { [weak self] in
            guard let self = self, self.allowsDismissal, !self.isBeingDismissed,
                  !self.finishing, !self.selectionTaken else { return }
            self.selectionTaken = true
            Task { await self.close(); action() }
        }
        button.contentHorizontalAlignment = .left
        button.configuration?.image = icon?.image(pointSize: 24)
        button.configuration?.imagePadding = icon == nil ? 0 : 16
        let foregroundColor: UIColor = destructive ? .systemRed : AxooTheme.textPrimary
        button.tintColor = foregroundColor
        button.configuration?.baseForegroundColor = foregroundColor
        button.configuration?.imageColorTransformer = UIConfigurationColorTransformer { _ in foregroundColor }
        button.configuration?.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8)
        button.configuration?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var attributes = incoming
            attributes.font = AxooTypography.sheetAction
            return attributes
        }
        button.accessibilityIdentifier = testID
        if cancelButton == nil {
            stack.addArrangedSubview(button)
        } else { stack.insertArrangedSubview(button, at: stack.arrangedSubviews.count - 1) }
        preferredContentSizeDidChange()
        return button
    }
    func appendContent(_ content: UIView) {
        let hasCancel = cancelButton != nil
        stack.insertArrangedSubview(content, at: stack.arrangedSubviews.count - (hasCancel ? 1 : 0))
        preferredContentSizeDidChange()
    }
    func preferredContentSizeDidChange() { sheetPresentationController?.invalidateDetents() }
    func fittingHeight(width: CGFloat) -> CGFloat {
        stack.systemLayoutSizeFitting(CGSize(width: max(1, width - 40), height: 0),
                        withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height + 53
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if measuredWidth != view.bounds.width {
            measuredWidth = view.bounds.width
            preferredContentSizeDidChange()
        }
    }

    func close() async {
        guard !finishing else { return }
        finishing = true

        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            dismiss(animated: true) { continuation.resume() }
        }
    }
    func presentationControllerShouldDismiss(_ presentationController: UIPresentationController) -> Bool {
        !isModalInPresentation
    }
    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        finishing = true
    }
}
