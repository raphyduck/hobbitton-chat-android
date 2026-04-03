import SwiftUI
import Shared

struct ForgotPasswordView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var email = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var isSent = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image(systemName: "lock.rotation")
                    .font(.system(size: 56))
                    .foregroundColor(.blue)
                    .padding(.top, 40)

                if isSent {
                    successView
                } else {
                    formView
                }
            }
            .padding(.horizontal, 32)
        }
        .navigationTitle("Reset Password")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var successView: some View {
        VStack(spacing: 16) {
            Image(systemName: "envelope.badge.fill")
                .font(.system(size: 48))
                .foregroundColor(.green)

            Text("Check Your Email")
                .font(.title3)
                .fontWeight(.semibold)

            Text("If an account exists for \(email), you'll receive a password reset link.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button("Back to Sign In") {
                dismiss()
            }
            .buttonStyle(.borderedProminent)
            .padding(.top, 8)
        }
    }

    private var formView: some View {
        VStack(spacing: 20) {
            Text("Forgot your password?")
                .font(.title3)
                .fontWeight(.semibold)

            Text("Enter your email address and we'll send you a link to reset your password.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

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

            if let errorMessage {
                Text(errorMessage)
                    .font(.callout)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }

            Button {
                requestReset()
            } label: {
                if isLoading {
                    ProgressView()
                        .tint(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                } else {
                    Text("Send Reset Link")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading || email.isEmpty)
        }
    }

    private func requestReset() {
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let authRepo = KoinHelper.authRepository
                let result = try await authRepo.requestPasswordReset(email: email)

                await MainActor.run {
                    isLoading = false
                    if result is ResultSuccess<AnyObject> {
                        isSent = true
                    } else if let error = result as? ResultError {
                        errorMessage = error.message ?? "Failed to send reset email"
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
