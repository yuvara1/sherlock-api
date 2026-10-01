package in.sherlock.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SherlockAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(SherlockAuthApplication.class, args);
    }
}
