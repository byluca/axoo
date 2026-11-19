import UIKit
import PhotosUI
import UniformTypeIdentifiers

final class EventEditorHost: AxooFormController, UITextViewDelegate {
    let model: EventEditorViewModel
    override var allowsBackSwipe: Bool { false }
    override var contentInsets: UIEdgeInsets { UIEdgeInsets(top: 20, left: 20, bottom: 24, right: 20) }
    private var fields: [UITextField] = []
    private let descriptionView = UITextView()
    private let descriptionPlaceholder = UILabel.axoo("Racconta l’evento e come partecipare", color: AxooTheme.textSecondary)
    private let start = UIDatePicker()
    private let end = UIDatePicker()
    private let status = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.destructive)
    private let coverImage = PostMediaImageView()
    private let coverEmpty = UIStackView()
    private let footer = UIView()
    private var displayedCoverID: String?
    private lazy var back = ArrowBackButton { [weak self] in self?.close() }
    private lazy var cover = UIButton.axoo("Scegli locandina") { [weak self] in self?.chooseCover() }
    private lazy var save = UIButton.axoo("Pubblica", primary: true) { [weak self] in self?.model.save() }

    init(events: EventsViewModel, event: CityEvent? = nil) {
        model = EventEditorViewModel(events: events, event: event); super.init()
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        title = model.event == nil ? "Crea evento" : "Modifica evento"
        navigationItem.titleView = UILabel.axoo(title ?? "", font: AxooTypography.statusTitle)
        back.accessibilityLabel = "Indietro"; back.accessibilityIdentifier = "event-back"
        let backItem = UIBarButtonItem(customView: back); backItem.hideSharedBackground()
        navigationItem.leftBarButtonItem = backItem
        view.backgroundColor = AxooTheme.cardBackground
        scrollView.backgroundColor = AxooTheme.surface
        stack.spacing = 16
        stack.addArrangedSubview(UILabel.axoo("L’evento sarà visibile nella città della tua università.", font: AxooTypography.statusBody, color: AxooTheme.textSecondary))
        addCover()
        addField("Titolo", key: \.title, limit: 120, placeholder: "Es. Aperitivo universitario", testID: "event-title", to: section())
        let whenWhere = section("Quando e dove")
        for (label, picker, date) in [("Inizio", start, model.draft.startsAt), ("Fine", end, model.draft.endsAt)] {
            let group = UIStackView(); group.axis = .vertical; group.spacing = 6; group.alignment = .leading
            group.addArrangedSubview(caption(label))
            picker.datePickerMode = .dateAndTime; picker.preferredDatePickerStyle = .compact
            picker.timeZone = TimeZone(identifier: "Europe/Rome"); picker.locale = Locale(identifier: "it_IT")
            picker.date = date; picker.tintColor = AxooTheme.accent
            picker.accessibilityIdentifier = label == "Inizio" ? "event-start" : "event-end"
            group.addArrangedSubview(picker); whenWhere.addArrangedSubview(group)
            picker.addAction(UIAction { [weak self] _ in self?.datesChanged() }, for: .valueChanged)
        }
        whenWhere.addArrangedSubview(UILabel.axoo("Orari di Roma", font: AxooTypography.statusBody, color: AxooTheme.textSecondary))
        addField("Locale / luogo", key: \.venue, limit: 160, placeholder: "Nome del locale o del luogo", testID: "event-venue", to: whenWhere)
        addField("Indirizzo completo", key: \.address, limit: 300, placeholder: "Via e numero civico", testID: "event-address", to: whenWhere)
        let details = section("Dettagli")
        let descriptionGroup = UIStackView(); descriptionGroup.axis = .vertical; descriptionGroup.spacing = 6
        descriptionGroup.addArrangedSubview(caption("Descrizione e contatti"))
        descriptionView.text = model.draft.description; descriptionView.font = AxooTypography.postBody
        descriptionView.textColor = AxooTheme.textPrimary; descriptionView.backgroundColor = AxooTheme.surface
        descriptionView.tintColor = AxooTheme.accent
        descriptionView.textContainerInset = UIEdgeInsets(top: 14, left: 14, bottom: 14, right: 14)
        descriptionView.textContainer.lineFragmentPadding = 0
        descriptionView.layer.cornerRadius = 16; descriptionView.layer.borderWidth = AxooTheme.controlBorderWidth
        descriptionView.delegate = self; descriptionView.accessibilityIdentifier = "event-description"
        descriptionView.heightAnchor.constraint(equalToConstant: 160).isActive = true
        descriptionView.addSubview(descriptionPlaceholder); descriptionPlaceholder.translatesAutoresizingMaskIntoConstraints = false
        descriptionPlaceholder.isUserInteractionEnabled = false
        NSLayoutConstraint.activate([
            descriptionPlaceholder.leadingAnchor.constraint(equalTo: descriptionView.leadingAnchor, constant: 14),
            descriptionPlaceholder.trailingAnchor.constraint(equalTo: descriptionView.trailingAnchor, constant: -14),
            descriptionPlaceholder.topAnchor.constraint(equalTo: descriptionView.topAnchor, constant: 14)
        ])
        descriptionGroup.addArrangedSubview(descriptionView); details.addArrangedSubview(descriptionGroup)
        addField("Ingresso e prezzo", key: \.entryInfo, limit: 160, placeholder: "Es. Ingresso libero", testID: "event-entry", to: details)
        addFooter()
        model.onChange = { [weak self] in self?.render() }
        model.onSaved = { [weak self] in self?.dismiss(animated: true) }
        render()
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        descriptionView.layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
        let inset = footer.bounds.height
        if abs(scrollView.contentInset.bottom - inset) > 0.5 {
            scrollView.contentInset.bottom = inset
            scrollView.verticalScrollIndicatorInsets.bottom = inset
        }
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if navigationController?.isBeingDismissed == true || isBeingDismissed { model.stop() }
    }
    private func section(_ heading: String? = nil) -> EventEditorSection {
        let group = EventEditorSection(heading: heading)
        stack.addArrangedSubview(group); return group
    }
    private func caption(_ text: String) -> UILabel {
        UILabel.axoo(text, font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    }
    private func addCover() {
        let group = section()
        group.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 12, leading: 12, bottom: 4, trailing: 12)
        group.spacing = 0
        let frame = UIView(); frame.backgroundColor = AxooTheme.surface
        frame.layer.cornerRadius = 12; frame.clipsToBounds = true
        frame.accessibilityIdentifier = "event-cover-preview"
        frame.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(chooseCover)))
        coverImage.contentMode = .scaleAspectFit; coverImage.clipsToBounds = true
        coverImage.isAccessibilityElement = false
        frame.addSubview(coverImage); coverImage.pinEdges(to: frame)
        coverEmpty.axis = .vertical; coverEmpty.spacing = 8; coverEmpty.alignment = .center
        let icon = AxooIconView(icon: .photo)
        icon.tintColor = AxooTheme.textSecondary
        icon.widthAnchor.constraint(equalToConstant: 36).isActive = true
        icon.heightAnchor.constraint(equalToConstant: 36).isActive = true
        coverEmpty.addArrangedSubview(icon)
        coverEmpty.addArrangedSubview(UILabel.axoo("Aggiungi la locandina", font: AxooTypography.editorTitle))
        coverEmpty.addArrangedSubview(caption("Tocca per scegliere una foto"))
        frame.addSubview(coverEmpty); coverEmpty.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            frame.heightAnchor.constraint(equalToConstant: 200),
            coverEmpty.centerXAnchor.constraint(equalTo: frame.centerXAnchor),
            coverEmpty.centerYAnchor.constraint(equalTo: frame.centerYAnchor),
            coverEmpty.leadingAnchor.constraint(greaterThanOrEqualTo: frame.leadingAnchor, constant: 12),
            coverEmpty.trailingAnchor.constraint(lessThanOrEqualTo: frame.trailingAnchor, constant: -12)
        ])
        cover.configuration?.baseForegroundColor = AxooTheme.accent
        cover.accessibilityIdentifier = "event-cover"
        group.addArrangedSubview(frame); group.addArrangedSubview(cover)
    }
    private func addField(_ label: String, key: WritableKeyPath<EventDraft, String>, limit: Int, placeholder: String, testID: String, to group: UIStackView) {
        let container = UIStackView(); container.axis = .vertical; container.spacing = 6
        container.addArrangedSubview(caption(label))
        let field = EventEditorField()
        field.font = AxooTypography.postBody; field.textColor = AxooTheme.textPrimary
        field.backgroundColor = AxooTheme.surface; field.tintColor = AxooTheme.accent
        field.text = model.draft[keyPath: key]; field.placeholder = placeholder
        field.autocapitalizationType = .sentences
        field.accessibilityIdentifier = testID; field.accessibilityLabel = label
        field.addAction(UIAction { [weak self, weak field] _ in
            guard let self, let field else { return }
            let value = String((field.text ?? "").prefix(limit))
            field.text = value; model.change { $0[keyPath: key] = value }
        }, for: .editingChanged)
        field.heightAnchor.constraint(greaterThanOrEqualToConstant: 52).isActive = true
        fields.append(field); container.addArrangedSubview(field); group.addArrangedSubview(container)
    }
    private func addFooter() {
        footer.backgroundColor = AxooTheme.cardBackground
        footer.accessibilityIdentifier = "event-editor-footer"
        let separator = UIView(); separator.backgroundColor = AxooTheme.cardBorder
        footer.addSubview(separator); separator.translatesAutoresizingMaskIntoConstraints = false
        let content = UIStackView(arrangedSubviews: [status, save]); content.axis = .vertical; content.spacing = 8
        footer.addSubview(content); content.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(footer); footer.translatesAutoresizingMaskIntoConstraints = false
        let preferredWidth = content.widthAnchor.constraint(equalTo: footer.widthAnchor, constant: -40)
        preferredWidth.priority = .defaultHigh
        NSLayoutConstraint.activate([
            footer.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            footer.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            footer.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor),
            separator.topAnchor.constraint(equalTo: footer.topAnchor), separator.leadingAnchor.constraint(equalTo: footer.leadingAnchor),
            separator.trailingAnchor.constraint(equalTo: footer.trailingAnchor), separator.heightAnchor.constraint(equalToConstant: 0.7),
            content.topAnchor.constraint(equalTo: footer.topAnchor, constant: 12),
            content.bottomAnchor.constraint(equalTo: footer.bottomAnchor, constant: -12),
            content.centerXAnchor.constraint(equalTo: footer.centerXAnchor),
            content.widthAnchor.constraint(lessThanOrEqualToConstant: 520),
            content.widthAnchor.constraint(lessThanOrEqualTo: footer.widthAnchor, constant: -40), preferredWidth
        ])
        save.accessibilityIdentifier = "event-save"; status.accessibilityIdentifier = "event-error"
    }
    func textViewDidChange(_ textView: UITextView) {
        let value = String(textView.text.prefix(10000))
        if textView.text != value { textView.text = value }
        model.change { $0.description = value }
    }
    private func datesChanged() { model.change { $0.startsAt = start.date; $0.endsAt = end.date } }
    private func render() {
        let busy = model.busy
        fields.forEach { $0.isEnabled = !busy }; descriptionView.isEditable = !busy
        start.isEnabled = !busy; end.isEnabled = !busy; cover.isEnabled = !busy; back.isEnabled = !busy
        save.isEnabled = !busy; save.configuration?.showsActivityIndicator = model.saving
        save.configuration?.title = model.event == nil ? "Pubblica" : "Salva modifiche"
        cover.configuration?.showsActivityIndicator = model.preparingPhoto
        let hasCover = model.draft.photo != nil || !model.draft.coverUrl.isEmpty
        cover.configuration?.title = hasCover ? "Cambia immagine" : "Scegli locandina"
        coverEmpty.isHidden = hasCover; coverImage.isHidden = !hasCover
        let nextCoverID = model.draft.photo?.id ?? model.draft.coverUrl
        if nextCoverID != displayedCoverID {
            displayedCoverID = nextCoverID; coverImage.clear()
            if let data = model.draft.photo?.photoData { coverImage.display(UIImage(data: data)) }
            else { coverImage.load(model.draft.coverUrl, maximumPixelSize: 1200) }
        }
        descriptionPlaceholder.isHidden = !model.draft.description.isEmpty
        status.text = model.error; status.isHidden = model.error == nil
        isModalInPresentation = busy || model.hasUnsavedChanges
    }
    @objc private func close() {
        guard !model.busy else { return }
        if !model.hasUnsavedChanges { dismiss(animated: true); return }
        let alert = UIAlertController(title: "Uscire dalla modifica?", message: "Le modifiche non salvate andranno perse.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Resta", style: .cancel))
        alert.addAction(UIAlertAction(title: "Esci", style: .destructive) { [weak self] _ in self?.dismiss(animated: true) })
        present(alert, animated: true)
    }
    @objc private func chooseCover() {
        guard !model.busy else { return }
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images; configuration.selectionLimit = 1
        let picker = PHPickerViewController(configuration: configuration); picker.delegate = self
        present(picker, animated: true)
    }

}

extension EventEditorHost: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider else { return }
        model.preparePhoto {
            try await withCheckedThrowingContinuation { continuation in
                provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, error in
                    if let data { continuation.resume(returning: data) }
                    else { continuation.resume(throwing: error ?? PhotoFailure.unreadable) }
                }
            }
        }
    }
}

private final class EventEditorSection: UIStackView {
    init(heading: String?) {
        super.init(frame: .zero)
        axis = .vertical; spacing = 16
        backgroundColor = AxooTheme.cardBackground; layer.cornerRadius = 20
        layer.borderWidth = AxooTheme.controlBorderWidth; clipsToBounds = true
        isLayoutMarginsRelativeArrangement = true
        directionalLayoutMargins = NSDirectionalEdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16)
        if let heading { addArrangedSubview(UILabel.axoo(heading, font: AxooTypography.editorTitle)) }
    }
    @available(*, unavailable)
    required init(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection); setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }
}

private final class EventEditorField: UITextField {
    override init(frame: CGRect) {
        super.init(frame: frame)
        layer.cornerRadius = 16; layer.borderWidth = AxooTheme.controlBorderWidth
    }
    convenience init() { self.init(frame: .zero) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func textRect(forBounds bounds: CGRect) -> CGRect { bounds.insetBy(dx: 14, dy: 0) }
    override func editingRect(forBounds bounds: CGRect) -> CGRect { textRect(forBounds: bounds) }
    override func placeholderRect(forBounds bounds: CGRect) -> CGRect { textRect(forBounds: bounds) }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection); setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }
}
