package multiurlcopy.burp;

/**
 * Records that Burp just asked extensions for context-menu items.
 *
 * <p>Burp calls a registered {@code ContextMenuItemsProvider} only for
 * components it owns, so a very recent call is a reliable signal that the
 * right-click now being handled belongs to Burp's own UI and already has
 * a menu. The popup installer uses it to stay out of the way instead of
 * opening a second menu on top of Burp's.
 */
final class BurpMenuActivity {

    /** Comfortably longer than one menu build, far shorter than two clicks. */
    private static final long WINDOW_MILLIS = 500;

    private volatile long lastInvocation = Long.MIN_VALUE;

    void markInvoked() {
        lastInvocation = System.currentTimeMillis();
    }

    boolean invokedRecently() {
        return System.currentTimeMillis() - lastInvocation < WINDOW_MILLIS;
    }
}
