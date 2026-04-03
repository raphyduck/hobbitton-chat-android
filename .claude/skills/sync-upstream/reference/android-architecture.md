# LibreChat Mobile Architecture Reference

Condensed architecture guide for the LibreChat Mobile app. Read module-specific `CLAUDE.md` files for full details.

## Module Dependency Graph

```
app
├── feature/auth       → core/common, core/model, core/network, core/data, core/ui
├── feature/chat       → core/common, core/model, core/network, core/data, core/ui
├── feature/conversations → core/common, core/model, core/data, core/ui
├── feature/settings   → core/common, core/model, core/data, core/ui
├── feature/agents     → core/common, core/model, core/data, core/ui
├── feature/files      → core/common, core/model, core/data, core/ui
└── core/data          → core/common, core/model, core/network
    core/network       → core/common, core/model
    core/ui            → core/common, core/model
    core/model         → (none — pure Kotlin)
    core/common        → (none — lowest layer)
```

**Rules:** Feature modules depend on `:core:*` only, never on each other.

## Data Flow Pattern (Unidirectional)

```
UI (Composable) → ViewModel (StateFlow) → Repository (interface) → API (Ktor) / Room (cache)
```

- ViewModels expose `StateFlow<UiState>` to Compose
- Repositories define interfaces in `:core:data`, implemented with `@Inject`
- Room is a read-through cache; server is always source of truth

## safeApiCall Pattern

All repository network calls use this wrapper (defined in `core/common`):

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    try { Result.Success(block()) }
    catch (e: ClientRequestException) { Result.Error(e, parseErrorMessage(e.response.bodyAsText())) }
    catch (e: ServerResponseException) { Result.Error(e, "Server error.") }
    catch (e: HttpRequestTimeoutException) { Result.Error(e, "Request timed out.") }
    catch (e: IOException) { Result.Error(e, "Network unavailable.") }
    catch (e: SerializationException) { Result.Error(e, "Unexpected response format.") }
```

## API Service Pattern (core/network)

Each API service is a Ktor-backed class with `@Inject constructor(client: HttpClient)`:

```kotlin
class ConversationsApi @Inject constructor(private val client: HttpClient) {
    suspend fun getConversations(pageNumber: Int, limit: Int, isArchived: Boolean): PaginatedConversations =
        client.get("/api/convos") {
            parameter("pageNumber", pageNumber.toString())
            parameter("limit", limit.toString())
            parameter("isArchived", isArchived.toString())
        }.body()
}
```

**Backend quirk:** Mutation endpoints wrap body in `arg` field: `{ "arg": { ... } }`

## Repository Pattern (core/data)

Interface + implementation, always with Hilt injection:

```kotlin
interface ConversationRepository {
    fun observeConversations(...): Flow<Result<List<Conversation>>>
    suspend fun updateTitle(id: String, title: String): Result<Conversation>
}

class ConversationRepositoryImpl @Inject constructor(
    private val api: ConversationsApi,
    private val dao: ConversationDao,
    private val mapper: EntityMapper,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : ConversationRepository { ... }
```

## Room Entity Pattern (core/data)

```kotlin
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val title: String?,
    val endpoint: String?,
    // Complex fields stored as JSON strings via TypeConverters
    val tagsJson: String?,
)
```

DAO read methods return `Flow<T>` for reactive observation.

## ViewModel Pattern (feature modules)

```kotlin
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    fun loadConversations() {
        viewModelScope.launch {
            conversationRepository.observeConversations(...)
                .collect { result ->
                    _uiState.update { state ->
                        when (result) {
                            is Result.Success -> state.copy(conversations = result.data)
                            is Result.Error -> state.copy(error = result.message)
                            is Result.Loading -> state.copy(isLoading = true)
                        }
                    }
                }
        }
    }
}
```

## SSE Streaming (core/network)

Custom line parser over raw `ByteReadChannel` (do NOT use Ktor SSE plugin).

Two-phase protocol:
1. `POST /api/agents/chat` → returns `{ streamId }` (streamId === conversationId)
2. `GET /api/agents/chat/stream/:streamId` opens SSE stream

`SseConnectionManager` handles lifecycle: start, reconnect (exponential backoff), abort, `StateFlow<StreamingState>`.

## Naming Conventions

- API classes: `{Domain}Api.kt` (e.g., `ConversationsApi.kt`)
- Repository: `{Domain}Repository.kt` (interface) + `{Domain}RepositoryImpl.kt`
- ViewModel: `{Feature}ViewModel.kt`
- Screen composable: `{Feature}Screen.kt`
- UI state: `{Feature}UiState` data class
- Room entity: `{Domain}Entity.kt`
- Room DAO: `{Domain}Dao.kt`
- DI module: `{Domain}Module.kt`
- Convention plugins: `librechat.mobile.{type}` (e.g., `librechat.mobile.feature`)

## Build System

- Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, compileSdk 35, minSdk 26
- 7 convention plugins in `build-logic/convention/`
- Version catalog: `gradle/libs.versions.toml`
- Convention plugin `apply()` must use `override fun apply(target: Project) { with(target) { ... } }`
