import CoreGraphics
@testable import OChords

/// ScanTextLine fixtures for ChordSheetParser tests (normalised page coordinates, origin top-left).
enum TestScan {
    /// A text line at `y`: each word at its x, as wide as its letters.
    static func line(
        y: CGFloat,
        h: CGFloat = 0.03,
        words: [(String, CGFloat)],
        confidence: Float = 0.95
    ) -> ScanTextLine {
        ScanTextLine(
            words: words.map { text, x in
                // Match Android fixtures: width from UTF-16 length (Kotlin String.length), not grapheme count.
                ScanWord(text: text, box: CGRect(x: x, y: y, width: 0.02 * CGFloat(text.utf16.count), height: h))
            },
            confidence: confidence
        )
    }
}
