package multiurlcopy.burp;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import multiurlcopy.core.CopyOutcome;
import multiurlcopy.core.CopyReporter;
import multiurlcopy.core.CopyService;
import multiurlcopy.core.UrlEntry;
import multiurlcopy.core.UrlFormat;
import multiurlcopy.core.UrlFormatter;
import multiurlcopy.core.UrlParser;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds a "Copy URLs" submenu to Burp's own tables.
 *
 * <p>This path never guesses: the URL comes straight from the request, so
 * every selected row yields exactly one entry.
 */
public class CopyUrlsContextMenu implements ContextMenuItemsProvider {

    private final CopyReporter reporter;
    private final BurpMenuActivity burpMenuActivity;

    public CopyUrlsContextMenu(CopyReporter reporter, BurpMenuActivity burpMenuActivity) {
        this.reporter = reporter;
        this.burpMenuActivity = burpMenuActivity;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        // Burp only asks for items on components it owns, so this call is
        // the signal TablePopupInstaller uses to keep its own menu shut.
        burpMenuActivity.markInvoked();

        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (selected.isEmpty()) {
            return List.of();
        }

        JMenu menu = new JMenu("Copy URLs");
        for (UrlFormat format : UrlFormat.values()) {
            JMenuItem item = new JMenuItem(format.displayName());
            item.addActionListener(e -> copy(selected, format));
            menu.add(item);
        }
        return List.of(menu);
    }

    private void copy(List<HttpRequestResponse> selected, UrlFormat format) {
        try {
            List<UrlEntry> entries = new ArrayList<>(selected.size());
            for (HttpRequestResponse item : selected) {
                HttpRequest request = item.request();
                UrlParser.parse(request.url(), request.method()).ifPresent(entries::add);
            }
            String payload = UrlFormatter.format(entries, format);
            CopyService.perform(CopyOutcome.of(payload, entries.size(), selected.size()), reporter);
        } catch (RuntimeException e) {
            CopyService.perform(CopyOutcome.error("copy failed: " + e), reporter);
        }
    }
}
