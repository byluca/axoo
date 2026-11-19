import Foundation

enum CommentDelivery: Equatable { case sending, sent }

struct CommentItem: Equatable {
    var comment: Comment
    var delivery: CommentDelivery = .sent
    var isBusy = false
    var isVoting = false
    var stableID: String { comment.stableID }
    var canInteract: Bool { delivery == .sent && !comment.isDeleted && !isBusy }
    var canReply: Bool { delivery == .sent && !comment.isDeleted && (!isBusy || isVoting) }
}

struct CommentRow: Equatable {
    let item: CommentItem
    let depth: Int
    let rootID: String
    var isFirst: Bool
    var isLast: Bool
    var linesAbove: [Int] = []
    var linesBelow: [Int] = []
    var stableID: String { item.stableID }
}

/// The same iterative depth-first traversal as Android. Visual indentation is capped, depth is not.
enum CommentThread {
    static let maximumIndent = 4

    static func flatten(_ items: [CommentItem]) -> [CommentRow] {
        var byID: [String: CommentItem] = [:]
        var unique: [CommentItem] = []
        for item in items where byID[item.comment.id] == nil {
            byID[item.comment.id] = item
            unique.append(item)
        }
        var children: [String: [CommentItem]] = [:]
        for item in unique {
            if let parent = item.comment.parentID { children[parent, default: []].append(item) }
        }
        for key in Array(children.keys) {
            children[key]?.sort {
                $0.comment.createdAt == $1.comment.createdAt ? $0.stableID < $1.stableID : $0.comment.createdAt < $1.comment.createdAt
            }
        }
        let roots = unique.filter { item in item.comment.parentID.flatMap { byID[$0] } == nil }
        var visited = Set<String>()
        var result: [CommentRow] = []
        for root in roots + unique where !visited.contains(root.comment.id) {
            let start = result.count
            var stack: [(CommentItem, Int)] = [(root, 0)]
            while let (item, depth) = stack.popLast() {
                guard visited.insert(item.comment.id).inserted else { continue }
                result.append(CommentRow(item: item, depth: depth, rootID: root.stableID, isFirst: result.count == start, isLast: false))
                for child in (children[item.comment.id] ?? []).reversed() { stack.append((child, depth + 1)) }
            }
            result[result.count - 1].isLast = true
            var active = Set<Int>()
            for index in stride(from: result.count - 1, through: start, by: -1) {
                let depth = result[index].depth
                result[index].linesBelow = active.sorted()
                active = Set(active.filter { $0 <= depth })
                if (1...maximumIndent).contains(depth) { active.insert(depth) }
                result[index].linesAbove = active.sorted()
            }
        }
        return result
    }

    /// Keep a deleted ancestor only while it connects a visible descendant. Strip personal content.
    static func removing(_ ids: Set<String>, from items: [CommentItem]) -> [CommentItem] {
        let updated = items.map { item -> CommentItem in
            var item = item
            if ids.contains(item.comment.id) { item.comment = item.comment.deleted(); item.isBusy = false }
            return item
        }
        var byID: [String: CommentItem] = [:]
        updated.forEach { byID[$0.comment.id] = $0 }
        var retained = Set<String>()
        for item in updated where !item.comment.isDeleted {
            var current: CommentItem? = item
            while let value = current, retained.insert(value.comment.id).inserted {
                current = value.comment.parentID.flatMap { byID[$0] }
            }
        }
        return updated.filter { retained.contains($0.comment.id) }
    }
}
