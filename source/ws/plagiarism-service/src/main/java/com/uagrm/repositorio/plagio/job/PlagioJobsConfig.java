package com.uagrm.repositorio.plagio.job;

import com.uagrm.repositorio.plagio.config.PlagioProperties;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra el job periodico de timeout y las propiedades del servicio. */
@Configuration
@EnableConfigurationProperties(PlagioProperties.class)
public class PlagioJobsConfig {

    static final String TIMEOUT = "timeoutPlagio";

    @Bean
    JobDetail timeoutPlagioJobDetail() {
        return JobBuilder.newJob(TimeoutPlagioJob.class)
                .withIdentity(TIMEOUT)
                .withDescription("Pasa a ERROR los analisis EN_PROCESO con mas de PLAGIO_GROUP.TIMEOUT_MINUTOS")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger timeoutPlagioTrigger(JobDetail timeoutPlagioJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(timeoutPlagioJobDetail)
                .withIdentity(TIMEOUT)
                .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }
}
