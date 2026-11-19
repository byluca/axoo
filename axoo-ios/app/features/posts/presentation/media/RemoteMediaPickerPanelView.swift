import UIKit

final class RemoteMediaPickerScreen: AxooViewController {
    var onSelect: ((PostEditorMedia) -> Void)?
    private let picker: RemoteMediaPickerPanelView
    private let api: APIClient?

    init(api: APIClient?, kind: PostEditorMedia.Kind) {
        self.api = api
        picker = RemoteMediaPickerPanelView(api: api, kind: kind)
        super.init()
        title = nil
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AxooTheme.background
        view.addSubview(picker)
        picker.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            picker.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            picker.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            picker.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            picker.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)
        ])
        picker.onSelect = { [weak self] in self?.onSelect?($0) }
        picker.onCreateSticker = { [weak self] in
            guard let self, let api, presentedViewController == nil else { return }
            view.endEditing(true)
            let creator = StickerCreatorScreen(api: api)
            creator.onCreated = { [weak self] in self?.onSelect?($0) }
            let navigation = UINavigationController(rootViewController: creator)
            navigation.modalPresentationStyle = .fullScreen
            present(navigation, animated: true)
        }
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || navigationController?.isBeingDismissed == true { picker.stop() }
    }
}

@MainActor
final class RemoteMediaPickerPanelView: UIView, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout, UISearchBarDelegate {
    
    var onSelect: ((PostEditorMedia) -> Void)?
    
    var onCreateSticker: () -> Void = {}
    private var personalItems: [PostEditorMedia] { viewModel.personalItems }
    private static let createTile = PostEditorMedia(kind: .sticker, url: "", previewURL: "", width: 512, height: 512,
        mimeType: "", provider: nil, providerID: nil, photoData: nil, id: "create-sticker")
    private let viewModel: RemoteMediaViewModel
    private var kind: PostEditorMedia.Kind { viewModel.kind }
    private var items: [PostEditorMedia] { viewModel.items }
    private var showingAup: Bool { kind == .sticker && viewModel.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var remoteSection: Int { showingAup ? 2 : 0 }
    private func media(in section: Int) -> [PostEditorMedia] {
        guard showingAup else { return items }
        return section == 0 ? [Self.createTile] + personalItems : (section == 1 ? AupStickers.all : items)
    }
    private func media(at path: IndexPath) -> PostEditorMedia? {
        let values = media(in: path.section)
        return values.indices.contains(path.item) ? values[path.item] : nil
    }
    
    private let headerView = UIView()
    private let searchBar = UISearchBar()
    private var collectionView: UICollectionView!
    private let emptyLabel = UILabel.axoo(AxooOverlayCopy.mediaEmpty, font: AxooTypography.statusTitle, color: AxooTheme.textSecondary)
    
    private var previewVisible = false
    private lazy var holdPreview = HoldMediaPreview(source: collectionView, resolve: { [weak self] point in
        guard let self, let path = collectionView.indexPathForItem(at: point), let media = media(at: path), media.id != Self.createTile.id else { return nil }
        let decoded = (collectionView.cellForItem(at: path) as? RemoteMediaCell)?.decodedPreview
        return HoldMediaPreview.Content(key: media.id, aspectRatio: CGFloat(media.width) / CGFloat(max(1, media.height))) {
            return RemoteMediaPreviewContent(url: media.previewURL, decoded: decoded)
        }
    }, onVisibility: { [weak self] visible in
        guard let self else { return }
        previewVisible = visible
        for case let cell as RemoteMediaCell in collectionView.visibleCells { cell.setPreviewPaused(visible) }
    })
    private var layoutWidth: CGFloat = 0
    
    init(api: APIClient?, kind: PostEditorMedia.Kind) {
        viewModel = RemoteMediaViewModel(repository: api.map { RemoteMediaRepository(api: $0) }, kind: kind,
            personalRepository: api.map { PersonalStickerRepository(api: $0) })
        super.init(frame: .zero)
        setupUI()
        viewModel.onChange = { [weak self] in self?.render($0) }
        viewModel.loadNextPage()
        viewModel.loadPersonalStickers()
    }
    
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    
    private func setupUI() {
        backgroundColor = AxooTheme.background
        
        headerView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(headerView)
        
        searchBar.placeholder = "Search KLIPY"
        searchBar.searchBarStyle = .minimal
        searchBar.searchTextField.backgroundColor = AxooTheme.surface
        searchBar.searchTextField.textColor = AxooTheme.textPrimary
        searchBar.searchTextField.tintColor = AxooTheme.accent
        searchBar.searchTextField.font = AxooTypography.postBody
        searchBar.searchTextField.layer.cornerRadius = 10
        searchBar.searchTextField.clipsToBounds = true
        searchBar.setImage(
            UIImage(systemName: "magnifyingglass", withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .regular)),
            for: .search, state: .normal)
        searchBar.searchTextField.leftView?.tintColor = AxooTheme.textSecondary
        searchBar.searchTextField.attributedPlaceholder = NSAttributedString(
            string: searchBar.placeholder ?? "", attributes: [.foregroundColor: AxooTheme.textSecondary, .font: AxooTypography.postBody])
        searchBar.searchTextField.accessibilityIdentifier = "gif-search"
        searchBar.delegate = self
        searchBar.translatesAutoresizingMaskIntoConstraints = false
        headerView.addSubview(searchBar)
        NSLayoutConstraint.activate([
            searchBar.leadingAnchor.constraint(equalTo: headerView.leadingAnchor, constant: 12),
            searchBar.trailingAnchor.constraint(equalTo: headerView.trailingAnchor, constant: -12),
            searchBar.topAnchor.constraint(equalTo: headerView.topAnchor, constant: 4),
            searchBar.bottomAnchor.constraint(equalTo: headerView.bottomAnchor, constant: -4)
        ])

        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 2
        layout.minimumLineSpacing = 2
        
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.showsVerticalScrollIndicator = false
        collectionView.backgroundColor = .clear
        collectionView.keyboardDismissMode = .interactive
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(RemoteMediaCell.self, forCellWithReuseIdentifier: "Cell")
        collectionView.register(CreateStickerCell.self, forCellWithReuseIdentifier: "create")
        collectionView.register(MediaPickerSectionHeader.self, forSupplementaryViewOfKind: UICollectionView.elementKindSectionHeader, withReuseIdentifier: "section")
        collectionView.register(MediaPickerStatus.self, forSupplementaryViewOfKind: UICollectionView.elementKindSectionFooter, withReuseIdentifier: "status")
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(collectionView)
        _ = holdPreview
        
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        emptyLabel.isHidden = true
        emptyLabel.textAlignment = .center
        addSubview(emptyLabel)
        
        NSLayoutConstraint.activate([
            // Give the native grabber room without changing either sheet detent.
            headerView.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            headerView.leadingAnchor.constraint(equalTo: leadingAnchor),
            headerView.trailingAnchor.constraint(equalTo: trailingAnchor),
            headerView.heightAnchor.constraint(equalToConstant: 52),
            
            collectionView.topAnchor.constraint(equalTo: headerView.bottomAnchor),
            collectionView.leadingAnchor.constraint(equalTo: leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor),
            
            emptyLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            emptyLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            emptyLabel.centerYAnchor.constraint(equalTo: collectionView.centerYAnchor)
        ])
    }
    
    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        viewModel.search(searchText)
    }
    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        viewModel.search(searchBar.text ?? "", immediately: true)
        searchBar.resignFirstResponder()
    }

    private func render(_ change: RemoteMediaViewModel.Change) {
        switch change {
        case .library:
            if showingAup {
                recalculateLayout()
                collectionView.reloadSections(IndexSet(integer: 0))
            }
        case .reset:
            itemSizes.removeAll()
            collectionView.reloadData()
            collectionView.setContentOffset(.zero, animated: false)
        case .append(let indices):
            recalculateLayout()
            // Preserve previews that are already playing on screen.
            collectionView.insertItems(at: indices.map { IndexPath(item: $0, section: remoteSection) })
            collectionView.collectionViewLayout.invalidateLayout()
        case .status:
            if showingAup && items.isEmpty { collectionView.reloadSections(IndexSet(integer: remoteSection)) }
        }
        emptyLabel.text = viewModel.hasError ? AxooOverlayCopy.mediaNetworkError : AxooOverlayCopy.mediaEmpty
        emptyLabel.isHidden = showingAup || (!viewModel.hasError && (!items.isEmpty || viewModel.isLoading))
    }

    // Dimensions cache for UICollectionView
    private var itemSizes: [[CGSize]] = []
    
    private func recalculateLayout() {
        let width = collectionView.bounds.width
        itemSizes = (0..<(showingAup ? 3 : 1)).map { section in
            buildJustifiedMediaRows(items: media(in: section), contentWidth: width, spacing: 2,
                targetRowHeight: 112, maximumItemsPerRow: 6).flatMap { row in
                    row.widthsPx.map { CGSize(width: $0, height: row.heightPx) }
                }
        }
    }


    // UICollectionView
    func numberOfSections(in collectionView: UICollectionView) -> Int { showingAup ? 3 : 1 }
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        media(in: section).count
    }
    func collectionView(_ collectionView: UICollectionView, viewForSupplementaryElementOfKind kind: String, at indexPath: IndexPath) -> UICollectionReusableView {
        if kind == UICollectionView.elementKindSectionHeader {
            let header = collectionView.dequeueReusableSupplementaryView(ofKind: kind, withReuseIdentifier: "section", for: indexPath) as! MediaPickerSectionHeader
            header.label.text = indexPath.section == 0 ? "I tuoi sticker" : (indexPath.section == 1 ? "AUP" : "KLIPY")
            return header
        }
        let status = collectionView.dequeueReusableSupplementaryView(ofKind: kind, withReuseIdentifier: "status", for: indexPath) as! MediaPickerStatus
        status.configure(loading: viewModel.isLoading, error: viewModel.hasError)
        status.onRetry = { [weak self] in self?.viewModel.loadNextPage() }
        return status
    }
    func collectionView(_ collectionView: UICollectionView, layout: UICollectionViewLayout, referenceSizeForHeaderInSection section: Int) -> CGSize {
        showingAup ? CGSize(width: collectionView.bounds.width, height: 40) : .zero
    }
    func collectionView(_ collectionView: UICollectionView, layout: UICollectionViewLayout, referenceSizeForFooterInSection section: Int) -> CGSize {
        showingAup && section == remoteSection && items.isEmpty
            ? CGSize(width: collectionView.bounds.width, height: viewModel.isLoading ? 120 : 80) : .zero
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        collectionView.dequeueReusableCell(withReuseIdentifier: showingAup && indexPath.section == 0 && indexPath.item == 0 ? "create" : "Cell", for: indexPath)
    }

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        guard let media = media(at: indexPath) else { return }
        (cell as? RemoteMediaCell)?.configure(media)
        (cell as? RemoteMediaCell)?.setPreviewPaused(previewVisible)
        if indexPath.section == remoteSection && indexPath.item >= items.count - 6 { viewModel.loadNextPage() }
    }

    func collectionView(_ collectionView: UICollectionView, didEndDisplaying cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        if !collectionView.visibleCells.contains(cell) { (cell as? RemoteMediaCell)?.stop() }
    }
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        if indexPath.section < itemSizes.count, indexPath.item < itemSizes[indexPath.section].count {
            return itemSizes[indexPath.section][indexPath.item]
        }
        return CGSize(width: 100, height: 100)
    }
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        guard let media = media(at: indexPath) else { return }
        if showingAup && indexPath.section == 0 && indexPath.item == 0 { onCreateSticker(); return }
        viewModel.rememberSelection(media)
        onSelect?(media)
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        for case let cell as RemoteMediaCell in collectionView.visibleCells { cell.updatePlayback() }
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        // Recalculate if width changes
        if collectionView.bounds.width > 0 && (layoutWidth != collectionView.bounds.width || itemSizes.count != (showingAup ? 3 : 1) || itemSizes.last?.count != items.count) {
            layoutWidth = collectionView.bounds.width
            recalculateLayout()
            collectionView.collectionViewLayout.invalidateLayout()
        }
    }
    
    func stop() {
        holdPreview.dismiss()
        viewModel.stop()
        for case let cell as RemoteMediaCell in collectionView.visibleCells { cell.stop() }
    }

}

final class RemoteMediaCell: UICollectionViewCell {
    var decodedPreview: UIImage? { imageView.decodedImage }
    private let imageView = PostMediaImageView()
    private var currentMedia: PostEditorMedia?

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.backgroundColor = AxooTheme.background
        contentView.clipsToBounds = true
        imageView.clipsToBounds = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            imageView.topAnchor.constraint(equalTo: contentView.topAnchor),
            imageView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }
    
    override func prepareForReuse() {
        super.prepareForReuse()
        stop()
    }

    func stop() {
        imageView.clear()
        contentView.layer.cornerRadius = 0
        currentMedia = nil
    }

    func configure(_ media: PostEditorMedia) {
        guard media != currentMedia else { return }
        currentMedia = media
        imageView.contentMode = media.kind == .gif ? .scaleAspectFill : .scaleAspectFit
        contentView.layer.cornerRadius = media.provider == "axoo" ? 12 : 0
        imageView.load(media.previewURL, previewURL: media.loadingPreviewURL, maximumPixelSize: 240)
    }

    func setPreviewPaused(_ paused: Bool) { imageView.playbackEnabled = !paused; imageView.updatePlayback() }
    func updatePlayback() { imageView.updatePlayback() }
}

/// The shared overlay owns this content view; removing it cancels decoding and playback.
@MainActor
private final class RemoteMediaPreviewContent: UIView {
    private let image = PostMediaImageView()
    private let url: URL?
    private let decoded: UIImage?
    private var loadTask: Task<Void, Never>?
    init(url: String, decoded: UIImage?) {
        self.url = URL(string: url)
        self.decoded = decoded
        super.init(frame: .zero)
        image.contentMode = .scaleAspectFit
        addSubview(image)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    deinit { loadTask?.cancel() }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        loadTask?.cancel()
        if window == nil { image.display(nil); return }
        if let decoded { image.display(decoded); return }
        guard let url else { return }
        loadTask = Task { [weak self] in
            let decoded = await PostMediaImage.load(url, maximumPixelSize: 600)
            guard !Task.isCancelled else { return }
            self?.image.display(decoded)
        }
    }
    override func layoutSubviews() { super.layoutSubviews(); image.frame = bounds }
}

private final class MediaPickerSectionHeader: UICollectionReusableView {
    let label = UILabel.axoo(font: AxooTypography.emojiCategory, color: AxooTheme.textSecondary)
    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(label)
        label.pinEdges(to: self, insets: UIEdgeInsets(top: 6, left: 12, bottom: 10, right: 12))
    }
    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
}

private final class MediaPickerStatus: UICollectionReusableView {
    var onRetry: () -> Void = {}
    private let button = UIButton(type: .system)
    private let spinner = UIActivityIndicatorView(style: .medium)
    override init(frame: CGRect) {
        super.init(frame: frame)
        button.titleLabel?.font = AxooTypography.confirmationAction
        button.titleLabel?.numberOfLines = 0
        button.titleLabel?.textAlignment = .center
        button.tintColor = AxooTheme.textSecondary
        button.addAction(UIAction { [weak self] _ in self?.onRetry() }, for: .touchUpInside)
        addSubview(button)
        button.pinEdges(to: self, insets: UIEdgeInsets(top: 16, left: 20, bottom: 16, right: 20))
        addSubview(spinner)
        spinner.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: centerYAnchor),
        ])
    }
    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(loading: Bool, error: Bool) {
        button.isHidden = loading
        button.isEnabled = error
        button.setTitle(error ? AxooOverlayCopy.mediaNetworkError : AxooOverlayCopy.mediaEmpty, for: .normal)
        if loading { spinner.startAnimating() } else { spinner.stopAnimating() }
    }
}

private final class CreateStickerCell: UICollectionViewCell {
    override init(frame: CGRect) {
        super.init(frame: frame)
        let icon = AxooIconView(icon: .sticker)
        icon.tintColor = AxooTheme.textSecondary
        let label = UILabel.axoo("Crea sticker", font: AxooTypography.button.withSize(14), color: AxooTheme.textSecondary)
        label.textAlignment = .center
        let stack = UIStackView(arrangedSubviews: [icon, label])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 8
        contentView.backgroundColor = AxooTheme.surface
        contentView.layer.cornerRadius = 16
        contentView.addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            icon.widthAnchor.constraint(equalToConstant: 32), icon.heightAnchor.constraint(equalToConstant: 32),
            stack.centerXAnchor.constraint(equalTo: contentView.centerXAnchor), stack.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            stack.widthAnchor.constraint(lessThanOrEqualTo: contentView.widthAnchor, constant: -8)
        ])
        accessibilityLabel = "Crea sticker"
        accessibilityTraits = .button
    }
    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
}
