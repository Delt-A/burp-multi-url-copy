# Multi URL Copy

A Burp Suite extension that copies the URLs of every selected table row to
the clipboard — in Burp's own tables **and** in tables drawn by other
extensions.

Built on the Montoya API.

## The problem

Burp's native tables let you select many rows, but getting their URLs out
one at a time is slow. Third-party extension tables are worse: a results
grid may support multi-row selection and still offer no way to copy
anything, because **Burp only routes context-menu events to components it
owns**. A registered `ContextMenuItemsProvider` is never called for an
extension's own `JTable`.

Montoya's hotkey API does not close the gap either. As of 2026.7,
`UserInterface.registerHotKeyHandler` binds to a `HotKeyContext`, and that
enum has exactly five members — `HTTP_MESSAGE_EDITOR`,
`PROXY_HTTP_HISTORY`, `INTRUDER_ATTACK_RESULTS`, `ORGANIZER_ENTRIES`,
`SITE_MAP_CONTENTS_TABLE`. All five are Burp's own.

So reaching a foreign table needs Swing-level hooks, and that is what this
extension adds.

## Use

| Where | How |
|---|---|
| Burp's own tables | Select rows → right-click → **Copy URLs** → pick a format |
| Any other table | Select rows → **Ctrl+Alt+U** |
| Any other table | Right-click → a **Copy URLs** submenu appended to that table's own menu |

Formats: `Full URL`, `Full URL (dedup)`, `METHOD + URL`, `Path only`.

The hotkey and the default format are configurable in the **Copy URLs**
tab and persist with your Burp preferences. The default deliberately
avoids `Ctrl+U` and `Ctrl+Shift+U`, which Burp binds to URL encode and
decode.

## Partial results are always flagged

Reading another extension's table is necessarily a heuristic. Per selected
row, the first rule that yields a URL wins:

1. a cell that is itself an absolute `http(s)` URL;
2. a host-like column joined with a path-like column;
3. a URL embedded inside a longer cell;
4. otherwise the row is counted as failed — and reported.

When some rows yield nothing, the recognized URLs are copied and the
shortfall is stated: `copied 12/15 - 3 rows had no recognizable URL`, in
the tab and in Burp's output log. When nothing is recognized, **the
clipboard is left untouched**, so a failed attempt never destroys what you
had there. The full copied text is always shown in the tab.

A short list that looked complete would be the worst outcome here: the
missing endpoint simply never gets tested. So a partial copy is never
reported as a plain success.

## Design notes

Some of this is unusual enough to deserve an explanation.

**Why a toolkit `AWTEventListener`.** It is the only way to offer a
right-click menu on a table this extension does not own. It is used as
narrowly as possible: it reacts only to a right-click that resolves to a
`JTable`, it mutates nothing on the way in, and it is removed when the
extension unloads.

**Why it waits for the mouse release.** On X11 a right-click is a popup
trigger on `MOUSE_PRESSED`, but extensions commonly build their menu in
`mouseReleased`. Acting on the press opened a menu first and swallowed the
release, making the host extension's own menu disappear.

**Why it appends rather than replaces.** If the table already produced a
popup, that menu is the user's. The submenu is added to it and the popup
re-shown at the same point. On Burp's own tables the listener does nothing
at all, because the items are already contributed through the proper
provider.

**Why the selection is snapshotted.** The toolkit callback runs before the
event reaches the component, and Swing's own handler collapses a multi-row
selection to the clicked row. Reading the selection later would turn
"copy these eighteen rows" into "copy one".

**Why only the public `TableModel` is read.** No reflection into another
extension's internals. Anything this extension can read is also visible on
screen, so an upgrade to a host extension cannot break it invisibly.

**Why URLs are parsed twice.** `java.net.URI` runs first, for its
correctness and its raw accessors, which keep percent-encoding verbatim.
Real traffic carries characters it refuses — Firefox's addon update check
sends `appID={uuid}` unencoded on every run — so a permissive fallback
accepts those rather than dropping the row.

Fuller rationale in [`docs/design.md`](docs/design.md).

## Build

Requires JDK 17 or newer. The build compiles with `release = 17`, which is
what the Montoya API targets.

```bash
./gradlew build
```

Output: `build/libs/multi-url-copy.jar`.

`./gradlew install` additionally writes the jar to `dist/`, a path outside
`build/` that survives `./gradlew clean` — useful because Burp holds the
jar it loaded open.

Load it in Burp via **Extensions → Installed → Add → Java**.

```bash
./gradlew test
```

## Compatibility

- Burp Suite Professional and Community
- Montoya API 2026.7 (`compileOnly`; the extension bundles no runtime
  dependencies)

## License

[Apache License 2.0](LICENSE).
