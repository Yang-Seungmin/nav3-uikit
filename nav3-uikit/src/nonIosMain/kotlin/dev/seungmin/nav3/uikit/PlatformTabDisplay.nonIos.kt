package dev.seungmin.nav3.uikit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay

@Composable
internal actual fun <T : Any> PlatformTabDisplay(
    tabs: List<NativeTab<T>>,
    entries: List<List<NavEntry<T>>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    onBack: (tab: Int) -> Unit,
    sceneStrategies: List<SceneStrategy<T>>,
    tabBar: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Box(Modifier.weight(1f)) {
            NavDisplay(
                entries = entries[selectedTab],
                sceneStrategies = sceneStrategies,
                onBack = { onBack(selectedTab) },
            )
        }
        tabBar()
    }
}
