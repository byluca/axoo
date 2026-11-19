import Foundation

/// Complete request identity; it owns no native resource.
struct AnimatedEmojiKey: Hashable {
    let id: Int
    let path: URL
    let sizePx: Int

    init(id: Int, path: URL, sizePx: Int) {
        precondition(sizePx > 0 && sizePx <= 1024)
        precondition(["tgs", "json"].contains(path.pathExtension.lowercased()))
        self.id = id
        self.path = path
        self.sizePx = sizePx
    }
    var playbackKey: String { "\(id):\(path.absoluteString)" }
}
