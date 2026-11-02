package dev.hypermediajava.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

/**
 * DaisyUI's theme-controller switches the theme instantly in CSS; this endpoint just remembers the
 * choice in a cookie so the next server render uses it. Theme switching with zero JavaScript.
 */
@Controller
public class PreferencesController {

    @PostMapping("/prefs/theme")
    public ResponseEntity<Void> theme(@RequestParam String theme, HttpServletResponse response) {
        if (GlobalModel.THEMES.contains(theme)) {
            ResponseCookie cookie = ResponseCookie.from("theme", theme)
                    .path("/").maxAge(Duration.ofDays(365)).sameSite("Lax").httpOnly(true).build();
            response.addHeader("Set-Cookie", cookie.toString());
        }
        return ResponseEntity.noContent().build();
    }
}
