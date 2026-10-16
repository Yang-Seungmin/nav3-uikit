package dev.seungmin.nav3.uikit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

@Composable
internal actual fun <T : Any> PlatformTabDisplay(
    tabs: List<NativeTab<T>>,
    entries: List<List<NavEntry<T>>>,
    selectedTab: Int,
    onTabSelected: (index: Int) -> Unit,
    onBack: (tab: Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Box(Modifier.weight(1f)) {
            NavDisplay(entries = entries[selectedTab], onBack = { onBack(selectedTab) })
        }
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Tab) { onTabSelected(index) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        tab.title,
                        style = TextStyle(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}
