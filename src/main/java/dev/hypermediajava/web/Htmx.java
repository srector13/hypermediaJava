package dev.hypermediajava.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The handful of htmx request/response headers this app uses. That's the whole "client protocol".
 *
 * @see <a href="https://htmx.org/reference/#headers">htmx headers reference</a>
 */
public final class Htmx {

    public static final String REQUEST = "HX-Request";
    public static final String BOOSTED = "HX-Boosted";
    public static final String HISTORY_RESTORE = "HX-History-Restore-Request";
    /** Response: fire a client-side event, e.g. tell the table to refresh itself. */
    public static final String TRIGGER = "HX-Trigger";
    /** Response: swap into a different element than the one the request targeted. */
    public static final String RETARGET = "HX-Retarget";
    public static final String RESWAP = "HX-Reswap";
    /** Custom header carrying the tab's identity (set once via hx-headers on &lt;body&gt;). */
    public static final String CLIENT_ID = "X-Client-Id";

    private Htmx() {
    }

    public static boolean isHtmx(HttpServletRequest request) {
        return "true".equals(request.getHeader(REQUEST));
    }

    /**
     * True when htmx wants a fragment. Boosted navigation and history-cache misses want the full page.
     */
    public static boolean wantsFragment(HttpServletRequest request) {
        return isHtmx(request)
                && !"true".equals(request.getHeader(BOOSTED))
                && !"true".equals(request.getHeader(HISTORY_RESTORE));
    }

    public static String clientId(HttpServletRequest request) {
        return request.getHeader(CLIENT_ID);
    }
}
