import XCTest
@testable import OChords

final class ChordTheoryTests: XCTestCase {
    func testTransposeRootAndSlashBass() {
        XCTAssertEqual(ChordTheory.transposeRoot("Am7", by: 2), "Bm7")
        XCTAssertEqual(ChordTheory.transpose("G/B", by: 2), "A/C#")
        XCTAssertEqual(ChordTheory.transpose("C", by: 0), "C")
        XCTAssertEqual(ChordTheory.transpose("E/Ab", by: 0), "E/Ab")
    }

    func testShownAppliesCapoAndEasySwap() {
        let raw = ChordTheory.shown("F", transpose: 0, capo: 0, easy: false)
        XCTAssertEqual(raw.name, "F")
        XCTAssertNil(raw.from)

        let easy = ChordTheory.shown("F", transpose: 0, capo: 0, easy: true)
        XCTAssertEqual(easy.name, "Fmaj7")
        XCTAssertEqual(easy.from, "F")
        XCTAssertTrue(easy.isEasy)

        let capoed = ChordTheory.shown("G", transpose: 0, capo: 2, easy: false)
        XCTAssertEqual(capoed.name, "F")
    }

    func testBarreAndOpenShapes() {
        XCTAssertFalse(ChordTheory.isBarre("C"))
        XCTAssertFalse(ChordTheory.isBarre("Am"))
        XCTAssertTrue(ChordTheory.isBarre("F"))
        XCTAssertTrue(ChordTheory.isBarre("Bm"))
    }

    func testCapoOptionsPreferOpenKeys() {
        let options = ChordTheory.capoOptions(
            chords: ["C", "G", "Am", "F"],
            songKey: "C",
            transpose: 0,
            current: 0
        )
        XCTAssertFalse(options.isEmpty)
        XCTAssertEqual(options.first?.capo, 0)
        XCTAssertTrue(options.contains { $0.capo == 0 })
    }

    func testChordLineParse() {
        let segs = ChordLine.parse("[C]Morning light [G/B]shines")
        XCTAssertEqual(segs.map(\.chord), ["C", nil, "G/B"])
        XCTAssertEqual(segs.map(\.text), ["Morning", "light", "shines"])
        XCTAssertEqual(ChordLine.chords(in: "[C]a [Am]b [F]"), ["C", "Am", "F"])
    }
}
