import XCTest
@testable import OChords

final class ChordSheetParserTests: XCTestCase {
    private var chordRow: ScanTextLine {
        TestScan.line(y: 0.22, words: [("C", 0.10), ("G", 0.40)])
    }
    private var lyricRow: ScanTextLine {
        TestScan.line(y: 0.27, words: [("Morning", 0.10), ("light", 0.26), ("shines", 0.40)])
    }

    func testChordRowPairsWithLyricBelowAndPreambleGivesTitleArtistAndStrum() {
        let sheet = ChordSheetParser.parse([
            TestScan.line(y: 0.05, h: 0.05, words: [("Morning", 0.30), ("Light", 0.46)]),
            TestScan.line(y: 0.12, words: [("The", 0.35), ("Wanderers", 0.43)]),
            TestScan.line(y: 0.16, words: [("↓", 0.30), ("↓", 0.34), ("↑", 0.38), ("↓", 0.42)]),
            chordRow,
            lyricRow,
        ], readsMyanmar: false)
        let song = sheet.song
        XCTAssertEqual(song.title, "Morning Light")
        XCTAssertEqual(song.artist, "The Wanderers")
        XCTAssertEqual(song.key, "C")
        XCTAssertEqual(song.sections.count, 1)
        let verse = song.sections[0]
        XCTAssertEqual(verse.kind, .verse)
        XCTAssertEqual(verse.label, "VERSE 1")
        XCTAssertEqual(verse.lines, ["[C]Morning light [G]shines"])
        XCTAssertEqual(verse.strumPattern, "↓ ↓ ↑ ↓")
        XCTAssertTrue(sheet.needsLyrics.isEmpty)
        XCTAssertEqual(sheet.regions.count, 1)
    }

    func testTitleAndSingerOnOneLineAreSplit() {
        let song = ChordSheetParser.parse([
            TestScan.line(y: 0.05, h: 0.05, words: [
                ("Morning", 0.20), ("Light", 0.36), ("–", 0.48), ("The", 0.52), ("Wanderers", 0.60),
            ]),
            chordRow,
            lyricRow,
        ], readsMyanmar: false).song
        XCTAssertEqual(song.title, "Morning Light")
        XCTAssertEqual(song.artist, "The Wanderers")
    }

    func testMyanmarLineWithLeadingLabelAndEndOfLineChord() {
        let sheet = ChordSheetParser.parse([
            TestScan.line(y: 0.05, h: 0.05, words: [("ညီလေး", 0.30)]),
            TestScan.line(y: 0.12, words: [("တေးဆို", 0.30), ("-", 0.40), ("မျိုးကြီး", 0.43)]),
            TestScan.line(y: 0.20, h: 0.04, words: [("V1", 0.05), ("မသေချာ", 0.12), ("Bm", 0.50)]),
        ], readsMyanmar: true)
        let song = sheet.song
        XCTAssertEqual(song.title, "ညီလေး")
        XCTAssertEqual(song.artist, "မျိုးကြီး")
        XCTAssertEqual(song.sections.count, 1)
        XCTAssertEqual(song.sections[0].label, "VERSE 1")
        XCTAssertEqual(song.sections[0].lines, ["မသေချာ [Bm]"])
        XCTAssertEqual(song.key, "Bm")
        XCTAssertTrue(song.isMyanmar)
    }

    func testChordOverMyanmarWordSnapsToSyllableStart() {
        let song = ChordSheetParser.parse([
            TestScan.line(y: 0.20, words: [("G", 0.20)]),
            TestScan.line(y: 0.25, h: 0.04, words: [("စက်ရုပ်", 0.10)]),
        ], readsMyanmar: true).song
        XCTAssertEqual(song.sections.count, 1, "expected a verse; got \(song.sections.map(\.label))")
        XCTAssertEqual(song.sections[0].lines, ["စက်[G]ရုပ်"])
        XCTAssertEqual(ChordSheetParser.myanmarSyllableStarts(Array("စက်ရုပ်")), [0, 2])
    }

    func testGluedChordsAreSplitAcrossTheWord() {
        XCTAssertEqual(ChordSheetParser.gluedChords("AmEm"), ["Am", "Em"])
        XCTAssertEqual(ChordSheetParser.gluedChords("CG/B"), ["C", "G/B"])
        XCTAssertNil(ChordSheetParser.gluedChords("CAB"))
        let song = ChordSheetParser.parse([
            TestScan.line(y: 0.20, words: [("AmEm", 0.10)]),
            TestScan.line(y: 0.25, words: [("hi", 0.10), ("world", 0.14)]),
        ], readsMyanmar: false).song
        XCTAssertEqual(song.sections[0].lines, ["[Am]hi [Em]world"])
    }

    func testBarLinesReadAsLettersMakeAnInstrumentalRow() {
        let sheet = ChordSheetParser.parse([
            TestScan.line(y: 0.30, words: [
                ("(ICI", 0.10), ("|", 0.20), ("Am", 0.25), ("|", 0.32),
                ("G", 0.36), ("|", 0.42), ("IGI)", 0.46),
            ]),
        ], readsMyanmar: false)
        XCTAssertEqual(sheet.song.sections.count, 1)
        let intro = sheet.song.sections[0]
        XCTAssertEqual(intro.kind, .music)
        XCTAssertEqual(intro.label, "INTRO")
        XCTAssertEqual(intro.bars, ["C", "Am", "G", "G"])
        XCTAssertEqual(intro.note, "4 bars")
        XCTAssertTrue(sheet.chordNotes["C"]!.hasPrefix("Bar line"))
        XCTAssertEqual(ChordSheetParser.token("x2"), .neutral)
        XCTAssertEqual(ChordSheetParser.barToken("||"), .neutral)
    }

    func testChordDiagramLegendIsDropped() {
        let song = ChordSheetParser.parse([
            TestScan.line(y: 0.05, h: 0.05, words: [("Song", 0.30), ("Title", 0.40)]),
            TestScan.line(y: 0.12, words: [("C", 0.10), ("G", 0.20), ("Am", 0.30), ("F", 0.40)]),
            TestScan.line(y: 0.16, words: [("x32010", 0.10), ("320003", 0.20), ("x02210", 0.30), ("133211", 0.40)]),
            TestScan.line(y: 0.25, words: [("C", 0.10)]),
            TestScan.line(y: 0.30, words: [("la", 0.10), ("la", 0.16), ("la", 0.22)]),
        ], readsMyanmar: false).song
        XCTAssertEqual(song.title, "Song Title")
        XCTAssertEqual(song.sections.count, 1)
        XCTAssertEqual(song.sections[0].kind, .verse)
        XCTAssertEqual(song.sections[0].lines, ["[C]la la la"])
    }

    func testRepeatMarkOnHeadingBecomesTheSectionNote() {
        let song = ChordSheetParser.parse([
            TestScan.line(y: 0.10, words: [("Chorus", 0.10), ("x2", 0.25)]),
            chordRow,
            lyricRow,
        ], readsMyanmar: false).song
        XCTAssertEqual(song.sections.count, 1)
        let chorus = song.sections[0]
        XCTAssertEqual(chorus.kind, .chorus)
        XCTAssertEqual(chorus.label, "CHORUS")
        XCTAssertEqual(chorus.note, "×2")
        XCTAssertEqual(chorus.lines, ["[C]Morning light [G]shines"])
    }

    func testKeyCapoAndTempoAreRead() {
        let song = ChordSheetParser.parse([
            TestScan.line(y: 0.05, h: 0.05, words: [("Morning", 0.30), ("Light", 0.46)]),
            TestScan.line(y: 0.12, words: [
                ("Key:", 0.10), ("G", 0.18), ("Capo", 0.30), ("2", 0.40), ("120", 0.50), ("bpm", 0.58),
            ]),
            chordRow,
            lyricRow,
        ], readsMyanmar: false).song
        XCTAssertEqual(song.key, "G")
        XCTAssertEqual(song.tempo, 120)
        XCTAssertEqual(song.myNote, "Capo 2")
        XCTAssertEqual(song.title, "Morning Light")
    }

    func testRewordKeepsChordsWithTheirWords() {
        XCTAssertEqual(
            ChordSheetParser.reword("[C]Morning li[G]ght shines", with: "Morning light shine"),
            "[C]Morning li[G]ght shine"
        )
        XCTAssertEqual(
            ChordSheetParser.reword("[C]Morning [G]light [Am]shines", with: "Morning shines"),
            "[C]Morning [G][Am]shines"
        )
        XCTAssertEqual(ChordSheetParser.words(of: "[C]Morning [G]light"), "Morning light")
        XCTAssertEqual(
            ChordSheetParser.place(chordsOf: "[C]hello [G]world", onto: "sing along"),
            "[C]sing [G]along"
        )
    }

    func testCreditsAndTitlesAreCleaned() {
        let credit = ChordSheetParser.credit("တေးရေး-ကန် တေးဆို-မျိုးကြီး")!
        XCTAssertEqual(credit.singer, "မျိုးကြီး")
        XCTAssertEqual(credit.writer, "ကန်")
        XCTAssertEqual(ChordSheetParser.credit("by The Wanderers")!.singer, "The Wanderers")
        XCTAssertNil(ChordSheetParser.credit("Stand by me"))
        XCTAssertEqual(ChordSheetParser.cleanTitle("6/8 beat Mary"), "Mary")
        XCTAssertEqual(ChordSheetParser.cleanTitle("ခေါင်းစဉ်: \"ညီလေး\""), "ညီလေး")
        XCTAssertEqual(ChordSheetParser.cleanTitle("Title: Mary."), "Mary")
    }
}
