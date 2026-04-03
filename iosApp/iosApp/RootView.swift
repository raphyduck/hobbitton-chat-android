import SwiftUI
import Shared

struct RootView: View {
    @StateObject private var authState = AuthState()

    var body: some View {
        Group {
            if authState.isLoggedIn {
                MainTabView(authState: authState)
            } else {
                LoginView(authState: authState)
            }
        }
    }
}

struct MainTabView: View {
    @ObservedObject var authState: AuthState

    var body: some View {
        TabView {
            ChatStreamView(authState: authState)
                .tabItem {
                    Label("Chat", systemImage: "message")
                }

            FilesView(authState: authState)
                .tabItem {
                    Label("Files", systemImage: "folder")
                }
        }
    }
}

@MainActor
class AuthState: ObservableObject {
    @Published var isLoggedIn = false
    @Published var userName: String = ""
    @Published var serverUrl: String = ""
}
