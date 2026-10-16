package dev.seungmin.nav3.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.seungmin.nav3.uikit.LocalNativeNavigationBar
import dev.seungmin.nav3.uikit.NativeBarButton
import dev.seungmin.nav3.uikit.NativeNavigation
import dev.seungmin.nav3.uikit.NativeNavigationBarItems
import kotlinx.serialization.Serializable

// Screens that exercise how the displays treat standard Navigation 3 apps: back that the app
// refuses or handles itself, dialog scenes, and screens with and without the native bar.

@Serializable data object Lab : Route

/** A screen the app will not leave while it is locked; its onBack leaves the back stack alone. */
@Serializable data object Locked : Route

/** A screen with unsaved changes, guarded by a NavigationBackHandler. */
@Serializable data object Editor : Route

/** A DialogSceneStrategy dialog. */
@Serializable data object Confirm : Route

/** A screen without native bar metadata, like a screen that draws its own top bar. */
@Serializable data object Plain : Route

/** A screen that sets the native bar from its content. */
@Serializable data object Toolbar : Route

/** Whether [Locked] refuses back; reset each time it is opened. */
object LockState {
    var locked by mutableStateOf(true)
}

fun EntryProviderScope<NavKey>.labEntries(backStack: MutableList<NavKey>, describe: () -> String) {
    entry<Lab>(metadata = NativeNavigation.title("Lab")) {
        Screen(describe()) {
            Button("Open locked") {
                LockState.locked = true
                backStack += Locked
            }
            Button("Open editor") { backStack += Editor }
            Button("Open dialog") { backStack += Confirm }
            Button("Open plain") { backStack += Plain }
            Button("Open toolbar") { backStack += Toolbar }
        }
    }
    entry<Locked>(metadata = NativeNavigation.title("Locked")) {
        Screen(describe()) {
            BasicText(if (LockState.locked) "Back is refused" else "Back is allowed")
            Button("Unlock") { LockState.locked = false }
        }
    }
    entry<Editor>(metadata = NativeNavigation.title("Editor")) {
        var dirty by remember { mutableStateOf(false) }
        var confirming by remember { mutableStateOf(false) }
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = dirty,
            onBackCompleted = { confirming = true },
        )
        Screen(describe()) {
            BasicText(if (dirty) "Unsaved changes" else "No changes")
            Button("Make changes") { dirty = true }
            if (confirming) {
                BasicText("Discard changes?", style = TextStyle(fontSize = 20.sp))
                Button("Discard") {
                    dirty = false
                    confirming = false
                    backStack.removeLastOrNull()
                }
                Button("Keep editing") { confirming = false }
            }
        }
    }
    entry<Confirm>(metadata = DialogSceneStrategy.dialog()) {
        Column(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("Dialog content", style = TextStyle(fontSize = 20.sp))
            Button("Close dialog") { backStack.removeLastOrNull() }
        }
    }
    entry<Plain> {
        Screen(describe()) {
            BasicText(
                if (LocalNativeNavigationBar.current) "Native bar available" else "No native bar here",
            )
            Button("Back") { backStack.removeLastOrNull() }
        }
    }
    entry<Toolbar> {
        var count by remember { mutableIntStateOf(0) }
        NativeNavigationBarItems(
            title = "Toolbar $count",
            trailing = listOf(NativeBarButton("Add", systemImage = "plus") { count++ }),
        )
        Screen(describe()) {
            BasicText("Count: $count")
        }
    }
}
