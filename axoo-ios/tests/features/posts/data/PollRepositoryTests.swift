import XCTest
@testable import Axoo

@MainActor
final class PollRepositoryTests: XCTestCase {
    private func repository(_ api: PollAPI) -> PostRepository {
        PostRepository(profile: ProfileRepository(api: api,
            currentUser: AccountProfile(id: "alice", nickname: "Alice", universityId: "uniba", universityName: "UniBa", isModerator: false, isAdmin: false)))
    }
    func testCreateIncludesNormalizedOptionEmojiPayloadAndDisablesEditing() async throws {
        let api = PollAPI()
        api.responses = [.success(PollFixtures.response(voted: false))]
        let posts = repository(api)
        let draft = PostEditorDraft(body: "Domanda", pollOptions: [PollOptionInput(text: "  😀 ", entities: [EmojiEntity(offset: 2, length: 1, id: 42)]), PollOptionInput(text: " Tè ")])
        _ = try await posts.save(draft, mode: .create, clientID: "request-1")
        let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.requests.first?.body)) as? [String: Any])
        let options = try XCTUnwrap((payload["poll"] as? [String: Any])?["options"] as? [[String: Any]])
        XCTAssertEqual(options[0]["text"] as? String, "😀")
        XCTAssertEqual((options[0]["entities"] as? [[String: Any]])?.first?["offset"] as? Int, 0)
        XCTAssertEqual((options[0]["entities"] as? [[String: Any]])?.first?["custom_emoji_id"] as? Int, 42)
        XCTAssertFalse(try XCTUnwrap(posts.records["poll-post"]).asPost().canEdit)
        do {
            _ = try await posts.save(PostEditorDraft(body: "Modifica"), mode: .edit(postID: "poll-post"), clientID: "unused")
            XCTFail("A published poll must reject editing")
        } catch let failure as HTTPFailure { XCTAssertEqual(failure.status, 422) }
        XCTAssertEqual(api.requests.count, 1)
    }
    func testVotePublishesPendingAndConfirmedStateAndCannotChangeChoice() async throws {
        let api = PollAPI()
        api.responses = [.success(PollFixtures.response(voted: false)), .success(PollFixtures.voteResponse())]
        let posts = repository(api)
        _ = try await posts.post("poll-post")
        var changes: [PostPoll] = []
        posts.onPollChanged = { _, poll in changes.append(poll) }
        let voted = try await posts.votePoll(postID: "poll-post", optionID: "o0")
        XCTAssertEqual(changes.map(\.isSending), [true, false])
        XCTAssertEqual(changes[0].percentages, [0, 0])
        XCTAssertEqual(voted.percentages, [100, 0])
        XCTAssertEqual(api.requests.last?.path, "posts/poll-post/poll/vote")
        XCTAssertEqual(api.requests.last?.method, "PUT")
        _ = try await posts.votePoll(postID: "poll-post", optionID: "o0")
        do {
            _ = try await posts.votePoll(postID: "poll-post", optionID: "o1")
            XCTFail("The vote is definitive")
        } catch let failure as HTTPFailure { XCTAssertEqual(failure.status, 409) }
        XCTAssertEqual(api.requests.count, 2)
    }
    func testLostResponseRecoversCommittedVoteAndStaleReadsCannotUndoIt() async throws {
        let api = PollAPI()
        api.responses = [.success(PollFixtures.response(voted: false)), .failure(URLError(.networkConnectionLost)),
                         .success(PollFixtures.response(voted: true)), .success(PollFixtures.response(voted: false))]
        let posts = repository(api)
        _ = try await posts.post("poll-post")
        let recovered = try await posts.votePoll(postID: "poll-post", optionID: "o0")
        XCTAssertTrue(recovered.hasVoted)
        let stale = try await posts.post("poll-post")
        XCTAssertTrue(stale.poll?.hasVoted == true)
        XCTAssertEqual(stale.poll?.myOptionId, "o0")
    }
    func testFailureRestoresTheChoiceAndAllowsRetry() async throws {
        let api = PollAPI()
        api.responses = [.success(PollFixtures.response(voted: false)), .failure(URLError(.notConnectedToInternet)),
                         .success(PollFixtures.response(voted: false)), .success(PollFixtures.voteResponse())]
        let posts = repository(api)
        _ = try await posts.post("poll-post")
        do { _ = try await posts.votePoll(postID: "poll-post", optionID: "o0"); XCTFail("Expected network failure") }
        catch { }
        XCTAssertNil(posts.records["poll-post"]?.poll?.myOptionId)
        XCTAssertFalse(posts.records["poll-post"]?.poll?.isSending == true)
        let retried = try await posts.votePoll(postID: "poll-post", optionID: "o0")
        XCTAssertTrue(retried.hasVoted)
    }
    func testConcurrentTapSendsOnlyOneRequest() async throws {
        let api = PollAPI()
        api.responses = [.success(PollFixtures.response(voted: false))]
        let posts = repository(api)
        _ = try await posts.post("poll-post")
        api.suspendVote = true
        let first = Task { try await posts.votePoll(postID: "poll-post", optionID: "o0") }
        for _ in 0..<1000 { if api.voteContinuation != nil { break }; await Task.yield() }
        XCTAssertNotNil(api.voteContinuation)
        do { _ = try await posts.votePoll(postID: "poll-post", optionID: "o1"); XCTFail("A second vote must not be sent") }
        catch let failure as HTTPFailure { XCTAssertEqual(failure.status, 409) }
        api.voteContinuation?.resume(returning: PollFixtures.voteResponse())
        _ = try await first.value
        XCTAssertEqual(api.requests.filter { $0.method == "PUT" }.count, 1)
    }
}

enum PollFixtures {
    static func pollJSON(voted: Bool) -> String {
        let choice = voted ? ",\"my_option_id\":\"o0\"" : ""
        let votes = voted ? 1 : 0
        return "{\"options\":[{\"id\":\"o0\",\"text\":\"Prima\",\"entities\":[],\"votes\":\(votes),\"voters\":[]},{\"id\":\"o1\",\"text\":\"Seconda\",\"entities\":[],\"votes\":0,\"voters\":[]}],\"total_votes\":\(votes)\(choice)}"
    }
    static func response(voted: Bool) -> Data {
        Data("{\"post\":{\"id\":\"poll-post\",\"user_id\":\"alice\",\"body\":\"Domanda\",\"created_at\":\"2026-10-25T10:00:00Z\",\"poll\":\(pollJSON(voted: voted))}}".utf8)
    }
    static func voteResponse() -> Data { Data("{\"poll\":\(pollJSON(voted: true))}".utf8) }
    static func poll(voted: Bool) throws -> PostPoll { try APIJSON.decode(PostPoll.self, from: Data(pollJSON(voted: voted).utf8)) }
}

@MainActor
private final class PollAPI: APIClient {
    struct Request { let method: String; let path: String; let body: Data? }
    var requests: [Request] = []
    var responses: [Result<Data, Error>] = []
    var suspendVote = false
    var voteContinuation: CheckedContinuation<Data, Never>?
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        requests.append(Request(method: method, path: path, body: body))
        if suspendVote && method == "PUT" { return await withCheckedContinuation { voteContinuation = $0 } }
        guard !responses.isEmpty else { throw URLError(.badServerResponse) }
        return try responses.removeFirst().get()
    }
}
