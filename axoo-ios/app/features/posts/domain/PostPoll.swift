import Foundation

struct PollOptionInput: Equatable, Encodable {
    var text = ""
    var entities: [EmojiEntity] = []
    var characterCount: Int { text.unicodeScalars.count }
    var isValid: Bool { !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && characterCount <= 100 }
    var normalized: Self {
        let document = EmojiDocument(text: text, entities: entities).normalized
        return Self(text: document.text, entities: document.entities)
    }
    private enum CodingKeys: String, CodingKey { case text, entities }
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(text, forKey: .text)
        try container.encode(entities.map(\.payload), forKey: .entities)
    }
}

struct PostPoll: Decodable, Equatable {
    let options: [PostPollOption]
    let totalVotes: Int
    var myOptionId: String?
    var isSending = false
    private enum CodingKeys: String, CodingKey { case options, totalVotes, myOptionId }
    var hasVoted: Bool { myOptionId?.isEmpty == false && !isSending }
    func selecting(_ optionID: String) -> Self {
        var next = self
        next.myOptionId = optionID
        next.isSending = true
        return next
    }
    /// Largest remainders keep the displayed total at 100%, including ties.
    var percentages: [Int] {
        let counts = options.map { max(0, $0.votes) }
        let total = counts.reduce(0, +)
        guard hasVoted, total > 0 else { return options.map { _ in 0 } }
        var result = counts.map { $0 * 100 / total }
        let ranked = counts.indices.sorted {
            let left = counts[$0] * 100 % total, right = counts[$1] * 100 % total
            return left == right ? $0 < $1 : left > right
        }
        for index in ranked.prefix(100 - result.reduce(0, +)) { result[index] += 1 }
        return result
    }
}

struct PostPollOption: Decodable, Equatable {
    let id: String
    let text: String
    let entities: [JSONValue]?
    let votes: Int
    let voters: [PollVoter]
    var document: EmojiDocument { EmojiDocument(text: text, entities: EmojiEntity.decode(entities)) }
}

struct PollVoter: Decodable, Equatable {
    let userId: String
    let avatarConfig: AvatarConfiguration?
}

@MainActor
protocol PollVotingSource {
    func votePoll(postID: String, optionID: String) async throws -> PostPoll
}

extension PollVotingSource {
    func votePoll(postID: String, optionID: String) async throws -> PostPoll { throw HTTPFailure(status: 501) }
}
