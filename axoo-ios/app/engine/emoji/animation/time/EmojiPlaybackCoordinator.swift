import UIKit

/// Reconciles visibility after UI changes, never on an otherwise idle animation tick.
@MainActor
final class EmojiPlaybackCoordinator: NSObject {
    static let shared = EmojiPlaybackCoordinator()
    private final class Host {
        weak var view: AnimatedLottieView?
        init(_ view: AnimatedLottieView) { self.view = view }
    }
    private var hosts: [UUID: Host] = [:]
    private let updateID = UUID()
    private var reconciling = false
    private var applicationActive = UIApplication.shared.applicationState == .active
    private(set) var activeHosts = Set<UUID>()
    private(set) var reconciliationCount = 0

    private override init() {
        super.init()
        NotificationCenter.default.addObserver(self, selector: #selector(becameActive),
                                               name: UIApplication.didBecomeActiveNotification, object: nil)
        for name in [UIApplication.willResignActiveNotification, UIApplication.didEnterBackgroundNotification] {
            NotificationCenter.default.addObserver(self, selector: #selector(resignedActive), name: name, object: nil)
        }
    }
    // App suspension is immediate; scrolling/layout updates are coalesced.
    @objc private func becameActive() { applicationActive = true; refreshVisibility() }
    @objc private func resignedActive() { applicationActive = false; refreshVisibility() }
    func attach(_ view: AnimatedLottieView) {
        hosts[view.leaseID] = Host(view)
        setNeedsVisibilityUpdate()
    }
    func detach(_ view: AnimatedLottieView) {
        hosts.removeValue(forKey: view.leaseID)
        activeHosts.remove(view.leaseID)
        view.updatePlayback(visible: false, key: nil, allowed: false)
        setNeedsVisibilityUpdate()
    }
    func setNeedsVisibilityUpdate() {
        LottieFrameClock.shared.scheduleUpdate(id: updateID) { [weak self] in self?.refreshVisibility() }
    }
    func refreshVisibility() {
        guard !reconciling else { return }
        LottieFrameClock.shared.cancelUpdate(id: updateID)
        reconciling = true
        defer { reconciling = false }
        reconciliationCount += 1
        AnimatedEmojiRegistry.shared.collectAbandonedLeases()
        hosts = hosts.filter { $0.value.view != nil }
        // Calculate geometry and request identity once per surface, outside the comparator.
        let visible = hosts.values.compactMap { host -> (view: AnimatedLottieView, key: AnimatedEmojiKey, rect: CGRect)? in
            guard let view = host.view else { return nil }
            guard self.applicationActive, view.axooIsVisible, let key = view.renderKey else {
                view.updatePlayback(visible: false, key: nil, allowed: false)
                return nil
            }
            return (view, key, view.convert(view.bounds, to: view.window))
        }.sorted { left, right in
            if left.rect.minY != right.rect.minY { return left.rect.minY < right.rect.minY }
            if left.rect.minX != right.rect.minX { return left.rect.minX < right.rect.minX }
            return left.view.leaseID.uuidString < right.view.leaseID.uuidString
        }
        var textKeys: [UUID: Set<AnimatedEmojiKey>] = [:]
        activeHosts.removeAll(keepingCapacity: true)
        for item in visible {
            let view = item.view
            var allowed = view.playbackEnabled && view.animates
            if let group = view.textPlaybackGroup, allowed {
                let keys = textKeys[group, default: []]
                allowed = keys.contains(item.key) || keys.count < PlaybackPolicy.maximumTextAnimations
                if allowed { textKeys[group, default: []].insert(item.key) }
            }
            if allowed { activeHosts.insert(view.leaseID) }
            view.updatePlayback(visible: true, key: item.key, allowed: allowed)
        }
    }
}
