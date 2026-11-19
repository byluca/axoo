import Foundation

@MainActor
final class PostRepository: HomeFeedSource, PostEditorService, MyPostsSource {
    let api: APIClient
    let profile: ProfileRepository
    private(set) var records: [String: PostRecord] = [:]
    private var uploadedPhotos: [String: JSONValue] = [:]
    private var pollSnapshots: [String: PostPoll] = [:]
    private var pendingPollVotes = Set<String>()
    var onPollChanged: (String, PostPoll) -> Void = { _, _ in }
    private(set) var blockedUsers = Set<String>()
    private(set) var hiddenPosts = Set<String>()
    var onHidden: (String) -> Void = { _ in }
    var onBlocked: (String) -> Void = { _ in }
    var mediaAPI: APIClient? { api }
    func mediaForEditing(_ postID: String) -> PostEditorMedia? {
        PostEditorMedia.fromPayload(records[postID]?.media)
    }
    var author: PostEditorAuthor? {
        guard let user = profile.currentUser else { return nil }
        return PostEditorAuthor(nickname: user.nickname, university: user.universityName ?? user.universityId,
                         avatar: AvatarIdentity(seed: user.id, configuration: user.avatarConfig))
    }
    init(profile: ProfileRepository) { self.api = profile.api; self.profile = profile }

    func loadBlocks() async throws {
        struct Response: Decodable { let users: [AccountProfile]? }
        let result: Response = try await decode("GET", path: "users/blocks")
        blockedUsers = Set((result.users ?? []).map(\.id))
    }
    func page(for tab: HomeFeedTab, cursor: String?) async throws -> HomeFeedPage {
        struct Response: Decodable { let posts: [PostRecord]?; let nextCursor: String? }
        var query = [URLQueryItem(name: "order", value: tab.rawValue), URLQueryItem(name: "limit", value: "20")]
        if let cursor = cursor { query.append(URLQueryItem(name: "cursor", value: cursor)) }
        let response: Response = try await decode("GET", path: "posts", query: query)
        let posts = (response.posts ?? []).filter { !blockedUsers.contains($0.userId) && !hiddenPosts.contains($0.id) }
        return HomeFeedPage(posts: posts.map { remember($0).asPost() }, nextCursor: response.nextCursor?.nilIfEmpty)
    }
    func myPostsPage(cursor: String?) async throws -> HomeFeedPage {
        struct Response: Decodable { let posts: [PostRecord]; let nextCursor: String? }
        var query = [URLQueryItem(name: "limit", value: "20")]
        if let cursor { query.append(URLQueryItem(name: "cursor", value: cursor)) }
        let response: Response = try await decode("GET", path: "posts/mine", query: query)
        return HomeFeedPage(posts: response.posts.map { remember($0).asPost() }, nextCursor: response.nextCursor?.nilIfEmpty)
    }
    func userPostsPage(userID: String, cursor: String?) async throws -> HomeFeedPage {
        struct Response: Decodable { let posts: [PostRecord]?; let nextCursor: String? }
        var query = [URLQueryItem(name: "limit", value: "20")]
        if let cursor { query.append(URLQueryItem(name: "cursor", value: cursor)) }
        let response: Response = try await decode("GET", path: "users/\(try APIIdentifier.path(userID))/posts", query: query)
        let visible = (response.posts ?? []).filter { !blockedUsers.contains($0.userId) && !hiddenPosts.contains($0.id) }
        return HomeFeedPage(posts: visible.map { remember($0).asPost() }, nextCursor: response.nextCursor?.nilIfEmpty)
    }
    func post(_ id: String) async throws -> PostRecord {
        struct Response: Decodable { let post: PostRecord }
        let response: Response = try await decode("GET", path: "posts/\(try APIIdentifier.path(id))")
        guard !blockedUsers.contains(response.post.userId) else { throw HTTPFailure(status: 404) }
        return remember(response.post)
    }
    func unreadCount() async throws -> Int? {
        struct Response: Decodable { let unreadCount: Int }
        let result: Response = try await decode("GET", path: "posts/new/unread-count")
        return max(0, result.unreadCount)
    }
    func markSeen(_ posts: [Post]) async throws {
        guard let latest = posts.compactMap(\.createdAt).max(by: {
            (APIJSON.date($0) ?? .distantPast) < (APIJSON.date($1) ?? .distantPast)
        }) else { return }
        _ = try await api.request("POST", path: "posts/new/seen", body: APIJSON.body(["seen_through": latest]))
    }
    func vote(_ post: Post, value: Post.Vote) async throws -> Post {
        let response: VoteResponse = try await decode("PUT", path: "posts/\(try APIIdentifier.path(post.id))/vote",
                                                     body: APIJSON.body(["value": value.rawValue]))
        var next = post
        next.score = response.vote.score
        next.vote = Post.Vote(rawValue: response.vote.value) ?? .none
        next.isVoting = false
        return next
    }
    func save(_ draft: PostEditorDraft, mode: PostEditorMode, clientID: String) async throws -> PostEditorResult {
        guard draft.isValid else { throw PostEditorValidationError.invalidBody }
        if case .edit(let id) = mode, draft.pollOptions != nil || records[id]?.poll != nil { throw HTTPFailure(status: 422) }
        struct PollPayload: Encodable { let options: [PollOptionInput] }
        struct Payload: Encodable {
            let body: String
            let flair: String
            let client_id: String?
            let media: JSONValue?
            let entities: [JSONValue]
            let poll: PollPayload?
        }
        let media: JSONValue?
        if let selected = draft.media {
            if let jpeg = selected.photoData {
                if let uploaded = uploadedPhotos[selected.id] { media = uploaded }
                else {
                    let uploaded = try await api.uploadPhoto(jpeg)
                    uploadedPhotos[selected.id] = uploaded
                    media = uploaded
                }
            } else { media = selected.payload }
        } else { media = nil }
        let saved: PostRecord
        switch mode {
        case .create:
            struct Response: Decodable { let post: PostRecord }
            let payload = Payload(body: draft.normalized.body, flair: draft.category, client_id: clientID, media: media,
                                  entities: draft.normalized.entities.map(\.payload), poll: draft.normalized.pollOptions.map { PollPayload(options: $0) })
            let response: Response = try await decode("POST", path: "posts", body: JSONEncoder().encode(payload))
            saved = remember(response.post)
        case .edit(let postID):
            let current = try await post(postID)
            guard current.poll == nil, current.userId == profile.currentUser?.id, EmojiDocument.canEdit(text: current.body ?? "", raw: current.entities) else { throw HTTPFailure(status: 422) }
            let payload = Payload(body: draft.normalized.body, flair: draft.category, client_id: nil,
                                  media: draft.mediaRemoved ? nil : (draft.media == nil || draft.media?.url == current.mediaURL ? current.media : media), entities: draft.normalized.entities.map(\.payload), poll: nil)
            _ = try await api.request("PATCH", path: "posts/\(try APIIdentifier.path(postID))", body: JSONEncoder().encode(payload))
            // PATCH has no post payload. Read the canonical record as Android does.
            saved = try await post(postID)
        }
        uploadedPhotos.removeAll()
        KlipyClient.shared.reportShare(draft.media)
        return PostEditorResult(postID: saved.id, draft: PostEditorDraft(body: saved.body ?? "", entities: EmojiEntity.decode(saved.entities), category: saved.flair ?? "",
                                pollOptions: saved.poll?.options.map { PollOptionInput(text: $0.text, entities: EmojiEntity.decode($0.entities)) }), mediaURL: saved.mediaURL, mediaPreviewURL: saved.asPost().mediaPreviewURL, mediaChanged: draft.mediaRemoved || draft.media != nil,
                                mediaWidth: saved.mediaDimensions.width, mediaHeight: saved.mediaDimensions.height)
    }

    private func remember(_ incoming: PostRecord) -> PostRecord {
        var post = incoming
        if let cached = pollSnapshots[post.id], cached.isSending || (cached.hasVoted && post.poll?.hasVoted != true) {
            post.poll = cached
        } else if let poll = post.poll { pollSnapshots[post.id] = poll }
        records[post.id] = post
        return post
    }

    private func publishPoll(_ poll: PostPoll, postID: String) {
        pollSnapshots[postID] = poll
        records[postID]?.poll = poll
        onPollChanged(postID, poll)
    }

    func votePoll(postID: String, optionID: String) async throws -> PostPoll {
        guard pendingPollVotes.insert(postID).inserted else { throw HTTPFailure(status: 409) }
        defer { pendingPollVotes.remove(postID) }
        let record: PostRecord
        if let cached = records[postID] { record = cached } else { record = try await post(postID) }
        guard let original = pollSnapshots[postID] ?? record.poll,
              original.options.contains(where: { $0.id == optionID }) else { throw HTTPFailure(status: 422) }
        if original.hasVoted {
            guard original.myOptionId == optionID else { throw HTTPFailure(status: 409) }
            return original
        }
        try Task.checkCancellation()
        publishPoll(original.selecting(optionID), postID: postID)
        do {
            struct Response: Decodable { let poll: PostPoll }
            let response: Response = try await decode("PUT", path: "posts/\(try APIIdentifier.path(postID))/poll/vote",
                                                      body: APIJSON.body(["option_id": optionID]))
            publishPoll(response.poll, postID: postID)
            return response.poll
        } catch {
            // A lost response can still mean the definitive vote reached the server.
            // Reconcile independently of cancellation before allowing another attempt.
            let recovered = await Task { @MainActor () -> PostPoll? in
                struct Response: Decodable { let post: PostRecord }
                let response: Response? = try? await self.decode("GET", path: "posts/\(try APIIdentifier.path(postID))")
                return response?.post.poll
            }.value
            if let recovered, recovered.hasVoted {
                publishPoll(recovered, postID: postID)
                return recovered
            }
            publishPoll(recovered ?? original, postID: postID)
            throw error
        }
    }
    func hide(_ postID: String) async throws {
        _ = try await api.request("PUT", path: "posts/\(try APIIdentifier.path(postID))/hidden")
        try Task.checkCancellation()
        hiddenPosts.insert(postID)
        onHidden(postID)
    }
    func unblockLocally(_ userID: String) { blockedUsers.remove(userID) }
    func block(_ userID: String) async throws {
        _ = try await api.request("PUT", path: "users/blocks/\(try APIIdentifier.path(userID))")
        blockedUsers.insert(userID)
        onBlocked(userID)
    }
    func decode<T: Decodable>(_ method: String, path: String, query: [URLQueryItem] = [], body: Data? = nil) async throws -> T {
        let data = try await api.request(method, path: path, query: query, body: body)
        return try APIJSON.decode(T.self, from: data)
    }
}

enum APIIdentifier {
    static func path(_ id: String) throws -> String {
        guard !id.isEmpty, id.utf8.count <= 128,
              id.unicodeScalars.allSatisfy({ CharacterSet.alphanumerics.contains($0) || $0 == "-" || $0 == "_" }) else {
            throw HTTPFailure(status: 400)
        }
        return id
    }
}

enum APIJSON {
    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(type, from: data)
    }
    static func body(_ value: [String: Any]) throws -> Data { try JSONSerialization.data(withJSONObject: value) }
    static func date(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}

struct VoteResponse: Decodable {
    struct Vote: Decodable {
        let upvotes: Int
        let downvotes: Int
        let myVote: String?
        var score: Int { upvotes - downvotes }
        var value: Int { myVote == "upvote" ? 1 : myVote == "downvote" ? -1 : 0 }
    }
    let vote: Vote
}

private extension String { var nilIfEmpty: String? { isEmpty ? nil : self } }
