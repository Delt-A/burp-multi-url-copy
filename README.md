# Multi URL Copy

A Burp Suite extension that copies the URLs of every selected table row
to the clipboard — in Burp's own tables and in tables drawn by other
extensions such as Auth Analyzer.

## Why the hotkey exists

Burp routes context-menu events only to components it owns. An
extension's table is plain Swing, so a registered
`ContextMenuItemsProvider` is never called there.

Montoya's own hotkey API does not close the gap either: as of 2026.7,
`registerHotKeyHandler` binds to a `HotKeyContext`, and that enum has
exactly five members — `HTTP_MESSAGE_EDITOR`, `PROXY_HTTP_HISTORY`,
`INTRUDER_ATTACK_RESULTS`, `ORGANIZER_ENTRIES`,
`SITE_MAP_CONTENTS_TABLE` — all Burp's own.

So the global hotkey is a Swing `KeyEventDispatcher`. It reads the
focused `JTable`'s public `TableModel`: no reflection into another
extension's internals, which means anything the extension can read is
also visible on screen.

## Use

- **Burp's own tables** — select rows, right-click, **Copy URLs**, pick a
  format. This path reads the URL straight off the request, so it never
  guesses.
- **Any other table** — select rows, press **Ctrl+Alt+U**. The format is
  the default chosen in the **Copy URLs** tab.

Formats: `Full URL`, `Full URL (dedup)`, `METHOD + URL`, `Path only`.

`Ctrl+U` and `Ctrl+Shift+U` are deliberately avoided — Burp binds them to
URL encode and decode.

## Partial results are always flagged

Scraping a foreign table is a heuristic. When some selected rows yield no
URL, the extension copies what it recognized and says so —
`copied 12/15 - 3 rows had no recognizable URL` — in the Copy URLs tab
and in Burp's output log. When it recognizes nothing, the clipboard is
left untouched, so an earlier copy is not destroyed by a failed attempt.
The full copied text is always shown in the tab, so it can be checked
before it is pasted into a scanner.

A short list that looked complete would be the worst outcome here: the
missing endpoint simply never gets tested.

## How a row is resolved

Per selected row, first rule that yields a URL wins:

1. a cell that is itself an absolute `http(s)` URL;
2. a host-like column joined with a path-like column — the Auth Analyzer
   shape, where `Host` and `URL` (a path) are separate columns;
3. a URL embedded inside a longer cell;
4. otherwise the row is counted as failed and reported.

Selection is mapped through `convertRowIndexToModel()`, so a sorted or
filtered table yields the rows that are visibly selected.

## Build

Requires a JDK 17 or newer; the build compiles with `release = 17`
because that is what the Montoya API targets.

    ./gradlew clean build

Output: `build/libs/multi-url-copy.jar`. Load it in Burp via
Extensions → Installed → Add → Java.
