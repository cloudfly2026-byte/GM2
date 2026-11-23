package com.app.starter1.controllers;


import com.app.starter1.dto.*;
import com.app.starter1.persistence.entity.Contrato;
import com.app.starter1.persistence.entity.Customer;
import com.app.starter1.persistence.entity.UserEntity;
import com.app.starter1.persistence.repository.CustomerRepository;
import com.app.starter1.persistence.repository.UserRepository;
import com.app.starter1.persistence.services.ContractService;
import com.app.starter1.persistence.services.CustomerService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    ContractService contractService;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createClienteYContrato(@RequestBody ClienteContratoRequest request) {
        // Crear el cliente
        Customer cliente = customerService.createCustomer(request);

        // Crear el contrato asociando el cliente creado
        Contrato contrato = contractService.createContrato(cliente, request);

        return ResponseEntity.ok(Map.of(
                "cliente", cliente,
                "contrato", contrato
        ));
    }

    @PostMapping("/account-setup")
    public ResponseEntity<?> accountSetup(@RequestBody ClienteSinContratoRequest request) {
        // Crear el cliente

        // Buscar el usuario por ID
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + request.getUserId()));

        // Obtener los datos del cliente desde el objeto form
        ClienteForm form = request.getForm();

        // Crear el cliente
        Customer customer = Customer.builder()
                .name(form.getName())
                .nit(form.getNit())
                .phone(form.getPhone())
                .email(form.getEmail())
                .address(form.getAddress())
                .contact(form.getContact())
                .position(form.getPosition())
                .type(form.getType())
                .status(form.getStatus() != null ? Boolean.valueOf(form.getStatus()) : null)
                .build();

        ClienteContratoRequest contratoRequest = new ClienteContratoRequest();

        contratoRequest.setDescripcionContrato("");
        contratoRequest.setFechaInicio("2000-01-01");
        contratoRequest.setFechaFinal("2000-01-01");

        // Asociar el cliente al usuario
        customer = customerRepository.save(customer);
        user.setCustomer(customer);
        UserEntity userSaved = userRepository.save(user);

        // Crear el contrato asociando el cliente creado
        Contrato contrato = contractService.createContrato(customer, contratoRequest);



        return ResponseEntity.ok(userSaved);
    }



    @GetMapping
    @Transactional

    public ResponseEntity<List<Customer>> getAllCustomersWithContracts() {
        List<Customer> customers = customerService.getAllCustomersWithContracts();
        return ResponseEntity.ok(customers);
    }

    // GET CUSTOMER BY ID
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(customerRepository.getById(id));
    }

    // UPDATE CUSTOMER
    @PutMapping("/{customerId}")
    public ResponseEntity<?> updateCustomerAndContract(
            @PathVariable Long customerId,
            @RequestBody Map<String, Object> payload) {
        try {
            Customer updatedCustomer = new Customer();
            updatedCustomer.setName(payload.get("name") != null ? String.valueOf(payload.get("name")) : "");
            updatedCustomer.setNit(payload.get("nit") != null ? String.valueOf(payload.get("nit")) : null);
            updatedCustomer.setPhone(payload.get("phone") != null ? String.valueOf(payload.get("phone")) : null);
            updatedCustomer.setEmail(payload.get("email") != null ? String.valueOf(payload.get("email")) : null);
            updatedCustomer.setAddress(payload.get("address") != null ? String.valueOf(payload.get("address")) : null);
            updatedCustomer.setContact(payload.get("contact") != null ? String.valueOf(payload.get("contact")) : null);
            updatedCustomer.setPosition(payload.get("position") != null ? String.valueOf(payload.get("position")) : null);
            updatedCustomer.setType(payload.get("type") != null ? String.valueOf(payload.get("type")) : null);
            updatedCustomer.setStatus(CustomerService.parseStatus(payload.get("status")));

            Contrato updatedContrato = new Contrato();
            String fechaInicioStr = payload.get("fechaInicio") != null ? String.valueOf(payload.get("fechaInicio")).trim() : "";
            String fechaFinalStr = payload.get("fechaFinal") != null ? String.valueOf(payload.get("fechaFinal")).trim() : "";
            if (!fechaInicioStr.isEmpty()) {
                updatedContrato.setFechaInicio(LocalDate.parse(fechaInicioStr));
            }
            if (!fechaFinalStr.isEmpty()) {
                updatedContrato.setFechaFinal(LocalDate.parse(fechaFinalStr));
            }
            updatedContrato.setDescripcionContrato(
                    payload.get("descripcionContrato") != null ? String.valueOf(payload.get("descripcionContrato")) : "");
            updatedContrato.setEstado("1");

            Customer savedCustomer = customerService.updateCustomerAndContract(customerId, updatedCustomer, updatedContrato);

            return ResponseEntity.ok(Map.of(
                    "result", "success",
                    "message", "Cliente actualizado correctamente.",
                    "cliente", savedCustomer
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("result", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("result", "error", "message", "Error al actualizar el cliente: " + e.getMessage()));
        }
    }

    // DELETE CUSTOMER (en cascada, requiere confirmación de contraseña del usuario autenticado)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCustomer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload,
            Authentication authentication) {
        try {
            String actingUsername = authentication != null ? authentication.getName() : null;
            String password = payload != null ? payload.get("password") : null;
            customerService.deleteCustomerWithCascade(id, actingUsername, password);
            return ResponseEntity.ok(Map.of(
                    "result", "success",
                    "message", "Cliente y toda su información relacionada eliminados correctamente."
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("result", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("result", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("result", "error", "message", "Error al eliminar el cliente: " + e.getMessage()));
        }
    }


    // GET CONTRACT BY CUSTOMER ID
    @GetMapping("/{customerId}/contracts")
    public ResponseEntity<Contrato> getContratoByCustomerId(@PathVariable Long customerId) {
        return ResponseEntity.ok(customerService.getContratoByCustomerId(customerId));
    }

    // DELETE CONTRACT
    @DeleteMapping("/contracts/{id}")
    public ResponseEntity<Void> deleteContrato(@PathVariable Long id) {
        customerService.deleteContrato(id);
        return ResponseEntity.noContent().build();
    }
}
