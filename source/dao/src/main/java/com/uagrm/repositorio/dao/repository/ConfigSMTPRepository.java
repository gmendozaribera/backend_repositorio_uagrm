package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.ConfigSMTP;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigSMTPRepository extends JpaRepository<ConfigSMTP, Integer> {

    Optional<ConfigSMTP> findFirstByActivoTrue();
}
