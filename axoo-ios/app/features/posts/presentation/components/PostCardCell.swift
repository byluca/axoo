import UIKit

/// Reused by the feed and the fixture gallery; explicit sizing avoids nested layout trees.
final class PostCardCell: UITableViewCell {
    private(set) var card: PostCardView?
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(post: Post, onAction: @escaping (PostCardView.Action) -> Void) {
        if let card = card {
            card.onAction = onAction
            card.configure(post)
        } else {
            let card = PostCardView(post: post, onAction: onAction)
            self.card = card
            contentView.addSubview(card)
        }
        setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        card?.frame = contentView.bounds.inset(by: UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12))
    }
    override func prepareForReuse() {
        super.prepareForReuse()
        card?.updateMediaPlayback(isActive: false)
        card?.onAction = { _ in }
    }
}
