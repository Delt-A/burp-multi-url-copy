package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableScraperTest {

    private static JTable table(String[] columns, Object[][] rows) {
        return new JTable(new DefaultTableModel(rows, columns));
    }

    /** Auth Analyzer's shape: host and path live in separate columns. */
    @Test
    void joinsSeparateHostAndPathColumns() {
        JTable t = table(
                new String[]{"Id", "Method", "Host", "URL", "Status", "Length"},
                new Object[][]{
                        {1, "GET", "api.target.com", "/v1/users?id=1", 200, 512},
                        {2, "POST", "api.target.com", "/v1/login", 302, 0}});
        t.setRowSelectionInterval(0, 1);

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(0, result.failedRows());
        assertEquals("https://api.target.com/v1/users?id=1\nhttps://api.target.com/v1/login",
                UrlFormatter.format(result.entries(), UrlFormat.FULL_URL));
    }

    @Test
    void keepsTheMethodFromAMethodColumn() {
        JTable t = table(
                new String[]{"Method", "Host", "Path"},
                new Object[][]{{"DELETE", "a.com", "/x"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("DELETE https://a.com/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.METHOD_AND_URL));
    }

    @Test
    void portEightyImpliesHttp() {
        JTable t = table(
                new String[]{"Host", "Port", "Path"},
                new Object[][]{{"a.com", 80, "/x"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("http://a.com/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    @Test
    void nonStandardPortIsCarriedThrough() {
        JTable t = table(
                new String[]{"Host", "Port", "Path"},
                new Object[][]{{"a.com", 8443, "/x"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("https://a.com:8443/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    /** A single column holding absolute URLs, the Logger++ shape. */
    @Test
    void usesACellThatIsAlreadyAnAbsoluteUrl() {
        JTable t = table(
                new String[]{"#", "URL"},
                new Object[][]{{1, "https://a.com/one"}, {2, "http://b.com:8080/two?q=1"}});
        t.setRowSelectionInterval(0, 1);

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(0, result.failedRows());
        assertEquals("https://a.com/one\nhttp://b.com:8080/two?q=1",
                UrlFormatter.format(result.entries(), UrlFormat.FULL_URL));
    }

    @Test
    void findsAUrlInUnnamedColumns() {
        JTable t = table(
                new String[]{"A", "B"},
                new Object[][]{{"noise", "visited https://a.com/x yesterday"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("https://a.com/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    @Test
    void aTableWithNoUrlFailsEveryRowAndYieldsNothing() {
        JTable t = table(
                new String[]{"Name", "Count"},
                new Object[][]{{"alpha", 1}, {"beta", 2}});
        t.setRowSelectionInterval(0, 1);

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(List.of(), result.entries());
        assertEquals(2, result.failedRows());
        assertEquals(2, result.total());
    }

    @Test
    void countsExactlyTheRowsThatCouldNotBeResolved() {
        JTable t = table(
                new String[]{"Host", "Path"},
                new Object[][]{{"a.com", "/x"}, {"", ""}, {"c.com", "/z"}});
        t.setRowSelectionInterval(0, 2);

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(2, result.entries().size());
        assertEquals(1, result.failedRows());
        assertEquals(3, result.total());
    }

    @Test
    void nothingSelectedYieldsAnEmptyResult() {
        JTable t = table(new String[]{"Host", "Path"}, new Object[][]{{"a.com", "/x"}});

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(List.of(), result.entries());
        assertEquals(0, result.failedRows());
        assertEquals(0, result.total());
    }

    /** With a sorter installed, the selected VIEW rows must map to the right model rows. */
    @Test
    void selectionMapsThroughTheRowSorter() {
        JTable t = table(
                new String[]{"Host", "Path"},
                new Object[][]{{"c.com", "/3"}, {"a.com", "/1"}, {"b.com", "/2"}});
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(t.getModel());
        t.setRowSorter(sorter);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        t.setRowSelectionInterval(0, 0);

        assertEquals("https://a.com/1",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    @Test
    void absoluteUrlCellWinsOverHostAndPathColumns() {
        JTable t = table(
                new String[]{"Host", "Path", "Full"},
                new Object[][]{{"wrong.com", "/wrong", "https://right.com/ok"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("https://right.com/ok",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    /** A third-party TableModel is foreign code and may throw; one bad row must not kill the copy. */
    @Test
    void aRowWhoseModelThrowsIsCountedAsFailedNotPropagated() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"a.com", "/x"}, {"b.com", "/y"}}, new String[]{"Host", "Path"}) {
            @Override
            public Object getValueAt(int row, int column) {
                if (row == 1) {
                    throw new IllegalStateException("boom");
                }
                return super.getValueAt(row, column);
            }
        };
        JTable t = new JTable(model);
        t.setRowSelectionInterval(0, 1);

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(1, result.entries().size());
        assertEquals(1, result.failedRows());
        assertEquals(2, result.total());
    }

    /**
     * Burp's own Proxy history model, as observed live: the Host column holds an
     * internal object whose toString is a debug dump, and the model carries a
     * "Listener port" column holding the proxy's own port, not the target's.
     */
    @Test
    void burpHistoryHostObjectAndListenerPortDoNotCorruptTheUrl() {
        Object burpHost = new Object() {
            @Override
            public String toString() {
                return "Ziu4[ZJA=cp-api-staging.adsplay.net, ZJ3=https://cp-api-staging.adsplay.net]";
            }
        };
        JTable t = table(
                new String[]{"#", "Host", "Method", "URL", "Status code", "Listener port", "Time"},
                new Object[][]{{3974, burpHost, "GET", "/api/categories-weight/get/report/metric", 400, 8080,
                        "17:31:33 27 Oct 2026"}});
        t.setRowSelectionInterval(0, 0);

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(0, result.failedRows());
        assertEquals("https://cp-api-staging.adsplay.net/api/categories-weight/get/report/metric",
                UrlFormatter.format(result.entries(), UrlFormat.FULL_URL));
    }

    @Test
    void aColumnMerelyContainingPortIsNotTreatedAsThePort() {
        JTable t = table(
                new String[]{"Host", "Path", "Listener port"},
                new Object[][]{{"a.com", "/x", 8080}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("https://a.com/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    @Test
    void aHostCellThatIsNotAHostnameAndCarriesNoUrlFailsTheRow() {
        JTable t = table(
                new String[]{"Host", "Path"},
                new Object[][]{{"Ziu4[ZJA=garbage, ZJ3=nothing]", "/x"}});
        t.setRowSelectionInterval(0, 0);

        ScrapeResult result = TableScraper.scrape(t);

        assertEquals(0, result.entries().size());
        assertEquals(1, result.failedRows());
    }

    @Test
    void aSchemeEmbeddedInTheHostCellWins() {
        JTable t = table(
                new String[]{"Host", "Path"},
                new Object[][]{{"X[v=http://a.com]", "/x"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("http://a.com/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    @Test
    void anExactPortColumnStillApplies() {
        JTable t = table(
                new String[]{"Host", "port", "Path"},
                new Object[][]{{"a.com", 8443, "/x"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("https://a.com:8443/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }

    @Test
    void anImplausiblePortValueIsIgnored() {
        JTable t = table(
                new String[]{"Host", "Port", "Path"},
                new Object[][]{{"a.com", 999999, "/x"}});
        t.setRowSelectionInterval(0, 0);

        assertEquals("https://a.com/x",
                UrlFormatter.format(TableScraper.scrape(t).entries(), UrlFormat.FULL_URL));
    }
    @Test
    void anExplicitRowSetIsScrapedInsteadOfTheLiveSelection() {
        JTable t = table(
                new String[]{"Host", "Path"},
                new Object[][]{{"a.com", "/1"}, {"b.com", "/2"}, {"c.com", "/3"}});
        t.setRowSelectionInterval(1, 1);

        ScrapeResult result = TableScraper.scrape(t, new int[]{0, 2});

        assertEquals(0, result.failedRows());
        assertEquals("https://a.com/1\nhttps://c.com/3",
                UrlFormatter.format(result.entries(), UrlFormat.FULL_URL));
    }
}
