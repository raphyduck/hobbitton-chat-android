import SwiftUI
import Shared

struct FilesView: View {
    @ObservedObject var authState: AuthState

    @State private var files: [FileObject] = []
    @State private var isLoading = true
    @State private var isRefreshing = false
    @State private var isUploading = false
    @State private var errorMessage: String?
    @State private var selectedFile: FileObject?
    @State private var showFilePicker = false
    @State private var deleteConfirmFile: FileObject?

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && files.isEmpty {
                    ProgressView("Loading files...")
                } else if let error = errorMessage, files.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 48))
                            .foregroundColor(.orange)
                        Text(error)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                        Button("Retry") { loadFiles() }
                            .buttonStyle(.borderedProminent)
                    }
                    .padding()
                } else if files.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "folder")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text("No Files")
                            .font(.title3)
                            .fontWeight(.medium)
                        Text("Upload files to get started")
                            .foregroundColor(.secondary)
                    }
                } else {
                    fileListView
                }
            }
            .navigationTitle("Files")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showFilePicker = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .disabled(isUploading)
                }
            }
            .refreshable {
                await refreshFiles()
            }
            .fileImporter(
                isPresented: $showFilePicker,
                allowedContentTypes: [.data],
                allowsMultipleSelection: false
            ) { result in
                handleFileImport(result)
            }
            .sheet(item: $selectedFile) { file in
                FilePreviewSheet(file: file, serverUrl: authState.serverUrl)
            }
            .alert("Delete File?", isPresented: .init(
                get: { deleteConfirmFile != nil },
                set: { if !$0 { deleteConfirmFile = nil } }
            )) {
                Button("Cancel", role: .cancel) { deleteConfirmFile = nil }
                Button("Delete", role: .destructive) {
                    if let file = deleteConfirmFile {
                        deleteFile(file)
                        deleteConfirmFile = nil
                    }
                }
            } message: {
                if let file = deleteConfirmFile {
                    Text("Are you sure you want to delete \"\(file.filename)\"? This cannot be undone.")
                }
            }
        }
        .task { loadFiles() }
    }

    private var fileListView: some View {
        List {
            if isUploading {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("Uploading...")
                        .foregroundColor(.secondary)
                }
            }

            ForEach(files, id: \.fileId) { file in
                FileRow(file: file)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        selectedFile = file
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            deleteConfirmFile = file
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
            }
        }
        .listStyle(.plain)
    }

    private func loadFiles() {
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let fileRepo = KoinHelper.fileRepository
                let result = try await fileRepo.getFiles()

                await MainActor.run {
                    isLoading = false
                    if let success = result as? ResultSuccess<NSArray> {
                        files = (success.data as? [FileObject]) ?? []
                    } else if let error = result as? ResultError {
                        errorMessage = error.message ?? "Failed to load files"
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

    private func refreshFiles() async {
        do {
            let fileRepo = KoinHelper.fileRepository
            let result = try await fileRepo.getFiles()

            await MainActor.run {
                if let success = result as? ResultSuccess<NSArray> {
                    files = (success.data as? [FileObject]) ?? []
                }
            }
        } catch {
            // Silently fail on refresh
        }
    }

    private func handleFileImport(_ result: Swift.Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            uploadFile(url)
        case .failure(let error):
            errorMessage = error.localizedDescription
        }
    }

    private func uploadFile(_ url: URL) {
        isUploading = true

        Task {
            do {
                guard url.startAccessingSecurityScopedResource() else {
                    await MainActor.run {
                        isUploading = false
                        errorMessage = "Could not access file"
                    }
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }

                let data = try Data(contentsOf: url)
                let filename = url.lastPathComponent
                let mimeType = "application/octet-stream" // Simplified; could use UTType

                let fileRepo = KoinHelper.fileRepository
                let bytes = KotlinByteArray(size: Int32(data.count))
                data.withUnsafeBytes { rawBuffer in
                    guard let baseAddress = rawBuffer.baseAddress else { return }
                    for i in 0..<data.count {
                        bytes.set(index: Int32(i), value: baseAddress.advanced(by: i).load(as: Int8.self))
                    }
                }

                let result = try await fileRepo.uploadFile(
                    bytes: bytes,
                    filename: filename,
                    type: mimeType
                )

                await MainActor.run {
                    isUploading = false
                    if let success = result as? ResultSuccess<FileObject> {
                        if let newFile = success.data {
                            files.insert(newFile, at: 0)
                        }
                    } else if let error = result as? ResultError {
                        errorMessage = error.message ?? "Upload failed"
                    }
                }
            } catch {
                await MainActor.run {
                    isUploading = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func deleteFile(_ file: FileObject) {
        Task {
            do {
                let entry = DeleteFileEntry(fileId: file.fileId, filepath: file.filepath)
                let fileRepo = KoinHelper.fileRepository
                let result = try await fileRepo.deleteFiles(files: [entry])

                await MainActor.run {
                    if result is ResultSuccess<AnyObject> {
                        files.removeAll { $0.fileId == file.fileId }
                    } else if let error = result as? ResultError {
                        errorMessage = error.message ?? "Failed to delete"
                    }
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

// MARK: - File Row

struct FileRow: View {
    let file: FileObject

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: iconName(for: file.type))
                .font(.title2)
                .foregroundColor(.secondary)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(file.filename)
                    .lineLimit(1)

                HStack(spacing: 4) {
                    Text(formatSize(file.bytes))
                    if let date = file.createdAt {
                        Text("·")
                        Text(date.prefix(10))
                    }
                }
                .font(.caption)
                .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func iconName(for type: String) -> String {
        if type.hasPrefix("image/") { return "photo" }
        if type.hasPrefix("video/") { return "video" }
        if type.hasPrefix("audio/") { return "music.note" }
        if type == "application/pdf" { return "doc.richtext" }
        return "doc"
    }

    private func formatSize(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

// MARK: - File Preview Sheet

struct FilePreviewSheet: View {
    let file: FileObject
    let serverUrl: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: iconName(for: file.type))
                    .font(.system(size: 64))
                    .foregroundColor(.blue)

                Text(file.filename)
                    .font(.title3)
                    .fontWeight(.medium)
                    .multilineTextAlignment(.center)

                VStack(alignment: .leading, spacing: 8) {
                    infoRow("Type", value: file.type)
                    infoRow("Size", value: formatSize(file.bytes))
                    if let date = file.createdAt {
                        infoRow("Created", value: String(date.prefix(10)))
                    }
                    if let source = file.source {
                        infoRow("Source", value: source)
                    }
                }
                .padding()
                .background(Color(.systemGroupedBackground))
                .cornerRadius(12)

                Spacer()
            }
            .padding()
            .navigationTitle("File Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func infoRow(_ label: String, value: String) -> some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .lineLimit(1)
        }
    }

    private func iconName(for type: String) -> String {
        if type.hasPrefix("image/") { return "photo" }
        if type.hasPrefix("video/") { return "video" }
        if type.hasPrefix("audio/") { return "music.note" }
        if type == "application/pdf" { return "doc.richtext" }
        return "doc"
    }

    private func formatSize(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

// MARK: - FileObject Identifiable conformance

extension FileObject: @retroactive Identifiable {
    public var id: String { fileId }
}
