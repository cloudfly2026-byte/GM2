package com.app.starter1.persistence.services;
import java.util.Optional;
import java.util.stream.Collectors;

import com.app.starter1.dto.ScheduleProductClientDTO;
import com.app.starter1.dto.ScheduleProductClientProjection;
import com.app.starter1.persistence.entity.UserEntity;
import com.app.starter1.persistence.repository.UserRepository;

import com.app.starter1.dto.ScheduleDto;
import com.app.starter1.dto.ScheduleRequest;
import com.app.starter1.persistence.entity.Schedule;
import com.app.starter1.persistence.entity.Product;
import com.app.starter1.persistence.repository.ScheduleRepository;
import com.app.starter1.persistence.repository.ProductRepository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private UserRepository userRepository;

    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    @Transactional
    public void deleteByDeviceId(Long deviceId) {
        scheduleRepository.deleteByDeviceId(deviceId);
    }

    @Transactional
    public void createSchedules(ScheduleRequest scheduleRequest) {
        Product device = productRepository.findById(scheduleRequest.getDeviceId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        System.out.println("device " + scheduleRequest.getDeviceId());

        Long deviceId = device.getId();
        long deleted = scheduleRepository.deleteByDeviceId(deviceId);
        System.out.println("Schedules eliminados para device " + deviceId + ": " + deleted);

        for (String fecha : scheduleRequest.getFechas()) {
            boolean exists = scheduleRepository.existsByDeviceIdAndDate(scheduleRequest.getDeviceId(), fecha);
            if (!exists) {
                Schedule schedule = Schedule.builder()
                        .device(device)
                        .date(LocalDate.parse(fecha).toString())
                        .status(Schedule.Status.ACTIVE)
                        .build();
                scheduleRepository.save(schedule);
            }
        }
    }

    /**
     * Devuelve schedules según el rol del usuario autenticado:
     * - SUPERADMIN → todos los schedules
     * - Cualquier otro rol → solo los schedules del Customer al que pertenece el usuario
     *
     * @param username  username extraído del JWT (SecurityContext)
     * @param isSuperAdmin  true si el token contiene ROLE_SUPERADMIN
     */
    public List<ScheduleProductClientProjection> getAllSchedulesForUser(String username, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return scheduleRepository.findAllScheduleWithProductAndClient();
        }

        UserEntity user = userRepository.findUserEntityByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));

        if (user.getCustomer() == null) {
            // Usuario sin customer asignado — retorna lista vacía por seguridad
            return List.of();
        }

        Long customerId = user.getCustomer().getId();
        return scheduleRepository.findAllScheduleWithProductAndClientByCustomer(customerId);
    }

    // Mantener método legacy por si se usa en otro lado
    public List<ScheduleProductClientProjection> getAllSchedulesWithProductAndCustomer() {
        return scheduleRepository.findAllScheduleWithProductAndClient();
    }

    public boolean setInactiveById(Long id) {
        Optional<Schedule> optionalSchedule = scheduleRepository.findById(id);

        if (optionalSchedule.isPresent()) {
            Schedule schedule = optionalSchedule.get();
            schedule.setStatus(Schedule.Status.valueOf("INACTIVE"));
            scheduleRepository.save(schedule);
            return true;
        }

        return false;
    }
}
