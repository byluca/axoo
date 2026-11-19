import UIKit

/// Exports the loaded card in light mode, keeping its layout and loaded media.
@MainActor
enum PostShare {
    static func present(card: PostCardView) {
        var responder: UIResponder? = card
        while let current = responder, !(current is UIViewController) { responder = current.next }
        guard let controller = responder as? UIViewController,
              controller.presentedViewController == nil, let root = controller.viewIfLoaded,
              card.window != nil else { return }
        guard card.isShareMediaReady else {
            controller.present(AxooConfirmationCard(title: nil,
                message: "Attendi il caricamento del post e riprova.", cancelTitle: nil,
                actionTitle: AxooOverlayCopy.ok), animated: true)
            return
        }
        guard let image = image(of: card) else { return }
        let sheet = UIActivityViewController(activityItems: [image], applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = root
        sheet.popoverPresentationController?.sourceRect = CGRect(x: root.bounds.midX, y: root.bounds.midY, width: 1, height: 1)
        sheet.popoverPresentationController?.permittedArrowDirections = []
        controller.present(sheet, animated: true)
    }

    static func image(of card: PostCardView) -> UIImage? {
        let size = card.bounds.size
        guard size.width > 0, size.height > 0, size.width.isFinite, size.height.isFinite else { return nil }
        let previousStyle = card.overrideUserInterfaceStyle
        card.overrideUserInterfaceStyle = .light
        defer {
            card.overrideUserInterfaceStyle = previousStyle
            refreshLayers(of: card)
        }
        refreshLayers(of: card)
        card.layoutIfNeeded()
        let unit = size.width / 366
        let padding = 24 * unit
        let footer = 84 * unit
        let width = size.width + padding * 2
        let height = max(width * 1.25, size.height + padding * 2 + footer)
        let format = UIGraphicsImageRendererFormat()
        format.scale = min(1080 / width, 4096 / height)
        format.opaque = true
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { renderer in
            UIColor(rgb: 0xFF00BF).setFill()
            renderer.fill(CGRect(x: 0, y: 0, width: width, height: height))
            let context = renderer.cgContext
            context.saveGState()
            context.translateBy(x: padding, y: (height - footer - size.height) / 2)
            context.clip(to: CGRect(origin: .zero, size: size))
            card.traitCollection.performAsCurrent {
                card.layer.render(in: context)
            }
            context.restoreGState()
            let attributes: [NSAttributedString.Key: Any] = [
                .font: AxooTypography.homeLogo.withSize(40 * unit),
                .foregroundColor: UIColor.white,
            ]
            let title = "Axoo" as NSString
            let titleSize = title.size(withAttributes: attributes)
            title.draw(at: CGPoint(x: (width - titleSize.width) / 2,
                y: height - footer / 2 - titleSize.height / 2), withAttributes: attributes)
        }
    }

    private static func refreshLayers(of view: UIView) {
        view.traitCollection.performAsCurrent {
            view.setNeedsDisplay()
            view.layer.displayIfNeeded()
            view.subviews.forEach { refreshLayers(of: $0) }
        }
    }

}
