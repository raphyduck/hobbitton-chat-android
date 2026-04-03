import SwiftUI

/// Placeholder content view — the actual root is RootView.
/// Kept for Preview support.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "message.fill")
                .font(.system(size: 64))
                .foregroundColor(.blue)
            Text("LibreChat iOS")
                .font(.largeTitle)
                .fontWeight(.bold)
            Text("KMP + SKIE Integrated")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
