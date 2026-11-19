import Foundation

/// Pixels re-enter the pool only after the final CGImage consumer releases them.
final class LottiePixelPool: @unchecked Sendable {
    static let shared = LottiePixelPool() //singleton
    static let maximumCachedBytes = 8 * 1024 * 1024 //singleton
    private let lock = NSLock()
    private var buffers: [Int: [UnsafeMutableRawPointer]] = [:]
    private var bytes = 0
    private let limit = LottiePixelPool.maximumCachedBytes

    func acquire(count: Int) -> UnsafeMutableRawPointer {
        lock.lock()
        if let buffer = buffers[count]?.popLast() {
            bytes -= count
            lock.unlock()
            return buffer
        }
        lock.unlock()
        return .allocate(byteCount: count, alignment: 64)
    }
    func release(_ pointer: UnsafeMutableRawPointer, count: Int) {
        lock.lock()
        if bytes + count <= limit {
            buffers[count, default: []].append(pointer)
            bytes += count
            lock.unlock()
        } else { lock.unlock(); pointer.deallocate() }
    }
    func clear() {
        lock.lock()
        let old = buffers
        buffers.removeAll(); bytes = 0
        lock.unlock()
        old.values.flatMap { $0 }.forEach { $0.deallocate() }
    }
}
