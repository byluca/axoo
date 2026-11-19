import UIKit

/// Shared animation curves mirroring Android's AxooInterpolators.
enum AxooInterpolators {
    static var linear: UICubicTimingParameters {
        UICubicTimingParameters(controlPoint1: CGPoint(x: 0.0, y: 0.0), controlPoint2: CGPoint(x: 1.0, y: 1.0))
    }

    static var decelerate: UICubicTimingParameters {
        UICubicTimingParameters(controlPoint1: CGPoint(x: 0.0, y: 0.0), controlPoint2: CGPoint(x: 0.2, y: 1.0))
    }

    static var enter: UICubicTimingParameters {
        UICubicTimingParameters(controlPoint1: CGPoint(x: 0.16, y: 1.0), controlPoint2: CGPoint(x: 0.3, y: 1.0))
    }

    static var exit: UICubicTimingParameters {
        UICubicTimingParameters(controlPoint1: CGPoint(x: 0.4, y: 0.0), controlPoint2: CGPoint(x: 1.0, y: 1.0))
    }

    static var shimmer: UICubicTimingParameters {
        UICubicTimingParameters(controlPoint1: CGPoint(x: 0.25, y: 0.1), controlPoint2: CGPoint(x: 0.25, y: 1.0))
    }
}
