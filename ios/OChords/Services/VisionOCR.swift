import Foundation
import CoreGraphics
import Vision
import CoreText

// On-device OCR. No UIKit here, so the same code runs in the macOS test harness.

/// One line of text. Boxes are normalised with the origin top-left; page n spans y = n…n+1.
struct ScanTextLine: Codable, Hashable {
    var words: [ScanWord]
    var confidence: Float
    var text: String { words.map(\.text).joined(separator: " ") }
    var box: CGRect { words.dropFirst().reduce(words.first?.box ?? .zero) { $0.union($1.box) } }
}

struct ScanWord: Codable, Hashable {
    var text: String
    var box: CGRect
}

protocol OCREngine {
    /// Whether lyrics in Myanmar script come back as text. Vision on device: no.
    var readsMyanmar: Bool { get }
    func recognize(_ image: CGImage) async throws -> [ScanTextLine]
}

/// Placeholder for a word that is on the page but couldn't be read (e.g. Myanmar script).
let unreadableText = "\u{FFFD}"

struct VisionOCREngine: OCREngine {
    var readsMyanmar: Bool { false }

    func recognize(_ image: CGImage) async throws -> [ScanTextLine] {
        try await withCheckedThrowingContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                do { cont.resume(returning: try Self.read(image)) } catch { cont.resume(throwing: error) }
            }
        }
    }

    /// Page-wide text recognition skips lone chord letters and, next to Myanmar script, whole chord rows.
    /// So the page is also cut into rows and words by where the ink is; every word the page pass didn't read
    /// is read again on its own. Words that still don't read stay as placeholders, which keeps the layout.
    static func read(_ image: CGImage) throws -> [ScanTextLine] {
        var image = image
        var observations = try observe(image)
        // Rows of ink are found by scanning straight across, so level a tilted photo first.
        if let angle = skew(observations, in: image), abs(angle) > 0.004, let level = rotated(image, by: -angle) {
            image = level
            observations = try observe(image)
        }
        // Keep words that read as chords, words or headings; drop noise ("8/9" for G/B, Myanmar read as Latin)
        // so those spots are read again on their own below.
        let pageLines = observations.compactMap(line(from:)).filter { $0.confidence >= 0.5 }.compactMap { l -> ScanTextLine? in
            let kept = l.words.filter { w in
                if case .word = ChordSheetParser.token(w.text) { return ChordSheetParser.isWordLike(w.text) }
                return true
            }
            return kept.isEmpty ? nil : ScanTextLine(words: kept, confidence: l.confidence)
        }
        guard let ink = InkMap(image) else { return pageLines }
        let readWords = pageLines.flatMap(\.words)
        var lines = pageLines

        for band in ink.bands() {
            let blobs = ink.blobs(in: band)
            let missing = blobs.filter { blob in
                let b = ink.normalised(blob)
                return !readWords.contains { w in
                    let i = w.box.intersection(b)
                    return !i.isNull && i.width * i.height > 0.4 * b.width * b.height
                }
            }
            guard !missing.isEmpty else { continue }
            var words: [ScanWord] = []
            var confidence: Float = 1
            // Chord rows are sparse — a few names with wide gaps. A row packed with ink that Vision couldn't read
            // is a lyric in a script it doesn't know; reading its pieces one by one only finds look-alikes (င → "C").
            let span = (blobs.map(\.maxX).max() ?? 0) - (blobs.map(\.minX).min() ?? 0)
            let packed = blobs.count >= 3 && blobs.map(\.width).reduce(0, +) > span * 0.6
            if missing.count > 12 || packed {
                words = missing.map { ScanWord(text: unreadableText, box: ink.normalised($0)) }
                confidence = 0
            } else {
                var reads = missing.map { readAlone(image, ink.pixels($0), labelled: false).flatMap { $0.1 >= 0.5 ? $0 : nil } }
                // Lone letters the plain read missed get a second, labelled read — but only on a row that is
                // already a chord row, since Myanmar letters (င, ဂ) pass for "C" and "G" when coaxed.
                let isChord = { (s: String) -> Bool in if case .chord = ChordSheetParser.token(s) { true } else { false } }
                let rowTop = CGFloat(band.0) / CGFloat(ink.height), rowBottom = CGFloat(band.1) / CGFloat(ink.height)
                let chordRow = readWords.contains { $0.box.midY > rowTop && $0.box.midY < rowBottom && isChord($0.text) }
                    || reads.contains { $0.map { isChord($0.0) } ?? false }
                if chordRow {
                    for i in reads.indices where reads[i] == nil && missing[i].width < missing[i].height * 2.2 {
                        reads[i] = readAlone(image, ink.pixels(missing[i]), labelled: true)
                    }
                }
                for (blob, read) in zip(missing, reads) {
                    let box = ink.normalised(blob)
                    if let (text, conf) = read {
                        // Spread several words read from one blob across its width.
                        let total = CGFloat(max(1, text.count))
                        for r in text.ranges(of: /\S+/) {
                            let at = CGFloat(text.distance(from: text.startIndex, to: r.lowerBound))
                            let len = CGFloat(text.distance(from: r.lowerBound, to: r.upperBound))
                            words.append(ScanWord(text: String(text[r]), box: CGRect(x: box.minX + box.width * at / total, y: box.minY,
                                                                                    width: box.width * len / total, height: box.height)))
                        }
                        confidence = min(confidence, conf)
                    } else {
                        words.append(ScanWord(text: unreadableText, box: box))
                    }
                }
            }
            if !words.isEmpty { lines.append(ScanTextLine(words: words, confidence: confidence)) }
        }
        return lines
    }

    /// Adds the chords the server's reader skipped. Google Vision drops some lone short words — a single "C" or "F"
    /// over a lyric, a chord after the last word — while reading the rest of the row. Ink that no returned word
    /// covers is read here on its own, but only where a chord can be: in a chord row, in a row with no Myanmar text,
    /// or after the last word of a Myanmar line (a chord written at the end — the server reads the Myanmar itself,
    /// so ink it left past the words isn't Myanmar). What's read is kept only if it's a chord name.
    static func addMissedChords(to lines: [ScanTextLine], in image: CGImage) -> [ScanTextLine] {
        guard let ink = InkMap(image) else { return lines }
        let words = lines.flatMap(\.words)
        let isChord = { (s: String) -> Bool in if case .chord = ChordSheetParser.token(s) { true } else { false } }
        var added: [ScanTextLine] = []
        for band in ink.bands() {
            let top = CGFloat(band.0) / CGFloat(ink.height), bottom = CGFloat(band.1) / CGFloat(ink.height)
            let inBand = words.filter { $0.box.midY > top && $0.box.midY < bottom }
            let chordRow = inBand.contains { isChord($0.text) }
            let myanmar = inBand.filter { ChordSheetParser.isMyanmar($0.text) }
            // In a lyric row, only past its last word.
            let after: CGFloat? = chordRow || myanmar.isEmpty ? nil : (myanmar.map(\.box.maxX).max() ?? 0) + 0.01
            let blobs = ink.blobs(in: band)
            // A row packed with ink is text, not a few chord names.
            let span = (blobs.map(\.maxX).max() ?? 0) - (blobs.map(\.minX).min() ?? 0)
            guard after != nil || !(blobs.count >= 3 && blobs.map(\.width).reduce(0, +) > span * 0.6 && !chordRow) else { continue }
            var found: [ScanWord] = []
            var confidence: Float = 1
            for blob in blobs {
                let box = ink.normalised(blob)
                let covered = words.contains { w in
                    let i = w.box.intersection(box)
                    return !i.isNull && i.width * i.height > 0.3 * box.width * box.height
                }
                // Chord-sized only: a few characters wide.
                guard !covered, blob.width < blob.height * 4, after.map({ box.minX > $0 }) ?? true else { continue }
                let read = readAlone(image, ink.pixels(blob), labelled: false).flatMap { isChord($0.0) ? $0 : nil }
                    ?? (blob.width < blob.height * 2.2 ? readAlone(image, ink.pixels(blob), labelled: true) : nil)
                guard let (text, conf) = read, conf >= 0.5, isChord(text) else { continue }
                found.append(ScanWord(text: text, box: box))
                confidence = min(confidence, conf)
            }
            if !found.isEmpty { added.append(ScanTextLine(words: found, confidence: confidence)) }
        }
        return lines + added
    }

    /// The page turned level. A tilt of even 2° puts the two ends of a chord row at different heights, which
    /// breaks rows apart for any reader (the server's too), so every page is levelled before it's read.
    /// The angle comes from the ink itself, so it works for Myanmar sheets with little Latin text.
    static func levelled(_ image: CGImage) -> CGImage {
        guard let ink = InkMap(image, maxWidth: 900), let angle = ink.skew(), abs(angle) > 0.004,
              let level = rotated(image, by: -angle) else { return image }
        return level
    }

    private static func observe(_ image: CGImage, minimumTextHeight: Float = 0.006) throws -> [VNRecognizedTextObservation] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["en-US"]
        // Language correction "fixes" chord names and slash chords, so keep it off.
        request.usesLanguageCorrection = false
        request.minimumTextHeight = minimumTextHeight
        try VNImageRequestHandler(cgImage: image, orientation: .up).perform([request])
        return request.results ?? []
    }

    /// Median slope of the longer lines read, in radians (positive = text rises to the right).
    private static func skew(_ observations: [VNRecognizedTextObservation], in image: CGImage) -> CGFloat? {
        let w = CGFloat(image.width), h = CGFloat(image.height)
        let angles = observations
            .filter { ($0.topRight.x - $0.topLeft.x) > 0.15 && ($0.topCandidates(1).first?.confidence ?? 0) >= 0.5 }
            .map { atan2(($0.topRight.y - $0.topLeft.y) * h, ($0.topRight.x - $0.topLeft.x) * w) }
            .sorted()
        return angles.isEmpty ? nil : angles[angles.count / 2]
    }

    /// The image turned about its centre on a white page.
    private static func rotated(_ image: CGImage, by angle: CGFloat) -> CGImage? {
        let w = CGFloat(image.width), h = CGFloat(image.height)
        guard let ctx = CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        ctx.interpolationQuality = .high
        ctx.translateBy(x: w / 2, y: h / 2)
        ctx.rotate(by: angle)
        ctx.draw(image, in: CGRect(x: -w / 2, y: -h / 2, width: w, height: h))
        return ctx.makeImage()
    }

    private static func recognize(_ image: CGImage, minimumTextHeight: Float = 0.006) throws -> [ScanTextLine] {
        try observe(image, minimumTextHeight: minimumTextHeight).compactMap(line(from:))
    }

    /// Reads one word cut out of the page and set on plain white, so neighbouring text can't interfere.
    /// Vision often won't read a lone letter ("C", "F"); `labelled` reads it after a printed label — "Chord: C" —
    /// which gives it a line to belong to, then drops the label and keeps the result only if it's a chord.
    static func readAlone(_ image: CGImage, _ rect: CGRect, labelled: Bool) -> (String, Float)? {
        guard let crop = image.cropping(to: rect.integral) else { return nil }
        guard labelled else { return read(crop, label: nil) }
        guard let read = read(crop, label: "Chord:") else { return nil }
        guard let r = read.0.range(of: "Chord:", options: [.caseInsensitive]) ?? read.0.range(of: "Chord", options: [.caseInsensitive]) else { return nil }
        let rest = read.0[r.upperBound...].trimmingCharacters(in: .whitespaces.union(CharacterSet(charactersIn: ":;.")))
        // The label invites a chord-like reading, so only trust it when the result is a chord.
        guard case .chord = ChordSheetParser.token(rest) else { return nil }
        return (rest, read.1)
    }

    private static func read(_ crop: CGImage, label: String?) -> (String, Float)? {
        let height = CGFloat(crop.height), width = CGFloat(crop.width)
        let scale = max(1, 64 / height)
        let glyphH = height * scale, margin = glyphH * 0.5
        let font = CTFontCreateWithName("Helvetica-Bold" as CFString, glyphH * 0.7, nil)
        let line = label.map { CTLineCreateWithAttributedString(NSAttributedString(string: $0, attributes: [kCTFontAttributeName as NSAttributedString.Key: font])) }
        let labelW = line.map { CGFloat(CTLineGetTypographicBounds($0, nil, nil, nil)) + glyphH * 0.4 } ?? 0
        let size = CGSize(width: labelW + width * scale + margin * 2, height: glyphH + margin * 2)
        guard let ctx = CGContext(data: nil, width: Int(size.width), height: Int(size.height), bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        ctx.fill(CGRect(origin: .zero, size: size))
        if let line {
            ctx.setFillColor(CGColor(red: 0, green: 0, blue: 0, alpha: 1))
            ctx.textPosition = CGPoint(x: margin, y: margin + glyphH * 0.2)
            CTLineDraw(line, ctx)
        }
        ctx.interpolationQuality = .high
        ctx.draw(crop, in: CGRect(x: margin + labelW, y: margin, width: width * scale, height: glyphH))
        guard let canvas = ctx.makeImage(), let lines = try? recognize(canvas, minimumTextHeight: 0.1), !lines.isEmpty else { return nil }
        return (lines.map(\.text).joined(separator: " "), lines.map(\.confidence).min() ?? 0)
    }

    private static func line(from obs: VNRecognizedTextObservation) -> ScanTextLine? {
        guard let candidate = obs.topCandidates(1).first else { return nil }
        let text = candidate.string
        let lineBox = flip(obs.boundingBox)
        let count = Double(max(1, text.count))
        let words = text.ranges(of: /\S+/).map { r -> ScanWord in
            // Fallback: place the word proportionally along the line.
            let start = Double(text.distance(from: text.startIndex, to: r.lowerBound)) / count
            let end = Double(text.distance(from: text.startIndex, to: r.upperBound)) / count
            let estimate = CGRect(x: lineBox.minX + lineBox.width * start, y: lineBox.minY, width: lineBox.width * (end - start), height: lineBox.height)
            return ScanWord(text: String(text[r]), box: (try? candidate.boundingBox(for: r)).map { flip($0.boundingBox) } ?? estimate)
        }
        return words.isEmpty ? nil : ScanTextLine(words: words, confidence: candidate.confidence)
    }

    /// Vision boxes are bottom-left origin.
    private static func flip(_ r: CGRect) -> CGRect { CGRect(x: r.minX, y: 1 - r.maxY, width: r.width, height: r.height) }
}

/// Dark pixels of a downscaled grey copy of the page, for finding rows of text and the words in them.
struct InkMap {
    let width: Int
    let height: Int
    private let dark: [Bool]
    /// Page pixels per map pixel.
    private let scale: CGFloat

    init?(_ image: CGImage, maxWidth: Int = 1400) {
        let s = min(1, CGFloat(maxWidth) / CGFloat(image.width))
        width = max(1, Int(CGFloat(image.width) * s))
        height = max(1, Int(CGFloat(image.height) * s))
        scale = 1 / s
        var grey = [UInt8](repeating: 255, count: width * height)
        let (w, h) = (width, height)
        let drawn = grey.withUnsafeMutableBytes { buf -> Bool in
            guard let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w,
                                      space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return false }
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard drawn else { return nil }
        // Ink = clearly darker than its surroundings (Bradley's adaptive threshold), so shadows across a photo don't count.
        var integral = [Int](repeating: 0, count: (w + 1) * (h + 1))
        for y in 0..<h {
            var rowSum = 0
            for x in 0..<w {
                rowSum += Int(grey[y * w + x])
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + rowSum
            }
        }
        let r = max(8, w / 32)
        var dark = [Bool](repeating: false, count: w * h)
        for y in 0..<h {
            let y0 = max(0, y - r), y1 = min(h, y + r + 1)
            for x in 0..<w {
                let x0 = max(0, x - r), x1 = min(w, x + r + 1)
                let sum = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                let v = Int(grey[y * w + x])
                dark[y * w + x] = v * 100 * (x1 - x0) * (y1 - y0) < sum * 82 && v < 215
            }
        }
        self.dark = dark
    }

    /// Tilt of the text in radians (positive = rising to the right), or nil if there's too little ink to tell.
    /// Tries small angles and keeps the one where rows of ink stack most sharply (projection-profile method).
    func skew(maxDegrees: Double = 5, stepDegrees: Double = 0.2) -> CGFloat? {
        // Skip the outer edge: the photo's border (table, background) runs dead straight and would win at 0°.
        let mx = width / 25, my = height / 25
        var points: [(x: Int, y: Int)] = []
        for y in stride(from: my, to: height - my, by: 2) {
            for x in stride(from: mx, to: width - mx, by: 2) where dark[y * width + x] { points.append((x, y)) }
        }
        guard points.count > 200 else { return nil }
        let steps = Int((maxDegrees / stepDegrees).rounded())
        var best = (angle: 0.0, score: -1.0)
        for i in -steps...steps {
            let angle = Double(i) * stepDegrees * .pi / 180
            let t = tan(angle)
            let offset = Int(Double(width) * abs(t)) + 1
            var rows = [Int](repeating: 0, count: (height + 2 * offset) / 2 + 1)
            // Shear each ink point back by the candidate tilt and count it in its row. Rows are 2 px bins to
            // match the sampling, otherwise 0° (where every point lands on an even row) always wins.
            for p in points { rows[(p.y + Int(Double(p.x) * t) + offset) / 2] += 1 }
            let score = rows.reduce(0.0) { $0 + Double($1 * $1) }
            if score > best.score { best = (angle, score) }
        }
        return CGFloat(best.angle)
    }

    private func rowInk(_ y: Int, _ x0: Int = 0, _ x1: Int? = nil) -> Int {
        var n = 0
        for x in x0..<(x1 ?? width) where dark[y * width + x] { n += 1 }
        return n
    }

    /// Rows of text, top to bottom, as (top, bottom) map rows. Over-tall rows (chords touching lyrics) are split.
    func bands() -> [(Int, Int)] {
        let profile = (0..<height).map { rowInk($0) }
        let minInk = max(2, width / 300)
        var runs: [(Int, Int)] = []
        var start: Int?
        for y in 0..<height {
            if profile[y] > minInk { if start == nil { start = y } } else if let s = start { runs.append((s, y)); start = nil }
        }
        if let s = start { runs.append((s, height)) }
        runs = runs.filter { $0.1 - $0.0 >= 3 }
        guard !runs.isEmpty else { return [] }
        let median = runs.map { $0.1 - $0.0 }.sorted()[runs.count / 2]
        // Marks above/below Myanmar letters show up as thin separate runs; join each to the nearer row it belongs to.
        var joined = runs
        var i = 0
        while i < joined.count {
            let r = joined[i], h = r.1 - r.0
            let prev = i > 0 ? joined[i - 1] : nil, next = i + 1 < joined.count ? joined[i + 1] : nil
            let gapUp = prev.map { r.0 - $0.1 } ?? .max, gapDown = next.map { $0.0 - r.1 } ?? .max
            let thinVsPrev = prev.map { h * 10 < ($0.1 - $0.0) * 6 } ?? false
            let thinVsNext = next.map { h * 10 < ($0.1 - $0.0) * 6 } ?? false
            let reach = max(2, median / 2)
            if gapUp <= max(2, median / 5) {
                joined[i - 1].1 = r.1; joined.remove(at: i)
            } else if thinVsNext && gapDown <= reach && (gapDown <= gapUp || !thinVsPrev) {
                joined[i + 1].0 = r.0; joined.remove(at: i)
            } else if thinVsPrev && gapUp <= reach {
                joined[i - 1].1 = r.1; joined.remove(at: i)
            } else {
                i += 1
            }
        }
        // Lines run together (blur, tight spacing) leave a waist of thin ink between them; a single tall line
        // (Myanmar with stacked marks) doesn't. Cut at the waist, again and again, keeping both parts line-sized.
        func split(_ r: (Int, Int)) -> [(Int, Int)] {
            let h = r.1 - r.0
            let edge = max(median * 7 / 10, h / 5)
            guard h > median * 17 / 10, h > 12, r.0 + edge < r.1 - edge else { return [r] }
            let cut = ((r.0 + edge)...(r.1 - edge)).min { profile[$0] < profile[$1] } ?? (r.0 + h / 2)
            let mean = Double(profile[r.0..<r.1].reduce(0, +)) / Double(h)
            guard Double(profile[cut]) < mean * 0.25 else { return [r] }
            return split((r.0, cut)) + split((cut, r.1))
        }
        return joined.flatMap(split).filter { b in (b.1 - b.0) >= 4 && (b.1 - b.0) < height / 6 }
    }

    /// Words in a row (separated by gaps wider than half the row height), as map rects.
    func blobs(in band: (Int, Int)) -> [CGRect] {
        let h = band.1 - band.0
        let gap = max(3, h / 2)
        var cols = [Int](repeating: 0, count: width)
        for y in band.0..<band.1 { for x in 0..<width where dark[y * width + x] { cols[x] += 1 } }
        var out: [CGRect] = []
        var start: Int?, lastInk = -gap - 1
        for x in 0..<width {
            if cols[x] > 0 {
                if start == nil { start = x } else if x - lastInk > gap {
                    out.append(CGRect(x: start!, y: band.0, width: lastInk - start! + 1, height: h)); start = x
                }
                lastInk = x
            }
        }
        if let s = start { out.append(CGRect(x: s, y: band.0, width: lastInk - s + 1, height: h)) }
        // Specks of dirt aren't words, and neither are solid shapes (rules, the page edge): text never fills its box.
        return out.filter { b in
            let ink = cols[Int(b.minX)..<Int(b.maxX)].reduce(0, +)
            return b.width >= 2 && ink > h / 2 && Double(ink) < Double(b.width) * Double(h) * 0.8
        }.map(trimmed)
    }

    /// Shrinks a word box to its main block of ink. Tall Myanmar letters reach into the chord row above,
    /// leaving loose fragments under a chord that stop it being read.
    private func trimmed(_ r: CGRect) -> CGRect {
        let x0 = Int(r.minX), x1 = Int(r.maxX), y0 = Int(r.minY), y1 = Int(r.maxY)
        var runs: [(top: Int, bottom: Int, ink: Int)] = []
        for y in y0..<y1 {
            let n = rowInk(y, x0, x1)
            if n > 0 {
                if let last = runs.last, y - last.bottom <= 2 { runs[runs.count - 1].bottom = y + 1; runs[runs.count - 1].ink += n }
                else { runs.append((y, y + 1, n)) }
            }
        }
        guard runs.count > 1, let main = runs.max(by: { $0.ink < $1.ink }) else { return r }
        var minX = x1, maxX = x0
        for y in main.top..<main.bottom { for x in x0..<x1 where dark[y * width + x] { minX = min(minX, x); maxX = max(maxX, x + 1) } }
        guard maxX > minX else { return r }
        return CGRect(x: minX, y: main.top, width: maxX - minX, height: main.bottom - main.top)
    }

    func normalised(_ r: CGRect) -> CGRect {
        CGRect(x: r.minX / CGFloat(width), y: r.minY / CGFloat(height), width: r.width / CGFloat(width), height: r.height / CGFloat(height))
    }

    /// The same rect in page pixels, with a little room around the ink.
    func pixels(_ r: CGRect) -> CGRect {
        r.insetBy(dx: -2, dy: -2).applying(CGAffineTransform(scaleX: scale, y: scale))
    }
}
