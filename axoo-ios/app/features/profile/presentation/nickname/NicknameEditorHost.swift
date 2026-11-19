import UIKit

final class NicknameEditorHost: AxooFormController, UITextFieldDelegate {
    private let viewModel: ProfileViewModel
    private let field = UITextField()
    private let card = UIView()
    private let status = UILabel.axoo(font: AxooTypography.statusBody)
    private let save = UIButton(type: .system)

    init(viewModel: ProfileViewModel) {
        self.viewModel = viewModel
        super.init()
        title = "Modifica nickname"
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        card.layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.largeTitleDisplayMode = .never
        let heading = UILabel.axoo(title ?? "", font: AxooTypography.statusTitle)
        heading.sizeToFit()
        navigationItem.titleView = heading
        installArrowBackButton(testID: "nickname-back")
        var configuration = UIButton.Configuration.filled()
        configuration.title = AxooOverlayCopy.save
        configuration.baseBackgroundColor = AxooTheme.accent
        configuration.baseForegroundColor = .white
        configuration.background.cornerRadius = 16
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 14, bottom: 8, trailing: 14)
        configuration.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attributes in
            var next = attributes
            next.font = AxooTypography.editorPublish
            next.foregroundColor = .white
            return next
        }
        save.configuration = configuration
        save.accessibilityIdentifier = "nickname-save"
        save.addTarget(self, action: #selector(saveNickname), for: .touchUpInside)
        let saveItem = UIBarButtonItem(customView: save)
        saveItem.hideSharedBackground()
        navigationItem.rightBarButtonItem = saveItem
        save.isEnabled = false
        viewModel.beginNicknameEditing()
        field.text = viewModel.state.nickname
        field.placeholder = "Nickname"
        field.font = AxooTypography.postBody
        field.textColor = AxooTheme.textPrimary
        field.autocapitalizationType = .none
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.returnKeyType = .done
        field.delegate = self
        field.accessibilityIdentifier = "nickname-input"
        field.addTarget(self, action: #selector(checkNickname), for: .editingChanged)
        card.backgroundColor = AxooTheme.panel
        card.layer.cornerRadius = 20
        card.layer.borderWidth = 0.7
        card.layer.borderColor = AxooTheme.cardBorder.cgColor
        card.addSubview(field)
        field.pinEdges(to: card, insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))
        field.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        let instructions = UILabel.axoo("Da 3 a 24 caratteri. Usa solo lettere minuscole e numeri, senza spazi.",
                                       font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
        status.accessibilityIdentifier = "nickname-status"
        [card, status, instructions].forEach { stack.addArrangedSubview($0) }
        view.backgroundColor = AxooTheme.background
        scrollView.backgroundColor = AxooTheme.surface
        bindViewModel()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        bindViewModel()
        navigationController?.setNavigationBarHidden(false, animated: animated)
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        field.becomeFirstResponder()
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        field.resignFirstResponder()
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isMovingFromParent || navigationController?.isBeingDismissed == true { viewModel.cancelNicknameEditing() }
    }
    private func bindViewModel() {
        viewModel.onChange = { [weak self] in self?.render() }
        render()
    }
    private func render() {
        guard isViewLoaded else { return }
        field.isEnabled = !viewModel.state.isSavingNickname
        save.isEnabled = viewModel.canSaveNickname
        save.alpha = save.isEnabled ? 1 : 0.5
        status.text = viewModel.state.nicknameMessage
        switch viewModel.state.nicknameStatus {
        case .neutral: status.textColor = AxooTheme.textSecondary
        case .available: status.textColor = AxooTheme.accent
        case .error: status.textColor = .systemRed
        }
    }
    @objc private func checkNickname() { viewModel.changeNickname(field.text ?? "") }
    @objc private func saveNickname() {
        viewModel.saveNickname { [weak self] in self?.navigationController?.popViewController(animated: true) }
    }
    func textFieldShouldReturn(_ textField: UITextField) -> Bool { saveNickname(); return false }
}
