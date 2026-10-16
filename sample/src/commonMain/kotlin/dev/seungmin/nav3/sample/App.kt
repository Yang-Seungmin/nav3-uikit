package dev.seungmin.nav3.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.seungmin.nav3.uikit.NativeNavDisplay
import dev.seungmin.nav3.uikit.NativeNavigation
import dev.seungmin.nav3.uikit.SheetDetent
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable sealed interface Route : NavKey

@Serializable data object Home : Route

@Serializable data class Detail(val id: Int) : Route

@Serializable data object Settings : Route

@Serializable data object Filter : Route

@Serializable data object Immersive : Route

@Serializable data object SearchHome : Route

@Serializable data object Profile : Route

internal val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class, Home.serializer())
            subclass(Detail::class, Detail.serializer())
            subclass(Settings::class, Settings.serializer())
            subclass(Filter::class, Filter.serializer())
            subclass(Immersive::class, Immersive.serializer())
            subclass(SearchHome::class, SearchHome.serializer())
            subclass(Profile::class, Profile.serializer())
            subclass(Lab::class, Lab.serializer())
            subclass(Locked::class, Locked.serializer())
            subclass(Editor::class, Editor.serializer())
            subclass(Confirm::class, Confirm.serializer())
            subclass(Plain::class, Plain.serializer())
            subclass(Toolbar::class, Toolbar.serializer())
            subclass(SignIn::class, SignIn.serializer())
            subclass(Focus::class, Focus.serializer())
        }
    }
}

/** Provided above NativeNavDisplay; screens read it to show that locals reach every entry. */
val LocalAccent = staticCompositionLocalOf { Color.Gray }

/** Events the PoC checks: ViewModel creation/clearing and back stack changes. */
object EventLog {
    val events = mutableStateListOf<String>()

    fun log(message: String) {
        events += message
        platformLog("[nav3-poc] $message")
    }
}

expect fun platformLog(message: String)

/** Number of hosted view controllers still alive after a GC, or null where not applicable. */
expect suspend fun liveViewControllerCount(): Int?

expect fun launchFlag(name: String): String?

class DetailViewModel(private val id: Int) : ViewModel() {
    var taps by mutableIntStateOf(0)

    init {
        EventLog.log("DetailViewModel($id) created")
    }

    override fun onCleared() {
        EventLog.log("DetailViewModel($id) cleared")
    }
}

@Composable
fun App() {
    when (launchFlag("NAV3_SAMPLE")) {
        "tabs" -> TabsApp()
        "swap" -> SwapApp()
        "restart" -> RestartApp()
        "stack-tabs" -> StackTabsApp()
        else -> StackApp()
    }
}

@Composable
private fun StackApp() {
    CompositionLocalProvider(LocalAccent provides Color(0xFF2E6BE6)) {
        val backStack = rememberNavBackStack(config, Home)
        val describe = { backStack.joinToString(" > ") { it.toString() } }

        // NAV3_LAYOUT=plain passes no modifier, as a call site copied from NavDisplay may.
        // NAV3_LAYOUT=bar puts the display above a Compose bar of the app's own.
        val display: @Composable (Modifier) -> Unit = { modifier ->
            NativeNavDisplay(
                backStack = backStack,
                modifier = modifier,
                onBack = {
                    if (backStack.lastOrNull() == Locked && LockState.locked) {
                        EventLog.log("onBack refused")
                    } else {
                        backStack.removeLastOrNull()
                        EventLog.log("onBack -> ${describe()}")
                    }
                },
                entryDecorators = when (launchFlag("NAV3_DECORATORS")) {
                    "none" -> emptyList()
                    "saveable" -> listOf(rememberSaveableStateHolderNavEntryDecorator())
                    "viewmodel" -> listOf(rememberViewModelStoreNavEntryDecorator())
                    else -> listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    )
                },
                sceneStrategies = listOf(DialogSceneStrategy()),
                entryProvider = entryProvider {
                    labEntries(backStack, describe)
                    entry<Home>(metadata = NativeNavigation.title("Home", large = true)) {
                        Screen(describe()) {
                            var plain by remember { mutableIntStateOf(0) }
                            var saved by rememberSaveable { mutableIntStateOf(0) }
                            Button("remember: $plain") { plain++ }
                            Button("rememberSaveable: $saved") { saved++ }
                            Button("Open detail 1") { backStack += Detail(1) }
                            Button("Open settings sheet") { backStack += Settings }
                            Button("Open immersive") { backStack += Immersive }
                            Button("Push two at once") {
                                backStack += Detail(1)
                                backStack += Detail(2)
                            }
                            Button("Push ten") { repeat(10) { backStack += Detail(it + 1) } }
                            Button("Open lab") { backStack += Lab }
                            var live by remember { mutableStateOf<Int?>(null) }
                            val scope = rememberCoroutineScope()
                            Button("Live controllers: ${live ?: "?"}") {
                                scope.launch { live = liveViewControllerCount() }
                            }
                            EventList()
                        }
                    }
                    entry<Detail>(metadata = { NativeNavigation.title("Detail ${it.id}") }) { key ->
                        val vm = viewModel { DetailViewModel(key.id) }
                        Screen(describe()) {
                            Button("ViewModel taps: ${vm.taps}") { vm.taps++ }
                            Button("Push detail ${key.id + 1}") { backStack += Detail(key.id + 1) }
                            Button("Open settings sheet") { backStack += Settings }
                            Button("Pop to root") {
                                while (backStack.size > 1) backStack.removeLastOrNull()
                            }
                            Button("Replace with detail 100") {
                                backStack.removeLastOrNull()
                                backStack += Detail(100)
                            }
                        }
                    }
                    entry<Settings>(
                        metadata = NativeNavigation.sheet(listOf(SheetDetent.Medium, SheetDetent.Large)),
                    ) {
                        Screen(describe()) {
                            BasicText("Settings sheet", style = TextStyle(fontSize = 22.sp))
                            Button("Open filter sheet") { backStack += Filter }
                            Button("Close sheet") { backStack.removeLastOrNull() }
                        }
                    }
                    entry<Filter>(metadata = NativeNavigation.sheet()) {
                        Screen(describe()) {
                            BasicText("Filter sheet", style = TextStyle(fontSize = 22.sp))
                            Button("Close all sheets") {
                                while (backStack.lastOrNull() is Settings || backStack.lastOrNull() is Filter) {
                                    backStack.removeLastOrNull()
                                }
                            }
                        }
                    }
                    entry<Immersive>(metadata = NativeNavigation.navigationBarHidden()) {
                        Screen(describe()) {
                            BasicText("No navigation bar here", style = TextStyle(fontSize = 22.sp))
                            Button("Back") { backStack.removeLastOrNull() }
                        }
                    }
                },
            )
        }
        when (launchFlag("NAV3_LAYOUT")) {
            "plain" -> display(Modifier)
            "bar" -> Column(Modifier.fillMaxSize()) {
                display(Modifier.weight(1f))
                BasicText(
                    "App bar",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalAccent.current)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    style = TextStyle(color = Color.White),
                )
            }
            else -> display(Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun Screen(backStack: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            "backStack: $backStack",
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalAccent.current.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            style = TextStyle(color = LocalAccent.current, fontSize = 13.sp),
        )
        content()
    }
}

@Composable
internal fun Button(label: String, onClick: () -> Unit) {
    BasicText(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAccent.current, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = TextStyle(color = Color.White, fontSize = 16.sp),
    )
}

@Composable
internal fun EventList() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("Events", style = TextStyle(fontSize = 15.sp, color = Color.Gray))
        EventLog.events.takeLast(12).forEach {
            BasicText(it, style = TextStyle(fontSize = 12.sp, color = Color.Gray))
        }
    }
}
