package com.uagrm.repositorio.dao.repository;

import com.uagrm.repositorio.dao.entity.Dominio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DominioRepository extends JpaRepository<Dominio, Integer> {

    /** Parametros de un grupo (carga de la cache config:dominio:{GRUPO} y filtro del listado). */
    List<Dominio> findByDomainGroupOrderByDomainNameAsc(String domainGroup);

    List<Dominio> findAllByOrderByDomainGroupAscDomainNameAsc();

    boolean existsByDomainGroupAndDomainName(String domainGroup, String domainName);
}
