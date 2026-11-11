import Foundation

/// Zawgyi is the older, non-standard Myanmar font encoding many sheets were typed in. It shows as
/// broken text on phones using Unicode. Detection is by encoding patterns that never occur in
/// well-formed Unicode Burmese; conversion is ICU's CLDR "Zawgyi-my" transform (built into iOS).
enum Zawgyi {
    /// Patterns that never occur in well-formed Unicode Burmese: one is enough.
    private static let markers: [NSRegularExpression] = [
        // U+1039 as Zawgyi's killer mark (asat); in Unicode it always stacks the next consonant.
        "\\u1039(?![\\u1000-\\u1021])",
        // ေ or ya-yit typed before the consonant, at the start of a word.
        "(?:^|[^\\u1000-\\u109F])[\\u1031\\u103B]",
    ].map { try! NSRegularExpression(pattern: $0) }

    /// Zawgyi's stacked and variant letter forms — but real Mon/Shan letters in Unicode, so it takes two.
    private static let variantForms = try! NSRegularExpression(pattern: "[\\u1060-\\u1097]")

    /// Signs of well-formed Unicode that Zawgyi text doesn't have.
    private static let unicodeSigns: [NSRegularExpression] = [
        // ှ: Zawgyi writes ha-htoe as U+103D.
        "\\u103E",
        // Kinzi (င်္): Zawgyi has its own kinzi letter.
        "\\u1004\\u103A\\u1039",
        // Asat closing a word; in Zawgyi U+103A is ya-pin, which is followed by the rest of the syllable.
        "[\\u1000-\\u1021]\\u103A(?=[\\s\\u1000-\\u1021]|$)",
    ].map { try! NSRegularExpression(pattern: $0) }

    /// How many Zawgyi patterns `text` has (0 for well-formed Unicode).
    static func markerCount(_ text: String) -> Int {
        let range = NSRange(text.startIndex..., in: text)
        return (markers + [variantForms]).reduce(0) { $0 + $1.numberOfMatches(in: text, range: range) }
    }

    /// Zawgyi when its patterns outweigh the signs of Unicode — so one odd mark in a Unicode page (a reader putting
    /// a space before ေ, say) doesn't send the whole page through the converter and break every line.
    static func isZawgyi(_ text: String) -> Bool {
        let range = NSRange(text.startIndex..., in: text)
        let variants = variantForms.numberOfMatches(in: text, range: range)
        let zawgyi = markers.reduce(0) { $0 + $1.numberOfMatches(in: text, range: range) } + (variants >= 2 ? variants : 0)
        let unicode = unicodeSigns.reduce(0) { $0 + $1.numberOfMatches(in: text, range: range) }
        return zawgyi > unicode
    }

    /// Unicode for Zawgyi text; leaves text without Myanmar letters untouched.
    static func toUnicode(_ text: String) -> String {
        guard text.unicodeScalars.contains(where: { (0x1000...0x109F).contains($0.value) }) else { return text }
        return text.applyingTransform(StringTransform("Zawgyi-my"), reverse: false) ?? text
    }

    /// Converts `text` only if it looks like Zawgyi — for things people type (their keyboard may be Zawgyi).
    static func normalised(_ text: String) -> String {
        isZawgyi(text) ? toUnicode(text) : text
    }
}
