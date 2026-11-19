import Foundation

extension PostRecord {
    var mediaDimensions: (width: Int, height: Int) {
        guard case .object(let fields)? = media,
              case .number(let width)? = fields["width"],
              case .number(let height)? = fields["height"],
              width.isFinite, height.isFinite,
              width > 0, height > 0, width <= 100_000, height <= 100_000 else { return (0, 0) }
        return (Int(width), Int(height))
    }
    var mediaURL: String? {
        guard case .object(let fields)? = media, case .string(let url)? = fields["url"] else { return nil }
        return url
    }
    func asPost() -> Post {
        let date = APIJSON.date(createdAt)
        let time = date.map { TimeAgo.format($0) } ?? ""
        return Post(id: id, nickname: nickname ?? "", campus: universityName ?? "", timeLabel: time,
                               isEdited: updatedAt.flatMap(APIJSON.date).map { $0 > (date ?? .distantFuture) } ?? false,
                               category: flair ?? "", body: body ?? "", entities: EmojiEntity.decode(entities),
                               mediaURL: mediaURL,
                               mediaPreviewURL: PostEditorMedia.fromPayload(media).flatMap { $0.kind == .gif ? $0.previewURL : nil },
                               mediaWidth: mediaDimensions.width, mediaHeight: mediaDimensions.height,
                               personalSticker: PostEditorMedia.fromPayload(media).flatMap { $0.kind == .sticker && $0.provider == "axoo" ? $0 : nil },
                               score: (upvotes ?? 0) - (downvotes ?? 0),
                               commentsCount: commentsCount ?? 0,
                               vote: myVote == "upvote" ? .up : myVote == "downvote" ? .down : .none,
                               userID: userId, createdAt: createdAt, canEdit: poll == nil && EmojiDocument.canEdit(text: body ?? "", raw: entities),
                               avatarConfig: avatarConfig, poll: poll)
    }
}
