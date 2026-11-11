import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

/// Reads a page on the O Chords server (Google Cloud Vision), which — unlike the phone — reads
/// Myanmar script. Needs a signed-in player; the scanner falls back to the phone otherwise.
final class ServerOCREngine: OCREngine {
    private let api: APIClient

    /// Long enough for small chord letters, small enough to upload quickly (≈ 300–800 KB).
    private static let maxSide: CGFloat = 2000
    private static let jpegQuality = 0.8

    init(api: APIClient) { self.api = api }

    var readsMyanmar: Bool { true }

    private struct Reply: Decodable {
        struct Line: Decodable {
            var confidence: Float
            var words: [Word]
        }
        struct Word: Decodable {
            var text: String
            var x, y, width, height: Double
        }
        var engine: String
        var lines: [Line]
    }

    func recognize(_ image: CGImage) async throws -> [ScanTextLine] {
        guard api.hasToken else { throw ServiceError.signedOut }
        guard let jpeg = Self.jpeg(image) else { throw ServiceError.scanFailed }
        let reply: Reply = try await api.upload("scans", file: jpeg, name: "image", filename: "sheet.jpg", mimeType: "image/jpeg")
        return reply.lines.map { line in
            ScanTextLine(words: line.words.map { ScanWord(text: $0.text, box: CGRect(x: $0.x, y: $0.y, width: $0.width, height: $0.height)) },
                         confidence: line.confidence)
        }
    }

    /// Scales the page down and encodes it as JPEG.
    static func jpeg(_ image: CGImage) -> Data? {
        let scale = min(1, maxSide / CGFloat(max(image.width, image.height)))
        var source = image
        if scale < 1 {
            let w = Int(CGFloat(image.width) * scale), h = Int(CGFloat(image.height) * scale)
            guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
            ctx.interpolationQuality = .high
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            guard let scaled = ctx.makeImage() else { return nil }
            source = scaled
        }
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, source, [kCGImageDestinationLossyCompressionQuality: jpegQuality] as CFDictionary)
        return CGImageDestinationFinalize(dest) ? data as Data : nil
    }
}
