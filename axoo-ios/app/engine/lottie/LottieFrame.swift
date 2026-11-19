import CoreGraphics

/// CGImage is immutable; the provider owns the pixels for its entire lifetime.
struct LottieFrame: @unchecked Sendable {
    let image: CGImage
    let index: Int
}
