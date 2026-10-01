package in.sherlock.auth.infrastructure.security;

import in.sherlock.auth.application.dto.AccessTokenClaims;
import in.sherlock.auth.application.port.out.SessionRepositoryPort;
import in.sherlock.auth.application.port.out.TokenProviderPort;
import in.sherlock.auth.domain.exception.AuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenProviderPort tokens;
    private final SessionRepositoryPort sessions;

    public JwtAuthenticationFilter(TokenProviderPort tokens, SessionRepositoryPort sessions) {
        this.tokens = tokens;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AccessTokenClaims claims = tokens.parseAccessToken(header.substring(7));
                boolean activeSession = sessions.findById(claims.sessionId())
                        .filter(session -> session.getUserId().equals(claims.userId()))
                        .filter(session -> session.isActive(Instant.now()))
                        .isPresent();
                if (activeSession) {
                    AuthPrincipal principal = new AuthPrincipal(claims.userId(), claims.sessionId(), claims.role());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (AuthException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
