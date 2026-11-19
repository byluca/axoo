import UIKit

extension UIButton {
    /// Ricrea le opzioni a ogni apertura, usando permessi e dati aggiornati.
    func setOptionsSheet(_ configure: @escaping (AxooBottomSheet) -> Void) {
        removeAction(identifiedBy: UIAction.Identifier("axoo-options"), for: .touchUpInside)
        menu = nil
        showsMenuAsPrimaryAction = false
        addAction(UIAction(identifier: UIAction.Identifier("axoo-options")) { [weak self] _ in
            guard let self, self.window != nil else { return }
            var responder: UIResponder? = self
            while let current = responder, !(current is UIViewController) {
                responder = current.next
            }
            guard let controller = responder as? UIViewController,
                  controller.presentedViewController == nil else { return }
            let sheet = AxooBottomSheet(title: "Opzioni", showsHeading: false, showsCancel: false)
            configure(sheet)
            guard !sheet.stack.arrangedSubviews.isEmpty else { return }
            sheet.view.accessibilityIdentifier = "options-sheet"
            controller.view.endEditing(true)
            controller.present(sheet, animated: true)
        }, for: .touchUpInside)
    }
}
