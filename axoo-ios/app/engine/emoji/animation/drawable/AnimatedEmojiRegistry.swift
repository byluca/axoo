import UIKit

/// Shares drawables and caches up to 24 entries without live leases.
/// Visibility, frame calculation, loading and rendering belong to other components.
@MainActor
final class AnimatedEmojiRegistry: NSObject {
    static let shared = AnimatedEmojiRegistry()
    private final class Entry {
        let drawable: AnimatedEmojiDrawable
        var leases: [ObjectIdentifier: AnimatedEmojiLease] = [:]
        var lastUsed: UInt64 = 0
        init(_ drawable: AnimatedEmojiDrawable) { self.drawable = drawable }
    }
    private var entries: [AnimatedEmojiKey: Entry] = [:]
    private var sequence: UInt64 = 0
    var entryCount: Int { entries.count }
    var loadedAnimationCount: Int { entries.values.filter { $0.drawable.isAnimationLoaded }.count }
    var retainedFrameBytes: Int { entries.values.reduce(0) { $0 + $1.drawable.frameBytes } }

    private override init() {
        super.init()
        NotificationCenter.default.addObserver(self, selector: #selector(memoryWarning),
            name: UIApplication.didReceiveMemoryWarningNotification, object: nil)
    }
    func acquire(key: AnimatedEmojiKey, host: any LottieHost) -> AnimatedEmojiLease {
        let entry = entries[key] ?? Entry(AnimatedEmojiDrawable(key: key))
        precondition(!entry.leases.values.contains { $0.host === host && !$0.isClosed }, "Host already owns this request")
        entries[key] = entry
        sequence += 1
        entry.lastUsed = sequence
        let lease = AnimatedEmojiLease(key: key, drawable: entry.drawable, host: host) { [weak self] in self?.release($0) }
        entry.leases[ObjectIdentifier(lease)] = lease
        entry.drawable.attachTo(host)
        trimCache()
        return lease
    }
    private func release(_ lease: AnimatedEmojiLease) {
        guard let entry = entries[lease.key], entry.drawable === lease.drawable else { return }
        entry.leases.removeValue(forKey: ObjectIdentifier(lease))
        if let host = lease.host { entry.drawable.detachFrom(host) }
        else { entry.drawable.refreshHostAttachment() }
        sequence += 1
        entry.lastUsed = sequence
        trimCache()
    }
    func collectAbandonedLeases() {
        let abandoned = entries.values.flatMap { $0.leases.values }.filter { $0.host == nil }
        for lease in abandoned { lease.close() }
    }
    private func trimCache() {
        let unused = entries.filter { $0.value.leases.isEmpty }.sorted { $0.value.lastUsed < $1.value.lastUsed }
        for (key, entry) in unused.prefix(max(0, unused.count - PlaybackPolicy.maximumCachedEntries)) {
            entries.removeValue(forKey: key)
            entry.drawable.dispose()
        }
    }
    func purgeUnused() {
        collectAbandonedLeases()
        for (key, entry) in Array(entries) where entry.leases.isEmpty {
            entries.removeValue(forKey: key)
            entry.drawable.dispose()
        }
    }
    @objc private func memoryWarning() {
        purgeUnused()
        LottiePixelPool.shared.clear()
    }
}
