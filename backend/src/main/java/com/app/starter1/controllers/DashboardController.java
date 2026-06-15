package com.app.starter1.controllers;

import com.app.starter1.dto.OverviewStats;
import com.app.starter1.persistence.repository.CustomerRepository;
import com.app.starter1.persistence.repository.ProductRepository;
import com.app.starter1.persistence.repository.ReportRepository;
import com.app.starter1.persistence.repository.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SolicitudRepository solicitudRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ReportRepository reportRepository;

    @GetMapping("/overview")
    public ResponseEntity<OverviewStats> getOverview(@RequestParam(name = "customerId", required = false) Long customerId) {
        long solicitudes;
        long clientes;
        long equipos;
        long reportes;

        if (customerId != null) {
            solicitudes = solicitudRepository.countByCustomerId(customerId);
            clientes = 1; // Un usuario normal solo gestiona su propio cliente
            equipos = productRepository.countByCustomer(customerId);
            reportes = reportRepository.countByCustomerId(customerId);
        } else {
            solicitudes = solicitudRepository.count();
            clientes = customerRepository.count();
            equipos = productRepository.count();
            reportes = reportRepository.count();
        }

        OverviewStats stats = new OverviewStats(solicitudes, clientes, equipos, reportes);
        return ResponseEntity.ok(stats);
    }
}
