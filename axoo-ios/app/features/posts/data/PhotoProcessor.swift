import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

/// Prepares bounded JPEG data independently of the picker and editor UI.
struct PhotoProcessor {
    static func prepare(_ data: Data) async throws -> PostEditorMedia {
        let task = Task.detached(priority: .userInitiated) { try encode(data) }
        return try await withTaskCancellationHandler(operation: { try await task.value }, onCancel: { task.cancel() })
    }

    private static func encode(_ data: Data) throws -> PostEditorMedia {
        try Task.checkCancellation()
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 1600,
                kCGImageSourceShouldCacheImmediately: true
              ] as CFDictionary),
              let context = CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8,
                bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
        else { throw PhotoFailure.unreadable }
        context.setFillColor(CGColor(gray: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: CGFloat(image.width), height: CGFloat(image.height)))
        context.draw(image, in: CGRect(x: 0, y: 0, width: CGFloat(image.width), height: CGFloat(image.height)))
        guard let flattened = context.makeImage() else { throw PhotoFailure.unreadable }
        for quality in [0.85, 0.7, 0.55] {
            try Task.checkCancellation()
            let output = NSMutableData()
            guard let destination = CGImageDestinationCreateWithData(output as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil)
            else { throw PhotoFailure.unreadable }
            CGImageDestinationAddImage(destination, flattened, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
            if CGImageDestinationFinalize(destination), output.length <= 3 * 1024 * 1024 {
                return PostEditorMedia(kind: .photo, url: "", previewURL: "", width: image.width, height: image.height,
                    mimeType: "image/jpeg", provider: nil, providerID: nil, photoData: output as Data, id: UUID().uuidString)
            }
        }
        throw PhotoFailure.tooLarge
    }
}

enum PhotoFailure: LocalizedError {
    case unreadable, tooLarge
    var errorDescription: String? {
        switch self {
        case .unreadable: return "Impossibile aprire la foto selezionata."
        case .tooLarge: return "Foto troppo grande: scegli un'immagine più piccola."
        }
    }
}
