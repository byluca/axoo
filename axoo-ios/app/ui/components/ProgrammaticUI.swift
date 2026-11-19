import UIKit

/// Common setup only; every screen builds its own hierarchy in code.
class AxooViewController: UIViewController {
    private let statusBarBackground = UIView()
    var allowsBackSwipe: Bool { true }
    init() { super.init(nibName: nil, bundle: nil) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AxooTheme.background
        view.tintColor = AxooTheme.accent
        navigationItem.backButtonDisplayMode = .minimal
        statusBarBackground.backgroundColor = AxooTheme.cardBackground
        statusBarBackground.isUserInteractionEnabled = false
        view.addSubview(statusBarBackground)
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        statusBarBackground.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: view.safeAreaInsets.top)
        view.bringSubviewToFront(statusBarBackground)
        EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate()
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if isViewLoaded { view.setNeedsLayout() }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate()
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate()
    }
}

/// Drawn components regenerate their backing store for new bounds and appearance traits.
class AxooDrawingView: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        contentMode = .redraw
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsDisplay()
        setNeedsLayout()
    }
}

extension UIView {
    func pinEdges(to view: UIView, insets: UIEdgeInsets = .zero) {
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: insets.left),
            trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -insets.right),
            topAnchor.constraint(equalTo: view.topAnchor, constant: insets.top),
            bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -insets.bottom)
        ])
    }

    func pinEdges(to guide: UILayoutGuide, insets: UIEdgeInsets = .zero) {
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: insets.left),
            trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -insets.right),
            topAnchor.constraint(equalTo: guide.topAnchor, constant: insets.top),
            bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -insets.bottom)
        ])
    }
}

extension UIScrollView {
    func hideTopEdgeEffect() {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            topEdgeEffect.isHidden = true
        }
        #endif
    }
}

extension UILabel {
    static func axoo(_ text: String = "", font: UIFont = AxooTypography.postBody,
                     color: UIColor = AxooTheme.textPrimary) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = font
        label.textColor = color
        label.numberOfLines = 0
        return label
    }
}

extension UIButton {
    static func axoo(_ title: String, primary: Bool = false, constrainsHeight: Bool = true,
                     action: @escaping () -> Void) -> UIButton {
        let button = UIButton(type: .system)
        var config = primary ? UIButton.Configuration.filled() : .plain()
        config.title = title
        config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var attributes = incoming
            attributes.font = AxooTypography.button
            return attributes
        }
        config.baseBackgroundColor = AxooTheme.accent
        config.baseForegroundColor = primary ? .white : AxooTheme.textPrimary
        config.cornerStyle = .large
        config.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16)
        button.configuration = config
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
        if constrainsHeight {
            let minimumHeight = button.heightAnchor.constraint(greaterThanOrEqualToConstant: primary ? 54 : 44)
            // UIStackView must be able to collapse a hidden arranged button to zero.
            minimumHeight.priority = UILayoutPriority(999)
            minimumHeight.isActive = true
        }
        return button
    }

    static func icon(_ icon: AxooIcon, size: CGFloat = 24, action: @escaping () -> Void) -> UIButton {
        let button = UIButton(type: .custom)
        // Render the original vector at a predictable point size inside a 44pt target.
        button.setImage(icon.image(pointSize: size), for: .normal)
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return button
    }
}

/// Scrollable, keyboard-aware form with a centered readable column.
class AxooFormController: AxooViewController {
    let scrollView = UIScrollView()
    let stack = UIStackView()
    var contentInsets: UIEdgeInsets { UIEdgeInsets(top: 24, left: 24, bottom: 24, right: 24) }
    var maximumContentWidth: CGFloat? { 520 }

    override func viewDidLoad() {
        super.viewDidLoad()
        scrollView.keyboardDismissMode = .interactive
        scrollView.showsVerticalScrollIndicator = false
        view.addSubview(scrollView)
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)
        ])
        let column = UIView()
        scrollView.addSubview(column)
        column.pinEdges(to: scrollView.contentLayoutGuide)
        column.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor).isActive = true
        stack.axis = .vertical
        stack.spacing = 24
        column.addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        let insets = contentInsets
        let horizontalInset = insets.left + insets.right
        let preferredWidth = stack.widthAnchor.constraint(equalTo: column.widthAnchor, constant: -horizontalInset)
        if let maximumContentWidth {
            preferredWidth.priority = .defaultHigh
            stack.widthAnchor.constraint(lessThanOrEqualToConstant: maximumContentWidth).isActive = true
        }
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: column.topAnchor, constant: insets.top),
            stack.bottomAnchor.constraint(equalTo: column.bottomAnchor, constant: -insets.bottom),
            stack.centerXAnchor.constraint(equalTo: column.centerXAnchor),
            stack.widthAnchor.constraint(lessThanOrEqualTo: column.widthAnchor, constant: -horizontalInset),
            preferredWidth
        ])
    }
}
