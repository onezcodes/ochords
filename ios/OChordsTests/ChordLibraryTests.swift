import XCTest
@testable import OChords

final class ChordLibraryTests: XCTestCase {
    func testInfoLooksUpKnownChords() {
        let c = ChordLibrary.info("C")!
        XCTAssertEqual(c.name, "C")
        XCTAssertEqual(c.full, "C major")
        XCTAssertEqual(c.voicings.first?.pattern, "x32010")

        let slash = ChordLibrary.info("G/B")!
        XCTAssertEqual(slash.voicings.first?.pattern, "x20003")
    }

    func testInfoFallsBackToRootTriad() {
        let unknown = ChordLibrary.info("Cadd9/G")
        // Falls back to the root part when the exact name isn't in the library.
        XCTAssertNotNil(ChordLibrary.info("Cadd9"))
        XCTAssertEqual(unknown?.name, "Cadd9")
    }

    func testCommonAndKeyChords() {
        XCTAssertTrue(ChordLibrary.common.contains("Em"))
        let inC = ChordLibrary.chords(inKey: "C")
        XCTAssertEqual(inC.map(\.degree), ["I", "ii", "iii", "IV", "V", "vi", "vii°"])
        XCTAssertEqual(inC.map(\.chord), ["C", "Dm", "Em", "F", "G", "Am", "Bdim"])
    }
}
