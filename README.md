# nav3-ios-native

A Compose Multiplatform library that displays Navigation 3 back stacks with native UIKit containers on iOS.

- `NativeNavDisplay` shows one back stack in a `UINavigationController`. It has the parameters and both overloads (`backStack` and `entries`) of `NavDisplay`, so switching only requires changing the function name.
- `NativeTabDisplay` shows tabs behind a `UITabBarController`, with a `UINavigationController` in each tab. One overload takes the single back stack an app passes to `NavDisplay`, with the tabs' root keys; the other takes one back stack per tab.
- `NativeNavigationBarItems` lets a screen set the native navigation bar's title and trailing buttons from its content.
- `NativeNavigationViewController` is the iOS root view controller for apps built on these displays. It is required for the `NativeTabDisplay` tab bar to appear on an open iPhone Duo (see below).

The library targets Android, JVM, `iosArm64`, and `iosSimulatorArm64`, so it can be used from common code. On Android and JVM, `NativeNavDisplay` passes every parameter to `NavDisplay`, and `NativeTabDisplay` shows a `NavDisplay` above the `tabBar` slot, which defaults to a plain row of tab icons and titles. The single back stack overload passes the whole back stack to that `NavDisplay`, so back behaves as it did before. The per-tab overload shows the selected tab's back stack, and back at the root of any tab but the first selects the first tab.

```kotlin
NativeNavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = entryProvider {
        entry<Home>(metadata = NativeNavigation.title("Home", large = true)) { HomeScreen() }
        entry<Detail>(metadata = { NativeNavigation.title("Detail ${it.id}") }) { DetailScreen(it.id) }
        entry<Settings>(metadata = NativeNavigation.sheet(listOf(SheetDetent.Medium, SheetDetent.Large))) { SettingsSheet() }
        entry<Immersive>(metadata = NativeNavigation.navigationBarHidden()) { ImmersiveScreen() }
        entry<ConfirmDelete>(metadata = DialogSceneStrategy.dialog()) { ConfirmDeleteDialog() }
    },
    sceneStrategies = listOf(DialogSceneStrategy()),
)
```

```kotlin
// A screen that uses the native bar on iOS and its own top bar elsewhere.
if (LocalNativeNavigationBar.current) {
    NativeNavigationBarItems(title = note.title, trailing = listOf(NativeBarButton("Edit", systemImage = "pencil") { onEdit() }))
} else {
    TopAppBar(title = { Text(note.title) }, actions = { /* ... */ })
}
```

```kotlin
// The back stack of Navigation 3's commonui recipe: every visited tab in one list,
// e.g. [Home, Detail(1), Search, Detail(2)].
val topLevelBackStack = remember { TopLevelBackStack<NavKey>(Home) }

NativeTabDisplay(
    backStack = topLevelBackStack.backStack,
    tabs = listOf(
        NativeTabRoot(Home, "Home", icon = "house"),        // icon: SF Symbol name
        NativeTabRoot(Search, "Search", icon = painterResource(Res.drawable.search)), // drawn as a template image on iOS
    ),
    onTabSelected = { topLevelBackStack.addTopLevel(it) },  // what the app's own tab bar called
    onBack = { topLevelBackStack.removeLast() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider = entryProvider { /* entries for every tab */ },
)
```

```kotlin
// One back stack per tab.
val home = rememberNavBackStack(config, Home)
val search = rememberNavBackStack(config, SearchHome)
var selected by rememberSaveable { mutableIntStateOf(0) }

NativeTabDisplay(
    tabs = listOf(
        NativeTab(home, "Home", icon = "house"),
        NativeTab(search, "Search", icon = painterResource(Res.drawable.search)),
    ),
    selectedTab = selected,
    onTabSelected = { selected = it },
    onBack = { tab -> listOf(home, search)[tab].removeLastOrNull() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider = entryProvider { /* entries for every tab */ },
)
```

```kotlin
// iosMain
fun MainViewController(): UIViewController = NativeNavigationViewController { App() }
```

## Moving from NavDisplay

- A `NavDisplay` call becomes a `NativeNavDisplay` call by changing the function name. Both overloads and every parameter are the same.
- `NavDisplay` has no tabs. An app's tab bar is its own UI next to `NavDisplay`, so a native tab bar needs the tab list, and the change is to replace the app's tab bar and `NavDisplay` with one `NativeTabDisplay` call. The app's navigation state stays as it is:
  - An app that passes one back stack with the tabs' root keys in it, as the `TopLevelBackStack` of Navigation 3's `commonui` recipe does, passes that back stack to the single back stack overload. The selected tab is read from the back stack, and `onTabSelected` receives what the app's tab bar called.
  - An app that keeps one back stack per tab, as Navigation 3's `multiplestacks` recipe does, passes those back stacks to the `NativeTab` overload and keeps passing its selection.
  - On Android and JVM, the app's own tab bar goes in the `tabBar` slot.
- An app that keeps its Compose tab bar can change only the function name. Its screens then use native navigation, and the tab bar stays a Compose view: it is not a `UITabBar` and does not move into the iPhone Duo vertical bar.

Limits:

- The tabs must be keys in the back stack. Tabs that one entry draws inside its own content, with the selection held in that screen's state, are not visible to the display, so it cannot give them a native tab bar. Such an app either makes each tab's root a key or keeps its Compose tab bar.
- The single back stack overload has no `entries` form. `NavEntry` does not expose its key, so the display cannot find the tab roots among decorated entries. An app that builds `entries` from several back stacks already has one back stack per tab and uses the `NativeTab` overload.
- The back stack of the single back stack overload must start with a tab's root key. An app that shows a sign-in flow in the same back stack before the tabs picks the display by the root: `if (backStack.first() in tabKeys) NativeTabDisplay(...) else NativeNavDisplay(...)`.
- UIKit has no back action at a tab's root. Back from one tab's root to the tab before it in the back stack, which `NavDisplay` performs, happens on Android and JVM only.

## How it works on iOS

- An embedded display fills the space its `modifier` leaves it, because a UIKit container has no content size for Compose to measure. This matches `NavDisplay` showing screens that fill the screen, so a call site without `Modifier.fillMaxSize()` still shows its screens.
- Each entry is placed in its own `ComposeUIViewController`, and those controllers are kept in the `UINavigationController` stack. Entries with sheet metadata are presented modally on top of the stack.
- `sceneStrategies` run as in `NavDisplay`. An entry that a strategy shows in an `OverlayScene`, such as a `DialogSceneStrategy` dialog, is presented over a clear full-screen controller with the scene's own content, so the screens below stay visible. Entries of other scenes are pushed one by one.
- The back stack is the source of truth. When the back stack changes, the entries are compared against the hosted controllers by `contentKey` and position: the common prefix is kept and the rest is replaced.
- When UIKit removes a controller itself (edge swipe-back, the back button, dragging a sheet down), `onBack` is called once per removed entry. Cancelling a swipe partway changes nothing. If the app leaves its back stack unchanged, for example because the screen is busy, the display puts the removed screens back. A tab the user picks but the app does not select is undone the same way.
- When the top entry's content has an enabled back handler (`NavigationBackHandler`, `BackHandler`, an open `ModalBottomSheet`, and so on), UIKit does not pop it. The back button, the edge swipe, the back button's history menu, and dragging a sheet down all go to that handler, as system back does under `NavDisplay`.
- The native navigation bar is shown only for entries that ask for it, with `NativeNavigation.title` metadata or a `NativeNavigationBarItems` call. Screens that draw their own top bar look as they do under `NavDisplay`. `LocalNativeNavigationBar` tells a screen whether it can use the native bar.
- The `configure` block of `NativeNavigationViewController` also configures every entry's controller. Entry controllers skip Compose's `Info.plist` check, which the app's root controller has already made.
- Decorators run through Navigation 3's `rememberDecoratedNavEntries` in the host composition. Each entry's content is composed inside its own controller. Once a popped entry finishes its transition, its content leaves composition, and Navigation 3 calls the decorators' `onPop`. Because of this, the saveable state and ViewModel decorators work unchanged.
- Since each controller is a separate composition, the CompositionLocals from the display's call site (theme, DI, and so on) are re-provided to each one. The controller's own platform locals (lifecycle, `LocalUIViewController`, and so on) take precedence over them.

### Tabs

- Each tab is the same navigation coordinator `NativeNavDisplay` uses, so pushes, swipe-back, sheets, and metadata work inside every tab.
- With the single back stack overload, each tab shows its part of the back stack, from its root key up to the next tab's root key. The tab whose root key comes last is selected. A tab that occurs twice shows its last part, and a tab that is not in the back stack is empty. Tapping such a tab calls `onTabSelected` without switching, and UIKit selects the tab once the back stack holds it, so the empty tab is never shown. Its entries are decorated once in back stack order, as `NavDisplay` decorates them, so an entry that leaves the back stack loses its state there too.
- With the `NativeTab` overload, the caller owns the selection. Tapping a tab calls `onTabSelected`; changing `selectedTab` selects the tab in UIKit. Tapping the selected tab again makes UIKit pop that tab to its root, which reaches the back stack through `onBack(tab)` like any other native pop.
- With the `NativeTab` overload, every tab's entries stay decorated while the tab is hidden, so `remember`, `rememberSaveable`, and ViewModels survive tab switches. The library prefixes each entry's `contentKey` with its tab, so the same key on two tabs gets separate saveable state and a separate ViewModel.
- A back stack can change while its tab is hidden, for example to open a screen in another tab. The tab shows the new stack when it is selected. Every tab can also get a new root while another tab is selected in the same frame, as when an app rebuilds its main tabs after a flow finishes.
- `NativeNavigation.tabBarHidden()` metadata hides the tab bar while that entry is pushed above its tab's root, as detail screens in many apps do.
- A tab's icon is the SF Symbol `systemImage` when it is set, and the `icon` painter otherwise. iOS draws the painter as a template image, so the tab bar tints it.

### iPhone Duo

iPhone Duo can move the navigation bar items and the tab bar into a vertical bar on one side of the display (the trailing side in the simulator), with the tab bar at the bottom of it. Because the displays use the system containers, UIKit does this layout itself: the tab buttons and the back button move into the vertical bar, and the bar's width reaches Compose as a leading or trailing safe area inset, so content padded with `WindowInsets.safeDrawing` stays clear of it.

This only works when the native container is not embedded in Compose. A `UITabBarController` placed inside a `ComposeUIViewController` through `UIKitViewController` interop shows its vertical tab bar on the closed (outer) display, but on the open inner display the system reserves the vertical bar and draws no tabs in it. A `UITabBarController` that is a direct child of a plain `UIViewController` is laid out correctly. `NativeNavigationViewController` provides that structure: the first `NativeTabDisplay` composed inside it hands its tab bar controller to the root view controller, which places it above the Compose view instead of inside it. When that display leaves composition, the tab display that has waited longest takes its place. The root tab display's `modifier` is ignored, because the container always fills the screen, and Compose content drawn outside that display is covered by it. `NativeNavDisplay` always stays embedded in Compose, so a sign-in display or an app's own Compose tab bar around a `NativeNavDisplay` look the same under either root controller.

`UITraitCollection.verticalBarEdge` and the other iPhone Duo APIs are iOS 27.1 SDK API. Kotlin/Native binds UIKit from the SDK of the Xcode its toolchain was built with (see `targetToolchain` in `~/.konan/kotlin-native-prebuilt-*/konan/konan.properties`), so with an older binding Kotlin code reads them through key-value coding, as `sample/src/iosMain/.../VerticalBarEdge.kt` does. The vertical bar layout itself does not depend on the binding: the app target is linked by Xcode against its own SDK.

## Constraints

- Shared element transitions between screens, the transition parameters, and scenes other than overlays do not apply on iOS, because each screen is a separate composition and UIKit animates the transitions. An overlay scene that shows more than one entry is not supported; its entries are pushed.
- Sheet and overlay entries must come after all pushed entries in the back stack. A pushed entry that follows a sheet or an overlay is presented modally too.
- A sheet entry on a hidden tab is not presented until that tab is selected; until then the coordinator retries on every main-loop turn.
- Kotlin/Native's GC cannot collect reference cycles that pass through Objective-C objects. Nothing reachable from a hosted controller's content lambda, a `UITab`'s view controller provider, or a UIKit delegate may hold a strong reference to `EntryHost` or a coordinator. Breaking this rule leaks popped screens. `testPoppedControllersAreReleased` in the UI test classes checks it.

## Layout

- `nav3-uikit/`: the library. Targets Android, JVM, `iosArm64`, and `iosSimulatorArm64`. Android and JVM share the `nonIos` source set.
- `sample/`: the sample app. `NAV3_SAMPLE=tabs` starts the tab sample instead of the single-stack sample, `NAV3_SAMPLE=swap` starts a sign-in display that the tab sample replaces, `NAV3_SAMPLE=restart` starts two tabs that get new roots when a flow finishes, and `NAV3_SAMPLE=stack-tabs` runs the `commonui` recipe's `TopLevelBackStack` with three tabs. In the single-stack sample, `NAV3_LAYOUT=plain` calls the display without a modifier and `NAV3_LAYOUT=bar` places it above a Compose bar of the app's own. `NAV3_HOST=interop` uses a plain `ComposeUIViewController` as the root, which embeds the native containers in Compose.
- `iosApp/`: the iOS app shell and XCUITest target. The Xcode project is generated from `project.yml` with XcodeGen. `NAV3_SAMPLE=swiftui-tabs` and `NAV3_SAMPLE=uikit-tabs` show a SwiftUI `TabView` and a plain `UITabBarController` without Compose, for comparing the system layout.

## Build and test

Requires JDK 21, the Android SDK, Xcode, and XcodeGen. The versions are in `gradle/libs.versions.toml`. iPhone Duo needs the iOS 27.1 simulator runtime.

```sh
./gradlew :nav3-uikit:compileKotlinIosSimulatorArm64 :nav3-uikit:compileKotlinJvm :nav3-uikit:compileAndroidMain
cd iosApp && xcodegen generate && cd ..
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone Duo' test
```

`./gradlew :nav3-uikit:jvmTest` runs the unit tests of how a single back stack is split into tabs.

`./gradlew :nav3-uikit:publishToMavenLocal` publishes `dev.seungmin.nav3:nav3-uikit` with Android, JVM, and iOS variants, so an Android, desktop, or Kotlin Multiplatform app depends on that one coordinate and Gradle picks the variant. The version is `0.1.0-SNAPSHOT` unless the `nav3.version` Gradle property sets another.

- `NativeNavigationUITests` drives the single-stack sample and checks the `backStack: ...` label at the top of each screen. Setting `TEST_RUNNER_NAV3_DECORATORS` to `none`, `saveable`, or `viewmodel` changes which decorators the sample uses.
- `TabNavigationUITests` drives the tab sample: tab switching, per-tab state, reselecting a tab, programmatic selection, pushing into a hidden tab, sheets, a screen that hides the tab bar, where the tab container sits, and controller release.
- `StandardAppUITests` drives the single-stack sample's Lab screen: a back the app refuses, a `NavigationBackHandler` that receives native back, a `DialogSceneStrategy` dialog, a screen without native bar metadata, bar items set from content, and controller release.
- `RootSwapUITests` replaces the sign-in display with the tab display, checks where each container ends up, and signs out and in again to check that the root holds no container of a display that left.
- `DefaultSizeUITests` and `ComposeBarUITests` run the single-stack sample without a modifier and above a Compose bar.
- `TabRestartUITests` gives every tab a new root, with and without selecting another tab in the same frame, and checks that each tab's `UINavigationController` holds as many screens as its back stack.
- `StackTabsUITests` drives the single back stack tab sample: each tab's part of the back stack, selecting a tab that is not in the back stack, swipe-back, reselecting a tab, programmatic selection, a sheet, a screen that hides the tab bar, and controller release.
- `VerticalBarUITests` reads the vertical bar edge each screen reports and checks that the tab buttons and the back button sit on that edge and that the Compose content stays clear of them. Where the device reports no vertical bar, it checks that the tab bar is at the bottom.
- Setting `TEST_RUNNER_NAV3_HOST=interop` runs the tests with the containers embedded in Compose.
- The tests find the back button by its `BackButton` identifier and the tabs by label, not through `app.navigationBars` or `app.tabBars`, because on iPhone Duo both move into the vertical bar.

Neither `simctl` nor XCUITest has a fold control, and the outer display of the closed simulator does not rotate, so `testTabBarPlacementLandscape` skips itself there. A new iPhone Duo simulator starts closed, showing the outer display, so the command above covers the closed device. The open device has to be set up by hand before running the tests. A simulator that reached the inner display after its display power was toggled with `xcrun simctl io <udid> screenConfig` did not accept edge swipe gestures, even in the single-stack sample, so swipe test failures in that state say nothing about this library.
