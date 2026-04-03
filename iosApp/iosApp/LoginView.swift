import SwiftUI
import Shared

struct LoginView: View {
    @ObservedObject var authState: AuthState

    @State private var serverUrl = ""
    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Logo
                    Image(systemName: "message.fill")
                        .font(.system(size: 56))
                        .foregroundColor(.blue)
                        .padding(.top, 40)

                    Text("LibreChat")
                        .font(.largeTitle)
                        .fontWeight(.bold)

                    // Server URL
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Server URL")
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundColor(.secondary)

                        TextField("https://your-server.com", text: $serverUrl)
                            .textFieldStyle(.roundedBorder)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                            .keyboardType(.URL)
                    }

                    // Email
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Email")
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundColor(.secondary)

                        TextField("email@example.com", text: $email)
                            .textFieldStyle(.roundedBorder)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                            .keyboardType(.emailAddress)
                    }

                    // Password
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Password")
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundColor(.secondary)

                        SecureField("Password", text: $password)
                            .textFieldStyle(.roundedBorder)
                    }

                    // Error message
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.callout)
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)
                    }

                    // Login button
                    Button {
                        performLogin()
                    } label: {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        } else {
                            Text("Sign In")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isLoading || serverUrl.isEmpty || email.isEmpty || password.isEmpty)

                    // Forgot password link
                    NavigationLink(destination: ForgotPasswordView()) {
                        Text("Forgot Password?")
                            .font(.subheadline)
                            .foregroundColor(.blue)
                    }

                    // Register link
                    HStack {
                        Text("Don't have an account?")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        NavigationLink(destination: RegisterView(authState: authState, serverUrl: $serverUrl)) {
                            Text("Sign Up")
                                .font(.subheadline)
                                .fontWeight(.semibold)
                        }
                    }
                }
                .padding(.horizontal, 32)
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func performLogin() {
        guard !serverUrl.isEmpty, !email.isEmpty, !password.isEmpty else { return }

        isLoading = true
        errorMessage = nil

        // Normalize server URL
        var normalizedUrl = serverUrl
        if !normalizedUrl.hasPrefix("http://") && !normalizedUrl.hasPrefix("https://") {
            normalizedUrl = "https://\(normalizedUrl)"
        }
        if normalizedUrl.hasSuffix("/") {
            normalizedUrl = String(normalizedUrl.dropLast())
        }

        let sdk = KoinHelper.sdk

        Task {
            do {
                // Set the server URL (async — DataStore persistence)
                try await KoinHelper.serverDataStore.setServerUrl(url: normalizedUrl)

                let result = try await sdk.login(email: email, password: password)
                await MainActor.run {
                    authState.userName = result.response.user?.name ?? email
                    authState.serverUrl = normalizedUrl
                    authState.isLoggedIn = true
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }
}
