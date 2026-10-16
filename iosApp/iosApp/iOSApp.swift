import Sample
import SwiftUI
import UIKit

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            switch ProcessInfo.processInfo.environment["NAV3_SAMPLE"] {
            // Baselines without Compose, to compare how the system lays out its own tab bars.
            case "swiftui-tabs": SwiftUITabsBaseline()
            case "uikit-tabs": UIKitTabsBaseline().ignoresSafeArea()
            default: ComposeView().ignoresSafeArea()
            }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct SwiftUITabsBaseline: View {
    var body: some View {
        TabView {
            Tab("Home", systemImage: "house") { BaselineScreen(title: "Home") }
            Tab("Search", systemImage: "magnifyingglass") { BaselineScreen(title: "Search") }
            Tab("Profile", systemImage: "person.crop.circle") { BaselineScreen(title: "Profile") }
        }
    }
}

private struct BaselineScreen: View {
    let title: String

    var body: some View {
        NavigationStack {
            List(1..<4) { id in
                NavigationLink("Open detail \(id)") { Text("Detail \(id)").navigationTitle("Detail \(id)") }
            }
            .navigationTitle(title)
        }
    }
}

struct UIKitTabsBaseline: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UITabBarController()
        controller.tabs = [("Home", "house"), ("Search", "magnifyingglass"), ("Profile", "person.crop.circle")]
            .map { title, image in
                let root = UIViewController()
                root.title = title
                root.view.backgroundColor = .systemBackground
                let nav = UINavigationController(rootViewController: root)
                nav.navigationBar.prefersLargeTitles = true
                return UITab(title: title, image: UIImage(systemName: image), identifier: title) { _ in nav }
            }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
