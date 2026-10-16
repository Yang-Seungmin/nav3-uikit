import XCTest

/// Drives the single back stack tab sample (`NAV3_SAMPLE=stack-tabs`): the `commonui` recipe's
/// `TopLevelBackStack`, whose one back stack holds every visited tab. Every screen prints
/// "backStack: <whole stack>" and "native=<screens per tab>" at the top.
final class StackTabsUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["NAV3_SAMPLE"] = "stack-tabs"
        if let host = ProcessInfo.processInfo.environment["NAV3_HOST"] {
            app.launchEnvironment["NAV3_HOST"] = host
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

    private func tabButton(_ name: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", name)).firstMatch
    }

    private func isTabSelected(_ name: String) -> Bool {
        let matches = app.buttons.matching(NSPredicate(format: "label == %@", name))
        let deadline = Date().addingTimeInterval(3)
        repeat {
            if matches.allElementsBoundByIndex.contains(where: { $0.isSelected }) { return true }
            usleep(200_000)
        } while Date() < deadline
        print("tab \(name) not selected:\n" + matches.debugDescription)
        return false
    }

    private func selectTab(_ name: String) {
        let button = tabButton(name)
        XCTAssertTrue(button.waitForExistence(timeout: 5), "missing tab \(name)")
        button.tap()
    }

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

    private func expectVisible(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        let predicate = NSPredicate(format: "exists == true AND hittable == true")
        let match = expectation(for: predicate, evaluatedWith: element(label))
        if XCTWaiter().wait(for: [match], timeout: 5) != .completed {
            snapshot("failure")
            XCTFail("expected visible \(label)", file: file, line: line)
        }
    }

    /// The whole back stack and how many screens each tab's UINavigationController holds.
    private func expectState(_ backStack: String, native: String, file: StaticString = #filePath, line: UInt = #line) {
        expectVisible("backStack: \(backStack)", file: file, line: line)
        expectVisible("native=\(native)", file: file, line: line)
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Each tab shows its own part of the one back stack, and selecting a tab moves its part to
    /// the end (the recipe's `addTopLevel`), so a detail screen and its ViewModel are still there after a round trip.
    func testTabsFollowTheBackStack() {
        expectState("Home", native: "1,0,0")
        tap("Open detail 1")
        expectState("Home > Detail(id=1)", native: "2,0,0")
        tap("ViewModel taps: 0")
        expectVisible("ViewModel taps: 1")

        selectTab("Search")
        expectState("Home > Detail(id=1) > SearchHome", native: "2,1,0")
        XCTAssertTrue(isTabSelected("Search"))
        tap("Open detail 2")
        expectState("Home > Detail(id=1) > SearchHome > Detail(id=2)", native: "2,2,0")

        selectTab("Home")
        expectState("SearchHome > Detail(id=2) > Home > Detail(id=1)", native: "2,2,0")
        expectVisible("ViewModel taps: 1")
        XCTAssertTrue(isTabSelected("Home"))

        tapBack()
        expectState("SearchHome > Detail(id=2) > Home", native: "1,2,0")
    }

    /// A native pop in the selected tab removes the last entries of the back stack.
    func testSwipeBackAndReselect() {
        tap("Open detail 1")
        tap("Push detail 11")
        expectState("Home > Detail(id=1) > Detail(id=11)", native: "3,0,0")
        edgeSwipeBack()
        expectState("Home > Detail(id=1)", native: "2,0,0")
        tap("Push detail 11")
        expectState("Home > Detail(id=1) > Detail(id=11)", native: "3,0,0")
        selectTab("Home")
        expectState("Home", native: "1,0,0")
    }

    func testProgrammaticSelectionAndSheet() {
        tap("Go to Profile tab")
        expectState("Home > Profile", native: "1,0,1")
        XCTAssertTrue(isTabSelected("Profile"))
        tap("Go to Home tab")
        expectState("Profile > Home", native: "1,0,1")
        XCTAssertTrue(isTabSelected("Home"))

        tap("Open settings sheet")
        expectVisible("Settings sheet")
        expectVisible("backStack: Profile > Home > Settings")
        tap("Close sheet")
        expectState("Profile > Home", native: "1,0,1")
    }

    func testTabBarHiddenWhenPushed() {
        tap("Open focus")
        expectVisible("Tab bar hidden here")
        usleep(1_500_000)
        let search = tabButton("Search")
        XCTAssertFalse(search.exists && search.isHittable, "tab bar still shown")
        tapBack()
        expectState("Home", native: "1,0,0")
        XCTAssertTrue(tabButton("Search").waitForExistence(timeout: 5))
        XCTAssertTrue(tabButton("Search").isHittable)
    }

    /// Popped screens are released; only the roots of the tabs in the back stack stay alive.
    func testPoppedControllersAreReleased() {
        tap("Open detail 1")
        tap("Push detail 11")
        tapBack()
        tapBack()
        expectState("Home", native: "1,0,0")
        selectTab("Search")
        tap("Open detail 2")
        tapBack()
        expectState("Home > SearchHome", native: "1,1,0")
        selectTab("Home")
        tap("Open settings sheet")
        tap("Close sheet")
        expectState("SearchHome > Home", native: "1,1,0")
        sleep(2)
        tap("Live controllers: ?")
        sleep(3)
        let live = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH 'Live controllers: '")).firstMatch
        XCTAssertTrue(live.waitForExistence(timeout: 5))
        XCTAssertEqual(live.label, "Live controllers: 2", "leaked controllers")
    }
}
