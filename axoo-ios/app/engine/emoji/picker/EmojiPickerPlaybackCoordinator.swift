import UIKit

/// Applies geometry policy and starts at most three ready cells every 48 ms.
@MainActor
final class EmojiPickerPlaybackCoordinator {
    private weak var collection: UICollectionView?
    private let adapter: EmojiAdapter
    private var activeIDs = Set<UUID>()
    private var cascade: Task<Void, Never>?
    private var cascadeVersion = UUID()
    private let refreshID = UUID()
    var playbackAllowed: () -> Bool = { false }
    init(collection: UICollectionView, adapter: EmojiAdapter) {
        self.collection = collection
        self.adapter = adapter
        adapter.onContentReady = { [weak self] in self?.markDirty() }
    }
    func markDirty() {
        LottieFrameClock.shared.scheduleUpdate(id: refreshID) { [weak self] in
            guard let self else { return }
            self.collection?.layoutIfNeeded()
            self.reconcile()
        }
    }
    func reconcile() {
        LottieFrameClock.shared.cancelUpdate(id: refreshID)
        guard let collection else { return }
        guard playbackAllowed() else { pauseAll(); return }
        let center = collection.bounds.midY
        let decision = PlaybackPolicy.decide(candidates: adapter.collectPlaybackCandidates(in: collection),
            activeIDs: activeIDs, playbackAllowed: true, maxConcurrent: PlaybackPolicy.maximumPickerAnimations,
            viewportCenterY: center)
        adapter.pauseExcept(decision.desiredIDs, in: collection)
        activeIDs.formIntersection(decision.desiredIDs)
        // An existing cascade owns the next start interval; scroll updates only pause losers.
        guard cascade == nil else { return }
        var started = 0
        for candidate in decision.toStart {
            if started == PlaybackPolicy.startBatch { break }
            if adapter.startCandidate(candidate) { activeIDs.insert(candidate.id); started += 1 }
        }
        if started > 0 && !decision.desiredIDs.isSubset(of: activeIDs) {
            let version = UUID()
            cascadeVersion = version
            cascade = Task { [weak self] in
                do { try await Task.sleep(nanoseconds: PlaybackPolicy.startIntervalNanoseconds) }
                catch { return }
                guard let self, self.cascadeVersion == version else { return }
                self.cascade = nil
                self.reconcile()
            }
        }
    }
    func pauseAll() {
        LottieFrameClock.shared.cancelUpdate(id: refreshID)
        cascadeVersion = UUID()
        cascade?.cancel(); cascade = nil
        activeIDs.removeAll()
        if let collection { adapter.pauseExcept([], in: collection) }
    }
    deinit { cascade?.cancel() }
}
