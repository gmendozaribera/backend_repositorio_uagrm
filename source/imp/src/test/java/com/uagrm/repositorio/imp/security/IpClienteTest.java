package com.uagrm.repositorio.imp.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpClienteTest {

    @Test
    void ipv4QuedaIgual() {
        assertThat(IpCliente.normalizar(" 192.168.1.10 ")).isEqualTo("192.168.1.10");
    }

    @Test
    void ipv4EscritaComoIpv6SeTrataComoIpv4() {
        assertThat(IpCliente.normalizar("::ffff:192.168.1.10")).isEqualTo("192.168.1.10");
    }

    @Test
    void ipv6SeAgrupaPorPrefijo64SinImportarComoSeEscriba() {
        String esperado = "2001:db8:1:2::/64";
        assertThat(IpCliente.normalizar("2001:DB8:1:2::1")).isEqualTo(esperado);
        assertThat(IpCliente.normalizar("2001:0db8:0001:0002:abcd:ef01:2345:6789")).isEqualTo(esperado);
        assertThat(IpCliente.normalizar("[2001:db8:1:2::ffff]")).isEqualTo(esperado);
        assertThat(IpCliente.normalizar("0:0:0:0:0:0:0:1")).isEqualTo("0:0:0:0::/64");
        assertThat(IpCliente.normalizar("fe80::1%eth0")).isEqualTo("fe80:0:0:0::/64");
    }

    @Test
    void valorQueNoEsIpNoRompe() {
        assertThat(IpCliente.normalizar(null)).isEqualTo("desconocida");
        assertThat(IpCliente.normalizar("No-Es-Ip")).isEqualTo("no-es-ip");
    }
}
