import Foundation

struct ModerationItem: Decodable, Equatable {
    enum TargetType: String, Decodable { case post, comment, user }
    let targetType: TargetType
    let targetId: String
    let authorNickname: String
    let body: String
    let reportCount: Int
    let reasons: [String]
    let isHidden: Bool
    let isDeleted: Bool
    var id: String { "\(targetType.rawValue):\(targetId)" }
}

enum ModerationDecision: String { case hide, keep }
