package multiurlcopy.ui;

import burp.api.montoya.persistence.Preferences;
import multiurlcopy.core.Hotkey;
import multiurlcopy.core.UrlFormat;

/** Extension settings, persisted in Burp's own preference store. */
public class Settings {

    private static final String KEY_HOTKEY = "multiurlcopy.hotkey";
    private static final String KEY_FORMAT = "multiurlcopy.format";

    private final Preferences preferences;

    public Settings(Preferences preferences) {
        this.preferences = preferences;
    }

    public Hotkey hotkey() {
        return Hotkey.parse(preferences.getString(KEY_HOTKEY));
    }

    public void setHotkey(Hotkey hotkey) {
        preferences.setString(KEY_HOTKEY, hotkey.display());
    }

    public UrlFormat defaultFormat() {
        String stored = preferences.getString(KEY_FORMAT);
        if (stored == null) {
            return UrlFormat.FULL_URL;
        }
        try {
            return UrlFormat.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return UrlFormat.FULL_URL;
        }
    }

    public void setDefaultFormat(UrlFormat format) {
        preferences.setString(KEY_FORMAT, format.name());
    }
}
