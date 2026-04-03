# UI Mapping: Official React Components → Mobile Feature Modules

Maps web client component directories to mobile Compose feature modules.

## Implemented

| Web Component Dir | Mobile Module | Coverage |
|-------------------|---------------|----------|
| `components/Chat/` | `feature/chat/` | Core chat UI, message list, input |
| `components/Messages/` | `feature/chat/` | Message rendering, branching, siblings |
| `components/Input/` | `feature/chat/` | Message input, attachments, mentions |
| `components/Conversations/` | `feature/conversations/` | List, search, tags, CRUD |
| `components/Auth/` | `feature/auth/` | Login, register, 2FA, forgot password |
| `components/Nav/` | `app/` (navigation) | Side nav, navigation rail, adaptive layout |
| `components/Files/` | `feature/files/` | Upload, list, delete, image viewer |
| `components/Agents/` | `feature/agents/` | Marketplace grid, detail screen |
| `components/Banners/` | `app/` | System banner display |
| `components/Endpoints/` | `feature/chat/` | Model/endpoint selector bottom sheet |

## Gaps (no mobile counterpart)

| Web Component Dir | What It Does | Priority | Notes |
|-------------------|-------------|----------|-------|
| `components/Prompts/` | Prompts library with CRUD, categories, sharing | Medium | Mobile has PromptsApi but no dedicated UI |
| `components/Artifacts/` | Code/document artifact viewer and editor | Medium | New feature in recent versions |
| `components/MCP/` | MCP server configuration UI | Low | Admin-oriented feature |
| `components/MCPUIResource/` | MCP UI resource rendering | Low | Depends on MCP support |
| `components/Bookmarks/` | Conversation bookmarks management | Low | Tags partially cover this |
| `components/Share/` | Share conversation dialog and link management | Low | ShareApi exists, no UI |
| `components/Sharing/` | Shared conversation viewer | Low | Public link viewer |
| `components/SharePoint/` | SharePoint integration | N/A | Enterprise feature |
| `components/SidePanel/` | Side panel for artifacts, files, etc. | Medium | Tablet layout opportunity |
| `components/Audio/` | Voice input/output, TTS | Medium | SpeechApi exists, no UI |
| `components/Plugins/` | Legacy plugin system | N/A | Superseded by agents |
| `components/Tools/` | Tool configuration and management | Low | Part of agent setup |
| `components/OAuth/` | OAuth provider buttons | Low | Mobile uses Chrome Custom Tabs |
| `components/Web/` | Web search integration | Low | Agent tool feature |
| `components/System/` | System-level UI (error boundaries, etc.) | Low | Mobile has own error handling |

## Key Web Patterns → Mobile Equivalents

| Web Pattern | Mobile Equivalent |
|------------|-------------------|
| Recoil atoms (`store/`) | ViewModel state (StateFlow) |
| React Query hooks (`hooks/`) | Repository + ViewModel |
| React Context (`Providers/`) | Hilt DI + CompositionLocal |
| Radix UI components | Material 3 components |
| Tailwind CSS | Material 3 theme + Modifier |
| react-router-dom | Compose Navigation |
| React.memo / useMemo | remember / derivedStateOf |
| useCallback | rememberUpdatedState / LaunchedEffect |
