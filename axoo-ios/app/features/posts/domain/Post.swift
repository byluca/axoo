import Foundation

struct Post: Identifiable, Equatable {
    enum Vote: Int, CaseIterable {
        case down = -1, none = 0, up = 1
    }

    let id: String
    let nickname: String
    let campus: String
    let timeLabel: String
    var isEdited = false
    var category = ""
    var body: String
    var entities: [EmojiEntity] = []
    var mediaURL: String? = nil
    var mediaPreviewURL: String? = nil
    var mediaWidth = 0
    var mediaHeight = 0
    var personalSticker: PostEditorMedia? = nil
    var score = 0
    var commentsCount = 0
    var vote: Vote = .none
    var isVoting = false
    var userID = ""
    var createdAt: String?
    var canEdit = true
    var avatarConfig: AvatarConfiguration?
    var poll: PostPoll? = nil

    /// A local transition for the preview; networking and rollback belong to
    /// the screen's state owner, never the card's rendering code.
    mutating func toggleVote(_ requested: Vote) {
        guard requested != .none, !isVoting else { return }
        let next: Vote = vote == requested ? .none : requested
        score += next.rawValue - vote.rawValue
        vote = next
    }
}
