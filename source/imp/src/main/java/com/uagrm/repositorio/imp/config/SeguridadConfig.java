package com.uagrm.repositorio.imp.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.uagrm.repositorio.imp.security.JwtService;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Beans de seguridad compartidos por todos los microservicios: firma/validacion JWT (HS256),
 * conversion de claims a roles, BCrypt y CORS. Cada microservicio define su propio SecurityFilterChain.
 */
@Configuration
@EnableConfigurationProperties(SeguridadProperties.class)
public class SeguridadConfig {

    /** Autoridad unica que recibe un usuario que aun debe cambiar su password_default. */
    public static final String AUTORIDAD_CAMBIO_PASSWORD = "PASSWORD_CHANGE_REQUIRED";

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    @Bean
    SecretKey jwtSecretKey(SeguridadProperties props) {
        byte[] bytes = Base64.getDecoder().decode(props.jwt().secreto());
        if (bytes.length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("app.seguridad.jwt.secreto debe tener al menos 256 bits (32 bytes en Base64)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /** Decoder de access tokens: el que usa el resource server en cada request. */
    @Bean
    @Primary
    JwtDecoder accessTokenDecoder(SecretKey jwtSecretKey, SeguridadProperties props) {
        return decoderDeTipo(jwtSecretKey, props, JwtService.TIPO_ACCESS);
    }

    /** Decoder de refresh tokens: solo lo usa /auth/refresh y /auth/logout. */
    @Bean
    JwtDecoder refreshTokenDecoder(SecretKey jwtSecretKey, SeguridadProperties props) {
        return decoderDeTipo(jwtSecretKey, props, JwtService.TIPO_REFRESH);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtService.CLAIM_CAMBIO_PASSWORD))) {
                return List.of(new SimpleGrantedAuthority(AUTORIDAD_CAMBIO_PASSWORD));
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(JwtService.CLAIM_ROL)));
        });
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SeguridadProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsOrigenes());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static JwtDecoder decoderDeTipo(SecretKey key, SeguridadProperties props, String tipo) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.jwt().emisor()),
                new JwtClaimValidator<String>(JwtService.CLAIM_TIPO, tipo::equals)));
        return decoder;
    }
}
