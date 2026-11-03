package multiurlcopy.core;

/** The shapes a selection can be copied in. */
public enum UrlFormat {

    FULL_URL("Full URL"),
    FULL_URL_DEDUP("Full URL (dedup)"),
    METHOD_AND_URL("METHOD + URL"),
    PATH_ONLY("Path only");

    private final String displayName;

    UrlFormat(String displayName) {
        this.displayName = displayName;
    }

    /** Label shown in the context menu and the settings tab. */
    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
