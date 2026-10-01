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

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || tab != firstTab

    fun push(s: Screen) { stack.add(s) }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        else if (tab != firstTab) tab = firstTab
    }
}
