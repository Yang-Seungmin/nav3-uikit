package dev.seungmin.nav3.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.seungmin.nav3.uikit.NativeNavigation
import dev.seungmin.nav3.uikit.NativeTabDisplay
import dev.seungmin.nav3.uikit.NativeTabRoot
import dev.seungmin.nav3.uikit.SheetDetent

/**
 * The top-level back stack of Navigation 3's `commonui` recipe, unchanged in behavior: one back
 * stack per tab, flattened into [backStack] in the order the tabs were last selected.
 */
class TopLevelBackStack<T : Any>(startKey: T) {
    private val topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> =
        linkedMapOf(startKey to mutableStateListOf(startKey))

    var topLevelKey by mutableStateOf(startKey)
        private set

    val backStack = mutableStateListOf(startKey)

    private fun updateBackStack() = backStack.apply {
        clear()
        addAll(topLevelStacks.flatMap { it.value })
    }

    fun addTopLevel(key: T) {
        val stack = topLevelStacks.remove(key) ?: mutableStateListOf(key)
        topLevelStacks[key] = stack
        topLevelKey = key
        updateBackStack()
    }

    fun add(key: T) {
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    fun removeLast() {
        val removedKey = topLevelStacks[topLevelKey]?.removeLastOrNull()
        topLevelStacks.remove(removedKey)
        topLevelKey = topLevelStacks.keys.last()
        updateBackStack()
    }
}

/**
 * The `commonui` recipe's app with its Compose tab bar and `NavDisplay` replaced by one
 * [NativeTabDisplay] call that receives the same back stack and callbacks.
 */
@Composable
fun StackTabsApp() {
    CompositionLocalProvider(LocalAccent provides Color(0xFF1E8C5A)) {
        val topLevel = remember { TopLevelBackStack<NavKey>(Home) }
        val backStack = topLevel.backStack
        val tabs = listOf(
            NativeTabRoot(Home, "Home", icon = "house"),
            NativeTabRoot(SearchHome, "Search", icon = "magnifyingglass"),
            NativeTabRoot(Profile, "Profile", icon = "person.crop.circle"),
        )
        val describe = { backStack.joinToString(" > ") }

        NativeTabDisplay(
            backStack = backStack,
            tabs = tabs,
            onTabSelected = { topLevel.addTopLevel(it) },
            modifier = Modifier.fillMaxSize(),
            onBack = { topLevel.removeLast() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Home>(metadata = NativeNavigation.title("Home", large = true)) {
                    StackTabScreen(describe()) {
                        StateCounters()
                        Button("Open detail 1") { topLevel.add(Detail(1)) }
                        Button("Open settings sheet") { topLevel.add(Settings) }
                        Button("Open focus") { topLevel.add(Focus) }
                        Button("Go to Profile tab") { topLevel.addTopLevel(Profile) }
                        LiveControllersButton()
                    }
                }
                entry<SearchHome>(metadata = NativeNavigation.title("Search", large = true)) {
                    StackTabScreen(describe()) {
                        Button("Open detail 2") { topLevel.add(Detail(2)) }
                    }
                }
                entry<Profile>(metadata = NativeNavigation.title("Profile", large = true)) {
                    StackTabScreen(describe()) {
                        Button("Go to Home tab") { topLevel.addTopLevel(Home) }
                    }
                }
                entry<Detail>(metadata = { NativeNavigation.title("Detail ${it.id}") }) { key ->
                    val vm = viewModel { DetailViewModel(key.id) }
                    StackTabScreen(describe()) {
                        Button("ViewModel taps: ${vm.taps}") { vm.taps++ }
                        Button("Push detail ${key.id + 10}") { topLevel.add(Detail(key.id + 10)) }
                    }
                }
                entry<Settings>(
                    metadata = NativeNavigation.sheet(listOf(SheetDetent.Medium, SheetDetent.Large)),
                ) {
                    StackTabScreen(describe()) {
                        BasicText("Settings sheet", style = TextStyle(fontSize = 22.sp))
                        Button("Close sheet") { topLevel.removeLast() }
                    }
                }
                entry<Focus>(metadata = NativeNavigation.title("Focus") + NativeNavigation.tabBarHidden()) {
                    StackTabScreen(describe()) {
                        BasicText("Tab bar hidden here", style = TextStyle(fontSize = 22.sp))
                    }
                }
            },
        )
    }
}

/** [Screen] plus how many screens each tab's native stack holds, such as "native=2,1,0". */
@Composable
private fun StackTabScreen(backStack: String, content: @Composable () -> Unit) {
    Screen(backStack) {
        BasicText("native=${nativeStackSizes() ?: "?"}", style = TextStyle(fontSize = 12.sp, color = Color.Gray))
        content()
    }
}
