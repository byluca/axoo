import Foundation

enum RLottieError: Error { case invalidAsset, invalidFrame, imageCreation }

/// Owns the C++ handle. Native calls serialize under its mutex; in-flight work
/// retains this object, so deinit cannot destroy a handle that is being rendered.
final class RLottieAnimation: @unchecked Sendable {
    let frameCount: Int
    let framesPerSecond: Double
    let width: Int
    let height: Int
    private let handle: OpaquePointer

    init(data: Data) throws {
        guard let pointer = data.withUnsafeBytes({ bytes in
            AXAnimationCreate(bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count)
        }) else { throw RLottieError.invalidAsset }
        handle = pointer
        let info = AXAnimationGetInfo(pointer)
        frameCount = Int(info.frame_count)
        framesPerSecond = info.frames_per_second
        width = Int(info.width)
        height = Int(info.height)
    }

    deinit { AXAnimationDestroy(handle) }

    func renderPixels(frame: Int, edge: Int, buffer: UnsafeMutableRawPointer, count: Int) throws {
        guard (0..<frameCount).contains(frame), (1...1024).contains(edge) else { throw RLottieError.invalidFrame }
        let result = AXAnimationRender(handle, UInt32(frame), UInt32(edge), UInt32(edge),
                                       buffer.assumingMemoryBound(to: UInt8.self), count)
        guard result == 0 else { throw RLottieError.invalidFrame }
    }
}
