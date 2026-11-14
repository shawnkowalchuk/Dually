import CoreText
import DuallyShared
import UIKit

// The report's PDF on the iPhone (phase 4g): the second of its two passes, as Android's
// ReportPdf.kt. The shared code lays every page out (core/report/ReportLayout.kt), measuring each
// line with ReportFonts, the very fonts that then draw it here, so a line measured to fit a column
// fits it on the page. A page is US Letter in points; a coordinate there is one on the paper here.

/// The report's fonts, one for each kind of text: Archivo at the weight and width Android sets
/// (its variable font's axes), and IBM Plex Mono for figures. The shared layout asks it how wide a
/// text is.
final class ReportFonts: NSObject, TextMeasure {
    private var cache: [ReportTextStyle: UIFont] = [:]

    func width(text: String, style: ReportTextStyle) -> Float {
        Float((text as NSString).size(withAttributes: [.font: font(style)]).width)
    }

    func font(_ style: ReportTextStyle) -> UIFont {
        if let known = cache[style] { return known }
        let size = CGFloat(style.size)
        let made: UIFont
        switch style.face {
        case ReportFace.figures:
            made = UIFont(name: "IBMPlexMono-SemiBold", size: size) ?? .monospacedDigitSystemFont(ofSize: size, weight: .semibold)
        case ReportFace.headline:
            made = Self.archivo(size: size, weight: CGFloat(style.weight), width: 112)
        default:
            made = Self.archivo(size: size, weight: CGFloat(style.weight), width: 100)
        }
        cache[style] = made
        return made
    }

    /// Archivo with its "wght" and "wdth" axes set, as Android sets them on its variable font.
    private static func archivo(size: CGFloat, weight: CGFloat, width: CGFloat) -> UIFont {
        let weightAxis = 0x7767_6874 // 'wght'
        let widthAxis = 0x7764_7468 // 'wdth'
        let descriptor = UIFontDescriptor(fontAttributes: [
            .name: "Archivo",
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [
                weightAxis: weight,
                widthAxis: width,
            ],
        ])
        return UIFont(descriptor: descriptor, size: size)
    }
}

enum ReportPdf {
    /// Draws [pages] into PDF data.
    static func data(_ pages: [ReportPage], fonts: ReportFonts) -> Data {
        let size = CGSize(width: Int(ReportPageKt.REPORT_PAGE_WIDTH), height: Int(ReportPageKt.REPORT_PAGE_HEIGHT))
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: size))
        return renderer.pdfData { context in
            for page in pages {
                context.beginPage()
                for item in page.items {
                    draw(item, fonts: fonts, in: context.cgContext)
                }
            }
        }
    }

    private static func draw(_ item: PageItem, fonts: ReportFonts, in context: CGContext) {
        if let text = item as? PageItemText {
            let font = fonts.font(text.style)
            let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color(text.ink)]
            let string = text.text as NSString
            let width = string.size(withAttributes: attributes).width
            let x = CGFloat(text.x) - (text.rightAligned ? width : 0)
            string.draw(at: CGPoint(x: x, y: CGFloat(text.baseline) - font.ascender), withAttributes: attributes)
        } else if let rule = item as? PageItemRule {
            context.setStrokeColor(color(rule.ink).cgColor)
            context.setLineWidth(CGFloat(rule.thickness))
            context.move(to: CGPoint(x: CGFloat(rule.fromX), y: CGFloat(rule.y)))
            context.addLine(to: CGPoint(x: CGFloat(rule.toX), y: CGFloat(rule.y)))
            context.strokePath()
        } else if let box = item as? PageItemBox {
            let rect = CGRect(x: CGFloat(box.left), y: CGFloat(box.top),
                              width: CGFloat(box.right - box.left), height: CGFloat(box.bottom - box.top))
            context.setFillColor(color(box.ink).cgColor)
            context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: CGFloat(box.radius)).cgPath)
            context.fillPath()
        } else if let mark = item as? PageItemMarkLetter {
            let rect = CGRect(x: CGFloat(mark.left), y: CGFloat(mark.top),
                              width: CGFloat(mark.right - mark.left), height: CGFloat(mark.bottom - mark.top))
            context.setFillColor(color(mark.ink).cgColor)
            context.addPath(markLetter(in: rect))
            context.fillPath(using: .evenOdd)
        }
    }

    /// The mark's "D" filling [rect]: the letter and its counter, each a stem and half an
    /// ellipse, as website/mark.svg and Android's report draw it.
    private static func markLetter(in rect: CGRect) -> CGPath {
        let path = CGMutablePath()
        let scaleX = rect.width / 72
        let scaleY = rect.height / 76
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.midX + x * scaleX, y: rect.midY + y * scaleY)
        }
        for (left, radiusX, radiusY) in [(CGFloat(-36), CGFloat(36), CGFloat(38)), (-18, 18, 20)] {
            path.move(to: point(left, -radiusY))
            let steps = 48
            for step in 0...steps {
                let angle = -CGFloat.pi / 2 + CGFloat.pi * CGFloat(step) / CGFloat(steps)
                path.addLine(to: point(radiusX * cos(angle), radiusY * sin(angle)))
            }
            path.addLine(to: point(left, radiusY))
            path.closeSubpath()
        }
        return path
    }

    private static func color(_ ink: ReportInk) -> UIColor {
        let argb = UInt32(bitPattern: ink.argb)
        return UIColor(
            red: CGFloat((argb >> 16) & 0xFF) / 255,
            green: CGFloat((argb >> 8) & 0xFF) / 255,
            blue: CGFloat(argb & 0xFF) / 255,
            alpha: CGFloat((argb >> 24) & 0xFF) / 255
        )
    }
}
