import Foundation

enum AupStickers {
    private static let baseURL = "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/stickers/aup/v1/"
    static let all: [PostEditorMedia] = (1...17).map { number in
        let url = "\(baseURL)\(number).webp"
        return PostEditorMedia(kind: .sticker, url: url, previewURL: url, width: 512, height: 512,
            mimeType: "image/webp", provider: "aup", providerID: "aup-\(number)", photoData: nil, id: "aup-\(number)",
            previewFallbackURL: number == 12 ? "\(baseURL)12.gif" : nil)
    }
}
