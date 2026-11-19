import UIKit

final class StickerCropView: UIView, UIScrollViewDelegate {
    private let scroll = UIScrollView()
    private let imageView = UIImageView()
    private let guide = StickerCropGuide()
    private var previousSize: CGSize = .zero
    var image: UIImage? {
        didSet {
            scroll.zoomScale = 1
            imageView.image = image
            imageView.frame = CGRect(origin: .zero, size: image?.size ?? .zero)
            scroll.contentSize = image?.size ?? .zero
            previousSize = .zero
            setNeedsLayout()
        }
    }
    override init(frame: CGRect) {
        super.init(frame: frame)
        scroll.delegate = self
        scroll.showsHorizontalScrollIndicator = false
        scroll.showsVerticalScrollIndicator = false
        scroll.bounces = false
        scroll.bouncesZoom = false
        scroll.contentInsetAdjustmentBehavior = .never
        backgroundColor = UIColor(rgb: 0x16161A)
        layer.cornerRadius = 20
        clipsToBounds = true
        scroll.clipsToBounds = false
        scroll.addSubview(imageView)
        addSubview(scroll)
        addSubview(guide)
        accessibilityLabel = "Ritaglio dello sticker"
    }
    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override func layoutSubviews() {
        super.layoutSubviews()
        let side = max(0, min(bounds.width, bounds.height) - 32)
        scroll.frame = CGRect(x: (bounds.width-side)/2, y: (bounds.height-side)/2, width: side, height: side)
        guide.frame = bounds
        guide.crop = scroll.frame
        guide.isHidden = image == nil
        guard let image, side > 0, previousSize != scroll.bounds.size else { return }
        previousSize = scroll.bounds.size
        let scale = max(side/image.size.width, side/image.size.height)
        scroll.minimumZoomScale = scale
        scroll.maximumZoomScale = scale*4
        scroll.zoomScale = scale
        scroll.contentOffset = CGPoint(x: max(0, (image.size.width*scale-side)/2), y: max(0, (image.size.height*scale-side)/2))
    }
    func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }
    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) { guide.showGrid = true }
    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) { if !decelerate { guide.showGrid = false } }
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) { guide.showGrid = false }
    func scrollViewWillBeginZooming(_ scrollView: UIScrollView, with view: UIView?) { guide.showGrid = true }
    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) { guide.showGrid = false }

    func croppedPNG() -> Data? {
        guard let image, scroll.bounds.width > 0 else { return nil }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        format.preferredRange = .standard
        guard let source = image.cgImage else { return nil }
        let side = min(source.width, source.height, max(1, Int((scroll.bounds.width/scroll.zoomScale).rounded())))
        let x = min(source.width-side, max(0, Int((scroll.contentOffset.x/scroll.zoomScale).rounded())))
        let y = min(source.height-side, max(0, Int((scroll.contentOffset.y/scroll.zoomScale).rounded())))
        guard let cropped = source.cropping(to: CGRect(x: CGFloat(x), y: CGFloat(y), width: CGFloat(side), height: CGFloat(side))) else { return nil }
        return UIGraphicsImageRenderer(size: CGSize(width: 512, height: 512), format: format).pngData { _ in
            UIImage(cgImage: cropped).draw(in: CGRect(x: 0, y: 0, width: 512, height: 512))
        }
    }
}

/// A drawing overlay only; gestures and the exported PNG belong to the crop view.
private final class StickerCropGuide: UIView {
    var crop: CGRect = .zero { didSet { setNeedsDisplay() } }
    var showGrid = false { didSet { setNeedsDisplay() } }
    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        isOpaque = false
        backgroundColor = .clear
    }
    @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func draw(_ rect: CGRect) {
        guard !crop.isEmpty, let context = UIGraphicsGetCurrentContext() else { return }
        let dim = UIBezierPath(rect: bounds)
        dim.append(UIBezierPath(rect: crop))
        dim.usesEvenOddFillRule = true
        UIColor.black.withAlphaComponent(0.5).setFill()
        dim.fill()
        context.setLineWidth(1)
        context.setStrokeColor(UIColor.white.withAlphaComponent(0.6).cgColor)
        context.stroke(crop)
        if showGrid {
            context.setStrokeColor(UIColor.white.withAlphaComponent(0.4).cgColor)
            for third in 1...2 {
                let x = crop.minX + crop.width * CGFloat(third)/3
                let y = crop.minY + crop.height * CGFloat(third)/3
                context.move(to: CGPoint(x: x, y: crop.minY)); context.addLine(to: CGPoint(x: x, y: crop.maxY))
                context.move(to: CGPoint(x: crop.minX, y: y)); context.addLine(to: CGPoint(x: crop.maxX, y: y))
            }
            context.strokePath()
        }
        context.setLineWidth(2)
        context.setStrokeColor(UIColor.white.cgColor)
        for horizontal in 0...1 { for vertical in 0...1 {
            let x = horizontal == 0 ? crop.minX : crop.maxX
            let y = vertical == 0 ? crop.minY : crop.maxY
            context.move(to: CGPoint(x: x + (horizontal == 0 ? 16 : -16), y: y))
            context.addLine(to: CGPoint(x: x, y: y))
            context.addLine(to: CGPoint(x: x, y: y + (vertical == 0 ? 16 : -16)))
        } }
        context.strokePath()
    }
}
