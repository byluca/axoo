import UIKit

/// Author and university displayed beside the avatar.
final class PostEditorHeader: UIView {
    private let avatar = AnonAvatarView(frame: .zero)
    private let nickname = UILabel.axoo()
    private let university = UILabel.axoo(color: AxooTheme.textSecondary)
    private let labels = UIStackView()

    init(author: PostEditorAuthor?) {
        super.init(frame: .zero)
        nickname.textColor = AxooTheme.textPrimary
        nickname.text = author?.nickname.isEmpty == false ? author?.nickname : "Anonimo"
        university.text = author.map { "· " + $0.university }
        university.isHidden = author?.university.isEmpty != false
        nickname.accessibilityIdentifier = "editor-author"
        university.accessibilityIdentifier = "editor-university"
        avatar.configure(author?.avatar)
        labels.axis = .horizontal
        labels.alignment = .center
        labels.spacing = PostEditorStyle.metadataSpacing
        nickname.setContentHuggingPriority(.required, for: .horizontal)
        university.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        university.lineBreakMode = .byTruncatingTail
        university.numberOfLines = 1
        [nickname, university].forEach { labels.addArrangedSubview($0) }
        [avatar, labels].forEach { addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        NSLayoutConstraint.activate([
            avatar.leadingAnchor.constraint(equalTo: leadingAnchor), avatar.topAnchor.constraint(equalTo: topAnchor),
            avatar.widthAnchor.constraint(equalToConstant: PostEditorStyle.avatarSize), avatar.heightAnchor.constraint(equalToConstant: PostEditorStyle.avatarSize),
            avatar.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor),
            labels.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: PostEditorStyle.avatarTextSpacing),
            labels.trailingAnchor.constraint(equalTo: trailingAnchor), labels.topAnchor.constraint(equalTo: topAnchor),
            labels.bottomAnchor.constraint(equalTo: bottomAnchor), heightAnchor.constraint(greaterThanOrEqualToConstant: PostEditorStyle.avatarSize)
        ])
        nickname.font = AxooTypography.editorAuthor
        nickname.setVerifiedName(nickname.text ?? "Anonimo", configuration: author?.avatar.configuration)
        university.font = AxooTypography.editorMetadata
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

}

final class PostEditorTopBar: UIView {
    let back = UIButton(type: .system)
    let publish = UIButton(type: .system)

    init() {
        super.init(frame: .zero)
        backgroundColor = AxooTheme.cardBackground
        back.tintColor = AxooTheme.textPrimary
        back.setImage(AxooIcon.back.image(pointSize: 24), for: .normal)
        back.accessibilityIdentifier = "editor-back"
        publish.accessibilityIdentifier = "editor-publish"
        publish.setContentHuggingPriority(.required, for: .horizontal)
        publish.setContentCompressionResistancePriority(.required, for: .horizontal)
        [back, publish].forEach { addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        NSLayoutConstraint.activate([
            back.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 4),
            back.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            back.widthAnchor.constraint(equalToConstant: 48), back.heightAnchor.constraint(equalToConstant: 48),
            back.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4),
            publish.leadingAnchor.constraint(greaterThanOrEqualTo: back.trailingAnchor, constant: 8),
            publish.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            publish.centerYAnchor.constraint(equalTo: centerYAnchor),
            publish.heightAnchor.constraint(equalToConstant: PostEditorStyle.publishHeight)
        ])
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(canPublish: Bool, isSaving: Bool, isEditing: Bool) {
        back.isEnabled = !isSaving
        publish.isEnabled = canPublish
        var configuration = UIButton.Configuration.filled()
        configuration.title = isSaving ? nil : isEditing ? AxooOverlayCopy.save : "Pubblica"
        configuration.showsActivityIndicator = isSaving
        configuration.baseBackgroundColor = canPublish ? AxooTheme.accent : AxooTheme.surface
        configuration.baseForegroundColor = canPublish ? .white : AxooTheme.textSecondary
        configuration.cornerStyle = .fixed
        configuration.background.cornerRadius = PostEditorStyle.publishRadius
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: PostEditorStyle.publishHorizontalInset, bottom: 0, trailing: PostEditorStyle.publishHorizontalInset)
        configuration.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attributes in
            var next = attributes
            next.font = AxooTypography.editorPublish
            return next
        }
        publish.configuration = configuration
    }
}
