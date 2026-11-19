import Foundation

/// Explicit ownership for one host/request pair. The view closes it on detach or rebinding.
@MainActor
final class AnimatedEmojiLease {
    let key: AnimatedEmojiKey
    let drawable: AnimatedEmojiDrawable
    weak var host: (any LottieHost)?
    private let release: (AnimatedEmojiLease) -> Void
    private(set) var isClosed = false
    init(key: AnimatedEmojiKey, drawable: AnimatedEmojiDrawable, host: any LottieHost,
         release: @escaping (AnimatedEmojiLease) -> Void) {
        self.key = key; self.drawable = drawable; self.host = host; self.release = release
    }
    func play() { setPlayback(.playing) }
    func pause() { setPlayback(.paused) }
    func setPlayback(_ mode: AnimatedEmojiDrawable.Playback) {
        if !isClosed, let host { drawable.requestPlayback(host, mode: mode) }
    }
    func close() {
        guard !isClosed else { return }
        isClosed = true
        release(self)
    }
}
