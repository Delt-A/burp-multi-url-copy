package multiurlcopy.core;

import java.util.List;

/**
 * What a scrape of the selected rows produced.
 *
 * <p>{@code entries.size() + failedRows} always equals the number of
 * selected rows. That invariant is what makes the "copied N/M" warning
 * trustworthy, so a silently short list cannot be reported as a complete
 * one.
 */
public record ScrapeResult(List<UrlEntry> entries, int failedRows) {

    /** Number of rows that were selected. */
    public int total() {
        return entries.size() + failedRows;
    }
}
