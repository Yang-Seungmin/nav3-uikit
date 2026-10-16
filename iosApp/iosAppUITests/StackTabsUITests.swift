import XCTest

/// Drives the single back stack tab sample (`NAV3_SAMPLE=stack-tabs`): the `commonui` recipe's
/// `TopLevelBackStack`, whose one back stack holds every visited tab. Every screen prints
/// "backStack: <whole stack>" and "native=<screens per tab>" at the top.
final class StackTabsUITests: SampleUITestCase {
    override var sample: String? { "stack-tabs" }

    /// The whole back stack and how many screens each tab's UINavigationController holds.
    private func expectState(_ backStack: String, native: String, file: StaticString = #filePath, line: UInt = #line) {
        expectVisible("backStack: \(backStack)", file: file, line: line)
        expectVisible("native=\(native)", file: file, line: line)
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
        settle()
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
        assertLiveControllers(2)
    }
}
