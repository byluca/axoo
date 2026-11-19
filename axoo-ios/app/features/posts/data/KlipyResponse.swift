import Foundation

struct KlipyResponse: Decodable {
    struct Page: Decodable {
        let data: [Item]
        let currentPage: Int
        let hasNext: Bool
    }
    struct Item: Decodable {
        let slug: String
        let title: String
        let type: String
        let file: [String: [String: File]]
    }
    struct File: Decodable {
        let url: String
        let width: Int
        let height: Int
        var valid: Bool {
            guard let parsed = URL(string: url) else { return false }
            return KlipyMedia.isMediaURL(parsed) && (1...8192).contains(width) && (1...8192).contains(height)
        }
    }
    let result: Bool
    let data: Page

    func mediaPage(kind: PostEditorMedia.Kind, query: String) throws -> RemoteMediaPage {
        guard result, data.currentPage >= 1 else { throw HTTPFailure(status: 502) }
        let items = try data.data.map { item in
            // Keep the complete provider order; unsupported responses fail as a whole.
            guard item.type == (kind == .meme ? "static_meme" : kind.rawValue), !item.slug.isEmpty else { throw HTTPFailure(status: 502) }
            func rendition(_ sizes: [String], _ formats: [String]) -> File? {
                for size in sizes {
                    for format in formats {
                        if let file = item.file[size]?[format], file.valid { return file }
                    }
                }
                return nil
            }
            let sizes = kind == .gif ? ["sm", "xs", "md", "hd"] : ["hd", "md", "sm"]
            guard let original = rendition(sizes, kind == .meme ? ["png", "jpg", "webp"] : ["gif"]) else { throw HTTPFailure(status: 502) }
            let preview = rendition(["sm", "xs", "md", "hd"], kind == .meme ? ["png", "jpg", "webp"] : ["gif"]) ?? original
            let ext = URL(string: original.url)?.pathExtension.lowercased() ?? ""
            let mimeType = kind == .meme ? (["png": "image/png", "webp": "image/webp"][ext] ?? "image/jpeg") : "image/gif"
            return PostEditorMedia(kind: kind, url: original.url, previewURL: preview.url,
                width: original.width, height: original.height, mimeType: mimeType, provider: "klipy",
                providerID: item.slug, photoData: nil, id: item.slug,
                loadingPreviewURL: rendition(["xs", "sm"], ["jpg", "png"])?.url, searchQuery: query)
        }
        return RemoteMediaPage(items: items, nextOffset: data.hasNext ? data.currentPage + 1 : nil)
    }
}
