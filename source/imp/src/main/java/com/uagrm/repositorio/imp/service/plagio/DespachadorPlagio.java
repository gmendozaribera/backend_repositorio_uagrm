package com.uagrm.repositorio.imp.service.plagio;

/**
 * Agenda el envio de un analisis al motor en segundo plano, fuera de la transaccion HTTP que lo creo.
 * Implementacion: plagiarism-service (job de Quartz). Los demas servicios no la tienen.
 */
public interface DespachadorPlagio {

    /** Agenda el envio "ya"; idempotente si el analisis dejo de estar EN_PROCESO. */
    void despachar(Integer idAnalisis);
}
