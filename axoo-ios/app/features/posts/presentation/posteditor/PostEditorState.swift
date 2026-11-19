import Foundation

struct PostEditorState: Equatable {
    var draft: PostEditorDraft
    var isSaving = false
    var isPreparingPhoto = false
    var error: String?
    var result: PostEditorResult?
}
