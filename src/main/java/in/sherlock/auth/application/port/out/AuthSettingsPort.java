package in.sherlock.auth.application.port.out;

import java.time.Duration;

/** Token lifetimes the application layer needs; supplied by infrastructure configuration. */
public interface AuthSettingsPort {
    Duration refreshTtl();
    Duration resetTtl();
}
