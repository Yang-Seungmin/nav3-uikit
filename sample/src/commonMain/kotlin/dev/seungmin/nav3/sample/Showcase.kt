package dev.seungmin.nav3.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.seungmin.nav3.uikit.NativeBarButton
import dev.seungmin.nav3.uikit.NativeNavigation
import dev.seungmin.nav3.uikit.NativeNavigationBarItems
import dev.seungmin.nav3.uikit.NativeTab
import dev.seungmin.nav3.uikit.NativeTabDisplay
import dev.seungmin.nav3.uikit.SheetDetent
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

// A travel app with realistic content, used for the README's screenshots and recordings.
// NAV3_SHOWCASE opens it in a given state (trip, sheet, saved, or profile), or plays a short tour.

@Serializable data object ShowcaseDiscover : Route

@Serializable data object ShowcaseSaved : Route

@Serializable data object ShowcaseProfile : Route

@Serializable data class ShowcaseTrip(val id: Int) : Route

@Serializable data class ShowcasePlan(val id: Int) : Route

private class Destination(
    val name: String,
    val country: String,
    val tagline: String,
    val days: Int,
    val price: String,
    val rating: String,
    val from: Color,
    val to: Color,
)

private val destinations = listOf(
    Destination("Kyoto", "Japan", "Temples, tea houses, and maple-lined lanes", 7, "$1,240", "4.9", Color(0xFFFFA94D), Color(0xFFE8445A)),
    Destination("Reykjavík", "Iceland", "Northern lights over black sand beaches", 5, "$1,580", "4.8", Color(0xFF3BC9DB), Color(0xFF364FC7)),
    Destination("Lisbon", "Portugal", "Tiled streets, trams, and sunset viewpoints", 4, "$860", "4.7", Color(0xFFFFD43B), Color(0xFFF76707)),
    Destination("Patagonia", "Chile", "Granite peaks and glacier lakes", 10, "$2,150", "4.9", Color(0xFF8CE99A), Color(0xFF0B7285)),
    Destination("Santorini", "Greece", "Whitewashed cliffs above the Aegean", 6, "$1,320", "4.8", Color(0xFF74C0FC), Color(0xFF5F3DC4)),
    Destination("Marrakech", "Morocco", "Souks, riads, and desert nights", 5, "$940", "4.6", Color(0xFFFFA8A8), Color(0xFFA61E4D)),
)

private val Ink = Color(0xFF111114)
private val Muted = Color(0xFF6E6E76)
private val Page = Color(0xFFF2F2F7)

@Composable
fun ShowcaseApp() {
    val start = launchFlag("NAV3_SHOWCASE")
    val discover = rememberNavBackStack(
        config,
        *when (start) {
            "trip" -> arrayOf(ShowcaseDiscover, ShowcaseTrip(0))
            "sheet" -> arrayOf(ShowcaseDiscover, ShowcaseTrip(0), ShowcasePlan(0))
            else -> arrayOf(ShowcaseDiscover)
        },
    )
    val saved = rememberNavBackStack(config, ShowcaseSaved)
    val profile = rememberNavBackStack(config, ShowcaseProfile)
    val backStacks = listOf(discover, saved, profile)
    var selected by rememberSaveable {
        mutableIntStateOf(
            when (start) {
                "saved" -> 1
                "profile" -> 2
                else -> 0
            },
        )
    }
    val current = backStacks[selected]

    if (start == "tour") {
        // Changes only the back stacks and the selection; every transition on screen is the native one.
        LaunchedEffect(Unit) {
            delay(1500)
            discover += ShowcaseTrip(0)
            delay(1800)
            discover += ShowcasePlan(0)
            delay(1800)
            discover.removeLastOrNull()
            delay(1200)
            discover.removeLastOrNull()
            delay(1200)
            discover += ShowcaseTrip(3)
            delay(1500)
            discover.removeLastOrNull()
            delay(1200)
            selected = 1
            delay(1500)
            selected = 2
            delay(1500)
            selected = 0
        }
    }

    NativeTabDisplay(
        tabs = listOf(
            NativeTab(discover, "Discover", icon = "safari"),
            NativeTab(saved, "Saved", icon = "heart"),
            NativeTab(profile, "Profile", icon = "person.crop.circle"),
        ),
        selectedTab = selected,
        onTabSelected = { selected = it },
        onBack = { tab -> backStacks[tab].removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ShowcaseDiscover>(metadata = NativeNavigation.title("Discover", large = true)) {
                DiscoverScreen(onOpen = { current += ShowcaseTrip(it) })
            }
            entry<ShowcaseSaved>(metadata = NativeNavigation.title("Saved", large = true)) {
                SavedScreen(onOpen = { current += ShowcaseTrip(it) })
            }
            entry<ShowcaseProfile>(metadata = NativeNavigation.title("Profile", large = true)) {
                ProfileScreen()
            }
            entry<ShowcaseTrip>(metadata = { NativeNavigation.title(destinations[it.id].name) }) { key ->
                TripScreen(destinations[key.id], onPlan = { current += ShowcasePlan(key.id) })
            }
            entry<ShowcasePlan>(
                metadata = NativeNavigation.sheet(listOf(SheetDetent.Medium, SheetDetent.Large)),
            ) { key ->
                PlanSheet(destinations[key.id], onDone = { current.removeLastOrNull() })
            }
        },
    )
}

/** A scrolling page whose content passes under the translucent system bars. */
@Composable
private fun Page(background: Color = Page, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(insets)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun DiscoverScreen(onOpen: (Int) -> Unit) {
    Page {
        BasicText("Where to next?", style = TextStyle(color = Muted, fontSize = 17.sp))
        Postcard(destinations[0], height = 240.dp, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(0) }, large = true)
        SectionTitle("Popular this month")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            destinations.drop(1).take(3).forEachIndexed { i, d ->
                Postcard(d, height = 170.dp, modifier = Modifier.width(150.dp).clickable(role = Role.Button) { onOpen(i + 1) })
            }
        }
        SectionTitle("Weekend escapes")
        Card {
            destinations.drop(4).forEachIndexed { i, d ->
                if (i > 0) Divider()
                DestinationRow(d) { onOpen(i + 4) }
            }
        }
    }
}

@Composable
private fun SavedScreen(onOpen: (Int) -> Unit) {
    Page {
        BasicText("4 places for your next trip", style = TextStyle(color = Muted, fontSize = 17.sp))
        listOf(1, 3, 4, 2).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { id ->
                    Postcard(
                        destinations[id],
                        height = 200.dp,
                        modifier = Modifier.weight(1f).clickable(role = Role.Button) { onOpen(id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen() {
    Page {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF845EF7), Color(0xFFF06595)))),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("JL", style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(12.dp))
            BasicText("Jamie Lee", style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Traveler since 2019", style = TextStyle(color = Muted, fontSize = 15.sp))
        }
        Card {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                listOf("12" to "Trips", "8" to "Countries", "24" to "Saved").forEach { (value, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(value, style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        BasicText(label, style = TextStyle(color = Muted, fontSize = 13.sp))
                    }
                }
            }
        }
        Card {
            listOf("Upcoming trips", "Notifications", "Payment methods", "Help").forEachIndexed { i, label ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(label, modifier = Modifier.weight(1f), style = TextStyle(color = Ink, fontSize = 17.sp))
                    BasicText("›", style = TextStyle(color = Muted, fontSize = 22.sp))
                }
            }
        }
    }
}

@Composable
private fun TripScreen(destination: Destination, onPlan: () -> Unit) {
    var liked by rememberSaveable { mutableIntStateOf(0) }
    NativeNavigationBarItems(
        trailing = listOf(
            NativeBarButton("Save", systemImage = if (liked % 2 == 1) "heart.fill" else "heart") { liked++ },
            NativeBarButton("Share", systemImage = "square.and.arrow.up") {},
        ),
    )
    Page(background = Color.White) {
        Postcard(destination, height = 300.dp, modifier = Modifier.fillMaxWidth(), large = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("★ ${destination.rating}")
            Chip("${destination.days} days")
            Chip("from ${destination.price}")
        }
        BasicText(destination.tagline, style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
        BasicText(
            "A hand-picked itinerary through ${destination.name}'s best neighborhoods, with local guides, " +
                "boutique stays, and free days to wander on your own.",
            style = TextStyle(color = Muted, fontSize = 17.sp, lineHeight = 24.sp),
        )
        PrimaryButton("Plan this trip", destination, onPlan)
        SectionTitle("Highlights")
        Card(background = Page) {
            listOf("Guided old town walk", "Sunrise viewpoint", "Local food market", "Day trip by train")
                .forEachIndexed { i, item ->
                    if (i > 0) Divider()
                    BasicText(item, modifier = Modifier.fillMaxWidth().padding(16.dp), style = TextStyle(color = Ink, fontSize = 17.sp))
                }
        }
    }
}

@Composable
private fun PlanSheet(destination: Destination, onDone: () -> Unit) {
    var travelers by rememberSaveable { mutableIntStateOf(2) }
    var month by rememberSaveable { mutableIntStateOf(1) }
    Page(background = Color.White) {
        BasicText("Plan your trip", style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold))
        BasicText("${destination.name}, ${destination.country} · ${destination.days} days", style = TextStyle(color = Muted, fontSize = 17.sp))
        SectionTitle("When")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Oct", "Nov", "Dec", "Jan").forEachIndexed { i, label ->
                Chip(label, selected = i == month, accent = destination.to) { month = i }
            }
        }
        SectionTitle("Travelers")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Chip("−", accent = destination.to) { if (travelers > 1) travelers-- }
            BasicText("$travelers", style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            Chip("+", accent = destination.to) { travelers++ }
        }
        PrimaryButton("Continue", destination, onDone)
    }
}

/** A gradient card with a drawn landscape: sky, sun, and two mountain ridges. */
@Composable
private fun Postcard(destination: Destination, height: Dp, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(modifier.height(height).clip(RoundedCornerShape(if (large) 28.dp else 22.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(destination.from, destination.to)))
            drawCircle(Color.White.copy(alpha = 0.55f), radius = size.minDimension * 0.13f, center = Offset(size.width * 0.72f, size.height * 0.3f))
            val far = Path().apply {
                moveTo(0f, size.height * 0.62f)
                lineTo(size.width * 0.28f, size.height * 0.42f)
                lineTo(size.width * 0.5f, size.height * 0.58f)
                lineTo(size.width * 0.74f, size.height * 0.38f)
                lineTo(size.width, size.height * 0.56f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(far, Color.White.copy(alpha = 0.22f))
            val near = Path().apply {
                moveTo(0f, size.height * 0.78f)
                quadraticTo(size.width * 0.3f, size.height * 0.6f, size.width * 0.6f, size.height * 0.76f)
                quadraticTo(size.width * 0.82f, size.height * 0.86f, size.width, size.height * 0.7f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(near, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.45f))))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(if (large) 20.dp else 14.dp)) {
            BasicText(
                destination.name,
                style = TextStyle(color = Color.White, fontSize = if (large) 32.sp else 20.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                if (large) "${destination.country} · ${destination.tagline}" else destination.country,
                style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = if (large) 15.sp else 13.sp),
            )
        }
    }
}

@Composable
private fun DestinationRow(destination: Destination, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Postcard(destination.copyForThumb(), height = 56.dp, modifier = Modifier.width(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(destination.name, style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            BasicText(destination.tagline, style = TextStyle(color = Muted, fontSize = 14.sp), maxLines = 1)
        }
        BasicText(destination.price, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

/** The same colors without the name, for a small thumbnail. */
private fun Destination.copyForThumb() = Destination("", "", "", days, price, rating, from, to)

@Composable
private fun Card(background: Color = Color.White, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background)) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(Color(0x33000000)))
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(text, style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun Chip(label: String, selected: Boolean = false, accent: Color = Ink, onClick: (() -> Unit)? = null) {
    val base = Modifier.clip(RoundedCornerShape(50)).background(if (selected) accent else Page)
    BasicText(
        label,
        modifier = (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = TextStyle(color = if (selected) Color.White else Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun PrimaryButton(label: String, destination: Destination, onClick: () -> Unit) {
    BasicText(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(destination.from, destination.to)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
    )
}
