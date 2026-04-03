import SwiftUI
import Shared

struct RegisterView: View {
    @ObservedObject var authState: AuthState
    @Binding var serverUrl: String
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var email = ""
    @State private var username = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var isRegistered = false

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Text("Create Account")
                    .font(.title2)
                    .fontWeight(.bold)
                    .padding(.top, 20)

                if isRegistered {
                    registrationSuccessView
                } else {
                    registrationFormView
                }
            }
            .padding(.horizontal, 32)
        }
        .navigationTitle("Register")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var registrationSuccessView: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundColor(.green)

            Text("Registration Successful!")
                .font(.title3)
                .fontWeight(.semibold)

            Text("You can now sign in with your credentials.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button("Back to Sign In") {
                dismiss()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(.top, 20)
    }

    private var registrationFormView: some View {
        VStack(spacing: 16) {
            formField("Name", text: $name, keyboardType: .default)
            formField("Email", text: $email, keyboardType: .emailAddress)
            formField("Username", text: $username, keyboardType: .default)

            VStack(alignment: .leading, spacing: 8) {
                Text("Password")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)
                SecureField("Password", text: $password)
                    .textFieldStyle(.roundedBorder)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Confirm Password")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)
                SecureField("Confirm Password", text: $confirmPassword)
                    .textFieldStyle(.roundedBorder)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.callout)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }

            Button {
                performRegistration()
            } label: {
                if isLoading {
                    ProgressView()
                        .tint(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                } else {
                    Text("Create Account")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading || !isFormValid)
        }
    }

    private func formField(_ label: String, text: Binding<String>, keyboardType: UIKeyboardType) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(.secondary)
            TextField(label, text: text)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
                .disableAutocorrection(true)
                .keyboardType(keyboardType)
        }
    }

    private var isFormValid: Bool {
        !name.isEmpty && !email.isEmpty && !username.isEmpty &&
        !password.isEmpty && !confirmPassword.isEmpty
    }

    private func performRegistration() {
        guard password == confirmPassword else {
            errorMessage = "Passwords do not match"
            return
        }
        guard password.count >= 8 else {
            errorMessage = "Password must be at least 8 characters"
            return
        }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                // Ensure server URL is set
                if !serverUrl.isEmpty {
                    var normalizedUrl = serverUrl
                    if !normalizedUrl.hasPrefix("http://") && !normalizedUrl.hasPrefix("https://") {
                        normalizedUrl = "https://\(normalizedUrl)"
                    }
                    if normalizedUrl.hasSuffix("/") {
                        normalizedUrl = String(normalizedUrl.dropLast())
                    }
                    try await KoinHelper.serverDataStore.setServerUrl(url: normalizedUrl)
                }

                let authRepo = KoinHelper.authRepository
                let result = try await authRepo.register(
                    name: name,
                    email: email,
                    username: username,
                    password: password
                )

                await MainActor.run {
                    isLoading = false
                    // Check if registration succeeded
                    if result is ResultSuccess<AnyObject> {
                        isRegistered = true
                    } else if let error = result as? ResultError {
                        errorMessage = error.message ?? "Registration failed"
                    }
                }
            } catch {
                await MainActor.run {
                    isLoading = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}
