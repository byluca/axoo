import UIKit

/// Same action for custom stickers in posts and comments; the existing model owns the request.
final class StickerSaveSheet: AxooBottomSheet {
    private let model: StickerCreatorViewModel
    private let sticker: PostEditorMedia
    private let errorLabel = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    private lazy var save = UIButton.axoo("Salva nei miei sticker") { [weak self] in
        guard let self else { return }
        errorLabel.isHidden = true
        model.saveSticker(sticker)
    }
    private let spinner = UIActivityIndicatorView(style: .medium)

    init(api: APIClient, sticker: PostEditorMedia) {
        model = StickerCreatorViewModel(api: api)
        self.sticker = sticker
        super.init(title: "Sticker", showsHeading: false, showsCancel: false)
        save.accessibilityIdentifier = "save-personal-sticker"
        save.contentHorizontalAlignment = .left
        save.configuration?.image = AxooIcon.sticker.image(pointSize: 24)
        save.configuration?.imagePadding = 16
        save.configuration?.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8)
        save.configuration?.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var attributes = incoming
            attributes.font = AxooTypography.sheetAction
            return attributes
        }
        appendContent(save)
        errorLabel.isHidden = true
        appendContent(errorLabel)
        spinner.hidesWhenStopped = true
        appendContent(spinner)
        model.onChange = { [weak self] in
            guard let self else { return }
            save.isEnabled = !model.isSaving
            allowsDismissal = !model.isSaving
            if model.isSaving { spinner.startAnimating() } else { spinner.stopAnimating() }
            preferredContentSizeDidChange()
        }
        model.onError = { [weak self] message in
            guard let self else { return }
            errorLabel.text = message
            errorLabel.isHidden = false
            preferredContentSizeDidChange()
        }
        model.onCreated = { [weak self] _ in
            guard let self else { return }
            save.configuration?.title = "Sticker salvato"
            save.isEnabled = false
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 700_000_000)
                await self?.close()
            }
        }
    }

    static func present(from controller: UIViewController, sticker: PostEditorMedia?, api: APIClient?) {
        guard let sticker, sticker.kind == .sticker, sticker.provider == "axoo", let api,
              controller.presentedViewController == nil else { return }
        controller.view.endEditing(true)
        controller.present(StickerSaveSheet(api: api, sticker: sticker), animated: true)
    }
}
