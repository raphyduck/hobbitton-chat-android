import SwiftUI
import Shared

@main
struct iOSApp: App {

    init() {
        // Initialize Koin DI — must happen before any KMP code is used
        IosKoinHelperKt.startIosKoin()
    }

    var body: some Scene {
        WindowGroup {
            LibreChatComposeView()
                .ignoresSafeArea()
        }
    }
}
