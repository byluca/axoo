import UIKit

final class EventDetailHost: AxooFormController {
    private let eventID: String
    private let viewModel: EventsViewModel
    private let card = EventCardView(detail: true)
    private let descriptionView = UITextView()
    private let end = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    private lazy var maps = UIButton.axoo("") { [weak self] in self?.openMaps() }
    private let status = UILabel.axoo(font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
    private let body = UIStackView()
    private var rendered: CityEvent?

    init(eventID: String, viewModel: EventsViewModel) {
        self.eventID = eventID; self.viewModel = viewModel; super.init()
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Evento"; installArrowBackButton(testID: "event-back")
        body.axis = .vertical; body.spacing = 16
        [card, end, maps, descriptionView].forEach { body.addArrangedSubview($0) }
        stack.addArrangedSubview(status); stack.addArrangedSubview(body)
        status.textAlignment = .center
        descriptionView.font = AxooTypography.postBody; descriptionView.textColor = AxooTheme.textPrimary
        descriptionView.backgroundColor = .clear; descriptionView.isEditable = false
        descriptionView.isScrollEnabled = false; descriptionView.textContainerInset = .zero
        descriptionView.textContainer.lineFragmentPadding = 0; descriptionView.dataDetectorTypes = [.link, .phoneNumber]
        maps.contentHorizontalAlignment = .leading
        status.isUserInteractionEnabled = true
        status.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(retry)))
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        viewModel.onChange = { [weak self] in self?.render() }
        viewModel.open(id: eventID)
    }
    private func render() {
        guard isViewLoaded else { return }
        let event = viewModel.detail.flatMap { $0.id == eventID ? $0 : nil }
        body.isHidden = event == nil; status.isHidden = event != nil
        status.text = viewModel.detailError ? "Evento non disponibile. Tocca per riprovare." : "Caricamento evento…"
        if let event, rendered != event {
            rendered = event; card.configure(event)
            descriptionView.text = event.description
            end.text = "Fine: \(CityEvent.formattedDate(event.endsAt))"
            maps.configuration?.title = "\(event.address)\nApri nelle mappe ↗"
            maps.configuration?.titleLineBreakMode = .byWordWrapping
        }
    }
    @objc private func retry() { if viewModel.detailError { viewModel.open(id: eventID) } }
    private func openMaps() {
        guard let event = rendered else { return }
        var link = URLComponents(string: "https://www.google.com/maps/search/")!
        link.queryItems = [URLQueryItem(name: "api", value: "1"), URLQueryItem(name: "query", value: "\(event.venue), \(event.address)")]
        if let url = link.url { UIApplication.shared.open(url) }
    }
}
