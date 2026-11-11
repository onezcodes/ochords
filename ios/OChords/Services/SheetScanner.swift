import UIKit
import Vision
import PDFKit
import CoreImage

// Real sheet reading: photo/PDF → positioned text lines → `ChordSheetParser` → draft song.
// OCR sits behind `OCREngine` so a server engine that reads Myanmar can replace Vision later.

// OCR types and the Vision engine live in VisionOCR.swift.

// MARK: - Images

enum ScanImage {
    /// Upright image at most `maxSide` pixels on its longest side.
    static func prepare(_ data: Data, maxSide: CGFloat = 2600) -> UIImage? {
        guard let image = UIImage(data: data) else { return nil }
        let scale = min(1, maxSide / max(image.size.width, image.size.height))
        let size = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }

    /// The four corners of a sheet in a photo, as fractions of its size with the origin at the top left (as drawn on
    /// screen).
    struct PageCorners: Equatable {
        var topLeft: CGPoint, topRight: CGPoint, bottomRight: CGPoint, bottomLeft: CGPoint

        static let whole = PageCorners(topLeft: CGPoint(x: 0, y: 0), topRight: CGPoint(x: 1, y: 0),
                                       bottomRight: CGPoint(x: 1, y: 1), bottomLeft: CGPoint(x: 0, y: 1))

        var all: [CGPoint] { [topLeft, topRight, bottomRight, bottomLeft] }

        /// A proper four-sided outline (no corner dragged across another), which flattening needs.
        var isConvex: Bool {
            let p = all
            var sign = 0.0
            for i in 0..<4 {
                let a = p[i], b = p[(i + 1) % 4], c = p[(i + 2) % 4]
                let cross = Double((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x))
                guard abs(cross) > 1e-6 else { return false }
                if sign == 0 { sign = cross } else if (cross > 0) != (sign > 0) { return false }
            }
            return true
        }
    }

    /// Where the sheet of paper is, if Vision finds a clear page edge (nil otherwise). Document segmentation first;
    /// the classic rectangle detector when it isn't sure (or can't run — it needs the Neural Engine).
    static func detectPage(_ image: CGImage) -> PageCorners? {
        let segmentation = VNDetectDocumentSegmentationRequest()
        let rectangles = VNDetectRectanglesRequest()
        rectangles.minimumAspectRatio = 0.3
        rectangles.maximumAspectRatio = 1
        rectangles.minimumSize = 0.3
        rectangles.minimumConfidence = 0.6
        rectangles.quadratureTolerance = 30
        try? VNImageRequestHandler(cgImage: image, orientation: .up).perform([segmentation])
        var found = segmentation.results?.first.flatMap { $0.confidence > 0.6 ? $0 as VNRectangleObservation : nil }
        if found == nil {
            try? VNImageRequestHandler(cgImage: image, orientation: .up).perform([rectangles])
            found = rectangles.results?.first
        }
        guard let page = found else { return nil }
        let area = page.boundingBox.width * page.boundingBox.height
        guard area > 0.2, area < 0.98 else { return nil }
        // Vision's origin is bottom-left; the screen's is top-left.
        func flip(_ p: CGPoint) -> CGPoint { CGPoint(x: p.x, y: 1 - p.y) }
        return PageCorners(topLeft: flip(page.topLeft), topRight: flip(page.topRight),
                           bottomRight: flip(page.bottomRight), bottomLeft: flip(page.bottomLeft))
    }

    /// The area inside `corners`, straightened into a rectangle (perspective correction).
    static func flatten(_ image: CGImage, to corners: PageCorners) -> CGImage? {
        let w = CGFloat(image.width), h = CGFloat(image.height)
        // Core Image's origin is bottom-left.
        func point(_ p: CGPoint) -> CIVector { CIVector(x: p.x * w, y: (1 - p.y) * h) }
        let filter = CIFilter(name: "CIPerspectiveCorrection")
        filter?.setValue(CIImage(cgImage: image), forKey: kCIInputImageKey)
        filter?.setValue(point(corners.topLeft), forKey: "inputTopLeft")
        filter?.setValue(point(corners.topRight), forKey: "inputTopRight")
        filter?.setValue(point(corners.bottomLeft), forKey: "inputBottomLeft")
        filter?.setValue(point(corners.bottomRight), forKey: "inputBottomRight")
        guard let out = filter?.outputImage else { return nil }
        return CIContext().createCGImage(out, from: out.extent)
    }

    /// Finds the sheet of paper and flattens it. Returns the input when no clear page edge is found.
    static func cropToPage(_ image: CGImage) -> CGImage {
        guard let page = detectPage(image) else { return image }
        return flatten(image, to: page) ?? image
    }

    /// The image turned a quarter turn clockwise.
    static func rotatedClockwise(_ image: CGImage) -> CGImage? {
        let w = image.width, h = image.height
        guard let ctx = CGContext(data: nil, width: h, height: w, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        ctx.translateBy(x: 0, y: CGFloat(w))
        ctx.rotate(by: -.pi / 2)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        return ctx.makeImage()
    }

    static func thumbnail(_ image: CGImage, maxSide: CGFloat = 520) -> Data? {
        let ui = UIImage(cgImage: image)
        let scale = min(1, maxSide / max(ui.size.width, ui.size.height))
        let size = CGSize(width: (ui.size.width * scale).rounded(), height: (ui.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in ui.draw(in: CGRect(origin: .zero, size: size)) }
            .jpegData(compressionQuality: 0.7)
    }
}

// MARK: - PDF

enum ScanPDF {
    static let maxPages = 4

    /// Lines from the PDF's text layer (typed PDFs keep real Myanmar text). Empty for scanned PDFs.
    static func textLines(_ doc: PDFDocument) -> [ScanTextLine] {
        var out: [ScanTextLine] = []
        for i in 0..<min(doc.pageCount, maxPages) {
            guard let page = doc.page(at: i), let pageText = page.string, !pageText.isEmpty else { continue }
            let bounds = page.bounds(for: .mediaBox)
            let ns = pageText as NSString
            func normalised(_ r: CGRect) -> CGRect {
                CGRect(x: (r.minX - bounds.minX) / bounds.width, y: CGFloat(i) + 1 - (r.maxY - bounds.minY) / bounds.height,
                       width: r.width / bounds.width, height: r.height / bounds.height)
            }
            var cursor = 0
            for lineSel in page.selection(for: bounds)?.selectionsByLine() ?? [] {
                // U+25CC (dotted circle) is a placeholder fonts draw for "invalid" mark sequences — e.g. Zawgyi
                // shown with a Unicode font — and is never part of the lyrics.
                guard let raw = lineSel.string?.replacingOccurrences(of: "\u{25CC}", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { continue }
                let found = ns.range(of: raw, range: NSRange(location: cursor, length: ns.length - cursor))
                let lineBox = normalised(lineSel.bounds(for: page))
                var words: [ScanWord] = []
                for r in raw.ranges(of: /\S+/) {
                    var box = lineBox
                    if found.location != NSNotFound {
                        let local = NSRange(r, in: raw)
                        let global = NSRange(location: found.location + local.location, length: local.length)
                        if let sel = page.selection(for: global) { box = normalised(sel.bounds(for: page)) }
                    }
                    words.append(ScanWord(text: String(raw[r]), box: box))
                }
                if found.location != NSNotFound { cursor = found.location + found.length }
                if !words.isEmpty { out.append(ScanTextLine(words: words, confidence: 1)) }
            }
        }
        return out
    }

    /// Renders a page for OCR (scanned PDFs have no text layer).
    static func render(_ page: PDFPage, maxSide: CGFloat = 2400) -> CGImage? {
        let b = page.bounds(for: .mediaBox)
        let scale = maxSide / max(b.width, b.height)
        let size = CGSize(width: b.width * scale, height: b.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let img = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            ctx.cgContext.translateBy(x: 0, y: size.height)
            ctx.cgContext.scaleBy(x: scale, y: -scale)
            page.draw(with: .mediaBox, to: ctx.cgContext)
        }
        return img.cgImage
    }
}

// MARK: - Service

final class OnDeviceScanService: ScanService {
    /// Reads Myanmar lyrics (the O Chords server). Tried first when there is one.
    private let server: OCREngine?
    /// Apple Vision on the phone: chords and English, works offline.
    private let device: OCREngine = VisionOCREngine()
    /// Flatten the photo to the page edges before reading.
    var cropToPage = true

    init(server: OCREngine? = nil) { self.server = server }

    private struct Reading {
        var lines: [ScanTextLine]
        var readsMyanmar: Bool
        var onServer: Bool
        /// What the reader itself returned (the server's words before the phone fills in skipped chords).
        var raw: [ScanTextLine] = []
    }

    /// The server when it answers; otherwise the phone (guest, offline, no key on the server, daily limit).
    private func read(_ page: CGImage) async throws -> Reading {
        if let server {
            do {
                let lines = try await server.recognize(page)
                // Lone chord letters the server skipped are read again on the phone.
                let completed = await Task.detached { VisionOCREngine.addMissedChords(to: lines, in: page) }.value
                return Reading(lines: completed, readsMyanmar: server.readsMyanmar, onServer: true, raw: lines)
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                try Task.checkCancellation()
            }
        }
        let lines = try await device.recognize(page)
        return Reading(lines: lines, readsMyanmar: device.readsMyanmar, onServer: false, raw: lines)
    }

    func scan(_ source: ScanSource, progress: @escaping @MainActor (ScanStage) -> Void) async throws -> ScanResult {
        await progress(.page)
        var reading = Reading(lines: [], readsMyanmar: false, onServer: false)
        var firstPage: CGImage?
        var cropped = false

        switch source {
        case .image(let data):
            guard let cg = ScanImage.prepare(data)?.cgImage else { throw ServiceError.scanFailed }
            let flat = cropToPage ? await Task.detached { ScanImage.cropToPage(cg) }.value : cg
            cropped = flat !== cg
            let page = await Task.detached { VisionOCREngine.levelled(flat) }.value
            firstPage = page
            try Task.checkCancellation()
            await progress(.text)
            reading = try await read(page)
            // The page-edge finder sometimes picks a shadow or a fold and crops the sheet away; read the whole photo then.
            if cropped, ChordSheetParser.parse(reading.lines, readsMyanmar: reading.readsMyanmar).song.usedChords.count < 2 {
                try Task.checkCancellation()
                let whole = await Task.detached { VisionOCREngine.levelled(cg) }.value
                reading = try await read(whole)
                cropped = false
                firstPage = whole
            }

        case .pdf(let data):
            guard let doc = PDFDocument(data: data), doc.pageCount > 0 else { throw ServiceError.scanFailed }
            firstPage = doc.page(at: 0).flatMap { ScanPDF.render($0, maxSide: 1200) }
            await progress(.text)
            let textLayer = ScanPDF.textLines(doc)
            // A typed PDF carries its own text, Myanmar included; only scanned PDFs need reading.
            reading = Reading(lines: textLayer, readsMyanmar: true, onServer: false)
            if textLayer.isEmpty {
                reading.readsMyanmar = server != nil
                for i in 0..<min(doc.pageCount, ScanPDF.maxPages) {
                    try Task.checkCancellation()
                    guard let page = doc.page(at: i), let cg = ScanPDF.render(page) else { continue }
                    let pageReading = try await read(cg)
                    reading.readsMyanmar = reading.readsMyanmar && pageReading.readsMyanmar
                    reading.onServer = pageReading.onServer
                    // Stack pages: page i spans y = i…i+1.
                    reading.lines += pageReading.lines.map { l in
                        ScanTextLine(words: l.words.map { ScanWord(text: $0.text, box: $0.box.offsetBy(dx: 0, dy: CGFloat(i))) },
                                     confidence: l.confidence)
                    }
                }
            }
        }

        try Task.checkCancellation()
        await progress(.chords)
        let zawgyi = Zawgyi.isZawgyi(reading.lines.map(\.text).joined(separator: "\n"))
        // Convert word by word, before chords are slotted into the lyrics, so syllables reorder cleanly.
        let lines = zawgyi ? reading.lines.map(Self.unicodeLine) : reading.lines
        let sheet = ChordSheetParser.parse(lines, readsMyanmar: reading.readsMyanmar)
        await progress(.song)
        guard !sheet.song.usedChords.isEmpty else { throw ServiceError.scanFailed }
        let report = zawgyi ? Self.report(scanned: reading.lines, converted: sheet.song, readsMyanmar: reading.readsMyanmar) : nil
        // Kept on the phone with the song, so it can be read again when the scanner improves (see `ScanArchive`).
        let archived: ScanReading = if case .image = source, let page = firstPage {
            ScanReading(kind: reading.onServer ? .server : .device, lines: reading.raw, readsMyanmar: reading.readsMyanmar,
                        zawgyi: zawgyi, page: ServerOCREngine.jpeg(page))
        } else {
            ScanReading(kind: .lines, lines: reading.lines, readsMyanmar: reading.readsMyanmar, zawgyi: zawgyi, page: nil)
        }

        return ScanResult(
            song: sheet.song,
            detectedZawgyi: zawgyi,
            lowConfidence: sheet.lowConfidence,
            chordNotes: sheet.chordNotes,
            needsLyrics: sheet.needsLyrics,
            thumbnail: firstPage.flatMap { ScanImage.thumbnail($0) },
            regions: sheet.regions,
            linesRead: reading.lines.count,
            croppedToPage: cropped,
            readOnServer: reading.onServer,
            zawgyi: report,
            reading: archived
        )
    }

    static func unicodeLine(_ line: ScanTextLine) -> ScanTextLine {
        ScanTextLine(words: line.words.map { ScanWord(text: Zawgyi.toUnicode($0.text), box: $0.box) }, confidence: line.confidence)
    }

    /// What changed, for the comparison screen: counts plus the title and first lyric line both ways.
    private static func report(scanned lines: [ScanTextLine], converted: Song, readsMyanmar: Bool) -> ZawgyiReport {
        let scannedSong = ChordSheetParser.parse(lines, readsMyanmar: readsMyanmar).song
        let words = lines.flatMap(\.words).map(\.text).filter(ChordSheetParser.isMyanmar)
        let changed = words.map { ($0, Zawgyi.toUnicode($0)) }.filter { $0.0 != $0.1 }
        let clean = changed.isEmpty ? 1 : Double(changed.filter { Zawgyi.markerCount($0.1) == 0 }.count) / Double(changed.count)

        func lyrics(_ song: Song) -> (label: String, text: String)? {
            for section in song.sections {
                if let line = section.lines.first(where: { ChordSheetParser.isMyanmar($0) }) {
                    return ("\(section.label) · LINE 1", ChordLine.parse(line).map(\.text).joined(separator: " "))
                }
            }
            return nil
        }
        var preview: [ZawgyiReport.Line] = []
        if ChordSheetParser.isMyanmar(scannedSong.title) {
            preview.append(.init(label: "TITLE", scanned: scannedSong.title, converted: converted.title))
        }
        if let before = lyrics(scannedSong), let after = lyrics(converted) {
            preview.append(.init(label: before.label, scanned: before.text, converted: after.text))
        }
        return ZawgyiReport(scannedSong: scannedSong, wordsConverted: changed.count, clean: clean, preview: preview)
    }
}
