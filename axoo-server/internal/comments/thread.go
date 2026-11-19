package comments

import "axoo-server/internal/models"

func commentTombstone(comment models.Comment) models.Comment {
	return models.Comment{
		ID: comment.ID, PostID: comment.PostID, ParentCommentID: comment.ParentCommentID,
		IsDeleted: true, CreatedAt: comment.CreatedAt, UpdatedAt: comment.UpdatedAt,
		Entities: models.MessageEntitySlice{},
	}
}

// Keep unavailable ancestors only when needed to connect a visible descendant.
// Iterative traversal also handles deep threads without growing the call stack.
func retainThreadAncestors(comments []models.Comment) []models.Comment {
	byID := make(map[string]models.Comment, len(comments))
	keep := make(map[string]bool, len(comments))
	for _, comment := range comments {
		byID[comment.ID] = comment
	}
	for _, comment := range comments {
		if comment.IsDeleted {
			continue
		}
		for !keep[comment.ID] {
			keep[comment.ID] = true
			if comment.ParentCommentID == nil {
				break
			}
			parent, exists := byID[*comment.ParentCommentID]
			if !exists {
				break
			}
			comment = parent
		}
	}
	result := make([]models.Comment, 0, len(keep))
	for _, comment := range comments {
		if keep[comment.ID] {
			result = append(result, comment)
		}
	}
	return result
}
