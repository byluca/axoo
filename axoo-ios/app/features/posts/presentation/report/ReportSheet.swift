import UIKit

final class AxooReportSheet: AxooBottomSheet {
    private let viewModel: ReportViewModel
    private var rows: [AxooReportReason: UIButton] = [:]
    private let errorLabel = UILabel.axoo(font: AxooTypography.statusBody, color: .systemRed)
    private lazy var send = UIButton.axoo(AxooOverlayCopy.reportSubmit, primary: true) { [weak self] in self?.sendReport() }
    init(submit: @escaping (AxooReportReason) async throws -> Void) {
        self.viewModel = ReportViewModel(submit: submit)
        super.init(title: AxooOverlayCopy.reportTitle, message: AxooOverlayCopy.reportMessage, showsCancel: false)
        viewModel.onChange = { [weak self] in self?.render() }
        for reason in AxooReportReason.allCases {
            let row = UIButton.axoo(reason.title) { [weak self] in self?.viewModel.select(reason) }
            row.contentHorizontalAlignment = .leading
            row.configuration?.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16)
            row.configuration?.baseForegroundColor = AxooTheme.textPrimary
            row.configuration?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
                var attributes = incoming
                attributes.font = AxooTypography.sheetOption
                return attributes
            }
            row.accessibilityIdentifier = "report-reason-\(reason.rawValue)"
            rows[reason] = row
            appendContent(row)
        }
        errorLabel.accessibilityIdentifier = "report-error"
        appendContent(errorLabel)
        send.configuration?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var attributes = incoming
            attributes.font = AxooTypography.primaryButton
            return attributes
        }
        send.accessibilityIdentifier = "report-submit"
        appendContent(send)
        render()
    }
    private func render() {
        for (reason, row) in rows {
            row.isSelected = viewModel.selected == reason
            row.isEnabled = !viewModel.isSubmitting
            // Match the neutral selection in the post category sheet, including
            // UIKit's selected/highlighted configuration states.
            row.configuration?.baseBackgroundColor = AxooTheme.surface
            row.configuration?.background.backgroundColor = viewModel.selected == reason ? AxooTheme.surface : .clear
            row.configuration?.background.cornerRadius = 18
            row.layer.cornerRadius = 18
        }
        send.isEnabled = viewModel.selected != nil && !viewModel.isSubmitting
        send.configuration?.showsActivityIndicator = viewModel.isSubmitting
        allowsDismissal = !viewModel.isSubmitting
        errorLabel.text = viewModel.error
        errorLabel.isHidden = errorLabel.text?.isEmpty != false
        preferredContentSizeDidChange()
        if viewModel.isComplete, !isBeingDismissed { Task { [weak self] in await self?.close() } }
    }
    private func sendReport() {
        guard !isBeingDismissed else { return }
        if viewModel.send() { Haptics.vibrate() }
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed { viewModel.stop() }
    }
}
