package multiurlcopy.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import multiurlcopy.ui.Settings;
import multiurlcopy.ui.SettingsTab;

public class MultiUrlCopyExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Multi URL Copy");

        Settings settings = new Settings(api.persistence().preferences());
        SettingsTab tab = new SettingsTab(settings, api.logging());

        api.userInterface().registerSuiteTab("Copy URLs", tab);
        api.userInterface().registerContextMenuItemsProvider(new CopyUrlsContextMenu(tab));

        Runnable unregisterHotkey = new HotkeyDispatcher(settings, tab).register();
        api.extension().registerUnloadingHandler(unregisterHotkey::run);

        api.logging().logToOutput(
                "Multi URL Copy loaded. Hotkey: " + settings.hotkey().display()
                        + " (works in other extensions' tables too).");
    }
}
