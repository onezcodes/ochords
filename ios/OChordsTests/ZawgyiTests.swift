import XCTest
@testable import OChords

final class ZawgyiTests: XCTestCase {
    func testUnicodeBurmeseIsNotTreatedAsZawgyi() {
        let unicode = "ညီလေး မသေချာတာတွေ"
        XCTAssertFalse(Zawgyi.isZawgyi(unicode))
        XCTAssertEqual(Zawgyi.normalised(unicode), unicode)
    }

    func testLatinTextPassesThrough() {
        XCTAssertEqual(Zawgyi.normalised("Morning Light"), "Morning Light")
        XCTAssertFalse(Zawgyi.isZawgyi("Morning Light"))
    }

    func testMarkerCountIsZeroForPlainUnicode() {
        XCTAssertEqual(Zawgyi.markerCount("Hello"), 0)
        XCTAssertEqual(Zawgyi.markerCount("မသေချာ"), 0)
    }
}
