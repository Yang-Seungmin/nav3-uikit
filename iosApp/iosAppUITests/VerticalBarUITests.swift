import XCTest

/// Checks where the system puts the tab bar and the back button of the tab sample.
///
/// On iPhone Duo the system can move the navigation bar items and the tab bar into a vertical
/// bar on the leading or trailing edge. Each screen prints the edge it sees
/// (`UITraitCollection.verticalBarEdge`) in its "insets ... · bar=<edge>" label. Where that is
/// "none", the tab bar must be at the bottom; otherwise the tab buttons and the back button must
/// sit on that edge and the Compose content must stay clear of them. Run on the iPhone Duo
/// simulator to cover the vertical bar; other simulators cover the bottom tab bar.
final class VerticalBarUITests: SampleUITestCase {
    override var sample: String? { "tabs" }

    override func setUp() {
        XCUIDevice.shared.orientation = .portrait
        super.setUp()
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
    }

    private func visible(_ predicate: String, _ args: CVarArg...) -> XCUIElement {
        let query = app.descendants(matching: .any).matching(NSPredicate(format: predicate, argumentArray: args))
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline {
            if let hit = query.allElementsBoundByIndex.first(where: { $0.exists && $0.isHittable }) {
                return hit
            }
            usleep(200_000)
        }
        XCTFail("nothing visible matches \(predicate) \(args)")
        return query.firstMatch
    }

    /// The vertical bar edge the visible screen reports: "leading", "trailing", or "none".
    private func barEdge() -> String {
        let label = visible("label BEGINSWITH 'insets '").label
        print("insets label: \(label)")
        return String(label.split(separator: "=").last ?? "none")
    }

    private func tabButtons() -> [XCUIElement] {
        ["Home", "Search", "Profile"].map { visible("elementType == %d AND label == %@", XCUIElement.ElementType.button.rawValue, $0) }
    }

    /// Asserts that `frame` hugs the bar edge (or the bottom when there is no vertical bar).
    private func assertOnBar(_ frame: CGRect, edge: String, what: String, file: StaticString = #filePath, line: UInt = #line) {
        let screen = app.frame
        switch edge {
        case "trailing":
            XCTAssertGreaterThan(frame.minX, screen.width * 0.75, "\(what) not on the trailing edge: \(frame)", file: file, line: line)
        case "leading":
            XCTAssertLessThan(frame.maxX, screen.width * 0.25, "\(what) not on the leading edge: \(frame)", file: file, line: line)
        default:
            XCTAssertGreaterThan(frame.minY, screen.height * 0.8, "\(what) not at the bottom: \(frame)", file: file, line: line)
        }
    }

    private func checkTabBarPlacement(_ name: String) {
        let edge = barEdge()
        snapshot("\(name)-bar-\(edge)")
        let tabs = tabButtons().map(\.frame)
        tabs.forEach { assertOnBar($0, edge: edge, what: "tab button") }
        if edge == "none" {
            XCTAssertEqual(tabs[0].midY, tabs[2].midY, accuracy: 4, "tab buttons not in one row")
        } else {
            // Stacked top to bottom, in the lower part of the bar (the status bar is at the top).
            XCTAssertEqual(tabs[0].midX, tabs[2].midX, accuracy: 4, "tab buttons not in one column")
            XCTAssertLessThan(tabs[0].midY, tabs[1].midY)
            XCTAssertLessThan(tabs[1].midY, tabs[2].midY)
            XCTAssertGreaterThan(tabs[0].midY, app.frame.height * 0.5, "tab bar not in the lower half")
        }

        // The Compose content stays clear of the bar through the safe area.
        let content = visible("label == 'Open detail 1'").frame
        switch edge {
        case "trailing": XCTAssertLessThanOrEqual(content.maxX, tabs[0].minX, "content under the bar")
        case "leading": XCTAssertGreaterThanOrEqual(content.minX, tabs[0].maxX, "content under the bar")
        default: XCTAssertLessThanOrEqual(content.maxY, tabs[0].minY, "content under the tab bar")
        }
    }

    func testTabBarPlacementPortrait() {
        checkTabBarPlacement("portrait")
    }

    func testTabBarPlacementLandscape() throws {
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(2)
        // The closed iPhone Duo simulator keeps the outer display upright.
        if app.frame.width < app.frame.height {
            throw XCTSkip("the device did not rotate")
        }
        checkTabBarPlacement("landscape")
    }

    /// After a push, the back button joins the tab bar on the vertical bar.
    func testBackButtonPlacement() {
        visible("label == 'Open detail 1'").tap()
        _ = visible("label == 'backStack: Home: Home > Detail(id=1)'")
        let edge = barEdge()
        snapshot("detail-bar-\(edge)")
        guard edge != "none" else { return } // the back button is in the top navigation bar
        let back = app.buttons["BackButton"]
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        assertOnBar(back.frame, edge: edge, what: "back button")
        XCTAssertLessThan(back.frame.midY, tabButtons()[0].frame.minY, "back button below the tabs")
        back.tap()
        _ = visible("label == 'backStack: Home: Home'")
    }
}
