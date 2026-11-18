package com.uagrm.repositorio.imp.service.plagio;

import com.uagrm.repositorio.dao.entity.TipoMotorPlagio;

/**
 * Motor de deteccion de plagio intercambiable. La implementacion vive en plagiarism-service (SIMULADO o
 * COPYLEAKS segun PLAGIO_MOTOR); los demas servicios no tienen ninguna y por eso {@link PlagioService} la pide con
 * ObjectProvider (sin motor, iniciar responde 503).
 *
 * <p>El motor NO escribe en la BD directamente: entrega cada resultado con
 * {@link PlagioService#registrarDocumento} (o {@link PlagioService#fallar}). Asi la regla de negocio (porcentaje =
 * maximo de los documentos, nivel, fuentes, notificacion, cache) es una sola para todos los motores.</p>
 */
public interface MotorPlagio {

    TipoMotorPlagio tipo();

    /**
     * Envia los documentos a analizar. Se llama fuera de toda transaccion HTTP (job de Quartz). Puede terminar
     * de forma sincrona (registrando ya los resultados) o asincrona (el resultado llega despues, p. ej. por webhook).
     *
     * @throws RuntimeException si el envio falla: quien lo llama pasa el analisis a ERROR
     */
    void enviar(SolicitudAnalisis solicitud);
}
