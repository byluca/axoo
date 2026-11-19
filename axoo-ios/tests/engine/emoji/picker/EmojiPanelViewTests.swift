import XCTest
import UIKit
@testable import Axoo

@MainActor
final class EmojiPanelViewTests: XCTestCase {
    func testMediaPillReusesSharedControlsAndKeepsDeleteButtonClear() throws {
        let picker = EmojiPanelView(catalog: catalog())
        let pill = try XCTUnwrap(picker.subviews.first { $0.accessibilityIdentifier == "emoji-media-pill" } as? AxooPillView)
        let control = try XCTUnwrap(pill.contentView.subviews.first as? SlidingCategoryControl)
        let delete = try XCTUnwrap(picker.subviews.compactMap { $0 as? EmojiBackspaceButton }.first)
        var selected: [Bool] = []
        picker.onMediaRequested = { selected.append($0) }
        let buttons = control.subviews.compactMap { $0 as? UIButton }
        for width: CGFloat in [320, 390, 430] {
            picker.frame = CGRect(x: 0, y: 0, width: width, height: 300)
            picker.setNeedsLayout()
            picker.layoutIfNeeded()
            XCTAssertEqual(pill.frame.midX, width / 2, accuracy: 0.5)
            XCTAssertLessThan(pill.frame.maxX, delete.frame.minX)
        }
        buttons[1].sendActions(for: .touchUpInside)
        buttons[2].sendActions(for: .touchUpInside)
        XCTAssertEqual(selected, [false, true])
        XCTAssertEqual(control.selectedSegmentIndex, 0)
    }

    func testCategoriesJumpImmediatelyToTheirHeadersAfterResizing() throws {
        let picker = EmojiPanelView(catalog: catalog())
        let grid = try XCTUnwrap(picker.subviews.compactMap { $0 as? UICollectionView }.first)
        let tabs = descendants(picker).compactMap { $0 as? UIButton }
            .filter { $0.accessibilityIdentifier?.hasPrefix("axooji-category-") == true }
        XCTAssertEqual(tabs.count, 3)
        XCTAssertFalse(tabs.contains { $0.accessibilityIdentifier == "axooji-category-recents" })
        for width: CGFloat in [320, 430] {
            picker.frame = CGRect(x: 0, y: 0, width: width, height: 300)
            picker.setNeedsLayout()
            picker.layoutIfNeeded()
            XCTAssertEqual(grid.numberOfSections, 3)
            for section in [2, 0, 1] {
                let header = try XCTUnwrap(grid.collectionViewLayout.layoutAttributesForSupplementaryView(
                    ofKind: UICollectionView.elementKindSectionHeader, at: IndexPath(item: 0, section: section)))
                tabs[section].sendActions(for: .touchUpInside)
                // Check now, without waiting for a scroll animation.
                XCTAssertEqual(grid.contentOffset.y, header.frame.minY - grid.contentInset.top, accuracy: 0.5)
                XCTAssertTrue(tabs[section].isSelected)
                XCTAssertEqual(tabs.filter(\.isSelected).count, 1)
            }
        }
        picker.isActive = true
        XCTAssertEqual(grid.contentOffset.y, -grid.contentInset.top)
        XCTAssertTrue(tabs[0].isSelected)
        picker.isActive = false
        picker.isActive = true
        XCTAssertTrue(descendants(picker).contains { $0 === tabs[0] }, "Reopening must reuse the tabs")
    }

    func testFloatingDeleteButtonStaysAtTheBottomRightDuringScrolling() throws {
        let picker = EmojiPanelView(catalog: catalog())
        picker.frame = CGRect(x: 0, y: 0, width: 320, height: 300)
        picker.layoutIfNeeded()
        let button = try XCTUnwrap(picker.subviews.compactMap { $0 as? EmojiBackspaceButton }.first)
        let grid = try XCTUnwrap(picker.subviews.compactMap { $0 as? UICollectionView }.first)
        let expected = CGRect(x: 254, y: 234, width: 52, height: 52)
        XCTAssertEqual(button.frame, expected)
        grid.setContentOffset(CGPoint(x: 0, y: 200), animated: false)
        XCTAssertEqual(button.frame, expected)
        XCTAssertFalse(button.isHidden)
        XCTAssertTrue(picker.hitTest(CGPoint(x: 280, y: 260), with: nil) === button)
        grid.setContentOffset(CGPoint(x: 0, y: grid.contentSize.height + grid.contentInset.bottom - grid.bounds.height), animated: false)
        let last = try XCTUnwrap(grid.collectionViewLayout.layoutAttributesForItem(at: IndexPath(item: 19, section: 2)))
        XCTAssertLessThanOrEqual(grid.convert(last.frame, to: picker).maxY, button.frame.minY)
    }

    func testGridExtendsUnderCategoriesWithoutLeavingABlankStrip() throws {
        let picker = EmojiPanelView(catalog: catalog())
        picker.frame = CGRect(x: 0, y: 0, width: 390, height: 300)
        picker.layoutIfNeeded()
        let grid = try XCTUnwrap(picker.subviews.compactMap { $0 as? UICollectionView }.first)
        let categories = try XCTUnwrap(picker.subviews.compactMap { $0 as? EmojiCategoryBar }.first)
        let header = try XCTUnwrap(grid.collectionViewLayout.layoutAttributesForSupplementaryView(
            ofKind: UICollectionView.elementKindSectionHeader, at: IndexPath(item: 0, section: 0)))
        XCTAssertEqual(grid.frame.minY, 0)
        XCTAssertGreaterThanOrEqual(grid.convert(header.frame, to: picker).minY, categories.frame.maxY)
        grid.setContentOffset(CGPoint(x: 0, y: 100), animated: false)
        XCTAssertEqual(grid.frame.minY, categories.frame.minY)
    }

    func testHeldDeleteRepeatsAndStopsWhenPickerCloses() async throws {
        let picker = EmojiPanelView(catalog: catalog())
        let button = try XCTUnwrap(picker.subviews.compactMap { $0 as? EmojiBackspaceButton }.first)
        defer { button.stopRepeating() }
        var deletions = 0
        picker.onBackspace = { deletions += 1 }
        picker.isActive = true
        button.sendActions(for: .touchDown)
        XCTAssertEqual(deletions, 1)
        try await Task.sleep(nanoseconds: 360_000_000)
        XCTAssertGreaterThan(deletions, 1)
        picker.isActive = false
        let stoppedCount = deletions
        try await Task.sleep(nanoseconds: 350_000_000)
        XCTAssertEqual(deletions, stoppedCount)
        button.sendActions(for: .touchDown)
        button.sendActions(for: .touchCancel)
        let cancelledCount = deletions
        try await Task.sleep(nanoseconds: 350_000_000)
        XCTAssertEqual(deletions, cancelledCount)
        button.sendActions(for: .touchDown)
        button.sendActions(for: .touchCancel)
        XCTAssertEqual(deletions, cancelledCount + 1)
    }

    func testPickerUsesStaticCategoriesAndLeavesCellsEmptyUntilFramesAreReady() throws {
        let picker = EmojiPanelView(catalog: catalog())
        picker.frame = CGRect(x: 0, y: 0, width: 390, height: 300)
        picker.layoutIfNeeded()
        let tabs = descendants(picker).compactMap { $0 as? EmojiCategoryButton }
        XCTAssertEqual(tabs.count, 3)
        for tab in tabs {
            XCTAssertTrue(tab.subviews.contains { $0 is UIImageView })
            XCTAssertFalse(tab.subviews.contains { $0 is AnimatedLottieView })
        }
        let cell = EmojiCellView()
        cell.configure(try XCTUnwrap(catalog().emojis.first))
        XCTAssertFalse(cell.emoji.showsUnicodeFallback)
        XCTAssertTrue(cell.emoji.subviews.compactMap { $0 as? UILabel }.allSatisfy(\.isHidden))
        XCTAssertTrue(cell.emoji.subviews.compactMap { $0 as? UIImageView }.allSatisfy { $0.image == nil })
        let grid = try XCTUnwrap(picker.subviews.compactMap { $0 as? UICollectionView }.first)
        let first = try XCTUnwrap(grid.collectionViewLayout.layoutAttributesForItem(at: IndexPath(item: 0, section: 0)))
        let sixth = try XCTUnwrap(grid.collectionViewLayout.layoutAttributesForItem(at: IndexPath(item: 5, section: 0)))
        let seventh = try XCTUnwrap(grid.collectionViewLayout.layoutAttributesForItem(at: IndexPath(item: 6, section: 0)))
        XCTAssertEqual(first.frame.minY, sixth.frame.minY)
        XCTAssertGreaterThan(seventh.frame.minY, first.frame.minY)
    }

    func testEveryCatalogCategoryHasABundledStaticIcon() throws {
        let sections = EmojiAdapter(catalog: .shared).sections
        XCTAssertFalse(sections.isEmpty)
        for section in sections {
            let button = EmojiCategoryButton(section: section)
            let image = try XCTUnwrap(button.subviews.compactMap { $0 as? UIImageView }.first?.image)
            XCTAssertEqual(image.size, CGSize(width: 66, height: 66))
        }
    }

    private func catalog() -> AxoojiCatalog {
        let emojis = (0..<60).map { Axooji(id: 100_000 + $0, name: "test/emoji-\($0)", fallback: "🙂") }
        let categories = (0..<3).map { index in
            AxoojiCategory(id: "test-\(index)", label: "Categoria \(index)", tabEmoji: "🙂", icon: emojis[index * 20].name,
                           items: Array(emojis[(index * 20)..<((index + 1) * 20)]).map(\.name))
        }
        return AxoojiCatalog(emojis: emojis, categories: categories,
                             byID: Dictionary(uniqueKeysWithValues: emojis.map { ($0.id, $0) }),
                             byName: Dictionary(uniqueKeysWithValues: emojis.map { ($0.name, $0) }), root: nil)
    }
    private func descendants(_ view: UIView) -> [UIView] {
        view.subviews.flatMap { [$0] + descendants($0) }
    }
}
