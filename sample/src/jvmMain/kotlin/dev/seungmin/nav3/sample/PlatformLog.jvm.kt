package dev.seungmin.nav3.sample

actual fun platformLog(message: String) {
    println(message)
}

actual suspend fun liveViewControllerCount(): Int? = null

actual fun launchFlag(name: String): String? = System.getenv(name)

@androidx.compose.runtime.Composable
actual fun verticalBarEdge(): String? = null
