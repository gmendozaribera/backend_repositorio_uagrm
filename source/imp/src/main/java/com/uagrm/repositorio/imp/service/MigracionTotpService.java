package com.uagrm.repositorio.imp.service;

import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository.SecretoTotpEnClaro;
import com.uagrm.repositorio.imp.security.CifradorTotp;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cifra los secretos TOTP que quedaron en claro de antes del cifrado (S5). Lo ejecuta auth-service al arrancar.
 *
 * <p>Idempotente: solo toca valores sin el prefijo {@code v1:}, y cada UPDATE exige que el valor siga siendo el mismo
 * que se leyo (si dos instancias arrancan a la vez, la segunda no cifra dos veces). Por lotes de {@value #LOTE}, cada
 * lote en su propia transaccion. El log solo dice CUANTOS se cifraron: nunca ids de usuario ni valores.</p>
 */
@Slf4j
@Service
public class MigracionTotpService {

    static final int LOTE = 100;

    private final UsuarioRepository usuarioRepository;
    private final CifradorTotp cifrador;
    private final TransactionTemplate transaccion;

    public MigracionTotpService(UsuarioRepository usuarioRepository, CifradorTotp cifrador,
                                PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.cifrador = cifrador;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /** @return cuantos secretos se cifraron (0 si ya estaban todos cifrados) */
    public int cifrarSecretosEnClaro() {
        int cifrados = 0;
        int desde = 0;
        while (true) {
            List<SecretoTotpEnClaro> lote = usuarioRepository.findTotpEnClaro(desde, Limit.of(LOTE));
            if (lote.isEmpty()) {
                break;
            }
            Integer enLote = transaccion.execute(estado -> lote.stream()
                    .mapToInt(fila -> usuarioRepository.cifrarTotp(fila.getIdUsuario(), fila.getTotpSecret(),
                            cifrador.cifrar(fila.getTotpSecret())))
                    .sum());
            cifrados += enLote == null ? 0 : enLote;
            desde = lote.getLast().getIdUsuario();
        }
        if (cifrados > 0) {
            log.warn("Secretos TOTP en claro cifrados al arrancar: {}", cifrados);
        } else {
            log.info("Secretos TOTP: ninguno en claro");
        }
        return cifrados;
    }
}
