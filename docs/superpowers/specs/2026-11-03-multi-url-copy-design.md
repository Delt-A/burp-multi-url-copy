# Multi URL Copy — Burp Suite Extension

**Date:** 2026-11-03
**Status:** Approved for implementation

## Problem

Burp's native tables let you select rows, but copying the URLs of many
requests at once is awkward. Third-party extension tables are worse: Auth
Analyzer's results table supports multi-row selection yet offers no
right-click menu and no copy action at all. A pentester who wants to feed
30 endpoints from an Auth Analyzer run into `ffuf` or `nuclei` has to
retype them.

This extension copies the URLs of every selected row to the clipboard, in
Burp's own tables **and** in tables drawn by other extensions.

## Constraint that shapes the whole design

Burp invokes a registered `ContextMenuItemsProvider` only for its own UI
components. An extension's `JTable` is plain Swing; Burp does not know it
exists and will never route a context-menu event to it.

Verified against the installed Auth Analyzer 1.1.14
(`com.protect7.authanalyzer.gui.main.CenterPanel`):

- its table never calls `setSelectionMode(...)`, so Swing's default
  `MULTIPLE_INTERVAL_SELECTION` is active — multi-row selection already
  works there;
- the table's own right-click menu is built in `CenterPanel$5.mouseReleased`,
  an anonymous `MouseListener` - not on the table as a
  `componentPopupMenu`, which is why a first pass over `CenterPanel`
  alone wrongly concluded there was no menu;
- `RequestTableModel.getValueAt` exposes `Id`, `Method`, `Host`,
  `URL` (path only), `Status`, `Length` as plain values.

Montoya's own hotkey API does not close this gap either. As of
2026.7, `UserInterface.registerHotKeyHandler` binds a handler to a
`HotKeyContext`, and that enum has exactly five members —
`HTTP_MESSAGE_EDITOR`, `PROXY_HTTP_HISTORY`, `INTRUDER_ATTACK_RESULTS`,
`ORGANIZER_ENTRIES`, `SITE_MAP_CONTENTS_TABLE`. All five are Burp's own;
no extension table can be addressed.

So the extension needs two trigger paths: the Montoya context menu for
Burp's tables, and a Swing-level global hotkey for everything else.

Only one hotkey mechanism is registered, and it is the Swing one. A
`KeyEventDispatcher` sees a key press before Burp's own bindings and
consumes it, so a Burp-registered `HotKeyHandler` on the same combination
would never fire. Registering both would be dead code at best and a
double copy at worst.

## Architecture

Six units. The two that carry the logic worth testing depend on neither
Burp nor each other.

| Unit | Responsibility | Depends on |
|---|---|---|
| `core.UrlEntry` | Immutable value: `method, scheme, host, port, path, query` | nothing |
| `core.UrlFormatter` | `List<UrlEntry>` → clipboard string, one per format | nothing |
| `core.TableScraper` | `JTable` → `ScrapeResult(entries, failedRows)` | Swing only |
| `burp.CopyUrlsContextMenu` | `ContextMenuItemsProvider` for Burp's own tables | Montoya |
| `burp.HotkeyDispatcher` | Global `KeyEventDispatcher` | Swing |
| `ui.SettingsTab` | Hotkey, default format, last-copy log | Swing |

`UrlFormatter` and `TableScraper` are the units under unit test.
Everything else is wiring thin enough to verify by hand in Burp.

### Data flow

Burp's own tables — exact, no guessing:

```
right-click → CopyUrlsContextMenu
            → event.selectedRequestResponses()
            → requestResponse.request().url()
            → UrlEntry → UrlFormatter → clipboard
```

The provider contributes a single **Copy URLs** submenu holding one item
per format. It returns no items at all when
`event.selectedRequestResponses()` is empty — so the menu never appears
in a message editor or on an empty selection, where it would do nothing.

Any other table — heuristic:

```
Ctrl+Alt+U  → HotkeyDispatcher
            → KeyboardFocusManager.getFocusOwner()
            → nearest enclosing JTable (or null)
            → TableScraper → UrlFormatter → clipboard
```

Any other table, by right-click — heuristic, same pipeline:

```
right-click → TablePopupInstaller (toolkit AWTEventListener)
            → snapshot selection on MOUSE_PRESSED
            → act after MOUSE_RELEASED
            → host popup open?  yes → append a "Copy URLs" submenu to it
                                no  → show our own popup
            → TableScraper → UrlFormatter → clipboard
```

Three rules keep this out of everyone's way:

- **Act after the release, never the press.** On X11 a right-click is a
  popup trigger on `MOUSE_PRESSED`, but extensions commonly build their
  menu in `mouseReleased`. Opening on the press swallowed the release and
  made Auth Analyzer's own menu vanish.
- **Append, do not replace.** A table that already produced a popup keeps
  it; the submenu is added and the popup re-shown at the same point,
  which is what re-packs an already-visible `JPopupMenu`.
- **Stand down on Burp's own tables.** `provideMenuItems` is called only
  for Burp-owned components, so a call within the last 500 ms means Burp
  is building the menu and already has our entries.

The selection is snapshotted during the toolkit callback, which runs
before the event reaches the component, because Swing's own handler
collapses a multi-row selection to the clicked row. It is restored only
under our own menu: a host extension's items may act on the selection
themselves, and rewriting it underneath would change what they do.

## `UrlFormatter` — four formats

| Format | Output |
|---|---|
| `FULL_URL` | `https://api.target.com/v1/users?id=1` |
| `FULL_URL_DEDUP` | same, duplicates removed, first-occurrence order kept |
| `METHOD_AND_URL` | `GET https://api.target.com/v1/users?id=1` |
| `PATH_ONLY` | `/v1/users?id=1` |

Rules:

- One entry per line, `\n` separated, no trailing newline.
- Default ports are omitted (`:443` for https, `:80` for http); any other
  port is kept.
- An empty path renders as `/`.
- A missing query renders as nothing, not as a bare `?`.
- Percent-encoding present in the source is preserved verbatim; the
  formatter never re-encodes or decodes.
- `METHOD_AND_URL` renders `-` when the method is unknown, so the column
  count per line stays stable for `awk`/`cut`.
- Deduplication is applied only by `FULL_URL_DEDUP`. The other three
  formats preserve every selected row, including duplicates.

## `TableScraper` — heuristic and its honesty contract

Resolution order per selected row. The first rule that yields a usable
URL wins.

`ScrapeResult` carries exactly two things: `List<UrlEntry> entries` and
`int failedRows`. `entries.size() + failedRows` always equals the number
of selected rows, which is what makes the `N/M` warning trustworthy.

1. **Named columns.** Match `getColumnName(i)` case-insensitively against
   `url`, `host`, `path`, `method`, `port`.
2. **Absolute URL cell.** Any cell whose string form matches
   `^https?://\S+` is used as-is.
3. **Host + path pair.** A host-like column joined with a path-like
   column. This is the Auth Analyzer case: `Host` + `URL`(=path).
4. **Regex sweep.** Scan every cell in the row for an embedded
   `https?://\S+`.
5. **Failure.** The row is counted in `failedRows`.

View-to-model indices always go through `convertRowIndexToModel()`, so a
sorted or filtered table yields the rows the user actually sees selected.

Scheme inference when rule 3 fires and no scheme is present: default to
`https`, unless a port column says `80`, in which case `http`.

### Honesty contract

Heuristic scraping can silently drop rows. That failure mode is worse
than no feature at all — a short list looks like a complete one, and the
missing endpoint never gets tested. So:

- **Nothing selected** → status message, clipboard left untouched. The
  previous clipboard contents are never destroyed by a no-op.
- **Partial success** → the N recognized URLs are copied, and a warning
  is surfaced in both the Settings tab and Burp's extension output:
  `copied 12/15 - 3 rows had no recognizable URL`.
- **Total failure** → clipboard left untouched, explicit message.
- **Focus is not inside a JTable** → `no table focused`, clipboard
  untouched.
- Every copy writes its full payload into the Settings tab's last-copy
  pane, so the result can be eyeballed before it is pasted into a
  scanner.

A partial copy is never reported as a plain success.

### Error containment

Three places run foreign or hostile-to-assumptions code, and none of them
may throw into Burp:

- `TableScraper` calls another extension's `getValueAt` and
  `getColumnName`. A row that throws is counted in `failedRows` rather
  than aborting the copy, which preserves the
  `entries + failedRows == selected` invariant and therefore the honesty
  of the `N/M` warning.
- `HotkeyDispatcher.dispatchKeyEvent` runs on every key press anywhere in
  Burp. An escaping exception would poison the event queue, so the body
  is wrapped and a failure is reported like any other unsuccessful copy.
- A system clipboard write can be refused when another X11 owner holds
  the selection. That is reported as a failure, with the payload kept
  visible in the tab for a manual copy, never as a success.

## Hotkey

Default `Ctrl+Alt+U`, verified free against the user's 48 configured Burp
hotkeys. `Ctrl+U` and `Ctrl+Shift+U` are taken by Burp's URL
encode/decode and must not be used.

The dispatcher consumes the event only when it actually acts, i.e. when
the focus owner resolves to a `JTable`. Otherwise the event is passed
through untouched so Burp's own bindings keep working.

The combination is editable in the Settings tab and persisted through
Montoya's `persistence().preferences()`.

## Settings tab

A single Burp tab named **Copy URLs**:

- hotkey chooser (modifiers + key), persisted;
- default format for the hotkey path, persisted;
- read-only pane showing the last copied payload and any warning.

## Testing

Unit tests (JUnit 5), no Burp runtime required:

**`UrlFormatter`** — each of the four formats against: default and
non-default port, empty query, empty path, already percent-encoded path,
unknown method, duplicate entries (present for three formats, collapsed
for `FULL_URL_DEDUP`), empty input list.

**`TableScraper`** — driven by `DefaultTableModel` fixtures standing in
for real tables:

- Auth Analyzer shape: `Id | Method | Host | URL(path) | Status | Length`
  → rule 3;
- full-URL shape: a single `URL` column holding absolute URLs → rule 2;
- unnamed columns holding absolute URLs → rule 4;
- a table with no URL anywhere → every row in `failedRows`, zero entries;
- a sorted table → selection maps through `convertRowIndexToModel()`;
- a mixed table where some rows resolve and others do not → exact
  `failedRows` count.
- a table model whose `getValueAt` throws on one row → that row counted
  in `failedRows`, the others still returned.

Manual verification in Burp: Proxy history (context menu path) and Auth
Analyzer (hotkey path).

## Build

Gradle wrapper 9.8.2, output a plain `jar`.

No shadow/fat jar is needed. Montoya is `compileOnly` — Burp supplies it
at runtime — and JUnit is `testImplementation`, so the extension has zero
runtime dependencies and the standard `jar` task produces a loadable
artifact. This also keeps the build free of the shadow plugin.

The machine previously carried exactly one JDK with `javac` — OpenJDK 11
— while 17, 21, 23 and 25 were JREs only. The Montoya API publishes a
Java 17 variant only, so JDK 11 cannot resolve it. OpenJDK 25 (25.0.5,
the newest LTS in the Kali repo) was installed as part of this work, so
the build no longer needs Gradle to auto-provision a toolchain or reach
the network on first build.

Gradle therefore runs on JDK 25 and compiles with `release = 17`: source
and target stay on the version Montoya requires, while the toolchain is
the one the machine actually has. The wrapper is pinned to Gradle 9.8.2;
the cached 8.5 cannot run a daemon on Java 25.

Montoya `2026.7` is the current release and, at a lower number than the
installed Burp Suite Pro 2026.10.1, is guaranteed to be present at
runtime. It is Java 17 bytecode, so `release = 17` still holds.
`options.encoding = 'UTF-8'` is set so the build is locale-independent.

## Out of scope

- Copying request or response bodies.
- Exporting to a file; the clipboard is the whole interface.
- Reflection into other extensions' internals. Rule 3 reads the public
  `TableModel`, nothing deeper, so an Auth Analyzer upgrade cannot break
  it in a way that is not visible on screen.
- A "copy as curl/ffuf command" format. Dedicated extensions already do
  this for single requests.
