package com.app.starter1.persistence.repository;

import com.app.starter1.persistence.entity.Reporte;
import com.app.starter1.persistence.entity.RoleEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends CrudRepository<Reporte,Long> {

    // Consulta basada en el nombre del campo
    Reporte findBySolicitud(Long idSolicitud);

    void deleteBySolicitud(Long idSolicitud);

    boolean existsBySolicitud(Long id);

    @Query("SELECT COUNT(r) FROM Reporte r, Solicitud s WHERE r.solicitud = s.idSolicitud AND s.customer.id = :customerId")
    long countByCustomerId(@Param("customerId") Long customerId);
}
