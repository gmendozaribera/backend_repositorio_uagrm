package com.uagrm.repositorio.imp.redis;

import com.uagrm.repositorio.imp.dominio.ConstantesDominio.Parametro;
import com.uagrm.repositorio.imp.dominio.ParametrosService;
import com.uagrm.repositorio.imp.exception.LimiteIntentosException;
import com.uagrm.repositorio.imp.security.IpCliente;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Limite de consultas anonimas al repositorio publico (GET /api/v1/publico/**) por IP:
 * {@code WORK_GROUP.PUBLICO_PETICIONES_POR_MINUTO} (60 por defecto) en una ventana fija de 60 s, con la key
 * {@code work:rate:public:{ip}}.
 * <ul>
 *   <li>Cada consulta cuenta (exitosa o no). Mismo script Lua que el login y las subidas (INCR + EXPIRE atomicos):
 *       la key nunca queda sin TTL.</li>
 *   <li>La IP se normaliza igual que en el login ({@link IpCliente}: IPv6 agrupada por /64).</li>
 *   <li>Al superarlo: 429 con Retry-After = segundos que le quedan a la ventana.</li>
 *   <li><b>Redis caido → se deja pasar</b> (fail-open, unica excepcion a la regla 6), con log warn como mucho una
 *       vez por minuto. Es aceptable porque lo que se protege es contenido publico de solo lectura y el limite solo
 *       evita que un visitante sature el servidor: no protege cuentas ni datos privados. Cortar el repositorio
 *       publico entero porque se cayo un contador seria peor que atender unos minutos sin limite.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimitePublicoService {

    static final Duration VENTANA = Duration.ofSeconds(60);
    private static final long AVISO_CADA_MS = VENTANA.toMillis();

    private final StringRedisTemplate redis;
    private final ParametrosService parametros;
    /** Momento (epoch ms) del ultimo aviso de Redis caido: evita llenar el log con una linea por visita. */
    private final AtomicLong ultimoAviso = new AtomicLong();

    /** Cuenta una consulta de la IP; 429 si con ella supera el maximo de la ventana actual. */
    public void registrar(String ip) {
        String clave = ClavesRedis.limitePublico(IpCliente.normalizar(ip));
        int maximo;
        Long restante;
        try {
            // Primero el contador: si Redis no responde ni siquiera se lee el maximo (se dejaria pasar igual)
            Long consultas = redis.execute(LimiteIntentosService.REGISTRAR_FALLO, List.of(clave),
                    Long.toString(VENTANA.toSeconds()));
            maximo = parametros.entero(Parametro.WORK_PUBLICO_PETICIONES_POR_MINUTO);
            if (consultas == null || consultas <= maximo) {
                return;
            }
            restante = redis.getExpire(clave, TimeUnit.SECONDS);
        } catch (DataAccessException ex) {
            avisarRedisCaido(ex);
            return;
        }
        long espera = restante == null || restante < 1 ? 1 : restante;
        throw new LimiteIntentosException(espera, "Demasiadas consultas",
                "Supero el limite de " + maximo + " consultas por minuto al repositorio publico. "
                        + "Intente de nuevo en " + espera + " segundos.");
    }

    private void avisarRedisCaido(DataAccessException ex) {
        long ahora = System.currentTimeMillis();
        long anterior = ultimoAviso.get();
        if (ahora - anterior >= AVISO_CADA_MS && ultimoAviso.compareAndSet(anterior, ahora)) {
            log.warn("Redis no disponible para el limite del repositorio publico: se atiende sin limite ({})",
                    ex.getMessage());
        }
    }
}
