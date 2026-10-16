import XCTest

/// Drives the native UIKit gestures and checks that the Navigation 3 back stack follows them.
/// Every screen prints the back stack as "backStack: ..." at the top.
final class NativeNavigationUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        for name in ["NAV3_DECORATORS", "NAV3_HOST"] {
            if let value = ProcessInfo.processInfo.environment[name] {
                app.launchEnvironment[name] = value
            }
        }
        app.launch()
    }

    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func tap(_ label: String) {
        let target = element(label)
        XCTAssertTrue(target.waitForExistence(timeout: 5), "missing \(label)")
        target.tap()
    }

    /// The back stack label of the screen on top. Hidden controllers keep theirs, so take the
    /// one that is hittable.
    private func expectBackStack(_ expected: String, file: StaticString = #filePath, line: UInt = #line) {
        let label = "backStack: \(expected)"
        let predicate = NSPredicate(format: "exists == true AND hittable == true")
        let match = expectation(for: predicate, evaluatedWith: element(label))
        if XCTWaiter().wait(for: [match], timeout: 5) != .completed {
            snapshot("failure")
            XCTFail("expected \(label)", file: file, line: line)
        }
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// On iPhone Duo the back button moves from the navigation bar into the vertical bar.
    private func tapBack() {
        let back = app.buttons["BackButton"]
        if back.waitForExistence(timeout: 2) && back.isHittable {
            back.tap()
        } else {
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }
    }

    private func edgeSwipeBack() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    func testPushAndNativeBackButton() {
        expectBackStack("Home")
        XCTAssertTrue(app.navigationBars["Home"].waitForExistence(timeout: 5))
        tap("Open detail 1")
        expectBackStack("Home > Detail(id=1)")
        XCTAssertTrue(app.navigationBars["Detail 1"].waitForExistence(timeout: 5))
        snapshot("detail-1")

        tapBack()
        expectBackStack("Home")
        XCTAssertTrue(app.navigationBars["Home"].waitForExistence(timeout: 5))
    }

    func testEdgeSwipeBack() {
        tap("Open detail 1")
        tap("Push detail 2")
        expectBackStack("Home > Detail(id=1) > Detail(id=2)")

        edgeSwipeBack()
        expectBackStack("Home > Detail(id=1)")
        XCTAssertTrue(app.navigationBars["Detail 1"].waitForExistence(timeout: 5))
        snapshot("after-swipe")
    }

    func testStateSurvivesPushAndPop() {
        tap("remember: 0")
        tap("remember: 1")
        tap("rememberSaveable: 0")
        tap("Open detail 1")
        tap("ViewModel taps: 0")
        tap("Push detail 2")
        edgeSwipeBack()
        // Detail 1 kept its ViewModel while Detail 2 was on top.
        XCTAssertTrue(element("ViewModel taps: 1").waitForExistence(timeout: 5))
        tapBack()
        expectBackStack("Home")
        XCTAssertTrue(element("remember: 2").waitForExistence(timeout: 5))
        XCTAssertTrue(element("rememberSaveable: 1").waitForExistence(timeout: 5))
        snapshot("home-after-return")
    }

    func testProgrammaticPopToRootAndReplace() {
        tap("Open detail 1")
        tap("Push detail 2")
        tap("Replace with detail 100")
        expectBackStack("Home > Detail(id=1) > Detail(id=100)")
        XCTAssertTrue(app.navigationBars["Detail 100"].waitForExistence(timeout: 5))
        tap("Pop to root")
        expectBackStack("Home")
    }

    func testSheetsAndDragDismiss() {
        tap("Open settings sheet")
        expectBackStack("Home > Settings")
        snapshot("settings-medium")
        tap("Open filter sheet")
        expectBackStack("Home > Settings > Filter")
        snapshot("filter-over-settings")
        tap("Close all sheets")
        expectBackStack("Home")

        tap("Open settings sheet")
        expectBackStack("Home > Settings")
        // Start at the sheet's top edge, just above its first line of content. The medium detent's
        // height depends on the screen, so a fixed screen position misses it on other devices.
        let sheetTop = element("backStack: Home > Settings").frame.minY - 24
        let grabberArea = app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: app.frame.width / 2, dy: sheetTop))
        grabberArea.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.99)))
        expectBackStack("Home")
    }

    func testInteractiveSwipeCancelKeepsEntry() {
        tap("Open detail 1")
        expectBackStack("Home > Detail(id=1)")
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
        let partway = app.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: partway, withVelocity: .slow, thenHoldForDuration: 0.5)
        expectBackStack("Home > Detail(id=1)")
        XCTAssertTrue(app.navigationBars["Detail 1"].waitForExistence(timeout: 5))
        tap("ViewModel taps: 0")
        XCTAssertTrue(element("ViewModel taps: 1").waitForExistence(timeout: 5))
    }

    func testPushTwoInOneFrame() {
        tap("Push two at once")
        expectBackStack("Home > Detail(id=1) > Detail(id=2)")
        tapBack()
        expectBackStack("Home > Detail(id=1)")
    }

    /// Popped controllers must be released, not just removed from the navigation stack.
    func testPoppedControllersAreReleased() {
        tap("Push ten")
        expectBackStack("Home > " + (1...10).map { "Detail(id=\($0))" }.joined(separator: " > "))
        tap("Pop to root")
        expectBackStack("Home")
        tap("Open settings sheet")
        tap("Open filter sheet")
        tap("Close all sheets")
        expectBackStack("Home")
        sleep(2)
        tap("Live controllers: ?")
        sleep(3)
        let live = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH 'Live controllers: '")).firstMatch
        XCTAssertTrue(live.waitForExistence(timeout: 5))
        XCTAssertEqual(live.label, "Live controllers: 1", "leaked controllers")
        snapshot("live-controllers")
    }

    func testNavigationBarHidden() {
        tap("Open immersive")
        expectBackStack("Home > Immersive")
        XCTAssertFalse(app.navigationBars.firstMatch.isHittable)
        snapshot("immersive")
        tap("Back")
        expectBackStack("Home")
        XCTAssertTrue(app.navigationBars["Home"].waitForExistence(timeout: 5))
    }
}
