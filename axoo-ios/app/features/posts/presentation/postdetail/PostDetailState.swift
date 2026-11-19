import Foundation

struct CommentDraft: Equatable {
    var text = ""
    var entities: [EmojiEntity] = []
    var replyID: String?
    var selection = NSRange(location: 0, length: 0)
}

struct PostDetailState: Equatable {
    var post: Post
    var items: [CommentItem] = []
    var isLoading = true
    var isRefreshing = false
    var loadError: String?
    var notice: String?
    var draft = CommentDraft()
    var editingID: String?
    var isSaving = false
    var isPerformingAction = false
    var isPostDeleted = false
    var followCommentID: String?
    var rows: [CommentRow] { CommentThread.flatten(items) }
    var canSend: Bool { CommentInput.isValid(draft.text) && !isSaving && !isPostDeleted && !isPerformingAction }
}
