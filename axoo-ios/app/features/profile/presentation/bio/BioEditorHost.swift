import UIKit

final class BioEditorHost: AxooFormController, UITextViewDelegate {
    private let viewModel: ProfileViewModel
    private let input = EmojiEditTextView()
    private let toggle = EmojiKeyboardToggle(iconSize: 28, hitSize: 40)
    private let pill = UIView()
    private let placeholder = UILabel.axoo("Qualcosa su di te…", font: AxooTypography.postBody, color: AxooTheme.textSecondary)
    private let status = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    private let save = UIButton(type: .system)

    init(viewModel: ProfileViewModel) {
        self.viewModel = viewModel
        super.init()
        title = "Modifica bio"
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.largeTitleDisplayMode = .never
        navigationItem.titleView = UILabel.axoo(title ?? "", font: AxooTypography.statusTitle)
        installArrowBackButton(testID: "bio-back")
        var configuration = UIButton.Configuration.filled()
        configuration.title = "Salva"
        configuration.baseBackgroundColor = AxooTheme.accent
        configuration.baseForegroundColor = .white
        configuration.background.cornerRadius = 16
        configuration.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attributes in
            var next = attributes; next.font = AxooTypography.editorPublish; return next
        }
        save.configuration = configuration
        save.accessibilityIdentifier = "bio-save"
        save.addAction(UIAction { [weak self] _ in self?.saveBio() }, for: .touchUpInside)
        let saveItem = UIBarButtonItem(customView: save)
        saveItem.hideSharedBackground()
        navigationItem.rightBarButtonItem = saveItem
        view.backgroundColor = AxooTheme.background
        scrollView.backgroundColor = AxooTheme.surface
        stack.spacing = 16
        let instructions = UILabel.axoo("La bio è facoltativa e visibile agli altri utenti. Racconta qualcosa di te, mantenendo il tuo anonimato.",
            font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
        pill.backgroundColor = AxooTheme.cardBackground
        pill.layer.cornerRadius = AxooTheme.controlCornerRadius
        pill.layer.borderWidth = AxooTheme.controlBorderWidth
        pill.accessibilityIdentifier = "bio-pill"
        input.font = AxooTypography.postBody
        input.textColor = AxooTheme.textPrimary
        input.tintColor = AxooTheme.accent
        input.lineHeightMultiple = 1.12
        input.textContainerInset = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 8)
        input.textContainer.lineFragmentPadding = 0
        input.delegate = self
        input.accessibilityIdentifier = "bio-input"
        input.supportsMedia = false
        input.connectPicker(to: toggle)
        toggle.accessibilityLabel = "Apri emoji"
        placeholder.isUserInteractionEnabled = false
        status.textAlignment = .right
        status.accessibilityIdentifier = "bio-status"
        [toggle, input, placeholder].forEach { pill.addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        NSLayoutConstraint.activate([
            pill.heightAnchor.constraint(greaterThanOrEqualToConstant: 120),
            toggle.leadingAnchor.constraint(equalTo: pill.leadingAnchor, constant: 4),
            toggle.topAnchor.constraint(equalTo: pill.topAnchor, constant: 2),
            toggle.widthAnchor.constraint(equalToConstant: 40),
            toggle.heightAnchor.constraint(equalToConstant: 40),
            input.leadingAnchor.constraint(equalTo: toggle.trailingAnchor),
            input.topAnchor.constraint(equalTo: pill.topAnchor, constant: 2),
            input.trailingAnchor.constraint(equalTo: pill.trailingAnchor, constant: -8),
            input.bottomAnchor.constraint(equalTo: pill.bottomAnchor, constant: -2),
            placeholder.topAnchor.constraint(equalTo: input.topAnchor, constant: 10),
            placeholder.leadingAnchor.constraint(equalTo: input.leadingAnchor, constant: 12),
            placeholder.trailingAnchor.constraint(lessThanOrEqualTo: input.trailingAnchor, constant: -8),
        ])
        [instructions, pill, status].forEach { stack.addArrangedSubview($0) }
        stack.setCustomSpacing(8, after: pill)
        viewModel.beginBioEditing()
        input.setDocument(viewModel.state.bio)
        bindViewModel()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        bindViewModel()
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        input.playbackEnabled = true
        input.becomeFirstResponder()
        input.selectedRange = NSRange(location: input.textStorage.length, length: 0)
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        input.resignFirstResponder()
        input.playbackEnabled = false
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isMovingFromParent || navigationController?.isBeingDismissed == true { viewModel.cancelBioEditing() }
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        pill.layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }
    private func bindViewModel() {
        viewModel.onChange = { [weak self] in self?.render() }
        render()
    }
    private func render() {
        guard isViewLoaded else { return }
        let state = viewModel.state
        input.isEditable = !state.isSavingBio
        toggle.isEnabled = input.isEditable
        placeholder.isHidden = !state.bio.text.isEmpty
        save.isEnabled = viewModel.canSaveBio
        save.configuration?.showsActivityIndicator = state.isSavingBio
        status.text = state.bioError ?? "\(state.bio.text.unicodeScalars.count)/\(AccountProfile.bioLimit)"
        status.textColor = state.bioError == nil ? AxooTheme.textSecondary : .systemRed
    }
    func textViewDidChange(_ textView: UITextView) {
        viewModel.changeBio(input.document)
        input.invalidateIntrinsicContentSize()
    }
    private func saveBio() {
        input.unmarkText()
        viewModel.changeBio(input.document)
        viewModel.saveBio { [weak self] in self?.navigationController?.popViewController(animated: true) }
    }
}
