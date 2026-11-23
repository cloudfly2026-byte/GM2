package com.app.starter1.persistence.services;

import com.app.starter1.dto.ClienteContratoRequest;
import com.app.starter1.persistence.entity.*;
import com.app.starter1.persistence.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    ContratoRepository contratoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SolicitudRepository solicitudRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private FirmaSolicitudRepository firmaSolicitudRepository;

    @Autowired
    private CheckeoRepository checkeoRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private VerificationRepository verificationRepository;

    @Autowired
    private FirmaSoporteRepository firmaSoporteRepository;

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;

    public static boolean parseStatus(Object rawStatus) {
        if (rawStatus == null) {
            return false;
        }
        if (rawStatus instanceof Boolean b) {
            return b;
        }
        if (rawStatus instanceof Number n) {
            return n.intValue() == 1;
        }
        String s = String.valueOf(rawStatus).trim().toLowerCase();
        return "1".equals(s) || "true".equals(s);
    }

    // Obtener todos los clientes
    public List<Customer> getAllCustomersWithContracts() {
        return customerRepository.findAll();
    }

    public Customer createCustomer(ClienteContratoRequest request) {
        Customer customer = Customer.builder()
                .name(request.getName())
                .nit(request.getNit())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .contact(request.getContact())
                .position(request.getPosition())
                .type(request.getType())
                .status(request.getStatus() == 1)
                .build();

        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomerAndContract(Long customerId, Customer updatedCustomer, Contrato updatedContrato) {
        Optional<Customer> existingCustomerOptional = customerRepository.findById(customerId);

        if (existingCustomerOptional.isEmpty()) {
            throw new EntityNotFoundException("Cliente no encontrado con ID: " + customerId);
        }

        Customer existingCustomer = existingCustomerOptional.get();

        // Actualizar toda la información del cliente
        existingCustomer.setName(updatedCustomer.getName());
        existingCustomer.setNit(updatedCustomer.getNit());
        existingCustomer.setPhone(updatedCustomer.getPhone());
        existingCustomer.setEmail(updatedCustomer.getEmail());
        existingCustomer.setAddress(updatedCustomer.getAddress());
        existingCustomer.setContact(updatedCustomer.getContact());
        existingCustomer.setPosition(updatedCustomer.getPosition());
        existingCustomer.setType(updatedCustomer.getType());
        existingCustomer.setStatus(updatedCustomer.getStatus());

        // Actualizar o crear el contrato asociado
        if (updatedContrato != null) {
            if (existingCustomer.getContrato() != null) {
                Contrato existingContrato = existingCustomer.getContrato();
                if (updatedContrato.getFechaInicio() != null) {
                    existingContrato.setFechaInicio(updatedContrato.getFechaInicio());
                }
                if (updatedContrato.getFechaFinal() != null) {
                    existingContrato.setFechaFinal(updatedContrato.getFechaFinal());
                }
                if (updatedContrato.getDescripcionContrato() != null) {
                    existingContrato.setDescripcionContrato(updatedContrato.getDescripcionContrato());
                }
                if (updatedContrato.getEstado() != null) {
                    existingContrato.setEstado(updatedContrato.getEstado());
                }
            } else {
                Contrato newContrato = new Contrato();
                newContrato.setFechaInicio(updatedContrato.getFechaInicio() != null
                        ? updatedContrato.getFechaInicio() : LocalDate.now());
                newContrato.setFechaFinal(updatedContrato.getFechaFinal() != null
                        ? updatedContrato.getFechaFinal() : LocalDate.now().plusYears(1));
                newContrato.setDescripcionContrato(updatedContrato.getDescripcionContrato() != null
                        ? updatedContrato.getDescripcionContrato() : "");
                newContrato.setEstado(updatedContrato.getEstado() != null ? updatedContrato.getEstado() : "1");
                newContrato.setCliente(existingCustomer);
                existingCustomer.setContrato(newContrato);
            }
        }

        return customerRepository.save(existingCustomer);
    }

    // DELETE legado (mantenido por compatibilidad interna si algún servicio lo invoca)
    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }

    // DELETE en cascada con verificación obligatoria de contraseña del usuario autenticado
    @Transactional
    public void deleteCustomerWithCascade(Long id, String actingUsername, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Debe ingresar su contraseña para confirmar la eliminación del cliente.");
        }

        if (actingUsername == null || actingUsername.isBlank()) {
            throw new IllegalArgumentException("Usuario autenticado no identificado.");
        }

        UserEntity actingUser = userRepository.findByUsername(actingUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado."));

        if (!passwordEncoder.matches(rawPassword, actingUser.getPassword())) {
            throw new IllegalArgumentException("La contraseña ingresada es incorrecta.");
        }

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + id));

        // Impedir eliminar la empresa a la que pertenece el usuario actualmente autenticado
        if (actingUser.getCustomer() != null && id.equals(actingUser.getCustomer().getId())) {
            throw new IllegalArgumentException("No puedes eliminar tu propia empresa actual.");
        }

        List<UserEntity> customerUsers = userRepository.findByCustomerId(id);
        if (customerUsers != null) {
            long superAdminsInCustomer = customerUsers.stream()
                    .filter(u -> u.getRoles() != null && u.getRoles().stream()
                            .anyMatch(r -> r.getRoleEnum() == RoleEnum.SUPERADMIN))
                    .count();
            if (superAdminsInCustomer > 0) {
                long totalSuperAdmins = userRepository.countByRoles_RoleEnum(RoleEnum.SUPERADMIN);
                if (totalSuperAdmins - superAdminsInCustomer < 1) {
                    throw new IllegalArgumentException("No se puede eliminar este cliente porque contiene al último SUPERADMIN del sistema.");
                }
            }
        }

        // 1. Eliminar solicitudes del cliente y sus registros dependientes (reportes, firmas, checkeos)
        List<Solicitud> customerSolicitudes = solicitudRepository.findByCustomerId(id);
        deleteSolicitudesAndChildren(customerSolicitudes);

        // 2. Obtener productos/equipos del cliente y eliminar sus dependencias
        List<Product> customerProducts = productRepository.findByCustomer(id);
        Optional<Contrato> contratoOpt = contratoRepository.findByClienteId(id);

        if (contratoOpt.isPresent()) {
            Contrato contrato = contratoOpt.get();
            if (contrato.getProductos() != null) {
                contrato.getProductos().clear();
            }
        }

        if (customerProducts != null && !customerProducts.isEmpty()) {
            for (Product product : customerProducts) {
                Long prodId = product.getId();
                if (prodId == null) continue;

                // Eliminar cualquier solicitud restante asociada a este equipo
                List<Solicitud> equipoSolicitudes = solicitudRepository.findByEquipoId(prodId);
                deleteSolicitudesAndChildren(equipoSolicitudes);

                // Documentos del equipo (+ limpieza segura de archivo físico si existe)
                List<Document> docs = documentRepository.findByEquipment(prodId);
                if (docs != null && !docs.isEmpty()) {
                    for (Document doc : docs) {
                        deleteFileSilently("uploads/documents/" + doc.getName());
                        deleteFileSilently("documents/" + doc.getName());
                    }
                    documentRepository.deleteAll(docs);
                }

                // Imágenes del equipo (+ limpieza segura de archivo físico si existe)
                List<Image> images = imageRepository.findByEquipment(prodId);
                if (images != null && !images.isEmpty()) {
                    for (Image img : images) {
                        deleteFileSilently("uploads/images/" + img.getName());
                        deleteFileSilently("media/" + img.getName());
                    }
                    imageRepository.deleteAll(images);
                }

                // Cronogramas y verificaciones del equipo
                scheduleRepository.deleteByDeviceId(prodId);
                List<PlantillaVerificacionData> verifications = verificationRepository.findByEquipment(String.valueOf(prodId));
                if (verifications != null && !verifications.isEmpty()) {
                    verificationRepository.deleteAll(verifications);
                }

                product.setImage(null);
                product.setContrato(null);
            }
            productRepository.deleteAll(customerProducts);
        }

        // 3. Eliminar contrato asociado al cliente
        if (contratoOpt.isPresent()) {
            contratoRepository.delete(contratoOpt.get());
            customer.setContrato(null);
        }

        // 4. Eliminar usuarios del cliente y sus dependencias
        if (customerUsers != null && !customerUsers.isEmpty()) {
            for (UserEntity user : customerUsers) {
                List<Solicitud> assignedSolicitudes = solicitudRepository.findByUsuarioAsignadoId(user.getId());
                if (assignedSolicitudes != null && !assignedSolicitudes.isEmpty()) {
                    for (Solicitud s : assignedSolicitudes) {
                        s.setUsuarioAsignado(null);
                    }
                    solicitudRepository.saveAll(assignedSolicitudes);
                }
                firmaSoporteRepository.deleteByIdUsuario(user.getId());
                if (user.getRoles() != null) {
                    user.getRoles().clear();
                }
                userRepository.delete(user);
            }
        }

        // 5. Finalmente eliminar el cliente
        customerRepository.delete(customer);
    }

    private void deleteSolicitudesAndChildren(List<Solicitud> solicitudes) {
        if (solicitudes == null || solicitudes.isEmpty()) {
            return;
        }
        Set<Long> processed = new HashSet<>();
        for (Solicitud sol : solicitudes) {
            if (sol.getIdSolicitud() == null || !processed.add(sol.getIdSolicitud())) {
                continue;
            }
            Long solId = sol.getIdSolicitud();
            reportRepository.deleteBySolicitud(solId);
            firmaSolicitudRepository.deleteByIdSolicitud(solId);
            List<Checkeo> checkeos = checkeoRepository.findByIdOrden(solId);
            if (checkeos != null && !checkeos.isEmpty()) {
                checkeoRepository.deleteAll(checkeos);
            }
        }
        solicitudRepository.deleteAll(solicitudes);
    }

    private void deleteFileSilently(String pathStr) {
        try {
            if (pathStr != null && !pathStr.endsWith("null")) {
                Files.deleteIfExists(Paths.get(pathStr));
            }
        } catch (Exception ignored) {
            // No bloquear la transacción de BD si el archivo físico no existe
        }
    }

    // READ (Get contract by customer ID)
    public Contrato getContratoByCustomerId(Long customerId) {
        return contratoRepository.findByClienteId(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for customer ID: " + customerId));
    }

    // CREATE or UPDATE CONTRACT
    public Contrato createOrUpdateContrato(Long customerId, Contrato contrato) {
        Optional<Contrato> contratoOptional = contratoRepository.findById(customerId);
        Optional<Customer> customer = customerRepository.findById(customerId);

        Contrato existingContrato = contratoOptional.get();
        Customer existingCliente = customer.get();
        existingContrato.setCliente(existingCliente);
        return contratoRepository.save(contrato);
    }

    // DELETE CONTRACT
    public void deleteContrato(Long contratoId) {
        Contrato contrato = contratoRepository.findById(contratoId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found with ID: " + contratoId));
        contratoRepository.delete(contrato);
    }

    public Customer getById(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }
}
