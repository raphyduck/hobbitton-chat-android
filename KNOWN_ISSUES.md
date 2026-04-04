# Known Issues


## Chat search: sub-message scroll precision

**Status**: Requires live debugging session

**Symptoms**: When navigating search results (prev/next) in the chat search feature, the view scrolls to the correct message but not to the specific text occurrence within long messages. The highlighted orange match can be off-screen if the message is tall.

**What works**: Match counting per-occurrence, yellow/orange highlighting, dark mode contrast, keyboard dismiss — all work correctly. The only issue is the scroll target precision within a message.

**Investigation so far** (2 rounds of fixes attempted):
1. **BringIntoViewRequester approach** (`122419c`): Attached a `BringIntoViewRequester` to the `HighlightedTextSegment` containing the focused occurrence. Called `bringIntoView()` after `animateScrollToItem()`. Didn't work because the `LazyColumn` treats each `MessageBubble` as a single item — `bringIntoView()` degrades to `scrollToItem()` and can't scroll within an item.
2. **onGloballyPositioned + animateScrollBy approach** (`c75a76a`): Replaced `BringIntoViewRequester` with a callback chain. The focused `HighlightedTextSegment` reports its pixel coordinates via `Modifier.onGloballyPositioned`. `MessageList` compares the segment's `boundsInRoot()` to the viewport and calls `animateScrollBy()` with the delta. Still not working — likely a timing issue where `onGloballyPositioned` fires before the initial `animateScrollToItem()` completes, or coordinate space mismatches between the segment and the viewport.

**Key files**:
- `feature/chat/.../components/MessageList.kt` — scroll logic, `pendingFineTuneScroll` flag
- `feature/chat/.../components/MarkdownContent.kt` — `HighlightedTextSegment` with `onGloballyPositioned`
- `feature/chat/.../components/ContentPartRenderer.kt` — forwards `onFocusedOccurrencePositioned` callback
- `feature/chat/.../components/MessageBubble.kt` — forwards callback when `isCurrentSearchMatch`

**What's needed**: On-device debugging session with Logcat/Timber to trace:
- Whether `onGloballyPositioned` fires at the right time (after `animateScrollToItem` completes)
- The actual coordinate values from `boundsInRoot()` vs viewport bounds
- Whether `animateScrollBy()` is called with the correct delta
- Whether the `pendingFineTuneScroll` guard allows or blocks the fine-tune scroll

**Workaround**: User can manually scroll within the message to find the orange-highlighted occurrence after the search navigates to the correct message.

