import Foundation

/// The same selection and sampling policy used by Android.
enum PlaybackPolicy {
    static let maximumComposerAnimatedEmoji = 20
    static let maximumTextAnimations = 50
    static let maximumPickerAnimations = 24
    static let maximumCachedEntries = 24
    static let maximumPoolBytes = LottiePixelPool.maximumCachedBytes
    static let startBatch = 3
    static let startIntervalNanoseconds: UInt64 = 48_000_000

    static func maximumFrameRate(sizePx: Int, scale: CGFloat) -> Double {
        sizePx <= Int(32 * scale) ? 45 : 60
    }
    struct Candidate<ID: Hashable, Value> {
        let id: ID
        let value: Value
        let ready: Bool
        let visibleArea: CGFloat
        let visibleCenterY: CGFloat
        let visiblePermille: Int
        let top: CGFloat
        let left: CGFloat
        let stableOrder: Int
    }
    struct Decision<ID: Hashable, Value> {
        let desiredIDs: Set<ID>
        let toStart: [Candidate<ID, Value>]
    }
    static func decide<ID, Value>(candidates: [Candidate<ID, Value>], activeIDs: Set<ID>,
                                  playbackAllowed: Bool, maxConcurrent: Int,
                                  viewportCenterY: CGFloat, minimumVisiblePermille: Int = 300) -> Decision<ID, Value> {
        guard playbackAllowed, maxConcurrent > 0 else { return Decision(desiredIDs: [], toStart: []) }
        let eligible = candidates.filter { $0.ready && $0.visibleArea > 0 }
        func priority(_ a: Candidate<ID, Value>, _ b: Candidate<ID, Value>) -> Bool {
            if a.visibleArea != b.visibleArea { return a.visibleArea > b.visibleArea }
            let ad = abs(a.visibleCenterY - viewportCenterY), bd = abs(b.visibleCenterY - viewportCenterY)
            if ad != bd { return ad < bd }
            return position(a, b)
        }
        func position(_ a: Candidate<ID, Value>, _ b: Candidate<ID, Value>) -> Bool {
            if a.top != b.top { return a.top < b.top }
            if a.left != b.left { return a.left < b.left }
            return a.stableOrder < b.stableOrder
        }
        let preferred = eligible.filter { $0.visiblePermille >= minimumVisiblePermille }.sorted(by: priority)
        let partial = eligible.filter { $0.visiblePermille < minimumVisiblePermille }.sorted(by: priority)
        let selected = Array((preferred + partial).prefix(maxConcurrent))
        return Decision(desiredIDs: Set(selected.map(\.id)),
                        toStart: selected.filter { !activeIDs.contains($0.id) }.sorted(by: position))
    }
}
