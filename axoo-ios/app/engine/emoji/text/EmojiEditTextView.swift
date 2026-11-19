import UIKit

/// Editable UIKit host used by post and comment composers.
@MainActor
final class EmojiEditTextView: EmojiTextView {
    init() { super.init(readOnly: false) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
}
