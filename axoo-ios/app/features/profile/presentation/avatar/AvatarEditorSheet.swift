import UIKit

private enum AvatarCategory: Int, CaseIterable {
    case hair = 0, eyes, brows, mouth, colors, extras
    var title: String {
        switch self {
        case .hair: return "Capelli"
        case .eyes: return "Occhi"
        case .brows: return "Sopracciglia"
        case .mouth: return "Bocca"
        case .colors: return "Colori"
        case .extras: return AxooOverlayCopy.avatarAccessories
        }
    }
    var icon: String {
        switch self {
        case .hair: return "hair"
        case .eyes: return "avatar-category-eyes"
        case .brows: return "eyebrows"
        case .mouth: return "mouth"
        case .colors: return "avatar-category-colors"
        case .extras: return "avatar-category-extras"
        }
    }
}

private struct EditorSection {
    let title: String?
    let key: String
    let items: [String?]
    let isColor: Bool
}

final class AvatarEditorSheet: AxooViewController, UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout {
    private let viewModel: AvatarEditorViewModel
    private var draft: [String: String] { viewModel.draft }
    private var category = AvatarCategory.hair
    private var saving: Bool { viewModel.isSaving }

    private let preview = AnonAvatarView(vector: true)
    private let categoryControl = SlidingCategoryControl(items: AvatarCategory.allCases.map { $0.title })
    private var collectionView: UICollectionView!
    private var collectionWidth: CGFloat = 0
    
    private let saveButton = UIButton(type: .system)
    private let closeButton = UIButton(type: .system)
    private let errorLabel = UILabel.axoo(color: .systemRed)
    
    private var sections: [EditorSection] = []
    
    init(identity: AvatarIdentity, onSave: @escaping ([String: String]) -> Void) {
        self.viewModel = AvatarEditorViewModel(identity: identity, onSave: onSave)
        super.init()
        viewModel.onChange = { [weak self] in if self?.isViewLoaded == true { self?.refreshUI() } }
        modalPresentationStyle = .pageSheet
        isModalInPresentation = true
        if let sheet = sheetPresentationController {
            sheet.preferredCornerRadius = AxooSheetStyle.cornerRadius
            let customDetent = UISheetPresentationController.Detent.custom { context in
                return context.maximumDetentValue * 0.85
            }
            sheet.detents = [customDetent]
            sheet.prefersGrabberVisible = false
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AxooTheme.background
        
        let header = UIView()
        header.translatesAutoresizingMaskIntoConstraints = false
        closeButton.setImage(UIImage(named: "icon-close")?.withRenderingMode(.alwaysTemplate), for: .normal)
        closeButton.tintColor = .label
        closeButton.addTarget(self, action: #selector(dismissWithoutSaving), for: .touchUpInside)
        
        saveButton.setTitle(AxooOverlayCopy.save, for: .normal)
        saveButton.titleLabel?.font = UIFont(name: "Baloo2-Bold", size: 16)
        saveButton.setTitleColor(.white, for: .normal)
        saveButton.backgroundColor = AxooTheme.accent
        saveButton.layer.cornerRadius = 8
        saveButton.addTarget(self, action: #selector(saveTapped), for: .touchUpInside)
        
        header.addSubview(closeButton)
        header.addSubview(saveButton)
        [closeButton, saveButton].forEach { $0.translatesAutoresizingMaskIntoConstraints = false }
        
        preview.translatesAutoresizingMaskIntoConstraints = false
        
        categoryControl.translatesAutoresizingMaskIntoConstraints = false
        categoryControl.accessibilityIdentifier = "avatar-categories"
        categoryControl.addTarget(self, action: #selector(categoryTapped(_:)), for: .valueChanged)
        
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 8
        layout.minimumLineSpacing = 8
        layout.sectionInset = UIEdgeInsets(top: 16, left: 20, bottom: 16, right: 20)
        layout.headerReferenceSize = CGSize(width: 0, height: 40)
        
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.showsVerticalScrollIndicator = false
        collectionView.backgroundColor = .clear
        collectionView.delegate = self
        collectionView.dataSource = self
        collectionView.register(AssetChoiceCell.self, forCellWithReuseIdentifier: "Asset")
        collectionView.register(ColorChoiceCell.self, forCellWithReuseIdentifier: "Color")
        collectionView.register(TextChoiceCell.self, forCellWithReuseIdentifier: "Text")
        collectionView.register(SectionHeaderView.self, forSupplementaryViewOfKind: UICollectionView.elementKindSectionHeader, withReuseIdentifier: "Header")
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        
        errorLabel.isHidden = true
        errorLabel.textAlignment = .center
        errorLabel.font = UIFont(name: "Baloo2-SemiBold", size: 13)
        errorLabel.translatesAutoresizingMaskIntoConstraints = false
        
        [header, preview, categoryControl, errorLabel, collectionView].forEach { view.addSubview($0) }
        
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            header.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            header.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            header.heightAnchor.constraint(equalToConstant: 44),
            
            closeButton.leadingAnchor.constraint(equalTo: header.leadingAnchor),
            closeButton.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            closeButton.widthAnchor.constraint(equalToConstant: 44),
            closeButton.heightAnchor.constraint(equalToConstant: 44),
            
            saveButton.trailingAnchor.constraint(equalTo: header.trailingAnchor),
            saveButton.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            saveButton.widthAnchor.constraint(equalToConstant: 80),
            saveButton.heightAnchor.constraint(equalToConstant: 40),
            
            preview.topAnchor.constraint(equalTo: header.bottomAnchor, constant: 4),
            preview.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            preview.widthAnchor.constraint(equalToConstant: 176),
            preview.heightAnchor.constraint(equalToConstant: 176),
            
            categoryControl.topAnchor.constraint(equalTo: preview.bottomAnchor, constant: 8),
            categoryControl.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            categoryControl.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            categoryControl.heightAnchor.constraint(equalToConstant: 44),
            
            errorLabel.topAnchor.constraint(equalTo: categoryControl.bottomAnchor, constant: 8),
            errorLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            errorLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            
            collectionView.topAnchor.constraint(equalTo: errorLabel.bottomAnchor, constant: 8),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        refreshUI()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        preview.loadIfNeeded()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if collectionWidth != collectionView.bounds.width {
            collectionWidth = collectionView.bounds.width
            collectionView.collectionViewLayout.invalidateLayout()
        }
    }

    @objc private func dismissWithoutSaving() {
        if !saving { dismiss(animated: true) }
    }
    
    @objc private func saveTapped() {
        errorLabel.isHidden = true
        viewModel.save()
    }

    func showSaveError() {
        errorLabel.text = AxooOverlayCopy.avatarSaveError
        errorLabel.isHidden = false
        viewModel.saveFailed()
    }
    
    @objc private func categoryTapped(_ sender: SlidingCategoryControl) {
        if saving { return }
        guard let cat = AvatarCategory(rawValue: sender.selectedSegmentIndex) else { return }
        category = cat
        errorLabel.isHidden = true
        refreshUI()
    }
    
    private func refreshUI() {
        preview.configure(viewModel.preview)
        
        categoryControl.selectedSegmentIndex = category.rawValue
        categoryControl.isEnabled = !saving
        for cat in AvatarCategory.allCases {
            let image = UIImage(named: cat.icon).map { original in
                UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24)).image { _ in
                    original.draw(in: CGRect(x: 0, y: 0, width: 24, height: 24))
                }.withTintColor(cat == category ? AxooTheme.accent : AxooTheme.textPrimary, renderingMode: .alwaysOriginal)
            }
            categoryControl.setImage(image, forSegmentAt: cat.rawValue)
        }
        
        let changed = viewModel.hasChanges
        saveButton.backgroundColor = changed && !saving ? AxooTheme.accent : AxooTheme.surface
        saveButton.setTitleColor(changed && !saving ? .white : .secondaryLabel, for: .normal)
        saveButton.isEnabled = changed && !saving
        closeButton.isEnabled = !saving
        closeButton.alpha = saving ? 0.45 : 1.0
        
        switch category {
        case .hair:
            sections = [EditorSection(title: nil, key: "hairVariant", items: AvatarIdentity.options["hairVariant"] ?? [], isColor: false)]
        case .eyes:
            sections = [EditorSection(title: nil, key: "eyesVariant", items: AvatarIdentity.options["eyesVariant"] ?? [], isColor: false)]
        case .brows:
            sections = [EditorSection(title: nil, key: "eyebrowsVariant", items: AvatarIdentity.options["eyebrowsVariant"] ?? [], isColor: false)]
        case .mouth:
            sections = [EditorSection(title: nil, key: "mouthVariant", items: AvatarIdentity.options["mouthVariant"] ?? [], isColor: false)]
        case .colors:
            sections = [
                EditorSection(title: "Colore dei capelli", key: "hairColor", items: AvatarIdentity.options["hairColor"] ?? [], isColor: true),
                EditorSection(title: "Tonalità della pelle", key: "skinColor", items: AvatarIdentity.options["skinColor"] ?? [], isColor: true),
                EditorSection(title: "Sfondo", key: "backgroundColor", items: AvatarIdentity.options["backgroundColor"] ?? [], isColor: true)
            ]
        case .extras:
            sections = [
                EditorSection(title: "Occhiali", key: "glassesVariant", items: [nil] + (AvatarIdentity.options["glassesVariant"] ?? []), isColor: false),
                EditorSection(title: "Orecchini", key: "earringsVariant", items: [nil] + (AvatarIdentity.options["earringsVariant"] ?? []), isColor: false),
                EditorSection(title: "Dettagli", key: "detailsVariant", items: [nil] + (AvatarIdentity.options["detailsVariant"] ?? []), isColor: false)
            ]
        }
        collectionView.reloadData()
        collectionView.alpha = saving ? 0.55 : 1.0
    }
    
    func numberOfSections(in collectionView: UICollectionView) -> Int {
        return sections.count
    }
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return sections[section].items.count
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let layout = collectionViewLayout as! UICollectionViewFlowLayout
        let insets = collectionView.adjustedContentInset
        let width = collectionView.bounds.width - insets.left - insets.right
            - layout.sectionInset.left - layout.sectionInset.right - 2 * layout.minimumInteritemSpacing
        let side = max(1, floor(width / 3))
        return CGSize(width: side, height: side)
    }
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, referenceSizeForHeaderInSection section: Int) -> CGSize {
        return sections[section].title == nil ? .zero : CGSize(width: collectionView.frame.width, height: 40)
    }
    
    func collectionView(_ collectionView: UICollectionView, viewForSupplementaryElementOfKind kind: String, at indexPath: IndexPath) -> UICollectionReusableView {
        let view = collectionView.dequeueReusableSupplementaryView(ofKind: kind, withReuseIdentifier: "Header", for: indexPath) as! SectionHeaderView
        view.titleLabel.text = sections[indexPath.section].title
        return view
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let section = sections[indexPath.section]
        let value = section.items[indexPath.item]
        let isSelected = (value == nil && draft[section.key] == nil) || (draft[section.key] == value)
        
        if section.isColor {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "Color", for: indexPath) as! ColorChoiceCell
            cell.configure(hex: value ?? "", selected: isSelected)
            return cell
        }
        if let val = value {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "Asset", for: indexPath) as! AssetChoiceCell
            let folder: String
            switch section.key {
            case "hairVariant": folder = "hair"
            case "eyesVariant": folder = "eyes"
            case "eyebrowsVariant": folder = "eyebrows"
            case "mouthVariant": folder = "mouth"
            case "glassesVariant": folder = "glasses"
            case "earringsVariant": folder = "earrings"
            default: folder = "features"
            }
            cell.configure(asset: "avatar-\(folder)-\(val)", selected: isSelected)
            return cell
        } else {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "Text", for: indexPath) as! TextChoiceCell
            cell.configure(text: "Nessuno", selected: isSelected)
            return cell
        }
    }
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        if saving { return }
        let section = sections[indexPath.section]
        let value = section.items[indexPath.item]
        
        viewModel.select(section.key, value: value)
        errorLabel.isHidden = true
        refreshUI()
    }
}

class SectionHeaderView: UICollectionReusableView {
    let titleLabel = UILabel.axoo(color: .secondaryLabel)
    override init(frame: CGRect) {
        super.init(frame: frame)
        titleLabel.font = UIFont(name: "Baloo2-Bold", size: 13)
        addSubview(titleLabel)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            titleLabel.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4)
        ])
    }
    required init?(coder: NSCoder) { fatalError() }
}

class ChoiceCell: UICollectionViewCell {
    let container = UIView()
    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.addSubview(container)
        container.translatesAutoresizingMaskIntoConstraints = false
        container.layer.cornerRadius = 8
        container.layer.borderWidth = 1
        NSLayoutConstraint.activate([
            container.topAnchor.constraint(equalTo: contentView.topAnchor),
            container.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            container.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: contentView.trailingAnchor)
        ])
    }
    required init?(coder: NSCoder) { fatalError() }
    func setSelectedState(_ selected: Bool) {
        container.backgroundColor = AxooTheme.surface
        container.layer.borderColor = selected ? AxooTheme.accent.cgColor : AxooTheme.border.cgColor
        container.layer.borderWidth = selected ? 2 : 1
    }
}

class AssetChoiceCell: ChoiceCell {
    let imageView = UIImageView()
    override init(frame: CGRect) {
        super.init(frame: frame)
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        container.addSubview(imageView)
        imageView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: container.topAnchor, constant: 4),
            imageView.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -4),
            imageView.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 4),
            imageView.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -4)
        ])
    }
    required init?(coder: NSCoder) { fatalError() }
    func configure(asset: String, selected: Bool) {
        imageView.image = UIImage(named: asset)
        setSelectedState(selected)
    }
}

class ColorChoiceCell: ChoiceCell {
    let swatch = UIView()
    override init(frame: CGRect) {
        super.init(frame: frame)
        container.addSubview(swatch)
        swatch.layer.cornerRadius = 8
        swatch.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            swatch.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 9),
            swatch.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -9),
            swatch.topAnchor.constraint(equalTo: container.topAnchor, constant: 9),
            swatch.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -9)
        ])
    }
    required init?(coder: NSCoder) { fatalError() }
    func configure(hex: String, selected: Bool) {
        swatch.backgroundColor = UIColor(hex: "#" + hex)
        setSelectedState(selected)
    }
}

class TextChoiceCell: ChoiceCell {
    let label = UILabel.axoo(color: .secondaryLabel)
    override init(frame: CGRect) {
        super.init(frame: frame)
        label.font = UIFont(name: "Baloo2-Bold", size: 12)
        label.textAlignment = .center
        container.addSubview(label)
        label.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: container.centerYAnchor)
        ])
    }
    required init?(coder: NSCoder) { fatalError() }
    func configure(text: String, selected: Bool) {
        label.text = text
        setSelectedState(selected)
    }
}

private extension UIColor {
    convenience init(hex: String) {
        var cString = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cString.hasPrefix("#") { cString.removeFirst() }
        var rgbValue: UInt64 = 0
        Scanner(string: cString).scanHexInt64(&rgbValue)
        self.init(
            red: CGFloat((rgbValue & 0xFF0000) >> 16) / 255.0,
            green: CGFloat((rgbValue & 0x00FF00) >> 8) / 255.0,
            blue: CGFloat(rgbValue & 0x0000FF) / 255.0,
            alpha: 1.0
        )
    }
}
