import UIKit

/// Normalized geometry from Android AxooMascotCanvasPainter; no bitmap or emoji assets.
enum AxooMascotCanvasPainter {
    static func draw(in context: CGContext, bounds: CGRect, gillPhaseDegrees: CGFloat? = nil, squash: CGFloat = 0) {
        let size = min(bounds.width, bounds.height)
        guard size > 0 else { return }
        context.saveGState()
        context.translateBy(x: bounds.midX - size / 2, y: bounds.midY - size / 2)
        context.scaleBy(x: size, y: size)
        context.setLineCap(.round)
        context.setLineJoin(.round)
        let gills: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
            (0.365, 0.345, 0.255, 0.165), (0.635, 0.345, 0.745, 0.165),
            (0.765, 0.460, 0.860, 0.410), (0.795, 0.590, 0.865, 0.600),
            (0.205, 0.590, 0.135, 0.600), (0.235, 0.460, 0.140, 0.410)
        ]
        for (index, gill) in gills.enumerated() {
            let pulse = gillPhaseDegrees.map { (cos((CGFloat(index) / 6 - $0 / 360) * .pi * 2) + 1) / 2 } ?? 1
            context.setStrokeColor(UIColor.white.withAlphaComponent((158 + pulse * 97) / 255).cgColor)
            context.setLineWidth(0.118 + pulse * 0.014)
            context.move(to: CGPoint(x: gill.0, y: gill.1)); context.addLine(to: CGPoint(x: gill.2, y: gill.3)); context.strokePath()
        }
        context.translateBy(x: 0.5, y: 0.53)
        context.scaleBy(x: 1 - squash * 0.035, y: 1 + squash * 0.055)
        context.translateBy(x: -0.5, y: -0.53)
        let head = CGMutablePath()
        head.move(to: CGPoint(x: 0.5, y: 0.242))
        let curves: [[CGFloat]] = [
            [0.670,0.242,0.775,0.315,0.805,0.455], [0.832,0.575,0.810,0.690,0.735,0.760],
            [0.670,0.815,0.585,0.825,0.500,0.825], [0.415,0.825,0.330,0.815,0.265,0.760],
            [0.190,0.690,0.168,0.575,0.195,0.455], [0.225,0.315,0.330,0.242,0.500,0.242]
        ]
        for c in curves { head.addCurve(to: CGPoint(x: c[4], y: c[5]), control1: CGPoint(x: c[0], y: c[1]), control2: CGPoint(x: c[2], y: c[3])) }
        head.closeSubpath()
        context.setFillColor(UIColor.white.cgColor); context.addPath(head); context.fillPath()
        let ink = UIColor(rgb: 0x111014)
        context.setFillColor(ink.cgColor)
        for x: CGFloat in [0.305, 0.585] {
            context.addPath(CGPath(roundedRect: CGRect(x: x, y: 0.432, width: 0.110, height: 0.186), cornerWidth: 0.055, cornerHeight: 0.055, transform: nil))
            context.fillPath()
        }
        for x: CGFloat in [0.345, 0.625] {
            context.saveGState()
            context.translateBy(x: x, y: 0.478); context.rotate(by: 20 * .pi / 180)
            context.setFillColor(UIColor.white.cgColor)
            context.fillEllipse(in: CGRect(x: -0.017, y: -0.030, width: 0.034, height: 0.060))
            context.restoreGState()
        }
        context.setStrokeColor(ink.cgColor); context.setLineWidth(0.045)
        context.move(to: CGPoint(x: 0.390, y: 0.660))
        context.addCurve(to: CGPoint(x: 0.610, y: 0.660), control1: CGPoint(x: 0.445, y: 0.725), control2: CGPoint(x: 0.555, y: 0.725))
        context.strokePath()
        context.restoreGState()
    }
}
