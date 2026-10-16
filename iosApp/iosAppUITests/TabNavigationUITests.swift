import XCTest

/// Drives the tab sample (`NAV3_SAMPLE=tabs`): one Navigation 3 back stack per tab inside a
/// native UITabBarController. Every screen prints "backStack: <tab>: <stack>" at the top.
///
/// The tests find tab buttons and the back button by label and identifier rather than through
/// `app.tabBars` and `app.navigationBars`, because on iPhone Duo the system moves both into the
/// vertical bar.
final class TabNavigationUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["NAV3_SAMPLE"] = "tabs"
        if let host = ProcessInfo.processInfo.environment["NAV3_HOST"] {
            app.launchEnvironment["NAV3_HOST"] = host
        }
        app.launch()
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
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

    /// Whether the tab bar marks `name` as selected. Waits, because the selection indicator
    /// animates, and checks every match, because the tab bar can expose a tab more than once.
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

    private func expectBackStack(_ expected: String, file: StaticString = #filePath, line: UInt = #line) {
        let label = "backStack: \(expected)"
        let predicate = NSPredicate(format: "exists == true AND hittable == true")
        let match = expectation(for: predicate, evaluatedWith: element(label))
        if XCTWaiter().wait(for: [match], timeout: 5) != .completed {
            snapshot("failure")
            XCTFail("expected \(label)", file: file, line: line)
        }
    }

    private func expectVisible(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        let predicate = NSPredicate(format: "exists == true AND hittable == true")
        let match = expectation(for: predicate, evaluatedWith: element(label))
        if XCTWaiter().wait(for: [match], timeout: 5) != .completed {
            snapshot("failure")
            XCTFail("expected visible \(label)", file: file, line: line)
        }
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func edgeSwipeBack() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    func testEachTabKeepsItsOwnStack() {
        expectBackStack("Home: Home")
        tap("Open detail 1")
        expectBackStack("Home: Home > Detail(id=1)")
        snapshot("home-detail")

        selectTab("Search")
        expectBackStack("Search: SearchHome")
        tap("Open detail 1")
        expectBackStack("Search: SearchHome > Detail(id=1)")
        tap("ViewModel taps: 0")
        expectVisible("ViewModel taps: 1")

        // Detail(1) is on both stacks, but each tab has its own ViewModel for it.
        selectTab("Home")
        expectBackStack("Home: Home > Detail(id=1)")
        expectVisible("ViewModel taps: 0")

        selectTab("Search")
        expectBackStack("Search: SearchHome > Detail(id=1)")
        expectVisible("ViewModel taps: 1")
        XCTAssertTrue(isTabSelected("Search"))
    }

    func testStateSurvivesTabSwitch() {
        tap("remember: 0")
        tap("rememberSaveable: 0")
        selectTab("Profile")
        expectBackStack("Profile: Profile")
        selectTab("Home")
        expectBackStack("Home: Home")
        expectVisible("remember: 1")
        expectVisible("rememberSaveable: 1")
    }

    func testBackInsideTab() {
        tap("Open detail 1")
        tap("Push detail 2")
        expectBackStack("Home: Home > Detail(id=1) > Detail(id=2)")
        snapshot("detail-2")
        edgeSwipeBack()
        expectBackStack("Home: Home > Detail(id=1)")
        tapBack()
        expectBackStack("Home: Home")
    }

    /// Tapping the selected tab again pops its navigation stack to the root, which UIKit does on
    /// its own; the back stack has to follow.
    func testReselectingTabPopsToRoot() {
        tap("Open detail 1")
        tap("Push detail 2")
        expectBackStack("Home: Home > Detail(id=1) > Detail(id=2)")
        selectTab("Home")
        expectBackStack("Home: Home")
    }

    func testProgrammaticTabSelection() {
        tap("Go to Profile tab")
        expectBackStack("Profile: Profile")
        XCTAssertTrue(isTabSelected("Profile"))
        tap("Go to Home tab")
        expectBackStack("Home: Home")
        XCTAssertTrue(isTabSelected("Home"))
    }

    func testPushIntoHiddenTab() {
        tap("Push detail 7 in Search tab")
        expectBackStack("Home: Home")
        selectTab("Search")
        expectBackStack("Search: SearchHome > Detail(id=7)")
        XCTAssertTrue(app.navigationBars["Detail 7"].waitForExistence(timeout: 5))
        tapBack()
        expectBackStack("Search: SearchHome")
    }

    func testSheetInsideTab() {
        tap("Open settings sheet")
        expectBackStack("Home: Home > Settings")
        snapshot("settings-sheet")
        tap("Close sheet")
        expectBackStack("Home: Home")
    }

    func testNavigationBarHiddenInsideTab() {
        selectTab("Profile")
        tap("Open immersive")
        expectBackStack("Profile: Profile > Immersive")
        snapshot("immersive")
        tap("Back")
        expectBackStack("Profile: Profile")
    }

    /// Screens popped inside any tab must be released; only the three tab roots stay alive.
    func testPoppedControllersAreReleased() {
        tap("Open detail 1")
        tap("Push detail 2")
        tap("Pop to root")
        expectBackStack("Home: Home")
        selectTab("Search")
        tap("Open detail 1")
        tap("Push detail 2")
        tapBack()
        tapBack()
        expectBackStack("Search: SearchHome")
        selectTab("Home")
        tap("Open settings sheet")
        tap("Close sheet")
        expectBackStack("Home: Home")
        sleep(2)
        tap("Live controllers: ?")
        sleep(3)
        let live = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH 'Live controllers: '")).firstMatch
        XCTAssertTrue(live.waitForExistence(timeout: 5))
        XCTAssertEqual(live.label, "Live controllers: 3", "leaked controllers")
    }

    func testContainerPlacement() {
        let expected = ProcessInfo.processInfo.environment["NAV3_HOST"] == "interop"
            ? "container=embedded" : "container=root"
        XCTAssertTrue(element(expected).waitForExistence(timeout: 5))
    }

    func testTabBarHiddenWhenPushed() {
        tap("Open focus")
        expectVisible("Tab bar hidden here")
        usleep(1_500_000)
        let search = tabButton("Search")
        XCTAssertFalse(search.exists && search.isHittable, "tab bar still shown")
        snapshot("focus")
        tapBack()
        expectBackStack("Home: Home")
        XCTAssertTrue(tabButton("Search").waitForExistence(timeout: 5))
        XCTAssertTrue(tabButton("Search").isHittable)
    }
}
