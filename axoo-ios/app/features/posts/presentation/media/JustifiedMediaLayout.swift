import Foundation
import CoreGraphics

struct JustifiedMediaRow {
    let items: [PostEditorMedia]
    let widthsPx: [CGFloat]
    let heightPx: CGFloat
}

func buildJustifiedMediaRows(
    items: [PostEditorMedia],
    contentWidth: CGFloat,
    spacing: CGFloat,
    targetRowHeight: CGFloat,
    maximumItemsPerRow: Int
) -> [JustifiedMediaRow] {
    if items.isEmpty || contentWidth <= 0 { return [] }
    
    var rows: [JustifiedMediaRow] = []
    var cursor = 0
    
    while cursor < items.count {
        var rowItems: [PostEditorMedia] = []
        var ratios: [CGFloat] = []
        var expectedWidth: CGFloat = 0
        
        while cursor < items.count && rowItems.count < maximumItemsPerRow {
            let item = items[cursor]
            rowItems.append(item)
            let ratio = item.layoutAspectRatio()
            ratios.append(ratio)
            expectedWidth = ratios.reduce(0, +) * targetRowHeight + spacing * CGFloat(ratios.count - 1)
            cursor += 1
            if expectedWidth >= contentWidth { break }
        }
        
        let isLastRow = cursor == items.count
        let availableWidth = Swift.max(1, contentWidth - spacing * CGFloat(rowItems.count - 1))
        let naturalHeight = round(availableWidth / Swift.max(0.1, ratios.reduce(0, +)))
        
        let height = (isLastRow && expectedWidth < contentWidth) ? targetRowHeight : Swift.max(1, naturalHeight)
        
        let naturalWidths = ratios.map { Swift.max(1, round($0 * height)) }
        let shouldFillRow = !isLastRow || expectedWidth >= contentWidth || naturalWidths.reduce(0, +) > availableWidth
        
        let finalWidths = shouldFillRow ? naturalWidths.fitExactly(targetWidth: availableWidth) : naturalWidths
        rows.append(JustifiedMediaRow(items: rowItems, widthsPx: finalWidths, heightPx: height))
    }
    return rows
}

private extension PostEditorMedia {
    func layoutAspectRatio() -> CGFloat {
        if width > 0 && height > 0 {
            return Swift.min(2.4, Swift.max(0.55, CGFloat(width) / CGFloat(height)))
        }
        return 1.0
    }
}

private extension Array where Element == CGFloat {
    func fitExactly(targetWidth: CGFloat) -> [CGFloat] {
        if isEmpty { return self }
        let sourceWidth = Swift.max(1, reduce(0, +))
        var usedWidth: CGFloat = 0
        
        return enumerated().map { index, width in
            if index == count - 1 {
                return Swift.max(1, targetWidth - usedWidth)
            } else {
                let fitted = Swift.max(1, round(width * targetWidth / sourceWidth))
                usedWidth += fitted
                return fitted
            }
        }
    }
}
