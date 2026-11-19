import UIKit

final class EventCardView: UIView {
    private let cover = EventImageView()
    private let title = UILabel.axoo(font: AxooTypography.statusTitle)
    private let date = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.accent)
    private let venue = UILabel.axoo(font: AxooTypography.statusBody)
    private let entry = UILabel.axoo(font: AxooTypography.postMetadata, color: AxooTheme.textSecondary)

    init(detail: Bool = false) {
        super.init(frame: .zero)
        backgroundColor = AxooTheme.cardBackground
        layer.cornerRadius = 20; layer.borderWidth = 0.7; clipsToBounds = true
        title.numberOfLines = detail ? 0 : 2
        cover.heightAnchor.constraint(equalTo: cover.widthAnchor, multiplier: 4.0 / 3.0).isActive = true
        let body = UIStackView(arrangedSubviews: [title, date, venue, entry])
        body.axis = .vertical; body.spacing = 4
        body.setCustomSpacing(8, after: title)
        body.isLayoutMarginsRelativeArrangement = true
        body.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16)
        let stack = UIStackView(arrangedSubviews: [cover, body]); stack.axis = .vertical
        addSubview(stack); stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor), stack.bottomAnchor.constraint(equalTo: bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor), stack.trailingAnchor.constraint(equalTo: trailingAnchor)
        ])
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }
    func configure(_ event: CityEvent) {
        cover.configure(event.coverUrl)
        title.text = event.title
        date.text = event.cancelled ? "Evento annullato" : CityEvent.formattedDate(event.startsAt)
        venue.text = event.venue; entry.text = event.entryInfo
    }
}

private final class EventImageView: UIImageView {
    private var url: URL?
    private var task: Task<Void, Never>?
    override init(frame: CGRect) {
        super.init(frame: frame)
        contentMode = .scaleAspectFit; clipsToBounds = true
        backgroundColor = AxooTheme.surface
    }
    convenience init() { self.init(frame: .zero) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(_ value: String) {
        let next = URL(string: value)
        guard url != next else { return }
        task?.cancel(); task = nil; image = nil; url = next
        load()
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { task?.cancel(); task = nil } else { load() }
    }
    private func load() {
        guard window != nil, image == nil, task == nil, let url, url.scheme == "https" else { return }
        task = Task { [weak self] in
            let image = await PostMediaImage.load(url, animated: false, maximumPixelSize: 1200)
            guard !Task.isCancelled else { return }
            self?.image = image; self?.task = nil
        }
    }
}
