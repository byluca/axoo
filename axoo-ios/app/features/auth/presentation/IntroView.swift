import UIKit

final class IntroView: AxooViewController, UIScrollViewDelegate {
    private let onStart: () -> Void
    private let pager = UIScrollView()
    private var currentPage = 0
    private let content = UIStackView()
    private var animations: [AnimationView] = []
    private var lastWidth: CGFloat = 0
    private let entries = [
        ("", "Axoo", "<b>Connettiti</b> con chi ti sta <b>intorno</b>.", true),
        ("party-icon", "Locale", "Scopri le <b>storie</b> del campus,<br/>raccontate da chi lo <b>vive</b>.", false),
        ("ghost-hamster", "Anonimo", "Condividi i tuoi <b>pensieri</b><br/>in completo <b>anonimato</b>.", false),
        ("popcorn-icon", "Divertente", "<b>Domande</b>, <b>meme</b>, <b>spotted</b><br/>e un sacco di <b>altra roba</b>.", false)
    ]

    init(onStart: @escaping () -> Void) { self.onStart = onStart; super.init() }

    override func viewDidLoad() {
        super.viewDidLoad()
        pager.isPagingEnabled = true
        pager.showsHorizontalScrollIndicator = false
        pager.showsVerticalScrollIndicator = false
        pager.delegate = self
        view.addSubview(pager)
        let start = UIButton.axoo("Inizia", primary: true, constrainsHeight: false, action: onStart)
        start.configurationUpdateHandler = { button in
            var config = button.configuration
            config?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
                var attributes = incoming
                attributes.font = AxooTypography.primaryButton
                return attributes
            }
            button.configuration = config
        }
        start.accessibilityIdentifier = "intro-start"
        start.configuration?.cornerStyle = .fixed
        start.configuration?.background.cornerRadius = 18
        start.heightAnchor.constraint(equalToConstant: 52).isActive = true
        view.addSubview(start)
        [pager, start].forEach { $0.translatesAutoresizingMaskIntoConstraints = false }
        NSLayoutConstraint.activate([
            pager.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            pager.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            pager.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            pager.bottomAnchor.constraint(equalTo: start.topAnchor, constant: -8),
            start.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            start.widthAnchor.constraint(lessThanOrEqualToConstant: 500),
            start.leadingAnchor.constraint(greaterThanOrEqualTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            start.trailingAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            start.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24)
        ])
        let width = start.widthAnchor.constraint(equalTo: view.safeAreaLayoutGuide.widthAnchor, constant: -48)
        width.priority = .defaultHigh
        width.isActive = true
        content.axis = .horizontal
        content.distribution = .fillEqually
        pager.addSubview(content)
        content.pinEdges(to: pager.contentLayoutGuide)
        content.heightAnchor.constraint(equalTo: pager.frameLayoutGuide.heightAnchor).isActive = true
        for (index, entry) in entries.enumerated() {
            let page = UIScrollView()
            page.showsVerticalScrollIndicator = false
            page.delegate = self
            content.addArrangedSubview(page)
            page.widthAnchor.constraint(equalTo: pager.frameLayoutGuide.widthAnchor).isActive = true
            let pageContent = UIView()
            page.addSubview(pageContent)
            pageContent.pinEdges(to: page.contentLayoutGuide)
            let preferredHeight = pageContent.heightAnchor.constraint(equalTo: page.frameLayoutGuide.heightAnchor)
            preferredHeight.priority = .defaultLow
            let stack = UIStackView()
            stack.axis = .vertical
            stack.alignment = .center
            stack.spacing = 12
            pageContent.addSubview(stack)
            stack.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                pageContent.widthAnchor.constraint(equalTo: page.frameLayoutGuide.widthAnchor),
                pageContent.heightAnchor.constraint(greaterThanOrEqualTo: page.frameLayoutGuide.heightAnchor),
                preferredHeight,
                stack.leadingAnchor.constraint(equalTo: pageContent.leadingAnchor, constant: 20),
                stack.trailingAnchor.constraint(equalTo: pageContent.trailingAnchor, constant: -20),
                stack.topAnchor.constraint(greaterThanOrEqualTo: pageContent.topAnchor, constant: 24),
                stack.bottomAnchor.constraint(lessThanOrEqualTo: pageContent.bottomAnchor, constant: -24),
                stack.centerYAnchor.constraint(equalTo: pageContent.centerYAnchor)
            ])
            let iconContainer = UIView()
            iconContainer.translatesAutoresizingMaskIntoConstraints = false
            stack.addArrangedSubview(iconContainer)
            stack.setCustomSpacing(16, after: iconContainer)
            iconContainer.widthAnchor.constraint(equalToConstant: 220).isActive = true
            iconContainer.heightAnchor.constraint(equalToConstant: 220).isActive = true

            if index == 0 {
                let staticIcon = UIImageView(image: UIImage(named: "AxooLogo"))
                staticIcon.contentMode = .scaleAspectFit
                staticIcon.layer.cornerRadius = 40
                staticIcon.layer.cornerCurve = .continuous
                staticIcon.clipsToBounds = true
                staticIcon.translatesAutoresizingMaskIntoConstraints = false
                iconContainer.addSubview(staticIcon)
                NSLayoutConstraint.activate([
                    staticIcon.topAnchor.constraint(equalTo: iconContainer.topAnchor),
                    staticIcon.bottomAnchor.constraint(equalTo: iconContainer.bottomAnchor),
                    staticIcon.leadingAnchor.constraint(equalTo: iconContainer.leadingAnchor),
                    staticIcon.trailingAnchor.constraint(equalTo: iconContainer.trailingAnchor)
                ])
            } else {
                let animation = AnimationView(name: entry.0)
                animations.append(animation)
                animation.contentMode = .scaleAspectFit
                animation.translatesAutoresizingMaskIntoConstraints = false
                iconContainer.addSubview(animation)
                NSLayoutConstraint.activate([
                    animation.topAnchor.constraint(equalTo: iconContainer.topAnchor),
                    animation.bottomAnchor.constraint(equalTo: iconContainer.bottomAnchor),
                    animation.leadingAnchor.constraint(equalTo: iconContainer.leadingAnchor),
                    animation.trailingAnchor.constraint(equalTo: iconContainer.trailingAnchor)
                ])
            }
            let title = UILabel.axoo(entry.1, font: entry.3 ? AxooTypography.introTitleBrand : AxooTypography.introTitle)
            if entry.3 { title.textColor = AxooTheme.accent }
            let message = UILabel()
            message.numberOfLines = 0
            message.attributedText = parseHTMLSubtitle(entry.2)
            [title, message].forEach { $0.textAlignment = .center; stack.addArrangedSubview($0) }
            let pages = IntroPageIndicator()
            pages.numberOfPages = entries.count
            pages.currentPage = index
            pages.tag = index
            pages.addTarget(self, action: #selector(changePage(_:)), for: .valueChanged)
            stack.setCustomSpacing(16, after: message)
            stack.addArrangedSubview(pages)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if pager.bounds.width != lastWidth {
            lastWidth = pager.bounds.width
            scrollToCurrentPage()
        }
        updateAnimations()
    }
    private func parseHTMLSubtitle(_ html: String) -> NSAttributedString {
        let text = html.replacingOccurrences(of: "<br/>", with: "\n")
        let components = text.components(separatedBy: "<b>")
        let result = NSMutableAttributedString()

        for (i, component) in components.enumerated() {
            if i == 0 {
                result.append(NSAttributedString(string: component, attributes: [.font: AxooTypography.introSubtitle, .foregroundColor: AxooTheme.textSecondary]))
            } else {
                let subcomponents = component.components(separatedBy: "</b>")
                if let boldPart = subcomponents.first {
                    result.append(NSAttributedString(string: boldPart, attributes: [.font: AxooTypography.introSubtitleBold, .foregroundColor: AxooTheme.textSecondary]))
                }
                if subcomponents.count > 1 {
                    result.append(NSAttributedString(string: subcomponents[1], attributes: [.font: AxooTypography.introSubtitle, .foregroundColor: AxooTheme.textSecondary]))
                }
            }
        }
        return result
    }

    @objc private func changePage(_ sender: IntroPageIndicator) {
        currentPage = sender.currentPage
        sender.currentPage = sender.tag
        scrollToCurrentPage()
    }
    private func scrollToCurrentPage() {
        pager.setContentOffset(CGPoint(x: CGFloat(currentPage) * pager.bounds.width, y: 0), animated: false)
        updateAnimations()
    }
    func scrollViewDidScroll(_ scrollView: UIScrollView) { updateAnimations() }
    private func updateAnimations() { animations.forEach { $0.updateAnimationVisibility() } }
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        guard scrollView === pager, pager.bounds.width > 0 else { return }
        currentPage = Int((pager.contentOffset.x / pager.bounds.width).rounded())
    }
}

/// Same 8pt dots, 26pt selected marker and 8pt gaps as Android.
private final class IntroPageIndicator: UIControl {
    var numberOfPages = 0 { didSet { setNeedsDisplay(); invalidateIntrinsicContentSize() } }
    var currentPage = 0 { didSet { setNeedsDisplay() } }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
    }
    convenience init() { self.init(frame: .zero) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override var intrinsicContentSize: CGSize {
        CGSize(width: CGFloat(max(0, numberOfPages - 1)) * 16 + 34, height: 44)
    }
    override func draw(_ rect: CGRect) {
        let width = CGFloat(max(0, numberOfPages - 1)) * 16 + 26
        var x = (bounds.width - width) / 2
        for index in 0..<numberOfPages {
            let selected = index == currentPage
            let size: CGFloat = selected ? 26 : 8
            (selected ? AxooTheme.accent : AxooTheme.border).setFill()
            UIBezierPath(roundedRect: CGRect(x: x, y: (bounds.height - 8) / 2, width: size, height: 8), cornerRadius: 4).fill()
            x += size + 8
        }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let point = touches.first?.location(in: self), numberOfPages > 0 else { return }
        select(currentPage + (point.x < bounds.midX ? -1 : 1))
    }
    private func select(_ page: Int) {
        currentPage = min(max(0, page), max(0, numberOfPages - 1))
        sendActions(for: .valueChanged)
    }
}
