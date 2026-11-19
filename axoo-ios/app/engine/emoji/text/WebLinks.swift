import UIKit

@MainActor
enum WebLinks {
    private static let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)

    static func apply(to text: NSMutableAttributedString) {
        let range = NSRange(location: 0, length: text.length)
        detector?.enumerateMatches(in: text.string, range: range) { match, _, _ in
            guard let match, let url = match.url,
                  url.scheme == "http" || url.scheme == "https" else { return }
            text.addAttribute(.link, value: url, range: match.range)
        }
    }
}
