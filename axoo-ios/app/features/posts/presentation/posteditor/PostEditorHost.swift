import UIKit
import PhotosUI
import UniformTypeIdentifiers

/// Renders the editor and handles UIKit input, media pickers and navigation.
final class PostEditorHost: AxooViewController, UITextViewDelegate, PHPickerViewControllerDelegate {
    let viewModel: PostEditorViewModel
    private var state: PostEditorState { viewModel.state }
    private var mode: PostEditorMode { viewModel.mode }
    private let onSaved: (PostEditorResult) -> Void
    private let textView = EmojiEditTextView()
    private let emojiToggle = EmojiKeyboardToggle(iconSize: 24, hitSize: 40)
    private let placeholder = UILabel.axoo("Che succede qui intorno?", font: AxooTypography.editorPlaceholder, color: AxooTheme.textSecondary)
    private let counter = UILabel.axoo(font: AxooTypography.editorCounter, color: AxooTheme.textSecondary)
    private let errorLabel = UILabel.axoo(font: AxooTypography.statusBody, color: .systemRed)
    private let footer = UIStackView()
    private let scroll = UIScrollView()
    private let card = UIStackView()
    private let cardSurface = PostEditorSurface(frame: .zero)
    private let bodyContainer = UIView()
    private let toolbar = PostEditorToolbar(frame: .zero)
    private let pollEditor = PostPollEditorView()
    private weak var activeInput: EmojiTextView?
    private let mediaPreview = UIView()
    private let mediaImage = PostMediaImageView()
    private lazy var removeMedia: UIButton = {
        let btn = UIButton(type: .system)
        var config = UIButton.Configuration.plain()
        config.image = AxooIcon.close.image(pointSize: 14)
        config.baseForegroundColor = .white
        config.background.backgroundColor = UIColor(white: 0, alpha: 0.6)
        config.background.cornerRadius = 18
        btn.configuration = config
        btn.addAction(UIAction { [weak self] _ in self?.viewModel.setMedia(nil) }, for: .touchUpInside)
        return btn
    }()
    private var mediaImageTask: Task<Void, Never>?
    private var displayedMediaID: String?
    private var mediaWidth: NSLayoutConstraint?
    private var mediaHeight: NSLayoutConstraint?
    
    private let topBar = PostEditorTopBar()
    private lazy var header = PostEditorHeader(author: viewModel.author)
    private var bodyHeight: NSLayoutConstraint?
    private lazy var category: UIButton = {
        let button = UIButton(type: .custom)
        button.configuration = .plain()
        button.addAction(UIAction { [weak self] _ in self?.chooseCategory() }, for: .touchUpInside)
        return button
    }()
    var onClose: (() -> Void)?
    private var delivered = false
    init(viewModel: PostEditorViewModel, onSaved: @escaping (PostEditorResult) -> Void) {
        self.viewModel = viewModel
        self.onSaved = onSaved
        super.init()
        viewModel.onChange = { [weak self] in self?.render() }
        viewModel.onPublish = { Haptics.vibrate() }
    }
    convenience init(service: PostEditorService, mode: PostEditorMode, initial: PostEditorDraft = .init(),
                     onSaved: @escaping (PostEditorResult) -> Void) {
        self.init(viewModel: PostEditorViewModel(service: service, mode: mode, initial: initial), onSaved: onSaved)
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        title = nil
        view.accessibilityIdentifier = "post-editor"
        topBar.back.addAction(UIAction { [weak self] _ in self?.back() }, for: .touchUpInside)
        topBar.publish.addAction(UIAction { [weak self] _ in self?.publish() }, for: .touchUpInside)
        textView.delegate = self
        textView.backgroundColor = .clear
        textView.font = AxooTypography.editorBody
        textView.lineHeightMultiple = 1.12
        textView.textColor = AxooTheme.textPrimary
        textView.tintColor = AxooTheme.accent
        textView.textContainerInset = UIEdgeInsets(top: PostEditorStyle.textVerticalInset, left: 0, bottom: PostEditorStyle.textVerticalInset, right: 0)
        textView.textContainer.lineFragmentPadding = 0
        textView.accessibilityIdentifier = "editor-body"
        textView.isScrollEnabled = false
        scroll.keyboardDismissMode = .interactive
        scroll.showsVerticalScrollIndicator = false
        scroll.alwaysBounceVertical = true
        placeholder.isUserInteractionEnabled = false
        placeholder.accessibilityIdentifier = "editor-placeholder"
        placeholder.numberOfLines = 1
        placeholder.lineBreakMode = .byTruncatingTail
        category.titleLabel?.numberOfLines = 0
        category.accessibilityIdentifier = "editor-category"
        errorLabel.accessibilityIdentifier = "editor-error"
        counter.accessibilityIdentifier = "editor-counter"
        counter.textAlignment = .right
        textView.connectPicker(to: emojiToggle)
        textView.onMediaRequested = { [weak self] sticker in self?.openRemotePicker(sticker ? .sticker : .gif) }
        toolbar.onAction = { [weak self] action in
            guard let self else { return }
            switch action {
            case .photo: openPhotos()
            case .poll: viewModel.togglePoll()
            case .gif: openRemotePicker(.gif)
            case .sticker: openRemotePicker(.sticker)
            case .meme: openRemotePicker(.meme)
            }
        }
        pollEditor.onChange = { [weak self] index, text, entities in
            self?.viewModel.setPollOption(index, text: text, entities: entities)
        }
        pollEditor.onAdd = { [weak self] in self?.viewModel.addPollOption() }
        pollEditor.onRemove = { [weak self] in self?.viewModel.removePollOption($0) }
        pollEditor.onActivate = { [weak self] in self?.activateInput($0) }
        pollEditor.onUnbind = { [weak self] input in
            guard let self, activeInput === input else { return }
            activateInput(textView)
        }
        activateInput(textView)
        mediaImage.contentMode = .scaleAspectFill
        mediaPreview.layer.cornerRadius = PostEditorStyle.mediaRadius
        mediaPreview.clipsToBounds = true
        mediaImage.backgroundColor = AxooTheme.background
        mediaImage.layer.cornerRadius = PostEditorStyle.mediaRadius
        mediaImage.clipsToBounds = true
        removeMedia.accessibilityIdentifier = "editor-remove-media"
        [mediaImage, removeMedia].forEach { mediaPreview.addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        mediaWidth = mediaImage.widthAnchor.constraint(equalToConstant: 220)
        mediaHeight = mediaImage.heightAnchor.constraint(equalToConstant: 220)
        // The stack collapses the preview to zero height when no media is selected.
        mediaHeight?.priority = UILayoutPriority(999)
        mediaWidth?.isActive = true
        mediaHeight?.isActive = true
        NSLayoutConstraint.activate([
            mediaImage.centerXAnchor.constraint(equalTo: mediaPreview.centerXAnchor),
            mediaImage.topAnchor.constraint(equalTo: mediaPreview.topAnchor),
            mediaImage.bottomAnchor.constraint(equalTo: mediaPreview.bottomAnchor),
            removeMedia.trailingAnchor.constraint(equalTo: mediaImage.trailingAnchor, constant: -8),
            removeMedia.topAnchor.constraint(equalTo: mediaImage.topAnchor, constant: 8),
            removeMedia.widthAnchor.constraint(equalToConstant: 36),
            removeMedia.heightAnchor.constraint(equalToConstant: 36)
        ])
        mediaPreview.isHidden = true
        footer.axis = .vertical
        footer.spacing = 8
        [errorLabel, counter].forEach { footer.addArrangedSubview($0) }
        card.axis = .vertical
        card.spacing = PostEditorStyle.sectionSpacing
        card.isLayoutMarginsRelativeArrangement = true
        card.directionalLayoutMargins = NSDirectionalEdgeInsets(top: PostEditorStyle.contentInset, leading: PostEditorStyle.contentInset, bottom: PostEditorStyle.contentInset, trailing: PostEditorStyle.contentInset)
        let categoryRow = UIStackView(arrangedSubviews: [category, UIView()])
        category.setContentHuggingPriority(.required, for: .horizontal)
        [header, categoryRow, bodyContainer, footer, pollEditor, mediaPreview].forEach { card.addArrangedSubview($0) }
        card.setCustomSpacing(PostEditorStyle.categorySpacing, after: header)
        card.setCustomSpacing(PostEditorStyle.categorySpacing, after: bodyContainer)
        card.setCustomSpacing(8, after: footer)
        [textView, placeholder, emojiToggle].forEach { bodyContainer.addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        [topBar, scroll, toolbar].forEach { view.addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        scroll.addSubview(cardSurface)
        cardSurface.translatesAutoresizingMaskIntoConstraints = false
        cardSurface.addSubview(card)
        card.pinEdges(to: cardSurface.layoutMarginsGuide)
        cardSurface.layoutMargins = .zero
        cardSurface.insetsLayoutMarginsFromSafeArea = false
        cardSurface.preservesSuperviewLayoutMargins = false
        let safe = view.safeAreaLayoutGuide
        bodyHeight = textView.heightAnchor.constraint(equalToConstant: PostEditorStyle.minimumTextHeight)
        bodyHeight?.isActive = true
        NSLayoutConstraint.activate([
            topBar.topAnchor.constraint(equalTo: safe.topAnchor), topBar.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            topBar.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: topBar.bottomAnchor), scroll.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: safe.trailingAnchor), scroll.bottomAnchor.constraint(equalTo: toolbar.topAnchor, constant: -8),
            toolbar.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: PostEditorStyle.outerInset),
            toolbar.heightAnchor.constraint(equalToConstant: PostEditorStyle.toolbarHeight),
            toolbar.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -PostEditorStyle.outerInset),
            toolbar.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -4),
            cardSurface.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: PostEditorStyle.topInset),
            cardSurface.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: PostEditorStyle.outerInset),
            cardSurface.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -PostEditorStyle.outerInset),
            cardSurface.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -PostEditorStyle.contentInset),
            cardSurface.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -2 * PostEditorStyle.outerInset),
            emojiToggle.topAnchor.constraint(equalTo: bodyContainer.topAnchor),
            emojiToggle.leadingAnchor.constraint(equalTo: bodyContainer.leadingAnchor, constant: -8),
            emojiToggle.widthAnchor.constraint(equalToConstant: 40), emojiToggle.heightAnchor.constraint(equalToConstant: 40),
            textView.topAnchor.constraint(equalTo: bodyContainer.topAnchor), textView.leadingAnchor.constraint(equalTo: emojiToggle.trailingAnchor, constant: 8),
            textView.trailingAnchor.constraint(equalTo: bodyContainer.trailingAnchor), textView.bottomAnchor.constraint(equalTo: bodyContainer.bottomAnchor),
            placeholder.topAnchor.constraint(equalTo: bodyContainer.topAnchor, constant: PostEditorStyle.textVerticalInset),
            placeholder.leadingAnchor.constraint(equalTo: textView.leadingAnchor), placeholder.trailingAnchor.constraint(equalTo: textView.trailingAnchor),
            category.heightAnchor.constraint(greaterThanOrEqualToConstant: 26)
        ])
        view.backgroundColor = AxooTheme.surface
        scroll.backgroundColor = AxooTheme.surface
        render()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        navigationController?.setNavigationBarHidden(true, animated: animated)
        updateBackGesture()
        pollEditor.setPlaybackEnabled(true)
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if mode == .create && state.draft.body.isEmpty { textView.becomeFirstResponder() }
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        navigationController?.interactivePopGestureRecognizer?.isEnabled = true
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        pollEditor.setPlaybackEnabled(false)
        if isMovingFromParent || navigationController?.isBeingDismissed == true { viewModel.stop() }
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateBodyHeight()
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard isViewLoaded else { return }
        toolbar.layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
        textView.font = AxooTypography.editorBody
        placeholder.font = AxooTypography.editorPlaceholder
        counter.font = AxooTypography.editorCounter
        errorLabel.font = AxooTypography.statusBody
        render()
    }
    private func activateInput(_ input: EmojiTextView) {
        activeInput = input
        input.connectPicker(to: emojiToggle)
        emojiToggle.pickerOpen = input.inputView != nil
    }
    func textViewDidBeginEditing(_ textView: UITextView) { activateInput(self.textView) }
    func textViewDidChange(_ textView: UITextView) {
        guard !self.textView.isApplyingDocument else { return }
        viewModel.setBody(self.textView.document.text, entities: self.textView.document.entities)
        updateBodyHeight()
        view.layoutIfNeeded()
        if let selection = textView.selectedTextRange {
            let caret = textView.caretRect(for: selection.end).insetBy(dx: 0, dy: -12)
            scroll.scrollRectToVisible(textView.convert(caret, to: scroll), animated: false)
        }
    }
    private func updateBodyHeight() {
        guard view.bounds.width > 0 else { return }
        let textWidth = textView.bounds.width > 0 ? textView.bounds.width : max(1, view.bounds.width - 96)
        let minimumHeight = state.draft.pollOptions == nil ? PostEditorStyle.minimumTextHeight : PostEditorStyle.pollMinimumTextHeight
        let height = max(minimumHeight, ceil(textView.sizeThatFits(CGSize(width: textWidth, height: .greatestFiniteMagnitude)).height))
        if bodyHeight?.constant != height { bodyHeight?.constant = height }
        updateMediaSize()
    }
    private func updateBackGesture() {
        navigationController?.interactivePopGestureRecognizer?.isEnabled = !viewModel.hasUnsavedChanges && !viewModel.isBusy
    }
    private func render() {
        guard isViewLoaded else { return }
        let state = self.state
        textView.setDocument(EmojiDocument(text: state.draft.body, entities: state.draft.entities))
        textView.isEditable = !viewModel.isBusy
        emojiToggle.isEnabled = !viewModel.isBusy
        placeholder.isHidden = !state.draft.body.isEmpty
        category.isEnabled = !viewModel.isBusy
        toolbar.configure(hasPoll: state.draft.pollOptions != nil, media: state.draft.media?.kind,
                          enabled: !viewModel.isBusy, pollEnabled: mode == .create)
        pollEditor.configure(state.draft.pollOptions, enabled: !viewModel.isBusy)
        removeMedia.isEnabled = !viewModel.isBusy
        updateMediaPreview(state.draft.media)
        let appearance = PostCategoryLabel.appearance(for: state.draft.category)
        category.configuration?.title = state.draft.category.isEmpty ? "CATEGORIA" : state.draft.category.uppercased()
        category.configuration?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var attributes = incoming
            attributes.font = AxooTypography.editorCategory
            return attributes
        }
        category.configuration?.contentInsets = NSDirectionalEdgeInsets(top: 4, leading: 10, bottom: 4, trailing: 8)
        category.configuration?.background.cornerRadius = 14
        category.configuration?.image = appearance.icon?.image(pointSize: 14)
        category.configuration?.imagePadding = 4
        category.configuration?.baseForegroundColor = state.draft.category.isEmpty ? AxooTheme.textSecondary : UIColor(rgb: appearance.foreground)
        category.configuration?.background.backgroundColor = state.draft.category.isEmpty ? AxooTheme.surface : UIColor(rgb: appearance.background)
        category.configuration?.background.strokeColor = state.draft.category.isEmpty ? AxooTheme.border : .clear
        category.configuration?.background.strokeWidth = state.draft.category.isEmpty ? 0.7 : 0
        counter.text = "\(state.draft.characterCount)/500"
        counter.isHidden = state.draft.characterCount < 400
        counter.textColor = state.draft.characterCount > 500 ? AxooTheme.destructive : AxooTheme.textSecondary
        errorLabel.text = state.error
        errorLabel.isHidden = state.error == nil
        footer.isHidden = errorLabel.isHidden && counter.isHidden
        card.setCustomSpacing(footer.isHidden ? PostEditorStyle.sectionSpacing : PostEditorStyle.categorySpacing, after: bodyContainer)
        topBar.configure(canPublish: viewModel.canPublish, isSaving: state.isSaving, isEditing: mode != .create)
        updateBodyHeight()
        updateBackGesture()
        if let result = state.result, !delivered {
            delivered = true
            onSaved(result)
            closeEditor()
        }
    }
    private func updateMediaSize() {
        guard let media = state.draft.media else { return }
        let edge = min(PostEditorStyle.maximumMediaSize, max(1, cardSurface.bounds.width - 2 * PostEditorStyle.contentInset))
        let ratio = media.width > 0 && media.height > 0 ? CGFloat(media.width) / CGFloat(media.height) : 1
        let size = ratio >= 1 ? CGSize(width: edge, height: max(edge * 0.24, edge / ratio))
                              : CGSize(width: max(edge * 0.24, edge * ratio), height: edge)
        if mediaWidth?.constant != size.width { mediaWidth?.constant = size.width }
        if mediaHeight?.constant != size.height { mediaHeight?.constant = size.height }
    }
    private func updateMediaPreview(_ media: PostEditorMedia?) {
        mediaPreview.isHidden = media == nil
        guard let media else { displayedMediaID = nil; mediaImageTask?.cancel(); mediaImage.display(nil); return }
        updateMediaSize()
        guard displayedMediaID != media.id else { return }
        displayedMediaID = media.id
        mediaImageTask?.cancel()
        mediaImage.display(media.photoData.flatMap(UIImage.init(data:)))
        let previewURL = (media.kind == .gif || media.kind == .sticker) ? media.url : media.previewURL
        if media.photoData == nil, let url = URL(string: previewURL), url.scheme == "https" {
            mediaImageTask = Task { [weak self] in
                let image = await PostMediaImage.load(url)
                guard !Task.isCancelled,
                      self?.displayedMediaID == media.id else { return }
                self?.mediaImage.display(image)
            }
        }
    }
    private func openRemotePicker(_ kind: PostEditorMedia.Kind) {
        guard !viewModel.isBusy, presentedViewController == nil else { return }
        (activeInput ?? textView).resignFirstResponder()
        let picker = RemoteMediaPickerScreen(api: viewModel.mediaAPI, kind: kind)
        picker.onSelect = { [weak self, weak picker] media in
            self?.viewModel.setMedia(media)
            picker?.dismiss(animated: true) { [weak self] in (self?.activeInput ?? self?.textView)?.becomeFirstResponder() }
        }
        presentMediaPicker(picker)
    }
    private func presentMediaPicker(_ picker: UIViewController) {
        picker.modalPresentationStyle = .pageSheet
        if let sheet = picker.sheetPresentationController {
            sheet.preferredCornerRadius = AxooSheetStyle.cornerRadius
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
            sheet.prefersScrollingExpandsWhenScrolledToEdge = true
        }
        present(picker, animated: true)
    }
    private func openPhotos() {
        guard !viewModel.isBusy, presentedViewController == nil else { return }
        (activeInput ?? textView).resignFirstResponder()
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        presentMediaPicker(picker)
    }
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider else { return }
        viewModel.preparePhoto {
            try await withCheckedThrowingContinuation { continuation in
                provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, error in
                    if let data { continuation.resume(returning: data) }
                    else { continuation.resume(throwing: error ?? CocoaError(.fileReadCorruptFile)) }
                }
            }
        }
    }

    private func chooseCategory() {
        let sheet = AxooBottomSheet(title: AxooOverlayCopy.categoryTitle, message: AxooOverlayCopy.categoryMessage, showsCancel: false)
        for value in [""] + PostEditorDraft.categories {
            let selected = value == state.draft.category
            let button = sheet.addButton(value.isEmpty ? AxooOverlayCopy.uncategorized : value.uppercased()) { [weak self] in
                self?.viewModel.setCategory(value)
            }
            button.isSelected = selected
            let appearance = PostCategoryLabel.appearance(for: value)
            button.configuration?.image = appearance.icon?.image(pointSize: 22)
            button.configuration?.imagePadding = 10
            let foreground: UIColor
            if value.isEmpty {
                foreground = AxooTheme.textPrimary
            } else if AxooTheme.isDark && !selected {
                let mix: (UInt32) -> CGFloat = { shift in
                    let light = CGFloat((appearance.background >> shift) & 255)
                    let color = CGFloat((appearance.foreground >> shift) & 255)
                    return (color * 0.4 + light * 0.6) / 255
                }
                foreground = UIColor(red: mix(16), green: mix(8), blue: mix(0), alpha: 1)
            } else {
                foreground = UIColor(rgb: appearance.foreground)
            }
            button.configuration?.baseForegroundColor = foreground
            button.tintColor = foreground
            button.configuration?.imageColorTransformer = nil
            let selectedColor = value.isEmpty ? AxooTheme.surface : UIColor(rgb: appearance.background)
            // Override the shared button's accent, including its selected/highlighted state.
            button.configuration?.baseBackgroundColor = selectedColor
            button.configuration?.background.backgroundColor = selected ? selectedColor : .clear
            button.configuration?.background.cornerRadius = 18
            button.layer.cornerRadius = 18
            button.accessibilityIdentifier = "editor-category-\(value)"
        }
        (activeInput ?? textView).resignFirstResponder()
        present(sheet, animated: true)
    }
    @objc private func publish() {
        guard textView.markedTextRange == nil else { return }
        viewModel.setBody(self.textView.document.text, entities: self.textView.document.entities)
        viewModel.publishDraft()
    }
    private func closeEditor() {
        if let onClose { onClose() }
        else { navigationController?.popViewController(animated: true) }
    }

    @objc private func back() {
        guard !viewModel.isBusy else { return }
        guard viewModel.hasUnsavedChanges else { closeEditor(); return }
        (activeInput ?? textView).resignFirstResponder()
        let sheet = AxooBottomSheet(title: AxooOverlayCopy.discardTitle, message: mode == .create ? AxooOverlayCopy.discardCreateMessage : AxooOverlayCopy.discardEditMessage, dismissTitle: AxooOverlayCopy.continueWriting)
        sheet.addButton(mode == .create ? AxooOverlayCopy.discardCreate : AxooOverlayCopy.discardEdit, destructive: true,
                        testID: "editor-discard") { [weak self] in self?.closeEditor() }
        present(sheet, animated: true)
    }

}
