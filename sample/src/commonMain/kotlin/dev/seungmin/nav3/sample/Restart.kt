package dev.seungmin.nav3.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import dev.seungmin.nav3.uikit.NativeTab
import dev.seungmin.nav3.uikit.NativeTabDisplay
import kotlinx.serialization.Serializable

/** The root of one tab in one main session; a new session gives every tab a new root key. */
@Serializable data class TabRoot(val session: Int, val tab: Int) : Route

/**
 * How many screens each tab's native navigation stack holds, such as "1,2", or null where not
 * applicable. Compared with the back stacks, it shows screens UIKit kept that the app removed.
 */
@Composable
expect fun nativeStackSizes(): String?

/**
 * Main tabs that an app rebuilds when a flow finishes: every tab gets a new root and another tab
 * may be selected in the same frame, as when a purchase flow returns to the main screen.
 */
@Composable
fun RestartApp() {
    var session by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf("First", "Second")
    val stacks = remember(session) { names.indices.map { mutableStateListOf<NavKey>(TabRoot(session, it)) } }
    val describe = { tab: Int -> "${names[tab]}: " + stacks[tab].joinToString(" > ") { it.toString() } }
    val finish = { tab: Int ->
        session++
        selected = tab
    }

    NativeTabDisplay(
        tabs = names.mapIndexed { index, name -> NativeTab(stacks[index], name, icon = "circle") },
        selectedTab = selected,
        onTabSelected = { selected = it },
        modifier = Modifier.fillMaxSize(),
        onBack = { tab -> stacks[tab].removeLastOrNull() },
        entryProvider = entryProvider {
            entry<TabRoot> { key ->
                Screen(describe(key.tab)) {
                    BasicText("native=${nativeStackSizes() ?: "?"}")
                    Button("Open flow") { stacks[key.tab] += Detail(1) }
                    Button("Restart on First") { finish(0) }
                    Button("Restart on Second") { finish(1) }
                }
            }
            entry<Detail> {
                Screen(describe(selected)) {
                    Button("Finish on First") { finish(0) }
                    Button("Finish on Second") { finish(1) }
                }
            }
        },
    )
}
