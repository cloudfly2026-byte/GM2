package com.app.starter1.persistence.repository;

import com.app.starter1.dto.ScheduleProductClientProjection;
import com.app.starter1.persistence.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.app.starter1.dto.ScheduleProductClientDTO;

import com.app.starter1.dto.ScheduleDto;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // Método para encontrar todos los schedules por el dispositivo
    List<Schedule> findByDevice_Id(Long deviceId);

    // Método para encontrar un schedule por su fecha
    List<Schedule> findByDate(String date);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM schedule WHERE device = :deviceId", nativeQuery = true)
    int deleteByDeviceId(@Param("deviceId") Long deviceId);

    // Método para verificar si ya existe un Schedule con el mismo dispositivo y fecha
    boolean existsByDeviceIdAndDate(Long deviceId, String date);

    /**
     * Todos los schedules — solo para SUPERADMIN.
     */
    @Query(value = "SELECT " +
            "s.id AS id, s.date AS date, s.status AS status, " +
            "p.marca_producto AS brand, p.modelo_producto AS model, " +
            "p.nombre_producto AS nombreProducto, p.placa_producto AS placaProducto, " +
            "c.nombre_cliente AS nombreCliente " +
            "FROM schedule s " +
            "LEFT JOIN products p ON s.device = p.id_producto " +
            "LEFT JOIN clientes c ON p.cliente_producto = c.id",
            nativeQuery = true)
    List<ScheduleProductClientProjection> findAllScheduleWithProductAndClient();

    /**
     * Schedules filtrados por customer_id — para usuarios no SUPERADMIN.
     */
    @Query(value = "SELECT " +
            "s.id AS id, s.date AS date, s.status AS status, " +
            "p.marca_producto AS brand, p.modelo_producto AS model, " +
            "p.nombre_producto AS nombreProducto, p.placa_producto AS placaProducto, " +
            "c.nombre_cliente AS nombreCliente " +
            "FROM schedule s " +
            "LEFT JOIN products p ON s.device = p.id_producto " +
            "LEFT JOIN clientes c ON p.cliente_producto = c.id " +
            "WHERE c.id = :customerId",
            nativeQuery = true)
    List<ScheduleProductClientProjection> findAllScheduleWithProductAndClientByCustomer(
            @Param("customerId") Long customerId);
}
