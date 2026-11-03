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

        BurpMenuActivity burpMenuActivity = new BurpMenuActivity();

        api.userInterface().registerSuiteTab("Copy URLs", tab);
        api.userInterface().registerContextMenuItemsProvider(new CopyUrlsContextMenu(tab, burpMenuActivity));

        Runnable unregisterHotkey = new HotkeyDispatcher(settings, tab).register();
        Runnable uninstallPopup = new TablePopupInstaller(
                tab, burpMenuActivity, api.userInterface()::applyThemeToComponent).install();

        // A KeyEventDispatcher and an AWTEventListener both outlive an
        // extension reload unless removed, holding the old classloader.
        api.extension().registerUnloadingHandler(() -> {
            unregisterHotkey.run();
            uninstallPopup.run();
        });

        api.logging().logToOutput(
                "Multi URL Copy loaded. Hotkey: " + settings.hotkey().display()
                        + ". Right-click also works in other extensions' tables.");
    }
}
