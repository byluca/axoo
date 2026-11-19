import UIKit
import PhotosUI
import ImageIO
import UniformTypeIdentifiers

final class StickerCreatorScreen: AxooViewController, PHPickerViewControllerDelegate {
    var onCreated: ((PostEditorMedia) -> Void)?
    private let model: StickerCreatorViewModel
    private let crop = StickerCropView()
    private var pickedPhoto = false
    private var photoTask: Task<Void, Never>?
    private lazy var save = UIButton.axoo("Crea sticker", primary: true) { [weak self] in
        guard let self, let png = crop.croppedPNG() else { return }
        model.createSticker(png)
    }
    private let spinner = UIActivityIndicatorView(style: .medium)

    init(api: APIClient) { model = StickerCreatorViewModel(api: api); super.init(); title = "Crea sticker" }
    deinit { photoTask?.cancel() }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.leftBarButtonItem = UIBarButtonItem(image: AxooIcon.back.image(pointSize: 24), style: .plain,
            target: self, action: #selector(close))
        let hint = UILabel.axoo("Sposta e ingrandisci", font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
        hint.textAlignment = .center
        for child in [hint, crop, save, spinner] { child.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(child) }
        NSLayoutConstraint.activate([
            crop.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            hint.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            hint.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            hint.topAnchor.constraint(equalTo: crop.bottomAnchor, constant: 16),
            crop.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            crop.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            hint.bottomAnchor.constraint(equalTo: save.topAnchor, constant: -12),
            save.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            save.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            save.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),
            spinner.centerXAnchor.constraint(equalTo: save.centerXAnchor), spinner.centerYAnchor.constraint(equalTo: save.centerYAnchor)
        ])
        model.onChange = { [weak self] in self?.render() }
        model.onError = { [weak self] message in self?.showError(message) }
        model.onCreated = { [weak self] sticker in
            guard let self else { return }
            let completion = onCreated
            dismiss(animated: true) { completion?(sticker) }
        }
        render()
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !pickedPhoto else { return }
        pickedPhoto = true
        var configuration = PHPickerConfiguration()
        configuration.filter = .images
        configuration.selectionLimit = 1
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        present(picker, animated: true)
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || navigationController?.isBeingDismissed == true { photoTask?.cancel(); model.cancel() }
    }
    @objc private func close() { model.cancel(); dismiss(animated: true) }

    private func render() {
        let saving = model.isSaving
        save.isEnabled = crop.image != nil && !saving
        save.configuration?.title = saving ? "" : "Crea sticker"
        crop.isUserInteractionEnabled = !saving
        isModalInPresentation = saving
        navigationController?.isModalInPresentation = saving
        if saving { spinner.startAnimating() } else { spinner.stopAnimating() }
    }
    private func showError(_ message: String) {
        present(AxooConfirmationCard(title: "Crea sticker", message: message, cancelTitle: nil, actionTitle: "OK", action: {}), animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        guard let provider = results.first?.itemProvider else { picker.dismiss(animated: true) { [weak self] in self?.close() }; return }
        picker.dismiss(animated: true)
        photoTask = Task { [weak self] in
            do {
                let image: CGImage = try await withCheckedThrowingContinuation { continuation in
                    provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, _ in
                        guard let data, let source = CGImageSourceCreateWithData(data as CFData, nil),
                              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                                kCGImageSourceCreateThumbnailFromImageAlways: true,
                                kCGImageSourceCreateThumbnailWithTransform: true,
                                kCGImageSourceThumbnailMaxPixelSize: 2048,
                                kCGImageSourceShouldCacheImmediately: true
                              ] as CFDictionary) else { continuation.resume(throwing: PhotoFailure.unreadable); return }
                        continuation.resume(returning: image)
                    }
                }
                guard !Task.isCancelled, let self else { return }
                crop.image = UIImage(cgImage: image)
                render()
            } catch {
                guard !Task.isCancelled else { return }
                self?.showError("Impossibile aprire questa foto")
            }
        }
    }
}
