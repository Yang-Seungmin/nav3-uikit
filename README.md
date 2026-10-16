# nav3-ios-native

A Compose Multiplatform library that displays Navigation 3 back stacks with native UIKit containers on iOS.

- `NativeNavDisplay` shows one back stack in a `UINavigationController`. It has the parameters and both overloads (`backStack` and `entries`) of `NavDisplay`, so switching only requires changing the function name.
- `NativeTabDisplay` shows one back stack per tab behind a `UITabBarController`, with a `UINavigationController` in each tab.
- `NativeNavigationBarItems` lets a screen set the native navigation bar's title and trailing buttons from its content.
- `NativeNavigationViewController` is the iOS root view controller for apps built on these displays. It is required for the tab bar to appear on an open iPhone Duo (see below).

The library targets Android, JVM, `iosArm64`, and `iosSimulatorArm64`, so it can be used from common code. On Android and JVM, `NativeNavDisplay` passes every parameter to `NavDisplay`, and `NativeTabDisplay` shows the selected tab's `NavDisplay` above the `tabBar` slot, which defaults to a plain row of tab icons and titles. There, back at the root of any tab but the first selects the first tab.

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
val home = rememberNavBackStack(config, Home)
val search = rememberNavBackStack(config, SearchHome)
var selected by rememberSaveable { mutableIntStateOf(0) }

NativeTabDisplay(
    tabs = listOf(
        NativeTab(home, "Home", icon = "house"),            // icon: SF Symbol name
        NativeTab(search, "Search", icon = painterResource(Res.drawable.search)), // drawn as a template image on iOS
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

## How it works on iOS

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
- The caller owns the selection. Tapping a tab calls `onTabSelected`; changing `selectedTab` selects the tab in UIKit. Tapping the selected tab again makes UIKit pop that tab to its root, which reaches the back stack through `onBack(tab)` like any other native pop.
- Every tab's entries stay decorated while the tab is hidden, so `remember`, `rememberSaveable`, and ViewModels survive tab switches. The library prefixes each entry's `contentKey` with its tab, so the same key on two tabs gets separate saveable state and a separate ViewModel.
- A back stack can change while its tab is hidden, for example to open a screen in another tab. The tab shows the new stack when it is selected.
- `NativeNavigation.tabBarHidden()` metadata hides the tab bar while that entry is pushed above its tab's root, as detail screens in many apps do.
- A tab's icon is the SF Symbol `systemImage` when it is set, and the `icon` painter otherwise. iOS draws the painter as a template image, so the tab bar tints it.

### iPhone Duo

iPhone Duo can move the navigation bar items and the tab bar into a vertical bar on one side of the display (the trailing side in the simulator), with the tab bar at the bottom of it. Because the displays use the system containers, UIKit does this layout itself: the tab buttons and the back button move into the vertical bar, and the bar's width reaches Compose as a leading or trailing safe area inset, so content padded with `WindowInsets.safeDrawing` stays clear of it.

This only works when the native container is not embedded in Compose. A `UITabBarController` placed inside a `ComposeUIViewController` through `UIKitViewController` interop shows its vertical tab bar on the closed (outer) display, but on the open inner display the system reserves the vertical bar and draws no tabs in it. A `UITabBarController` that is a direct child of a plain `UIViewController` is laid out correctly. `NativeNavigationViewController` provides that structure: the first `NativeTabDisplay` or `NativeNavDisplay` composed inside it hands its container to the root view controller, which places it next to the Compose view instead of inside it. When that display leaves composition, the display that has waited longest takes its place, so replacing a sign-in display with the main tabs keeps the tab bar in the root. The root display's `modifier` is ignored, because the container always fills the screen.

`UITraitCollection.verticalBarEdge` and the other iPhone Duo APIs are iOS 27.1 SDK API. Kotlin/Native binds UIKit from the SDK of the Xcode its toolchain was built with (see `targetToolchain` in `~/.konan/kotlin-native-prebuilt-*/konan/konan.properties`), so with an older binding Kotlin code reads them through key-value coding, as `sample/src/iosMain/.../VerticalBarEdge.kt` does. The vertical bar layout itself does not depend on the binding: the app target is linked by Xcode against its own SDK.

## Constraints

- Shared element transitions between screens, the transition parameters, and scenes other than overlays do not apply on iOS, because each screen is a separate composition and UIKit animates the transitions. An overlay scene that shows more than one entry is not supported; its entries are pushed.
- Sheet and overlay entries must come after all pushed entries in the back stack. A pushed entry that follows a sheet or an overlay is presented modally too.
- A sheet entry on a hidden tab is not presented until that tab is selected; until then the coordinator retries on every main-loop turn.
- Kotlin/Native's GC cannot collect reference cycles that pass through Objective-C objects. Nothing reachable from a hosted controller's content lambda, a `UITab`'s view controller provider, or a UIKit delegate may hold a strong reference to `EntryHost` or a coordinator. Breaking this rule leaks popped screens. `testPoppedControllersAreReleased` in both UI test classes checks it.

## Layout

- `nav3-uikit/`: the library. Targets Android, JVM, `iosArm64`, and `iosSimulatorArm64`. Android and JVM share the `nonIos` source set.
- `sample/`: the sample app. `NAV3_SAMPLE=tabs` starts the tab sample instead of the single-stack sample, and `NAV3_SAMPLE=swap` starts a sign-in display that the tab sample replaces. `NAV3_HOST=interop` uses a plain `ComposeUIViewController` as the root, which embeds the native containers in Compose.
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

`./gradlew :nav3-uikit:publishToMavenLocal` publishes `dev.seungmin.nav3:nav3-uikit` with Android, JVM, and iOS variants, so an Android, desktop, or Kotlin Multiplatform app depends on that one coordinate and Gradle picks the variant. The version is `0.1.0-SNAPSHOT` unless the `nav3.version` Gradle property sets another.

- `NativeNavigationUITests` drives the single-stack sample and checks the `backStack: ...` label at the top of each screen. Setting `TEST_RUNNER_NAV3_DECORATORS` to `none`, `saveable`, or `viewmodel` changes which decorators the sample uses.
- `TabNavigationUITests` drives the tab sample: tab switching, per-tab state, reselecting a tab, programmatic selection, pushing into a hidden tab, sheets, a screen that hides the tab bar, where the tab container sits, and controller release.
- `StandardAppUITests` drives the single-stack sample's Lab screen: a back the app refuses, a `NavigationBackHandler` that receives native back, a `DialogSceneStrategy` dialog, a screen without native bar metadata, bar items set from content, and controller release.
- `RootSwapUITests` replaces the sign-in display with the tab display and checks where the tab container ends up.
- `VerticalBarUITests` reads the vertical bar edge each screen reports and checks that the tab buttons and the back button sit on that edge and that the Compose content stays clear of them. Where the device reports no vertical bar, it checks that the tab bar is at the bottom.
- Setting `TEST_RUNNER_NAV3_HOST=interop` runs the tests with the containers embedded in Compose.
- The tests find the back button by its `BackButton` identifier and the tabs by label, not through `app.navigationBars` or `app.tabBars`, because on iPhone Duo both move into the vertical bar.

Neither `simctl` nor XCUITest has a fold control, and the outer display of the closed simulator does not rotate, so `testTabBarPlacementLandscape` skips itself there. A new iPhone Duo simulator starts closed, showing the outer display, so the command above covers the closed device. The open device has to be set up by hand before running the tests. A simulator that reached the inner display after its display power was toggled with `xcrun simctl io <udid> screenConfig` did not accept edge swipe gestures, even in the single-stack sample, so swipe test failures in that state say nothing about this library.
