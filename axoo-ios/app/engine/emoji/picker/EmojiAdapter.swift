import UIKit

/// UIKit datasource and stable binding snapshots used by the playback policy.
@MainActor
final class EmojiAdapter: NSObject, UICollectionViewDataSource {
    struct Section {
        let id: String
        let title: String
        let icon: Axooji?
        let emojis: [Axooji]
    }
    private(set) var sections: [Section] = []
    private let catalog: AxoojiCatalog
    private let packs: [Section]
    var onContentReady: (() -> Void)?

    init(catalog: AxoojiCatalog) {
        self.catalog = catalog
        packs = catalog.categories.map { category in
            Section(id: category.id, title: category.label, icon: catalog.byName[category.icon],
                    emojis: category.items.compactMap { catalog.byName[$0] }.filter { $0.pickerHidden != true })
        }.filter { !$0.emojis.isEmpty }
        super.init()
        refreshRecents()
    }
    func refreshRecents() {
        sections = [Section(id: "recent", title: "Recenti", icon: nil, emojis: RecentEmoji.items(in: catalog))] + packs
    }

    func numberOfSections(in collectionView: UICollectionView) -> Int { sections.count }
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { sections[section].emojis.count }
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "emoji", for: indexPath) as! EmojiCellView
        cell.emoji.onContentReady = { [weak self] in self?.onContentReady?() }
        cell.configure(sections[indexPath.section].emojis[indexPath.item])
        return cell
    }
    func collectionView(_ collectionView: UICollectionView, viewForSupplementaryElementOfKind kind: String,
                        at indexPath: IndexPath) -> UICollectionReusableView {
        let header = collectionView.dequeueReusableSupplementaryView(ofKind: kind, withReuseIdentifier: "header", for: indexPath) as! EmojiPickerHeader
        let section = sections[indexPath.section]
        header.label.text = section.title
        header.label.font = AxooTypography.emojiCategory
        return header
    }
    func collectPlaybackCandidates(in collection: UICollectionView) -> [PlaybackPolicy.Candidate<UUID, EmojiCellView>] {
        collection.indexPathsForVisibleItems.sorted().enumerated().compactMap { order, path in
            guard let cell = collection.cellForItem(at: path) as? EmojiCellView else { return nil }
            let visible = cell.frame.intersection(collection.bounds)
            let area = visible.isNull ? 0 : visible.width * visible.height
            let fullArea = cell.frame.width * cell.frame.height
            return PlaybackPolicy.Candidate(id: cell.bindingID, value: cell, ready: cell.emoji.asset != nil,
                visibleArea: area, visibleCenterY: visible.midY,
                visiblePermille: fullArea > 0 ? Int(area / fullArea * 1000) : 0,
                top: cell.frame.minY, left: cell.frame.minX, stableOrder: order)
        }
    }
    func pauseExcept(_ ids: Set<UUID>, in collection: UICollectionView) {
        for case let cell as EmojiCellView in collection.visibleCells where !ids.contains(cell.bindingID) {
            cell.emoji.playbackEnabled = false
        }
    }
    func startCandidate(_ candidate: PlaybackPolicy.Candidate<UUID, EmojiCellView>) -> Bool {
        guard candidate.value.bindingID == candidate.id, candidate.value.window != nil,
              candidate.value.emoji.asset != nil else { return false }
        candidate.value.emoji.playbackEnabled = true
        return true
    }
}
