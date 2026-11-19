import UIKit

final class ProfileBioView: UIView {
    private let heading = UILabel.axoo("Bio", font: UIFont(name: "Baloo2-Bold", size: 15) ?? .boldSystemFont(ofSize: 15))
    private let body = EmojiTextView(readOnly: true)
    private let onEdit: (() -> Void)?
    private var bodyTop: NSLayoutConstraint!

    init(onEdit: (() -> Void)? = nil) {
        self.onEdit = onEdit
        super.init(frame: .zero)
        backgroundColor = AxooTheme.cardBackground
        layer.cornerRadius = 24
        layer.borderWidth = 0.7
        body.font = UIFont(name: "Inter-Regular", size: 17) ?? .systemFont(ofSize: 17)
        body.playbackEnabled = false
        body.isSelectable = false
        body.isUserInteractionEnabled = false
        [heading, body].forEach { addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        bodyTop = body.topAnchor.constraint(equalTo: topAnchor, constant: 16)
        NSLayoutConstraint.activate([
            heading.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            heading.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            heading.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            bodyTop,
            body.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            body.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            body.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -20),
        ])
        if onEdit != nil {
            accessibilityIdentifier = "profile-bio-edit"
            accessibilityTraits.insert(.button)
            addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(edit)))
        }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    func configure(_ document: EmojiDocument) {
        let empty = document.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        isHidden = empty && onEdit == nil
        heading.isHidden = empty
        bodyTop.constant = empty ? 16 : 16 + ceil(heading.font.lineHeight) + 10
        body.textColor = empty ? .secondaryLabel : AxooTheme.textPrimary
        body.setDocument(empty ? EmojiDocument(text: "Bio") : document)
    }
    func setActive(_ active: Bool) { body.playbackEnabled = active }
    @objc private func edit() { onEdit?() }
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }
}
