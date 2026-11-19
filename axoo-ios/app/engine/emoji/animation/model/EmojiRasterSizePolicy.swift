import Foundation

/// Android's point/dp buckets; layout continues to decide the displayed size.
enum EmojiRasterSizePolicy {
    static func pickerSizePx(scale: CGFloat) -> Int { pixels(48, scale: scale) }
    static func rasterSizePx(renderSizePoints: CGFloat, scale: CGFloat) -> Int {
        let size = min(1024, max(1, Int(ceil(renderSizePoints * scale))))
        for points: CGFloat in [26, 32, 48, 64] {
            let bucket = pixels(points, scale: scale)
            if size <= bucket { return bucket }
        }
        return min(1024, ((size + 7) / 8) * 8)
    }
    private static func pixels(_ points: CGFloat, scale: CGFloat) -> Int {
        min(1024, max(1, Int(points * scale)))
    }
}
