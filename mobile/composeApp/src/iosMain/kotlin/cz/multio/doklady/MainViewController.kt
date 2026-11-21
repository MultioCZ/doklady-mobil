package cz.multio.doklady

import androidx.compose.ui.window.ComposeUIViewController

@Suppress("FunctionName", "unused") // volá se ze Swiftu (iosApp/ContentView.swift)
fun MainViewController(): platform.UIKit.UIViewController {
    cz.multio.doklady.data.AppLock.init()
    return ComposeUIViewController { App() }
}

/** Volá se ze Swiftu (onOpenURL), když iPhone otevře odkaz multiodoklady://activate?code=… */
@Suppress("unused")
fun handleDeepLink(url: String) = cz.multio.doklady.platform.IncomingLink.handle(url)
