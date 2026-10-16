package dev.seungmin.nav3.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import dev.seungmin.nav3.uikit.NativeNavDisplay
import dev.seungmin.nav3.uikit.NativeNavigation
import kotlinx.serialization.Serializable

@Serializable data object SignIn : Route

/**
 * Where the native container that shows the current screen sits: "root" when it is a direct child
 * of NativeNavigationViewController's root, "embedded" when it is inside Compose, or null where
 * not applicable.
 */
@Composable
expect fun containerPlacement(): String?

/** A sign-in flow on one display that is replaced by the tab display, as many apps start. */
@Composable
fun SwapApp() {
    var signedIn by rememberSaveable { mutableStateOf(false) }
    if (signedIn) {
        TabsApp()
    } else {
        val backStack = rememberNavBackStack(config, SignIn)
        NativeNavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            entryProvider = entryProvider {
                entry<SignIn>(metadata = NativeNavigation.title("Sign in")) {
                    Screen(backStack.joinToString(" > ")) {
                        BasicText("container=${containerPlacement() ?: "?"}")
                        Button("Continue") { signedIn = true }
                    }
                }
            },
        )
    }
}
