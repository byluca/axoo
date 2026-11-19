import XCTest
@testable import Axoo

final class PostTests: XCTestCase {
    func testVoteTransitionsPreserveOtherUsersScore() {
        for previous in Post.Vote.allCases {
            for requested in [Post.Vote.up, .down] {
                var post = fixture(score: -3 + previous.rawValue, vote: previous)
                post.toggleVote(requested)
                let expected: Post.Vote = previous == requested ? .none : requested
                XCTAssertEqual(post.vote, expected)
                XCTAssertEqual(post.score - post.vote.rawValue, -3,
                               "Changing or removing a vote must preserve everybody else's votes")
            }
        }
    }

    func testRepeatedUpvoteReturnsToOriginalScore() {
        var post = fixture(score: 42)
        post.toggleVote(.up)
        XCTAssertEqual(post.score, 43)
        post.toggleVote(.up)
        XCTAssertEqual(post.score, 42)
        XCTAssertEqual(post.vote, .none)
    }

    func testPendingVoteIgnoresAdditionalTaps() {
        var post = fixture(score: 10, vote: .up)
        post.isVoting = true
        let original = post
        post.toggleVote(.down)
        XCTAssertEqual(post, original)
    }

    private func fixture(score: Int, vote: Post.Vote = .none) -> Post {
        Post(id: "fixture", nickname: "student", campus: "UniBa", timeLabel: "ora",
                        body: "Post di prova", score: score, vote: vote)
    }
}
