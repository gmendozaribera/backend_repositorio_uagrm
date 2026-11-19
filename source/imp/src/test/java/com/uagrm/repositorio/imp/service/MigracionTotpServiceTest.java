package com.uagrm.repositorio.imp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uagrm.repositorio.dao.repository.UsuarioRepository;
import com.uagrm.repositorio.dao.repository.UsuarioRepository.SecretoTotpEnClaro;
import com.uagrm.repositorio.imp.security.CifradorTotp;
import com.uagrm.repositorio.imp.security.CifradorTotpTest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;

/** Migracion de secretos TOTP en claro: por lotes (keyset por id), cifra cada uno y cuenta sin loguear valores. */
class MigracionTotpServiceTest {

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final CifradorTotp cifrador = new CifradorTotp(CifradorTotpTest.LLAVE);
    private final MigracionTotpService migracion =
            new MigracionTotpService(repository, cifrador, mock(PlatformTransactionManager.class));

    @Test
    void cifraPorLotesHastaQueNoQuedanEnClaro() {
        List<SecretoTotpEnClaro> primerLote = IntStream.rangeClosed(1, MigracionTotpService.LOTE)
                .mapToObj(id -> fila(id, "SECRETO" + id)).toList();
        when(repository.findTotpEnClaro(eq(0), any(Limit.class))).thenReturn(primerLote);
        when(repository.findTotpEnClaro(eq(MigracionTotpService.LOTE), any(Limit.class)))
                .thenReturn(List.of(fila(150, "JBSWY3DPEHPK3PXP")));
        when(repository.findTotpEnClaro(eq(150), any(Limit.class))).thenReturn(List.of());
        when(repository.cifrarTotp(any(), anyString(), anyString())).thenReturn(1);

        assertThat(migracion.cifrarSecretosEnClaro()).isEqualTo(MigracionTotpService.LOTE + 1);

        ArgumentCaptor<String> cifrado = ArgumentCaptor.forClass(String.class);
        verify(repository).cifrarTotp(eq(150), eq("JBSWY3DPEHPK3PXP"), cifrado.capture());
        assertThat(cifrado.getValue()).startsWith("v1:");
        assertThat(cifrador.descifrar(cifrado.getValue())).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void sinSecretosEnClaroNoHaceNada() {
        when(repository.findTotpEnClaro(eq(0), any(Limit.class))).thenReturn(List.of());

        assertThat(migracion.cifrarSecretosEnClaro()).isZero();
        verify(repository, never()).cifrarTotp(any(), anyString(), anyString());
    }

    @Test
    void siOtraInstanciaYaLoCifroNoSeCuenta() {
        when(repository.findTotpEnClaro(eq(0), any(Limit.class))).thenReturn(List.of(fila(5, "JBSWY3DPEHPK3PXP")));
        when(repository.findTotpEnClaro(eq(5), any(Limit.class))).thenReturn(List.of());
        when(repository.cifrarTotp(any(), anyString(), anyString())).thenReturn(0);

        assertThat(migracion.cifrarSecretosEnClaro()).isZero();
    }

    private static SecretoTotpEnClaro fila(int id, String secreto) {
        return new SecretoTotpEnClaro() {
            @Override
            public Integer getIdUsuario() {
                return id;
            }

            @Override
            public String getTotpSecret() {
                return secreto;
            }
        };
    }
}
