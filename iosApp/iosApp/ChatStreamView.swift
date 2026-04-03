import SwiftUI
import Shared

struct ChatStreamView: View {
    @ObservedObject var authState: AuthState

    @State private var streamOutput = ""
    @State private var isStreaming = false
    @State private var statusText = "Ready"
    @State private var streamTask: Task<Void, Never>?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Status bar
                HStack {
                    Circle()
                        .fill(isStreaming ? .green : .gray)
                        .frame(width: 8, height: 8)
                    Text(statusText)
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("Connected to \(authState.serverUrl)")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
                .background(Color(.systemGroupedBackground))

                // Stream output
                ScrollViewReader { proxy in
                    ScrollView {
                        Text(streamOutput.isEmpty ? "No stream data yet. Tap 'Test SSE Stream' to verify streaming works." : streamOutput)
                            .font(.system(.body, design: .monospaced))
                            .foregroundColor(streamOutput.isEmpty ? .secondary : .primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                            .id("bottom")
                    }
                    .onChange(of: streamOutput) { _ in
                        withAnimation {
                            proxy.scrollTo("bottom", anchor: .bottom)
                        }
                    }
                }

                Divider()

                // Controls
                HStack(spacing: 12) {
                    Button {
                        testSseStream()
                    } label: {
                        Label("Test SSE Stream", systemImage: "antenna.radiowaves.left.and.right")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isStreaming)

                    if isStreaming {
                        Button {
                            stopStream()
                        } label: {
                            Label("Stop", systemImage: "stop.fill")
                                .font(.subheadline)
                        }
                        .buttonStyle(.bordered)
                        .tint(.red)
                    }

                    Spacer()

                    Button {
                        streamOutput = ""
                        statusText = "Ready"
                    } label: {
                        Image(systemName: "trash")
                    }
                    .disabled(streamOutput.isEmpty)
                }
                .padding()
            }
            .navigationTitle("LibreChat iOS")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Text("Signed in as \(authState.userName)")
                        Button("Sign Out", role: .destructive) {
                            signOut()
                        }
                    } label: {
                        Image(systemName: "person.circle")
                    }
                }
            }
        }
    }

    private func testSseStream() {
        isStreaming = true
        statusText = "Connecting..."
        streamOutput += "[SSE Test] Starting stream connection...\n"

        let sdk = KoinHelper.sdk

        // For the SSE test, we use the ChatApi to start a minimal chat,
        // then connect to the stream. This proves the full pipeline works.
        streamTask = Task {
            do {
                // Start a chat request to get a streamId
                // ChatRequest constructor parameters match the Kotlin/Native export
                let request = ChatRequest(
                    text: "Hello! This is a test from the iOS app.",
                    conversationId: nil,
                    parentMessageId: "00000000-0000-0000-0000-000000000000",
                    endpoint: EModelEndpoint.agents,
                    endpointType: nil,
                    model: nil,
                    agentId: nil,
                    isContinued: false,
                    isEdited: false,
                    isRegenerate: false,
                    overrideParentMessageId: nil,
                    responseMessageId: nil,
                    temperature: nil,
                    topP: nil,
                    maxOutputTokens: nil,
                    maxContextTokens: nil,
                    system: nil,
                    reasoningEffort: nil,
                    effort: nil,
                    thinkingLevel: nil,
                    stop: nil,
                    tools: nil,
                    iconURL: nil,
                    greeting: nil,
                    spec: nil,
                    modelLabel: nil,
                    maxTokens: nil,
                    promptPrefix: nil,
                    chatGptLabel: nil,
                    resendFiles: nil,
                    imageDetail: nil,
                    key: nil,
                    extra: nil,
                    webSearch: nil,
                    files: nil,
                    addedConvo: nil,
                    ephemeralAgent: nil
                )

                await MainActor.run {
                    statusText = "Sending chat request..."
                    streamOutput += "[SSE Test] Sending chat request to agents endpoint...\n"
                }

                let startResponse = try await sdk.chatApi.startChat(endpoint: "agents", request: request)
                let streamPath = "api/agents/chat/stream/\(startResponse.conversationId)"

                await MainActor.run {
                    statusText = "Streaming..."
                    streamOutput += "[SSE Test] Got streamId: \(startResponse.conversationId)\n"
                    streamOutput += "[SSE Test] Connecting to SSE stream...\n\n"
                }

                // Connect to the SSE stream — SKIE converts Flow<StreamEvent> to AsyncSequence
                // Note: SKIE's Flow→AsyncSequence is non-throwing. Any flow exceptions
                // are caught by the outer try/catch in SseClient.connect() and emitted
                // as StreamEvent.Error events. The Kotlin flow MUST NOT throw or
                // SKIE's iterator will call fatalError.
                let streamingClient = KoinHelper.streamingHttpClient
                let eventFlow = sdk.sseClient.connect(
                    client: streamingClient,
                    streamPath: streamPath,
                    resume: false,
                    connectivityFlow: nil
                )

                // Iterate over the AsyncSequence (SKIE-bridged Flow)
                for await event in eventFlow {
                    if Task.isCancelled { break }

                    await MainActor.run {
                        handleStreamEvent(event)
                    }
                }

                await MainActor.run {
                    isStreaming = false
                    statusText = "Stream complete"
                    streamOutput += "\n[SSE Test] Stream ended.\n"
                }
            } catch {
                await MainActor.run {
                    isStreaming = false
                    statusText = "Error"
                    streamOutput += "\n[SSE Error] \(error.localizedDescription)\n"
                }
            }
        }
    }

    private func handleStreamEvent(_ event: StreamEvent) {
        // SKIE converts sealed interface to Swift enum via onEnum(of:)
        switch onEnum(of: event) {
        case .contentDelta(let delta):
            streamOutput += delta.chunk

        case .thinkingDelta(let thinking):
            streamOutput += "[thinking] \(thinking.chunk)"

        case .toolCallStart(let toolCall):
            streamOutput += "\n[tool: \(toolCall.toolName)] Starting...\n"

        case .toolCallComplete(let toolCall):
            streamOutput += "[tool: \(toolCall.toolCallId)] Complete\n"

        case .created(let created):
            streamOutput += "[created] conversationId: \(created.conversationId)\n"

        case .retrying(let retry):
            statusText = "Retrying (\(retry.attempt)/\(retry.maxAttempts))..."
            streamOutput += "[retry] Attempt \(retry.attempt) of \(retry.maxAttempts)\n"

        case .error(let error):
            statusText = "Error"
            streamOutput += "\n[error] \(error.message)\n"

        case .final(_):
            statusText = "Complete"
            streamOutput += "\n[final] Stream complete.\n"

        case .sync(let sync):
            streamOutput += "[sync] Received \(sync.aggregatedContent.count) content parts\n"

        case .step(let step):
            streamOutput += "[step: \(step.stepType)] \(step.stepData)\n"

        case .attachmentCreated(let attachment):
            streamOutput += "[attachment] \(attachment.filename)\n"
        }
    }

    private func stopStream() {
        streamTask?.cancel()
        streamTask = nil
        isStreaming = false
        statusText = "Stopped"
        streamOutput += "\n[SSE Test] Stream cancelled by user.\n"
    }

    private func signOut() {
        Task {
            try? await KoinHelper.sdk.tokenManager.clearTokens()
            await MainActor.run {
                authState.isLoggedIn = false
                authState.userName = ""
            }
        }
    }
}
