# Modern Syntax-Highlighted Rules Editor & Read/Edit Interaction

A high-fidelity upgrade to the **Rules Editor** (`RulesEditorActivity`), transforming the plain text area into a modern code editor with custom pill-badge syntax highlighting, glowing section headers, individual file extension chips, path badges, and an intuitive Read-Only vs. Edit Mode interaction model.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions were confirmed based on your preferences:
> - **Section Headers (`[settings]`, `[audio]`, etc.)**: Styled as glowing badge pills with bold accent text.
> - **File Extensions (`pdf, docx, etc.`)**: Rendered as individual rounded chip pills following `ext =`.
> - **Read vs. Edit Mode**: Tap inside the editor to edit; tap anywhere outside the editor to immediately dismiss the keyboard and lock into clean, scrollable Read Mode.

---

### 1. Overview & Core Concept

The Rules Editor is the core configuration hub of File Navigator. Instead of a basic raw-text input field, it will look and feel like a custom-crafted cyberpunk/monochrome code editor:
- **Visual Syntax Highlights**: Parses `rules.conf` syntax in real-time, elevating sections, paths, keys, and file extensions into distinct glowing pills and chips.
- **Distraction-Free Reading**: When viewing rules, the keyboard never intrudes. Users can freely scroll and read with beautiful pill formatting. Tapping inside immediately opens the keyboard and unlocks the cursor for editing. Tapping outside locks it back to read mode.

---

### 2. User Experience & Visual Design

#### Visual Elements & Syntax Pill Styling
- **Section Headers (`[header]`)**:
  - Encapsulated in glowing capsule badge pills with subtle ambient border and bold accent text (Cyan `#80D8FF` in Dark Mode, Deep Sapphire `#005B94` in Light Mode).
- **Directory Paths (`/sdcard/...`)**:
  - Rendered inside sleek path pills with contrasting rounded background (Emerald/Teal tinted in Dark Mode, Slate/Sky in Light Mode) for instant visual recognition.
- **Keys (`scan_dirs =`, `dest =`, `ext =`)**:
  - Highlighted in crisp keyword accent color with bold typography.
- **Extension Chips (`pdf, doc, docx`)**:
  - Each extension following `ext =` is displayed as an individual rounded micro-chip pill with distinct border and background tag styling.

#### Interaction Architecture (Read Mode vs. Edit Mode)
```
┌────────────────────────────────────────────────────────┐
│                   RulesEditorActivity                  │
├────────────────────────────────────────────────────────┤
│ [Back]            rules.conf                [Saved ✓]  │
├────────────────────────────────────────────────────────┤
│ Rules Editor                     (▶ Run Script)        │
│ Configure download sorting rules                       │
├────────────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────────────────┐ │
│ │ Mode: [READING MODE] (Tap inside to edit)          │ │
│ │ ┌──────────────────┐                               │ │
│ │ │  [settings]      │ ← Glowing Section Pill        │ │
│ │ └──────────────────┘                               │ │
│ │ scan_dirs = ┌──────────────────────┐               │ │
│ │             │ /sdcard/Download     │ ← Path Pill   │ │
│ │             └──────────────────────┘               │ │
│ │                                                    │ │
│ │ ┌──────────────────┐                               │ │
│ │ │  [document]      │                               │ │
│ │ └──────────────────┘                               │ │
│ │ dest = ┌────────────────────────┐                  │ │
│ │        │ /sdcard/Documents      │                  │ │
│ │        └────────────────────────┘                  │ │
│ │ ext  = ┌─────┐ ┌─────┐ ┌──────┐                    │ │
│ │        │ pdf │ │ doc │ │ docx │ ← Extension Chips  │ │
│ │        └─────┘ └─────┘ └──────┘                    │ │
│ └────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────┤
│ [Pick Path]            [Reset]                 [Save]  │
└────────────────────────────────────────────────────────┘
```

- **Read Mode (Default / Unfocused)**:
  - Cursor is hidden, soft keyboard is kept dismissed.
  - Full touch events enable silky scrolling and inspection without triggering accidental typing or keyboard jumps.
  - Mode indicator pill inside header displays `READ MODE • Tap to edit`.
- **Edit Mode (Focused)**:
  - Tapping inside the editor card or on the text immediately requests focus, places cursor at touch location, and smoothly slides up the keyboard.
  - Mode indicator transitions to `EDITING • Tap outside to lock`.
  - Status pill tracks `Modified •` vs `Saved ✓` in real time.
- **Tap Outside to Lock**:
  - Tapping anywhere outside the editor (app bar, hero title, action buttons, screen background) immediately clears editor focus, dismisses the keyboard via `InputMethodManager`, and locks the view back into Read Mode.

---

### 3. Key Technical Decisions & Trade-Offs

- **Custom Spannable Highlighting Engine (`RulesSyntaxHighlighter`)**:
  - *Chosen Approach*: Use a high-performance `SpannableStringBuilder` with custom `ReplacementSpan` / `PillSpan` subclasses (`SectionPillSpan`, `PathPillSpan`, `ChipPillSpan`) applied to the text.
  - *Why*: Allows real text editing and saving while rendering rich, custom-drawn canvas pills with rounded corners, padding, and theme-adaptive colors.
  - *Alternatives Considered*: WebView with Monaco/Prism (too heavy, high startup latency, broken touch/keyboard handling).
- **Window-Level Dispatch for Tap-Outside Detection**:
  - *Chosen Approach*: Override `dispatchTouchEvent` in `RulesEditorActivity`. When an `ACTION_DOWN` occurs outside the editor's bounding box while in edit mode, clear focus from the editor and hide the soft keyboard.
  - *Why*: Guarantees that tapping any button, background, header, or empty space reliably locks the editor and hides the keyboard without requiring click listeners on every single view.

---

### 4. Technical Architecture & Implementation Steps

#### Component & Logic Flow
```
User Touches Inside Editor Card
         │
         ▼
[Enable Cursor & Focus] ──► [Show Keyboard] ──► [Update Mode: EDITING]
         │
         ▼
[User Edits Text] ──► [RulesSyntaxHighlighter applies Spans] ──► [Status: Modified •]
         │
User Touches Outside Editor Card
         │
         ▼
[dispatchTouchEvent Detects Outside Rect]
         │
         ▼
[Clear Focus] ──► [Hide Keyboard] ──► [Re-apply Clean Spans] ──► [Update Mode: READ]
```

#### Step-by-Step Implementation:
1. **Custom Canvas Pill Spans (`PillSpan.kt`)**:
   - `SectionPillSpan`: Draws glowing rounded pill background with padding around section tags `[name]`.
   - `PathPillSpan`: Draws container pill around `/sdcard/...` and file paths with subtle border.
   - `ExtensionChipSpan`: Draws individual mini-chip pill around extensions (`pdf`, `docx`, `mp3`, etc.).
   - `KeySpan`: Renders syntax keyword styling for `scan_dirs`, `dest`, `ext`.
2. **Syntax Highlighter Controller (`RulesSyntaxHighlighter.kt`)**:
   - Compiles regex patterns for sections `^\[(.*?)\]`, paths `(/[^\s,]+)`, keys `^([a-zA-Z0-9_]+)\s*=`, and comma-separated extensions after `ext\s*=`.
   - Formats text dynamically on load, after edits, and when transitioning modes.
3. **Read/Edit Mode Manager in `RulesEditorActivity.kt`**:
   - Dynamic mode switching: `setEditMode(enabled: Boolean)`.
   - Override `dispatchTouchEvent(ev: MotionEvent)` to detect taps outside `editorCardContainer` bounds.
   - Toggle soft keyboard visibility gracefully via `WindowInsetsControllerCompat` / `InputMethodManager`.
   - Header mode chip (`READ MODE • Tap to edit` vs `EDITING • Tap outside to lock`).
4. **Theme Shifting Sync**:
   - Ensure all pill spans and syntax colors update smoothly between Dark Mode (`#80D8FF`, glowing dark pills) and Light Mode (`#005B94`, light-contrast pill tags).
