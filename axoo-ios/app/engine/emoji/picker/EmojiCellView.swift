import UIKit

final class EmojiCellView: UICollectionViewCell {
    private(set) var bindingID = UUID()
    let emoji = AnimatedLottieView(frame: .zero)
    override init(frame: CGRect) {
        super.init(frame: frame)
        emoji.pickerRaster = true
        emoji.showsUnicodeFallback = false
        emoji.playbackEnabled = false
        contentView.addSubview(emoji)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(_ asset: Axooji) {
        bindingID = UUID()
        emoji.playbackEnabled = false
        emoji.configure(asset)
        accessibilityIdentifier = "axooji-item-\(asset.id)"
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let cellSize = min(contentView.bounds.width, contentView.bounds.height)
        let requested: CGFloat = 48
        let edge = min(requested, max(1, floor(cellSize * 0.92)))
        emoji.frame = CGRect(x: contentView.bounds.midX - edge / 2, y: contentView.bounds.midY - edge / 2,
                             width: edge, height: edge)
    }
    override func prepareForReuse() { super.prepareForReuse(); bindingID = UUID(); emoji.playbackEnabled = false; emoji.configure(nil) }
}
