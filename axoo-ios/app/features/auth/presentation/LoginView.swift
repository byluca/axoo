import UIKit

final class PillTextField: UITextField {
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.borderColor = AxooTheme.border.resolvedColor(with: traitCollection).cgColor
    }

    override func textRect(forBounds bounds: CGRect) -> CGRect { bounds.insetBy(dx: 16, dy: 14) }
    override func editingRect(forBounds bounds: CGRect) -> CGRect { bounds.insetBy(dx: 16, dy: 14) }
    override func placeholderRect(forBounds bounds: CGRect) -> CGRect { bounds.insetBy(dx: 16, dy: 14) }
}

final class LoginView: AxooFormController, UITextFieldDelegate, UITextViewDelegate {
    private let viewModel: LoginViewModel
    private var countdown: Task<Void, Never>?
    private let illustration = AnimationView(name: "locked")
    private let explanation = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    private let field = PillTextField()
    private let errorLabel = UILabel.axoo(font: AxooTypography.statusBody, color: .systemRed)
    private var legalView: UIView?
    private lazy var termsCheck = UIButton(type: .system)
    private let activity = UIActivityIndicatorView(style: .medium)
    private lazy var back = ArrowBackButton { [weak self] in self?.goBack() }
    private lazy var submit = UIButton.axoo("", primary: true) { [weak self] in self?.submitInput() }
    private lazy var resend = UIButton.axoo("Invia un nuovo codice") { [weak self] in
        self?.view.endEditing(true)
        self?.viewModel.sendCode()
    }
    private var showingCode: Bool?

    init(viewModel: LoginViewModel) { self.viewModel = viewModel; super.init() }
    deinit { countdown?.cancel() }

    override func viewDidLoad() {
        super.viewDidLoad()
        scrollView.delegate = self
        back.accessibilityIdentifier = "auth-back"
        let backRow = UIView()
        backRow.addSubview(back)
        NSLayoutConstraint.activate([
            backRow.heightAnchor.constraint(equalToConstant: 48),
            back.leadingAnchor.constraint(equalTo: backRow.leadingAnchor),
            back.centerYAnchor.constraint(equalTo: backRow.centerYAnchor)
        ])
        illustration.contentMode = .scaleAspectFit
        let illustrationRow = UIView()
        illustrationRow.addSubview(illustration)
        illustration.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            illustrationRow.heightAnchor.constraint(equalToConstant: 80),
            illustration.centerXAnchor.constraint(equalTo: illustrationRow.centerXAnchor),
            illustration.topAnchor.constraint(equalTo: illustrationRow.topAnchor),
            illustration.widthAnchor.constraint(equalToConstant: 80),
            illustration.heightAnchor.constraint(equalToConstant: 80)
        ])
        explanation.numberOfLines = 0
        explanation.textAlignment = .center
        field.borderStyle = .none
        field.backgroundColor = AxooTheme.surface
        field.layer.cornerRadius = 16
        field.layer.borderWidth = 1
        field.layer.borderColor = AxooTheme.border.cgColor
        field.clipsToBounds = true
        field.font = AxooTypography.postBody
        field.textColor = AxooTheme.textPrimary
        field.autocapitalizationType = .none
        field.autocorrectionType = .no
        field.returnKeyType = .go
        field.delegate = self
        field.heightAnchor.constraint(equalToConstant: 54).isActive = true
        field.addTarget(self, action: #selector(editingChanged), for: .editingChanged)
        errorLabel.accessibilityIdentifier = "auth-error"
        errorLabel.textAlignment = .center
        for btn in [submit, resend] {
            let buttonFont = btn === submit ? AxooTypography.authButton : AxooTypography.authResend
            btn.configurationUpdateHandler = { b in
                var config = b.configuration
                config?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
                    var attributes = incoming
                    attributes.font = buttonFont
                    return attributes
                }
                b.configuration = config
            }
        }
        submit.configuration?.cornerStyle = .fixed
        submit.configuration?.background.cornerRadius = 16
        submit.configuration?.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16)
        // The shared button has a 54pt minimum; this form matches Android's 52pt button.
        submit.constraints.filter { $0.firstAttribute == .height }.forEach { $0.isActive = false }
        let submitHeight = submit.heightAnchor.constraint(equalToConstant: 52)
        submitHeight.priority = UILayoutPriority(999)
        submitHeight.isActive = true
        resend.configuration?.contentInsets = NSDirectionalEdgeInsets(top: 16, leading: 0, bottom: 16, trailing: 0)
        resend.accessibilityIdentifier = "resend-code"
        let legalText = UITextView()
        legalText.backgroundColor = .clear
        legalText.isEditable = false
        legalText.isScrollEnabled = false
        legalText.delegate = self
        legalText.textAlignment = .left
        legalText.textContainerInset = .zero
        legalText.textContainer.lineFragmentPadding = 0
        legalText.linkTextAttributes = [
            .foregroundColor: AxooTheme.accent,
            .underlineStyle: NSUnderlineStyle.single.rawValue
        ]

        let baseLegal = "Ho almeno 18 anni e accetto i Termini d'uso. Ho letto l'Informativa sulla privacy."
        let attrLegal = NSMutableAttributedString(string: baseLegal, attributes: [
            .font: AxooTypography.replyBody,
            .foregroundColor: AxooTheme.textSecondary
        ])

        let termsRange = (baseLegal as NSString).range(of: "Termini d'uso")
        let privacyRange = (baseLegal as NSString).range(of: "Informativa sulla privacy")

        attrLegal.addAttribute(.link, value: "https://axoocampus.it/terms.html", range: termsRange)
        attrLegal.addAttribute(.link, value: "https://axoocampus.it/privacy.html", range: privacyRange)
        legalText.attributedText = attrLegal

        termsCheck.tintColor = AxooTheme.accent
        termsCheck.setPreferredSymbolConfiguration(UIImage.SymbolConfiguration(pointSize: 24), forImageIn: .normal)
        termsCheck.accessibilityIdentifier = "accept-terms"
        termsCheck.widthAnchor.constraint(equalToConstant: 44).isActive = true
        let termsHeight = termsCheck.heightAnchor.constraint(equalToConstant: 44)
        termsHeight.priority = UILayoutPriority(999)
        termsHeight.isActive = true
        termsCheck.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.viewModel.setTermsAccepted(!self.viewModel.termsAccepted)
        }, for: .touchUpInside)
        let legalRow = UIStackView(arrangedSubviews: [termsCheck, legalText])
        legalRow.alignment = .center
        legalRow.spacing = 8

        stack.spacing = 12
        [backRow, illustrationRow, explanation, field, legalRow, errorLabel, activity, resend, submit].forEach { stack.addArrangedSubview($0) }
        stack.setCustomSpacing(4, after: backRow)
        stack.setCustomSpacing(12, after: illustrationRow)
        stack.setCustomSpacing(16, after: explanation)
        stack.setCustomSpacing(8, after: errorLabel)
        stack.setCustomSpacing(8, after: activity)
        activity.hidesWhenStopped = true
        self.legalView = legalRow
        render()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        countdown?.cancel()
        countdown = Task { [weak self] in
            while !Task.isCancelled {
                self?.renderButtons()
                do { try await Task.sleep(nanoseconds: 1_000_000_000) } catch { return }
            }
        }
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        countdown?.cancel()
        countdown = nil
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        focusInputIfAvailable()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) { illustration.updateAnimationVisibility() }

    private func focusInputIfAvailable() {
        guard view.window != nil, viewModel.isBusy == false, !viewModel.requiresTermsOnly, !field.isFirstResponder else { return }
        field.becomeFirstResponder()
    }

    func render() {
        guard isViewLoaded else { return }
        let isCode = viewModel.screen == .code
        let password = isCode && viewModel.passwordRequired
        let termsOnly = viewModel.requiresTermsOnly && !isCode
        field.isHidden = termsOnly
        if showingCode != isCode {
            view.endEditing(true)
            showingCode = isCode
            field.keyboardType = password ? .default : (isCode ? .numberPad : .emailAddress)
            field.isSecureTextEntry = password
            field.textContentType = password ? .password : (isCode ? .oneTimeCode : .emailAddress)
            field.textAlignment = isCode && !password ? .center : .left
            field.attributedPlaceholder = NSAttributedString(
                string: password ? "Password" : (isCode ? "000000" : "nome@studenti.tuocampus.it"),
                attributes: [.font: AxooTypography.postBody, .foregroundColor: AxooTheme.textSecondary]
            )
            field.accessibilityIdentifier = password ? "password-field" : (isCode ? "code-field" : "email-field")
            illustration.configure(name: isCode ? "mail-icon" : "locked")
        }
        let text = isCode ? viewModel.code : viewModel.email
        if field.text != text { field.text = text }
        if password {
            explanation.font = AxooTypography.statusBody
            explanation.text = "Accesso account\n\(viewModel.email)\nInserisci la password."
        } else if isCode {
            explanation.font = AxooTypography.authCodeBody
            let emailTrimmed = viewModel.email.trimmingCharacters(in: .whitespaces)
            let isReviewAccount = emailTrimmed == LoginInput.reviewEmail
            let baseText = isReviewAccount
                ? "Account di revisione:\n\(emailTrimmed)\n\nInserisci il codice fornito in App Review Information."
                : "Il tuo pass d'accesso è stato inviato a:\n\(emailTrimmed)\n\nNon lo trovi? Controlla la cartella Posta Indesiderata"
            let attrString = NSMutableAttributedString(string: baseText, attributes: [
                .font: AxooTypography.authCodeBody,
                .foregroundColor: AxooTheme.textSecondary
            ])
            let emailRange = (baseText as NSString).range(of: emailTrimmed)
            let junkRange = (baseText as NSString).range(of: "Posta Indesiderata")
            let boldFont = AxooTypography.authCodeBold
            if emailRange.location != NSNotFound { attrString.addAttribute(.font, value: boldFont, range: emailRange) }
            if junkRange.location != NSNotFound { attrString.addAttribute(.font, value: boldFont, range: junkRange) }
            explanation.attributedText = attrString
        } else {
            explanation.font = AxooTypography.statusBody
            explanation.text = termsOnly ? "Accetta i Termini d’uso per continuare." : "L'accesso è riservato agli studenti.\nSblocca il pass con email del tuo campus."
        }
        errorLabel.text = viewModel.errorMessage
        errorLabel.isHidden = viewModel.errorMessage == nil
        field.isEnabled = !viewModel.isBusy
        back.isEnabled = !viewModel.isBusy
        let codeSpacing: CGFloat = viewModel.errorMessage != nil || viewModel.isBusy ? 12 : 8
        stack.setCustomSpacing(isCode ? codeSpacing : 10, after: field)
        stack.setCustomSpacing(isCode ? 8 : 12, after: errorLabel)
        submit.isHidden = isCode && !password
        activity.isHidden = !isCode || password || !viewModel.isBusy
        if isCode && !password && viewModel.isBusy { activity.startAnimating() } else { activity.stopAnimating() }
        legalView?.isHidden = isCode
        termsCheck.setImage(UIImage(systemName: viewModel.termsAccepted ? "checkmark.square.fill" : "square"), for: .normal)
        termsCheck.isEnabled = !viewModel.isBusy
        resend.isHidden = !isCode || password || viewModel.email == LoginInput.reviewEmail
        renderButtons()
        focusInputIfAvailable()
    }
    private func renderButtons() {
        let isCode = viewModel.screen == .code
        let remaining = max(0, Int(ceil(viewModel.resendAvailableAt.timeIntervalSinceNow)))
        let title = viewModel.requiresTermsOnly ? "Accetto e continuo" : (isCode ? "Accedi" : (remaining > 0 ? "Riprova tra \(remaining)s" : "Sblocca l'accesso"))
        submit.configuration?.title = viewModel.isBusy ? nil : title
        submit.configuration?.showsActivityIndicator = viewModel.isBusy
        submit.accessibilityIdentifier = isCode ? "verify-code" : "send-code"
        submit.isEnabled = viewModel.canSubmit
        resend.configuration?.title = remaining > 0 ? "Nuovo codice tra \(remaining)s" : "Invia un nuovo codice"
        resend.isEnabled = !viewModel.isBusy && remaining == 0
        submit.alpha = submit.isEnabled ? 1 : 0.55
        resend.alpha = resend.isEnabled ? 1 : 0.55
    }
    @objc private func editingChanged() {
        viewModel.setInput(field.text ?? "")
        if viewModel.screen == .code { field.text = viewModel.code }
        renderButtons()
    }
    @objc private func goBack() { view.endEditing(true); viewModel.back() }
    private func submitInput() {
        if viewModel.screen == .code { viewModel.verifyCode() } else { viewModel.sendCode() }
    }
    func textFieldShouldReturn(_ textField: UITextField) -> Bool { submitInput(); return true }

    func textView(_ textView: UITextView, shouldInteractWith URL: URL, in characterRange: NSRange, interaction: UITextItemInteraction) -> Bool {
        Task { await UIApplication.shared.open(URL) }
        return false
    }
}
