import XCTest
@testable import Axoo

final class PostPollTests: XCTestCase {
    func testOptionsRequireTwoToFourNonemptyValuesAndCountScalars() {
        let option = PollOptionInput(text: "Prima")
        XCTAssertFalse(PostEditorDraft(body: "Domanda", pollOptions: [option]).isValid)
        XCTAssertTrue(PostEditorDraft(body: "Domanda", pollOptions: [option, option]).isValid)
        XCTAssertTrue(PostEditorDraft(body: "Domanda", pollOptions: Array(repeating: option, count: 4)).isValid)
        XCTAssertFalse(PostEditorDraft(body: "Domanda", pollOptions: Array(repeating: option, count: 5)).isValid)
        XCTAssertFalse(PostEditorDraft(body: "Domanda", pollOptions: [option, PollOptionInput(text: " \n")]).isValid)
        XCTAssertTrue(PostEditorDraft(pollOptions: [option, option]).hasContent)
        XCTAssertFalse(PostEditorDraft(pollOptions: [option, option]).isValid)
        XCTAssertEqual(PollOptionInput(text: "👩🏽‍💻").characterCount, 4)
        XCTAssertTrue(PollOptionInput(text: String(repeating: "a", count: 100)).isValid)
        XCTAssertFalse(PollOptionInput(text: String(repeating: "a", count: 101)).isValid)
    }
    func testNormalizationAdjustsEmojiOffsetsForEachOption() {
        let entity = EmojiEntity(offset: 2, length: 1, id: 42)
        let draft = PostEditorDraft(body: " Domanda ", pollOptions: [PollOptionInput(text: "  😀  ", entities: [entity]), PollOptionInput(text: " Tè ")])
        XCTAssertEqual(draft.normalized.body, "Domanda")
        XCTAssertEqual(draft.normalized.pollOptions?[0].text, "😀")
        XCTAssertEqual(draft.normalized.pollOptions?[0].entities.first?.offset, 0)
        XCTAssertEqual(draft.normalized.pollOptions?[1].text, "Tè")
    }
    func testPercentagesSumTo100AndRemainHiddenUntilConfirmed() {
        let options = (0..<3).map { PostPollOption(id: "o\($0)", text: "Opzione", entities: [], votes: 1, voters: []) }
        let poll = PostPoll(options: options, totalVotes: 3, myOptionId: nil)
        XCTAssertEqual(poll.percentages, [0, 0, 0])
        XCTAssertEqual(poll.selecting("o0").percentages, [0, 0, 0])
        var confirmed = poll
        confirmed.myOptionId = "o0"
        XCTAssertEqual(confirmed.percentages, [34, 33, 33])
        XCTAssertEqual(confirmed.percentages.reduce(0, +), 100)
    }
}
