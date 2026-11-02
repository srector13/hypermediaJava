package dev.hypermediajava.config;

import dev.hypermediajava.employee.Engine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Lets controllers declare {@code @PathVariable Engine engine} for {@code /jdbc/...} and {@code /jpa/...}. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Engine.class, Engine::fromPath);
    }

    /**
     * The same URL returns either a full page or a fragment depending on {@code HX-Request}.
     * {@code Vary} keeps browsers and proxies from serving one in place of the other.
     */
    @Bean
    OncePerRequestFilter varyOnHtmxFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                response.addHeader("Vary", "HX-Request, HX-Boosted, HX-History-Restore-Request");
                chain.doFilter(request, response);
            }
        };
    }
}
