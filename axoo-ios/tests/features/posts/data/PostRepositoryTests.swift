import XCTest
@testable import Axoo

@MainActor
final class PostRepositoryTests: XCTestCase {
    private let user = AccountProfile(id: "alice", nickname: "alice", universityId: "uniba", universityName: "UniBa", isModerator: false, isAdmin: false)

    func testMediaOnlyCommentSerializesAndDecodesGiphyAttachment() async throws {
        let api = RecordingAPI()
        let record = #"{"id":"c1","post_id":"p1","client_id":"send-1","user_id":"alice","body":"","created_at":"2026-10-16T10:00:00Z","media":{"type":"sticker","url":"https://media.giphy.com/a.gif","thumbnail_url":"https://media.giphy.com/preview.gif","width":200,"height":100,"mime_type":"image/gif","provider":"giphy","provider_id":"a"}}"#
        api.responses = [Data("{\"comment\":\(record)}".utf8)]
        let repository = RemoteCommentRepository(posts: PostRepository(profile: ProfileRepository(api: api, currentUser: user)), authorID: "bob")
        let media = PostEditorMedia(kind: .sticker, url: "https://media.giphy.com/a.gif", previewURL: "https://media.giphy.com/preview.gif",
            width: 200, height: 100, mimeType: "image/gif", provider: "giphy", providerID: "a", photoData: nil, id: "a")
        let comment = try await repository.create(postID: "p1", clientID: "send-1", body: "", parentID: nil, media: media)
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.requests.first?.body)) as? [String: Any])
        XCTAssertEqual(body["body"] as? String, "")
        XCTAssertEqual((body["media"] as? [String: Any])?["type"] as? String, "sticker")
        XCTAssertEqual(comment.media?.url, media.url)
        XCTAssertEqual(comment.media?.kind, .sticker)
        XCTAssertNil(comment.deleted().media)
    }

    func testPersonalHideRemovesThePostEvenFromAnOlderFeedResponse() async throws {
        let api = RecordingAPI()
        api.responses = [Data(), Data(#"{"posts":[{"id":"p1","user_id":"bob","body":"Vecchia risposta","created_at":"2026-09-25T10:00:00Z"}]}"#.utf8)]
        let repository = PostRepository(profile: ProfileRepository(api: api, currentUser: user))
        var hidden: [String] = []
        repository.onHidden = { hidden.append($0) }
        try await repository.hide("p1")
        XCTAssertEqual(hidden, ["p1"])
        XCTAssertEqual(api.requests.first?.method, "PUT")
        XCTAssertEqual(api.requests.first?.path, "posts/p1/hidden")
        let page = try await repository.page(for: .hot, cursor: nil)
        XCTAssertTrue(page.posts.isEmpty)
    }

    func testTextEditPreservesOpaqueMediaKeysAndDoesNotSendClientID() async throws {
        let api = RecordingAPI()
        api.responses = [Data(#"{"post":{"id":"post-1","user_id":"alice","body":"Originale","created_at":"2026-09-25T10:00:00Z","entities":[],"media":{"original_url":"https://example.com/image","width":1200,"future_field":{"crop_x":4}}}}"#.utf8), Data("{}".utf8),
                         Data(#"{"post":{"id":"post-1","user_id":"alice","body":"Testo confermato","flair":"Esami","created_at":"2026-09-25T10:00:00Z","updated_at":"2026-09-25T10:05:00Z"}}"#.utf8)]
        let repository = PostRepository(profile: ProfileRepository(api: api, currentUser: user))
        let result = try await repository.save(PostEditorDraft(body: "Nuovo testo", category: "Esami"), mode: .edit(postID: "post-1"), clientID: "unused")
        XCTAssertEqual(api.requests.map(\.method), ["GET", "PATCH", "GET"])
        let request = try XCTUnwrap(api.requests.first { $0.method == "PATCH" })
        XCTAssertEqual(request.method, "PATCH")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(request.body)) as? [String: Any])
        let media = try XCTUnwrap(body["media"] as? [String: Any])
        XCTAssertEqual(media["original_url"] as? String, "https://example.com/image")
        XCTAssertEqual((media["future_field"] as? [String: Any])?["crop_x"] as? Int, 4)
        XCTAssertNil(body["client_id"])
        XCTAssertEqual(body["body"] as? String, "Nuovo testo")
        XCTAssertEqual(repository.records["post-1"]?.body, "Testo confermato")
        XCTAssertEqual(repository.records["post-1"]?.flair, "Esami")
        XCTAssertEqual(repository.records["post-1"]?.updatedAt, "2026-09-25T10:05:00Z")
        XCTAssertEqual(result.draft.body, "Testo confermato")
    }

    func testAnimatedPostCreateSerializesTrimmedCodePointsAndMapsIDs() async throws {
        let api = RecordingAPI()
        let record = #"{"id":"p1","user_id":"alice","body":"👩🏽‍💻 😀","entities":[{"type":"custom_emoji","offset":5,"length":1,"custom_emoji_id":42}],"created_at":"2026-09-25T10:00:00Z"}"#
        api.responses = [Data("{\"post\":\(record)}".utf8)]
        let repository = PostRepository(profile: ProfileRepository(api: api, currentUser: user))
        let draft = PostEditorDraft(body: "  👩🏽‍💻 😀 ", entities: [EmojiEntity(offset: 7, length: 1, id: 42)])
        let result = try await repository.save(draft, mode: .create, clientID: "emoji-create")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.requests.first?.body)) as? [String: Any])
        let entity = try XCTUnwrap((body["entities"] as? [[String: Any]])?.first)
        XCTAssertEqual(body["body"] as? String, "👩🏽‍💻 😀")
        XCTAssertEqual(entity["offset"] as? Int, 5)
        XCTAssertEqual(entity["custom_emoji_id"] as? Int, 42)
        XCTAssertEqual(result.draft.entities, [EmojiEntity(offset: 5, length: 1, id: 42)])
        XCTAssertEqual(repository.records["p1"]?.asPost().entities, result.draft.entities)
        XCTAssertTrue(repository.records["p1"]?.asPost().canEdit == true)
    }

    func testAnimatedCommentEditPreservesEntityTypeAndID() async throws {
        let api = RecordingAPI()
        let record = #"{"id":"c1","post_id":"p1","user_id":"alice","body":"😀","entities":[{"type":"custom_emoji","offset":0,"length":1,"custom_emoji_id":42}],"created_at":"2026-09-25T10:00:00Z"}"#
        api.responses = [Data("{\"comments\":[\(record)]}".utf8), Data("{}".utf8)]
        let repository = RemoteCommentRepository(posts: PostRepository(profile: ProfileRepository(api: api, currentUser: user)), authorID: "bob")
        let original = try await repository.comments(postID: "p1")
        XCTAssertTrue(api.requests.first?.query.isEmpty == true, "Comment order is fixed on the server")
        XCTAssertTrue(original[0].canEdit)
        let entities = [EmojiEntity(type: "static_emoji", offset: 2, length: 1, id: 42)]
        let edited = try await repository.edit(postID: "p1", commentID: "c1", body: "a 😀", entities: entities)
        XCTAssertEqual(edited.entities, entities)
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.requests.last?.body)) as? [String: Any])
        XCTAssertEqual((body["entities"] as? [[String: Any]])?.first?["type"] as? String, "static_emoji")
        XCTAssertEqual((body["entities"] as? [[String: Any]])?.first?["offset"] as? Int, 2)
    }

    func testMyPostsUsesAuthenticatedEndpointAndMapsPersonalPage() async throws {
        let api = RecordingAPI()
        api.responses = [Data(#"{"posts":[{"id":"mine","user_id":"alice","body":"Mio","created_at":"2026-09-25T10:00:00Z"}],"next_cursor":""}"#.utf8)]
        let repository = PostRepository(profile: ProfileRepository(api: api, currentUser: user))
        let page = try await repository.myPostsPage(cursor: "cursor+/=")
        XCTAssertEqual(page.posts.map(\.id), ["mine"])
        XCTAssertEqual(repository.records["mine"]?.body, "Mio")
        XCTAssertNil(page.nextCursor)
        let request = try XCTUnwrap(api.requests.first)
        XCTAssertEqual(request.path, "posts/mine")
        XCTAssertEqual(request.method, "GET")
        XCTAssertEqual(Dictionary(uniqueKeysWithValues: request.query.map { ($0.name, $0.value ?? "") }),
                       ["limit": "20", "cursor": "cursor+/="])
    }

    func testHomeAndProfileUseSeparateDetailControllersForTheSamePost() {
        let repository = PostRepository(profile: ProfileRepository(api: RecordingAPI(), currentUser: user))
        let factory = ScreenFactory(posts: repository, onSignOut: {})
        let post = Post(id: "mine", nickname: "Alice", campus: "Campus", timeLabel: "ora", body: "Testo", userID: "alice")
        let homeDetail = factory.detail(post)
        let profileDetail = factory.detail(post, inProfile: true)
        XCTAssertFalse(homeDetail === profileDetail)
        XCTAssertTrue(factory.detail(post) === homeDetail)
        XCTAssertTrue(factory.detail(post, inProfile: true) === profileDetail)
        factory.stop()
    }

    func testFeedPaginationUsesAndroidQueryAndMergesServerPages() async throws {
        let api = RecordingAPI()
        api.responses = [
            Data(#"{"posts":[{"id":"p1","user_id":"alice","body":"Primo","created_at":"2026-09-25T10:00:00Z"}],"next_cursor":"cursor+/="}"#.utf8),
            Data(#"{"posts":[{"id":"p1","user_id":"alice","body":"Primo","created_at":"2026-09-25T10:00:00Z"},{"id":"p2","user_id":"bob","body":"Secondo","created_at":"2026-09-25T09:00:00Z"}],"next_cursor":null}"#.utf8)
        ]
        let screen = FeedViewModel(source: PostRepository(profile: ProfileRepository(api: api, currentUser: user)))
        await screen.refresh(.hot)
        XCTAssertEqual(screen.state(for: .hot).postIDs, ["p1"])
        screen.loadMore(.hot)
        await screen.requests[.hot]?.value
        XCTAssertEqual(screen.state(for: .hot).postIDs, ["p1", "p2"])
        XCTAssertNil(screen.state(for: .hot).nextCursor)
        XCTAssertEqual(api.requests.map(\.path), ["posts", "posts"])
        let request = try XCTUnwrap(api.requests.last)
        let query = Dictionary(uniqueKeysWithValues: request.query.map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(query["order"], "hot")
        XCTAssertEqual(query["limit"], "20")
        XCTAssertEqual(query["cursor"], "cursor+/=")
        screen.stop()
    }

    func testReplyPayloadAndServerIdentityMatchAndroidContract() async throws {
        let api = RecordingAPI()
        api.responses = [Data(#"{"comment":{"id":"c1","post_id":"p1","client_id":"submission-1","parent_comment_id":"parent","user_id":"alice","body":"Risposta 👩🏽‍💻","created_at":"2026-09-25T10:00:00Z"}}"#.utf8)]
        let repository = RemoteCommentRepository(posts: PostRepository(profile: ProfileRepository(api: api, currentUser: user)), authorID: "bob")
        let comment = try await repository.create(postID: "p1", clientID: "submission-1", body: "Risposta 👩🏽‍💻", parentID: "parent")
        let request = try XCTUnwrap(api.requests.first)
        XCTAssertEqual(request.method, "POST")
        XCTAssertEqual(request.path, "posts/p1/comments")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(request.body)) as? [String: Any])
        XCTAssertEqual(body["client_id"] as? String, "submission-1")
        XCTAssertEqual(body["reply_to_comment_id"] as? String, "parent")
        XCTAssertEqual(body["body"] as? String, "Risposta 👩🏽‍💻")
        XCTAssertEqual((body["entities"] as? [Any])?.count, 0)
        XCTAssertEqual(comment.id, "c1")
        XCTAssertEqual(comment.stableID, "client-submission-1")
        XCTAssertEqual(comment.parentID, "parent")
    }

    func testPublishedServerPostAppearsOnceInNewFeed() async throws {
        let api = RecordingAPI()
        let record = #"{"id":"created","user_id":"alice","body":"Testo confermato 👋","flair":"Esami","created_at":"2026-09-25T10:00:00Z"}"#
        api.responses = [Data("{\"post\":\(record)}".utf8), Data("{\"posts\":[\(record)],\"next_cursor\":null}".utf8), Data("{}".utf8)]
        let repository = PostRepository(profile: ProfileRepository(api: api, currentUser: user))
        let result = try await repository.save(PostEditorDraft(body: "  Testo inviato 👋  ", category: "Esami"), mode: .create, clientID: "create-1")
        let request = try XCTUnwrap(api.requests.first)
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(request.body)) as? [String: Any])
        XCTAssertEqual(request.method, "POST")
        XCTAssertEqual(request.path, "posts")
        XCTAssertEqual(body["client_id"] as? String, "create-1")
        XCTAssertEqual(body["body"] as? String, "Testo inviato 👋")
        XCTAssertEqual(body["flair"] as? String, "Esami")
        XCTAssertEqual(result.draft.body, "Testo confermato 👋")
        let screen = FeedViewModel(source: repository)
        screen.insertCreatedPost(try XCTUnwrap(repository.records[result.postID]).asPost())
        await screen.requests[.new]?.value
        XCTAssertEqual(screen.selectedFeed, .new)
        XCTAssertEqual(screen.state(for: .new).postIDs, ["created"])
        XCTAssertEqual(screen.posts["created"]?.body, "Testo confermato 👋")
        XCTAssertEqual(api.requests.last?.path, "posts/new/seen")
        let seen = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.requests.last?.body)) as? [String: String])
        XCTAssertEqual(seen["seen_through"], "2026-09-25T10:00:00Z")
        screen.stop()
    }
    func testAuthorAndAvatarConfigurationReachEditorCardsAndComments() throws {
        let userData = Data(#"{"id":"alice","nickname":"Alice","university_id":"uniba","university_name":"UniBa","avatar_config":{"hairColor":"b9a05f","glassesVariant":"variant02"}}"#.utf8)
        let user = try APIJSON.decode(AccountProfile.self, from: userData)
        let repository = PostRepository(profile: ProfileRepository(api: RecordingAPI(), currentUser: user))
        let editor = PostEditorViewModel(service: repository, mode: .create)
        XCTAssertEqual(editor.author?.nickname, "Alice")
        XCTAssertEqual(editor.author?.university, "UniBa")
        XCTAssertEqual(editor.author?.avatar.seed, "alice")
        XCTAssertEqual(editor.author?.avatar.parameters["glassesProbability"], "100")
        let postData = Data(#"{"id":"p1","user_id":"alice","created_at":"2026-09-25T10:00:00Z","avatar_config":{"hairColor":"b9a05f","glassesVariant":"variant02"}}"#.utf8)
        let card = try APIJSON.decode(PostRecord.self, from: postData).asPost()
        XCTAssertEqual(card.avatarConfig, user.avatarConfig)
        let commentData = Data(#"{"id":"c1","post_id":"p1","user_id":"alice","created_at":"2026-09-25T10:00:00Z","avatar_config":{"hairColor":"b9a05f","glassesVariant":"variant02"}}"#.utf8)
        let comment = try APIJSON.decode(CommentDTO.self, from: commentData).comment()
        XCTAssertEqual(comment.avatarConfig, user.avatarConfig)
        XCTAssertNil(comment.deleted().avatarConfig)
    }
    func testEntitiesRejectEditBeforeMutation() async throws {
        let api = RecordingAPI()
        api.responses = [Data(#"{"post":{"id":"post-1","user_id":"alice","body":"Originale","created_at":"2026-09-25T10:00:00Z","entities":[{"type":"custom_emoji","offset":0,"length":1}]}}"#.utf8)]
        let repository = PostRepository(profile: ProfileRepository(api: api, currentUser: user))
        do {
            _ = try await repository.save(PostEditorDraft(body: "Testo"), mode: .edit(postID: "post-1"), clientID: "unused")
            XCTFail("Unsupported rich content must not be overwritten")
        } catch let error as HTTPFailure { XCTAssertEqual(error.status, 422) }
        XCTAssertEqual(api.requests.map(\.method), ["GET"])
    }
    func testEmptyNullableBlocksAndFeedAreValid() async throws {
        let api = RecordingAPI()
        api.responses = [Data(#"{"users":null}"#.utf8), Data(#"{"posts":null,"next_cursor":""}"#.utf8)]
        let repository = PostRepository(profile: ProfileRepository(api: api, currentUser: user))
        try await repository.loadBlocks()
        let page = try await repository.page(for: .hot, cursor: nil)
        XCTAssertTrue(page.posts.isEmpty)
        XCTAssertNil(page.nextCursor)
    }
    func testCommentReplyClientIDVoteAndTombstoneMapping() throws {
        let data = Data(#"{"id":"c1","post_id":"p1","client_id":"retry-id","parent_comment_id":"parent","user_id":"alice","nickname":"alice","body":"ciao","created_at":"2026-09-25T10:00:00.123Z","updated_at":"2026-09-25T10:01:00Z","my_vote":"downvote","upvotes":4,"downvotes":2,"entities":[]}"#.utf8)
        let comment = try APIJSON.decode(CommentDTO.self, from: data).comment()
        XCTAssertEqual(comment.clientID, "retry-id")
        XCTAssertEqual(comment.parentID, "parent")
        XCTAssertEqual(comment.vote, .down)
        XCTAssertEqual(comment.score, 2)
        XCTAssertTrue(comment.isEdited)
        XCTAssertTrue(comment.deleted().body.isEmpty)
    }
}

@MainActor
private final class RecordingAPI: APIClient {
    struct Request { let method: String; let path: String; let query: [URLQueryItem]; let body: Data? }
    var requests: [Request] = []
    var responses: [Data] = []
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        requests.append(Request(method: method, path: path, query: query, body: body))
        guard !responses.isEmpty else { throw URLError(.badServerResponse) }
        return responses.removeFirst()
    }
}
