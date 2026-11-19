import UIKit

/// Option fields share the card's emoji toggle with the currently focused input.
final class PostPollEditorView: UIStackView {
    var onChange: (Int, String, [EmojiEntity]) -> Void = { _, _, _ in }
    var onAdd: () -> Void = {}
    var onRemove: (Int) -> Void = { _ in }
    var onActivate: (EmojiTextView) -> Void = { _ in }
    var onUnbind: (EmojiTextView) -> Void = { _ in }
    private var rows: [PollOptionEditorRow] = []
    private let add = UIButton(type: .system)
    private let note = UILabel.axoo("Una sola scelta. Dopo la pubblicazione il post non è modificabile.",
                                  font: AxooTypography.statusBody.withSize(12), color: AxooTheme.textSecondary)

    init() {
        super.init(frame: .zero)
        axis = .vertical
        spacing = 0
        isHidden = true
        add.setTitle("+ Aggiungi opzione", for: .normal)
        add.titleLabel?.font = AxooTypography.statusBody
        add.setTitleColor(AxooTheme.accent, for: .normal)
        add.contentHorizontalAlignment = .left
        add.accessibilityIdentifier = "editor-poll-add"
        add.addAction(UIAction { [weak self] _ in self?.onAdd() }, for: .touchUpInside)
        let addHeight = add.heightAnchor.constraint(equalToConstant: 44)
        addHeight.priority = UILayoutPriority(999)
        addHeight.isActive = true
        note.numberOfLines = 0
        addArrangedSubview(add)
        addArrangedSubview(note)
        setCustomSpacing(8, after: add)
    }
    @available(*, unavailable)
    required init(coder: NSCoder) { fatalError("Programmatic UI only") }

    func configure(_ options: [PollOptionInput]?, enabled: Bool) {
        isHidden = options == nil
        let options = options ?? []
        while rows.count > options.count {
            let row = rows.removeLast()
            onUnbind(row.input)
            row.input.resignFirstResponder()
            removeArrangedSubview(row)
            row.removeFromSuperview()
        }
        while rows.count < options.count {
            let index = rows.count
            let row = PollOptionEditorRow(index: index)
            row.onChange = { [weak self] text, entities in self?.onChange(index, text, entities) }
            row.onActivate = { [weak self] in self?.onActivate($0) }
            row.onRemove = { [weak self] in self?.onRemove(index) }
            rows.append(row)
            insertArrangedSubview(row, at: index)
        }
        for (index, row) in rows.enumerated() {
            row.configure(options[index], canRemove: options.count > 2, enabled: enabled)
        }
        add.isHidden = options.isEmpty || options.count >= 4
        note.isHidden = options.isEmpty
        add.isEnabled = enabled
    }
    func setPlaybackEnabled(_ enabled: Bool) { rows.forEach { $0.input.playbackEnabled = enabled } }
}

private final class PollOptionEditorRow: UIView, UITextViewDelegate {
    let input = EmojiEditTextView()
    var onChange: (String, [EmojiEntity]) -> Void = { _, _ in }
    var onActivate: (EmojiTextView) -> Void = { _ in }
    var onRemove: () -> Void = {}
    private let placeholder = UILabel.axoo(color: AxooTheme.textSecondary)
    private let counter = UILabel.axoo(font: AxooTypography.editorCounter.withSize(11))
    private let remove = UIButton(type: .custom)
    private let square = UIView()
    private let divider = UIView()
    private var inputHeight: NSLayoutConstraint!

    init(index: Int) {
        super.init(frame: .zero)
        input.delegate = self
        input.font = AxooTypography.editorBody
        input.textColor = AxooTheme.textPrimary
        input.tintColor = AxooTheme.accent
        input.textContainerInset = UIEdgeInsets(top: 12, left: 0, bottom: 12, right: 0)
        input.textContainer.lineFragmentPadding = 0
        input.isScrollEnabled = false
        input.supportsMedia = false
        input.accessibilityIdentifier = "editor-poll-option-\(index)"
        placeholder.font = AxooTypography.editorBody
        placeholder.text = "Opzione \(index + 1)"
        placeholder.isUserInteractionEnabled = false
        counter.textAlignment = .right
        square.layer.cornerRadius = 4
        square.layer.borderWidth = 1
        square.layer.borderColor = AxooTheme.cardBorder.cgColor
        divider.backgroundColor = AxooTheme.cardBorder
        remove.setImage(AxooIcon.close.image(pointSize: 14), for: .normal)
        remove.tintColor = AxooTheme.textSecondary
        remove.accessibilityLabel = "Rimuovi opzione \(index + 1)"
        remove.addAction(UIAction { [weak self] _ in self?.onRemove() }, for: .touchUpInside)
        [square, input, placeholder, counter, remove, divider].forEach {
            addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false
        }
        inputHeight = input.heightAnchor.constraint(equalToConstant: 48)
        NSLayoutConstraint.activate([
            square.leadingAnchor.constraint(equalTo: leadingAnchor), square.topAnchor.constraint(equalTo: topAnchor, constant: 15),
            square.widthAnchor.constraint(equalToConstant: 18), square.heightAnchor.constraint(equalToConstant: 18),
            input.leadingAnchor.constraint(equalTo: square.trailingAnchor, constant: 12), input.topAnchor.constraint(equalTo: topAnchor),
            input.trailingAnchor.constraint(equalTo: remove.leadingAnchor), inputHeight,
            placeholder.leadingAnchor.constraint(equalTo: input.leadingAnchor), placeholder.trailingAnchor.constraint(equalTo: input.trailingAnchor),
            placeholder.topAnchor.constraint(equalTo: input.topAnchor, constant: 12),
            counter.topAnchor.constraint(equalTo: input.bottomAnchor), counter.leadingAnchor.constraint(equalTo: input.leadingAnchor),
            counter.trailingAnchor.constraint(equalTo: input.trailingAnchor), counter.heightAnchor.constraint(equalToConstant: 14),
            counter.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4),
            remove.trailingAnchor.constraint(equalTo: trailingAnchor), remove.topAnchor.constraint(equalTo: topAnchor, constant: 2),
            remove.widthAnchor.constraint(equalToConstant: 36), remove.heightAnchor.constraint(equalToConstant: 44),
            divider.leadingAnchor.constraint(equalTo: leadingAnchor), divider.trailingAnchor.constraint(equalTo: trailingAnchor),
            divider.bottomAnchor.constraint(equalTo: bottomAnchor), divider.heightAnchor.constraint(equalToConstant: 0.5)
        ])
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(_ value: PollOptionInput, canRemove: Bool, enabled: Bool) {
        input.setDocument(EmojiDocument(text: value.text, entities: value.entities))
        input.isEditable = enabled
        remove.isHidden = !canRemove
        remove.isEnabled = enabled
        placeholder.isHidden = !value.text.isEmpty
        counter.text = value.characterCount >= 80 ? "\(value.characterCount)/100" : ""
        counter.textColor = value.characterCount > 100 ? AxooTheme.destructive : AxooTheme.textSecondary
        setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard input.bounds.width > 0 else { return }
        let height = max(48, ceil(input.sizeThatFits(CGSize(width: input.bounds.width, height: .greatestFiniteMagnitude)).height))
        if inputHeight.constant != height { inputHeight.constant = height }
    }
    func textViewDidBeginEditing(_ textView: UITextView) { onActivate(input) }
    func textViewDidChange(_ textView: UITextView) {
        guard !input.isApplyingDocument else { return }
        onChange(input.document.text, input.document.entities)
        setNeedsLayout()
    }
}
