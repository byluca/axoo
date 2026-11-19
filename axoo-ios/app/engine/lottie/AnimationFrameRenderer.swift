import Foundation
import CoreGraphics

enum AnimationFrameRenderer {
    static func render(_ animation: RLottieAnimation, frame: Int, edge: Int) throws -> LottieFrame {
        guard (0..<animation.frameCount).contains(frame), (1...1024).contains(edge) else { throw RLottieError.invalidFrame }
        let count = edge * edge * 4
        let buffer = LottiePixelPool.shared.acquire(count: count)
        do {
            try animation.renderPixels(frame: frame, edge: edge, buffer: buffer, count: count)
        } catch {
            LottiePixelPool.shared.release(buffer, count: count)
            throw error
        }
        let data = Data(bytesNoCopy: buffer, count: count, deallocator: .custom { pointer, size in
            LottiePixelPool.shared.release(pointer, count: size)
        })
        guard let provider = CGDataProvider(data: data as CFData),
              let image = CGImage(width: edge, height: edge, bitsPerComponent: 8, bitsPerPixel: 32,
                                  bytesPerRow: edge * 4, space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGBitmapInfo.byteOrder32Big.union(CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)),
                                  provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else { throw RLottieError.imageCreation }
        return LottieFrame(image: image, index: frame)
    }
}
