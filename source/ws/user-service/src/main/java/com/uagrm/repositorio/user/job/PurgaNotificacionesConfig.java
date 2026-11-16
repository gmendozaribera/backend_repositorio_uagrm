package com.uagrm.repositorio.user.job;

import jakarta.validation.constraints.NotBlank;
import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Registra {@link PurgaNotificacionesJob} en el scheduler de Quartz que autoconfigura Spring Boot.
 * El horario es infraestructura (variable de entorno); los dias de retencion son negocio (tabla dominio).
 */
@Configuration
@EnableConfigurationProperties(PurgaNotificacionesConfig.PurgaProperties.class)
public class PurgaNotificacionesConfig {

    static final String NOMBRE = "purgaNotificaciones";

    /**
     * @param cron expresion cron de Quartz (seg min hora dia mes dia-semana), por defecto todos los dias a las 03:00
     * @param zona zona horaria del cron (por defecto America/La_Paz)
     */
    @Validated
    @ConfigurationProperties("app.notificaciones.purga")
    public record PurgaProperties(@NotBlank @DefaultValue("0 0 3 * * ?") String cron,
                                  @NotBlank @DefaultValue("America/La_Paz") String zona) {
    }

    @Bean
    JobDetail purgaNotificacionesJobDetail() {
        return JobBuilder.newJob(PurgaNotificacionesJob.class)
                .withIdentity(NOMBRE)
                .withDescription("Borra las notificaciones leidas mas antiguas que NOTIF_GROUP.RETENCION_DIAS")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger purgaNotificacionesTrigger(JobDetail purgaNotificacionesJobDetail, PurgaProperties props) {
        return TriggerBuilder.newTrigger()
                .forJob(purgaNotificacionesJobDetail)
                .withIdentity(NOMBRE)
                .withSchedule(CronScheduleBuilder.cronSchedule(props.cron())
                        .inTimeZone(TimeZone.getTimeZone(props.zona()))
                        // Si el servicio estaba caido a la hora programada, no se recupera: corre en la siguiente
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
