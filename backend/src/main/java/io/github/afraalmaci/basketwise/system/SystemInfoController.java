package io.github.afraalmaci.basketwise.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small endpoint the frontend calls on startup. It proves the whole chain
 * (browser -> API -> database) works and tells the UI which data mode is active.
 */
@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    private final JdbcTemplate jdbc;
    private final String dataMode;

    public SystemInfoController(JdbcTemplate jdbc,
                                @Value("${basketwise.data-mode}") String dataMode) {
        this.jdbc = jdbc;
        this.dataMode = dataMode;
    }

    @GetMapping("/info")
    public SystemInfo info() {
        boolean dbUp;
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            dbUp = one != null && one == 1;
        } catch (RuntimeException e) {
            dbUp = false;
        }
        return new SystemInfo("basketwise", dataMode, dbUp);
    }

    public record SystemInfo(String app, String dataMode, boolean databaseUp) {
    }
}
