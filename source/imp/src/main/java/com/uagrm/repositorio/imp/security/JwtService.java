package com.uagrm.repositorio.imp.security;

import com.uagrm.repositorio.dao.entity.Usuario;
import com.uagrm.repositorio.imp.config.SeguridadProperties;
import com.uagrm.repositorio.imp.exception.NegocioException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Emite y lee los JWT del sistema.
 * Access token: sub, usuario, rol, pwd_change, typ=access. Refresh token: sub, typ=refresh.
 * Ambos llevan jti (UUID) para poder revocarlos individualmente en Redis.
 */
@Service
public class JwtService {

    public static final String CLAIM_TIPO = "typ";
    public static final String CLAIM_USUARIO = "usuario";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_CAMBIO_PASSWORD = "pwd_change";
    /** Instante de emision en milisegundos: iat solo tiene precision de segundos y no alcanza para revocado-desde. */
    public static final String CLAIM_EMITIDO_MS = "iat_ms";
    public static final String TIPO_ACCESS = "access";
    public static final String TIPO_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshTokenDecoder;
    private final SeguridadProperties props;

    public JwtService(JwtEncoder jwtEncoder,
                      @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
                      SeguridadProperties props) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.props = props;
    }

    public ParTokens generar(Usuario usuario, Duration accessTtl) {
        Instant ahora = Instant.now();
        String sub = usuario.getIdUsuario().toString();

        JwtClaimsSet access = base(sub, ahora, accessTtl)
                .claim(CLAIM_TIPO, TIPO_ACCESS)
                .claim(CLAIM_USUARIO, usuario.getUsuario())
                .claim(CLAIM_ROL, usuario.getTipoUsuario().getNombre())
                .claim(CLAIM_CAMBIO_PASSWORD, usuario.getRequiereCambioPassword())
                .build();
        JwtClaimsSet refresh = base(sub, ahora, props.jwt().refreshTtl())
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                .build();

        return new ParTokens(codificar(access), codificar(refresh), accessTtl);
    }

    public Jwt decodificarRefresh(String refreshToken) {
        try {
            return refreshTokenDecoder.decode(refreshToken);
        } catch (JwtException ex) {
            throw NegocioException.noAutorizado("El refresh token es invalido o expiro. Inicie sesion nuevamente.");
        }
    }

    private JwtClaimsSet.Builder base(String sub, Instant ahora, Duration ttl) {
        return JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.jwt().emisor())
                .subject(sub)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(ttl))
                .claim(CLAIM_EMITIDO_MS, ahora.toEpochMilli());
    }

    private String codificar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record ParTokens(String accessToken, String refreshToken, Duration accessTtl) {
    }
}
