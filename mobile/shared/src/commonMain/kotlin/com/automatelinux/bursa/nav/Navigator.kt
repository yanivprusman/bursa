package com.automatelinux.bursa.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Tab(val label: String) { Mine("שלי"), Market("שוק") }

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Indices : Screen
    /** [name] is shown while the page loads. */
    data class Security(val id: String, val name: String) : Screen
    data class Index(val id: String, val name: String) : Screen
}

class Navigator(private val firstTab: Tab) {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableStateOf(firstTab)

    /** +1 after going deeper, -1 after coming back: which way the next screen slides in. */
    var direction by mutableStateOf(1)
        private set

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || tab != firstTab

    fun push(s: Screen) {
        direction = 1
        stack.add(s)
    }

    fun back() {
        direction = -1
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        else if (tab != firstTab) tab = firstTab
    }
}
