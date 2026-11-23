import SwiftUI
import UIKit
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
            // odkaz z pozvánky: multiodoklady://activate?code=ABCD-EFGH
            .onOpenURL { url in MainViewControllerKt.handleDeepLink(url: url.absoluteString) }
    }
}
