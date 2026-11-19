import UIKit

/// Shared UIKit picker for post and comment inputs. Selection returns the whole
/// catalog entry: callers must serialize the ID as well as the fallback text.
final class EmojiPanelView: UIView, UICollectionViewDelegateFlowLayout {
    var onSelect: (Axooji) -> Void = { _ in }
    var onBackspace: () -> Void = {}
    var onMediaRequested: (Bool) -> Void = { _ in }
    var supportsMedia = true {
        didSet { mediaPill.isHidden = !supportsMedia }
    }
    var isActive = false {
        didSet {
            guard oldValue != isActive else { return }
            if isActive {
                playback.pauseAll()
                adapter.refreshRecents()
                collection.reloadData()
                lastWidth = 0
                setNeedsLayout()
                collection.setContentOffset(CGPoint(x: 0, y: -collection.contentInset.top), animated: false)
                selectTab(0)
                setControlsVisible(true)
                mediaTabs.selectedSegmentIndex = 0
            } else {
                holdPreview.dismiss()
                backspace.stopRepeating()
                playback.pauseAll()
            }
            refreshPlayback()
        }
    }
    private let adapter: EmojiAdapter
    private let categoryBar: EmojiCategoryBar
    private var sections: [EmojiAdapter.Section] { adapter.sections }
    private var sectionOffsets: [CGFloat] = []
    private let backspace = EmojiBackspaceButton()
    private let bottomFade = BottomFadeView(color: AxooTheme.pickerBackground)
    private let divider = UIView()
    private let mediaPill = AxooPillView(surface: .white)
    private let mediaTabs = SlidingCategoryControl(items: ["Emoji", "GIF", "Sticker"])
    private var controlsVisible = true
    private var previousScrollY: CGFloat = 0
    private var directionDistance: CGFloat = 0
    private let layout = UICollectionViewFlowLayout()
    private lazy var collection = UICollectionView(frame: .zero, collectionViewLayout: layout)
    private lazy var playback = EmojiPickerPlaybackCoordinator(collection: collection, adapter: adapter)
    private var previewVisible = false
    private lazy var holdPreview = HoldMediaPreview(source: collection, resolve: { [weak self] point in
        guard let self, isActive, let path = collection.indexPathForItem(at: point) else { return nil }
        let emoji = sections[path.section].emojis[path.item]
        return HoldMediaPreview.Content(key: "emoji-\(emoji.id)", aspectRatio: 1, maximumSize: 220) {
            let view = AnimatedLottieView(frame: .zero)
            view.showsUnicodeFallback = false
            view.configure(emoji)
            return view
        }
    }, onVisibility: { [weak self] visible in
        guard let self else { return }
        previewVisible = visible
        if visible { playback.pauseAll() }
        refreshPlayback()
    })
    private var lastWidth: CGFloat = 0

    init(catalog: AxoojiCatalog = .shared) {
        adapter = EmojiAdapter(catalog: catalog)
        categoryBar = EmojiCategoryBar(sections: adapter.sections)
        super.init(frame: .zero)
        backgroundColor = AxooTheme.pickerBackground
        layer.cornerRadius = AxooTheme.controlCornerRadius
        layer.cornerCurve = .continuous
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = AxooTheme.controlShadowOpacity
        layer.shadowRadius = AxooTheme.controlShadowRadius
        layer.shadowOffset = AxooTheme.controlShadowOffset
        layer.borderWidth = AxooTheme.controlBorderWidth
        layer.borderColor = AxooTheme.cardBorder.cgColor
        bottomFade.layer.cornerRadius = AxooTheme.controlCornerRadius
        bottomFade.layer.maskedCorners = [.layerMinXMaxYCorner, .layerMaxXMaxYCorner]
        bottomFade.clipsToBounds = true
        divider.backgroundColor = AxooTheme.cardBorder
        divider.accessibilityIdentifier = "emoji-category-divider"
        accessibilityIdentifier = "axooji-picker"
        _ = holdPreview
        collection.backgroundColor = .clear
        collection.showsVerticalScrollIndicator = false
        collection.accessibilityIdentifier = "axooji-grid"
        collection.dataSource = adapter; collection.delegate = self
        collection.alwaysBounceVertical = true
        collection.contentInsetAdjustmentBehavior = .never
        collection.contentInset.top = 44 + 1 / UIScreen.main.scale
        collection.contentOffset.y = -collection.contentInset.top
        categoryBar.backgroundColor = AxooTheme.pickerBackground
        // Leave the final row clear of the floating delete button.
        collection.contentInset.bottom = 68
        layout.minimumInteritemSpacing = 0; layout.minimumLineSpacing = 0
        layout.sectionInset = UIEdgeInsets(top: 0, left: 8, bottom: 8, right: 8)
        collection.register(EmojiCellView.self, forCellWithReuseIdentifier: "emoji")
        collection.register(EmojiPickerHeader.self, forSupplementaryViewOfKind: UICollectionView.elementKindSectionHeader,
                            withReuseIdentifier: "header")
        categoryBar.onSelect = { [weak self] in self?.showSection($0) }
        playback.playbackAllowed = { [weak self] in self?.canPlay == true }
        backspace.onBackspace = { [weak self] in self?.onBackspace() }
        [collection, categoryBar, bottomFade, backspace, divider, mediaPill].forEach { addSubview($0); $0.translatesAutoresizingMaskIntoConstraints = false }
        mediaTabs.usesCompactPillStyle = true
        mediaPill.accessibilityIdentifier = "emoji-media-pill"
        mediaPill.contentView.addSubview(mediaTabs)
        mediaTabs.translatesAutoresizingMaskIntoConstraints = false
        let preferredWidth = mediaPill.widthAnchor.constraint(equalToConstant: 180)
        preferredWidth.priority = UILayoutPriority(999)
        preferredWidth.isActive = true
        mediaTabs.addAction(UIAction { [weak self] _ in
            guard let self, self.mediaTabs.selectedSegmentIndex != 0 else { return }
            let sticker = self.mediaTabs.selectedSegmentIndex == 2
            self.mediaTabs.selectedSegmentIndex = 0
            self.onMediaRequested(sticker)
        }, for: .valueChanged)
        NSLayoutConstraint.activate([
            mediaPill.centerXAnchor.constraint(equalTo: centerXAnchor),
            mediaPill.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -22),
            mediaPill.heightAnchor.constraint(equalToConstant: 36),
            mediaPill.widthAnchor.constraint(lessThanOrEqualTo: widthAnchor, constant: -156),
            mediaTabs.leadingAnchor.constraint(equalTo: mediaPill.contentView.leadingAnchor),
            mediaTabs.trailingAnchor.constraint(equalTo: mediaPill.contentView.trailingAnchor),
            mediaTabs.topAnchor.constraint(equalTo: mediaPill.contentView.topAnchor),
            mediaTabs.bottomAnchor.constraint(equalTo: mediaPill.contentView.bottomAnchor),
            categoryBar.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor, constant: 8),
            categoryBar.topAnchor.constraint(equalTo: topAnchor), categoryBar.heightAnchor.constraint(equalToConstant: 44),
            categoryBar.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor, constant: -8),
            backspace.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -14),
            backspace.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor, constant: -14),
            backspace.widthAnchor.constraint(equalToConstant: 52), backspace.heightAnchor.constraint(equalToConstant: 52),
            divider.topAnchor.constraint(equalTo: categoryBar.bottomAnchor),
            divider.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            divider.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            divider.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale),
            collection.topAnchor.constraint(equalTo: topAnchor),
            collection.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor),
            collection.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor),
            collection.bottomAnchor.constraint(equalTo: bottomAnchor),
            bottomFade.leadingAnchor.constraint(equalTo: leadingAnchor), bottomFade.trailingAnchor.constraint(equalTo: trailingAnchor),
            bottomFade.bottomAnchor.constraint(equalTo: bottomAnchor), bottomFade.heightAnchor.constraint(equalToConstant: BottomFadeView.height)
        ])
        for name in [UIApplication.didBecomeActiveNotification, UIApplication.willResignActiveNotification,
                     UIApplication.didEnterBackgroundNotification, UIResponder.keyboardDidShowNotification,
                     UIResponder.keyboardDidChangeFrameNotification] {
            NotificationCenter.default.addObserver(self, selector: #selector(applicationChanged), name: name, object: nil)
        }
        selectTab(0)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    @objc private func applicationChanged() {
        if UIApplication.shared.applicationState != .active { backspace.stopRepeating() }
        refreshPlayback()
        schedulePlaybackRefresh()
    }

    private func showSection(_ section: Int) {
        guard sectionOffsets.indices.contains(section) else { return }
        let maxY = max(0, collection.contentSize.height + collection.contentInset.bottom - collection.bounds.height)
        // O(1) category lookup, with an immediate jump like Android.
        collection.setContentOffset(CGPoint(x: 0, y: min(maxY, sectionOffsets[section] - collection.contentInset.top)), animated: false)
        selectTab(section)
        refreshPlayback()
    }
    private func selectTab(_ section: Int) { categoryBar.select(section) }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        refreshPlayback()
        schedulePlaybackRefresh()
    }
    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        // Let the surface reach the bottom while keeping the last row above Home.
        collection.contentInset.bottom = 68 + safeAreaInsets.bottom
        schedulePlaybackRefresh()
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor
        layer.shadowPath = UIBezierPath(roundedRect: bounds, cornerRadius: AxooTheme.controlCornerRadius).cgPath
        if collection.bounds.width > 0, lastWidth != collection.bounds.width {
            lastWidth = collection.bounds.width
            layout.invalidateLayout()
            collection.layoutIfNeeded()
            // Cache exact UIKit header positions only when the geometry changes.
            sectionOffsets = sections.indices.map { section in
                layout.layoutAttributesForSupplementaryView(ofKind: UICollectionView.elementKindSectionHeader,
                                                            at: IndexPath(item: 0, section: section))?.frame.minY ?? 0
            }
        }
        refreshPlayback()
        schedulePlaybackRefresh()
    }

    private var canPlay: Bool { isActive && axooIsVisible && !previewVisible }
    private func schedulePlaybackRefresh() { playback.markDirty() }
    private func refreshPlayback() {
        playback.markDirty()
        EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate()
    }
    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        previousScrollY = scrollView.contentOffset.y
        directionDistance = 0
    }
    private func setControlsVisible(_ visible: Bool) {
        guard controlsVisible != visible else { return }
        controlsVisible = visible
        if !visible { backspace.stopRepeating() }
        [categoryBar, divider, backspace, mediaPill].forEach { $0.isUserInteractionEnabled = visible }
        UIView.animate(withDuration: 0.16, delay: 0, options: [.beginFromCurrentState]) {
            [self.categoryBar, self.divider, self.backspace, self.mediaPill].forEach { $0.alpha = visible ? 1 : 0 }
        }
    }
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        guard scrollView === collection else { return }
        let y = scrollView.contentOffset.y
        if scrollView.isDragging || scrollView.isDecelerating {
            let delta = y - previousScrollY
            if (delta > 0) != (directionDistance > 0) { directionDistance = 0 }
            directionDistance += delta
            if y <= -collection.contentInset.top { setControlsVisible(true) }
            else if abs(directionDistance) >= 8 { setControlsVisible(directionDistance < 0) }
        }
        previousScrollY = y
        if collection.contentOffset.y >= collection.contentSize.height + collection.contentInset.bottom - collection.bounds.height {
            selectTab(sections.count - 1)
        } else if let section = collection.indexPathsForVisibleItems.min()?.section { selectTab(section) }
        refreshPlayback()
    }
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let emoji = sections[indexPath.section].emojis[indexPath.item]
        RecentEmoji.record(emoji.id)
        onSelect(emoji)
    }
    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        schedulePlaybackRefresh()
    }
    func collectionView(_ collectionView: UICollectionView, didEndDisplaying cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        (cell as? EmojiCellView)?.emoji.playbackEnabled = false
        refreshPlayback()
    }
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout,
                        sizeForItemAt indexPath: IndexPath) -> CGSize {
        let edge = floor(max(1, collectionView.bounds.width - 16) / 6)
        return CGSize(width: edge, height: edge)
    }
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout,
                        referenceSizeForHeaderInSection section: Int) -> CGSize {
        CGSize(width: collectionView.bounds.width, height: max(40, AxooTypography.emojiCategory.lineHeight + 16))
    }
}
