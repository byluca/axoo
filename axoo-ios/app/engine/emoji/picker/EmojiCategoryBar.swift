import UIKit

/// Static category icons with the shared sliding selection control.
final class EmojiCategoryBar: UIView {
    var onSelect: (Int) -> Void = { _ in }
    private let tabs = UIScrollView()
    private let control: SlidingCategoryControl
    private let buttons: [EmojiCategoryButton]
    private(set) var selectedSection: Int?

    init(sections: [EmojiAdapter.Section]) {
        buttons = sections.map { EmojiCategoryButton(section: $0) }
        control = SlidingCategoryControl(buttons: buttons, indicatorSize: CGSize(width: 36, height: 44))
        super.init(frame: .zero)
        control.apportionsSegmentWidthsByContent = true
        control.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.onSelect(self.control.selectedSegmentIndex)
        }, for: .valueChanged)
        tabs.showsHorizontalScrollIndicator = false
        tabs.showsVerticalScrollIndicator = false
        addSubview(tabs)
        tabs.pinEdges(to: self)
        tabs.addSubview(control)
        control.pinEdges(to: tabs.contentLayoutGuide)
        control.heightAnchor.constraint(equalTo: tabs.frameLayoutGuide.heightAnchor).isActive = true
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    func select(_ section: Int) {
        guard selectedSection != section, buttons.indices.contains(section) else { return }
        selectedSection = section
        control.selectedSegmentIndex = section
        control.layoutIfNeeded()
        tabs.scrollRectToVisible(buttons[section].convert(buttons[section].bounds, to: tabs), animated: false)
    }
}
