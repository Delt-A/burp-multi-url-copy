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
}
