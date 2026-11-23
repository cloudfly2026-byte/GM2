package com.app.starter1.persistence.services;

import com.app.starter1.persistence.entity.*;
import com.app.starter1.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ContratoRepository contratoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private FirmaSolicitudRepository firmaSolicitudRepository;
    @Mock
    private CheckeoRepository checkeoRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private FirmaSoporteRepository firmaSoporteRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerService customerService;

    private Customer targetCustomer;
    private Customer actingCustomer;
    private UserEntity actingUser;
    private RoleEntity roleSuperAdmin;
    private RoleEntity roleAdmin;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(customerService, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(customerService, "contratoRepository", contratoRepository);
        ReflectionTestUtils.setField(customerService, "userRepository", userRepository);
        ReflectionTestUtils.setField(customerService, "productRepository", productRepository);
        ReflectionTestUtils.setField(customerService, "solicitudRepository", solicitudRepository);
        ReflectionTestUtils.setField(customerService, "reportRepository", reportRepository);
        ReflectionTestUtils.setField(customerService, "firmaSolicitudRepository", firmaSolicitudRepository);
        ReflectionTestUtils.setField(customerService, "checkeoRepository", checkeoRepository);
        ReflectionTestUtils.setField(customerService, "documentRepository", documentRepository);
        ReflectionTestUtils.setField(customerService, "imageRepository", imageRepository);
        ReflectionTestUtils.setField(customerService, "scheduleRepository", scheduleRepository);
        ReflectionTestUtils.setField(customerService, "verificationRepository", verificationRepository);
        ReflectionTestUtils.setField(customerService, "firmaSoporteRepository", firmaSoporteRepository);
        ReflectionTestUtils.setField(customerService, "passwordEncoder", passwordEncoder);

        roleSuperAdmin = RoleEntity.builder().id(1L).roleEnum(RoleEnum.SUPERADMIN).build();
        roleAdmin = RoleEntity.builder().id(2L).roleEnum(RoleEnum.ADMIN).build();

        actingCustomer = Customer.builder().id(1L).name("Equibiomedic").status(true).build();
        targetCustomer = Customer.builder().id(99L).name("Cliente Demo").nit("900123456").status(true).build();

        actingUser = UserEntity.builder()
                .id(1L)
                .username("ingenieria")
                .password("$2a$10$encodedSuperAdminPass")
                .customer(actingCustomer)
                .roles(new HashSet<>(Set.of(roleSuperAdmin)))
                .build();
    }

    @Test
    @DisplayName("parseStatus interpreta correctamente '1', 'true', true como activo y '0', 'false', false como inactivo")
    void parseStatus_handlesMultipleFormats() {
        assertTrue(CustomerService.parseStatus("1"));
        assertTrue(CustomerService.parseStatus("true"));
        assertTrue(CustomerService.parseStatus(Boolean.TRUE));
        assertTrue(CustomerService.parseStatus(1));
        assertFalse(CustomerService.parseStatus("0"));
        assertFalse(CustomerService.parseStatus("false"));
        assertFalse(CustomerService.parseStatus(Boolean.FALSE));
        assertFalse(CustomerService.parseStatus(0));
    }

    @Test
    @DisplayName("updateCustomerAndContract actualiza toda la información del cliente y contrato")
    void updateCustomerAndContract_updatesAllFields() {
        Contrato existingContrato = new Contrato();
        existingContrato.setId(50L);
        existingContrato.setFechaInicio(LocalDate.of(2025, 1, 1));
        existingContrato.setFechaFinal(LocalDate.of(2025, 12, 31));
        existingContrato.setDescripcionContrato("Viejo");
        existingContrato.setCliente(targetCustomer);
        targetCustomer.setContrato(existingContrato);

        Customer updated = Customer.builder()
                .name("Cliente Actualizado")
                .nit("900999888")
                .phone("3001112233")
                .email("nuevo@cliente.com")
                .address("Calle 100 # 10-20")
                .contact("Maria Perez")
                .position("Gerente")
                .type("1")
                .status(true)
                .build();

        Contrato updatedContrato = new Contrato();
        updatedContrato.setFechaInicio(LocalDate.of(2026, 1, 1));
        updatedContrato.setFechaFinal(LocalDate.of(2027, 1, 1));
        updatedContrato.setDescripcionContrato("Contrato renovado");
        updatedContrato.setEstado("1");

        when(customerRepository.findById(99L)).thenReturn(Optional.of(targetCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer saved = customerService.updateCustomerAndContract(99L, updated, updatedContrato);

        assertEquals("Cliente Actualizado", saved.getName());
        assertEquals("900999888", saved.getNit());
        assertEquals("3001112233", saved.getPhone());
        assertEquals("nuevo@cliente.com", saved.getEmail());
        assertEquals("Calle 100 # 10-20", saved.getAddress());
        assertEquals("Maria Perez", saved.getContact());
        assertEquals("Gerente", saved.getPosition());
        assertEquals("1", saved.getType());
        assertEquals(Boolean.TRUE, saved.getStatus());
        assertEquals(LocalDate.of(2026, 1, 1), saved.getContrato().getFechaInicio());
        assertEquals(LocalDate.of(2027, 1, 1), saved.getContrato().getFechaFinal());
        assertEquals("Contrato renovado", saved.getContrato().getDescripcionContrato());
    }

    @Test
    @DisplayName("Eliminar cliente con contraseña vacía lanza error y no elimina nada")
    void deleteCustomerWithCascade_emptyPassword_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> customerService.deleteCustomerWithCascade(99L, "ingenieria", "   "));
        assertTrue(ex.getMessage().toLowerCase().contains("contraseña"));
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Eliminar cliente con contraseña incorrecta lanza error y no elimina nada")
    void deleteCustomerWithCascade_wrongPassword_throwsException() {
        when(userRepository.findByUsername("ingenieria")).thenReturn(Optional.of(actingUser));
        when(passwordEncoder.matches("claveMala", "$2a$10$encodedSuperAdminPass")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> customerService.deleteCustomerWithCascade(99L, "ingenieria", "claveMala"));
        assertTrue(ex.getMessage().toLowerCase().contains("incorrecta"));
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("No permite eliminar la empresa a la que pertenece el usuario actualmente autenticado")
    void deleteCustomerWithCascade_ownCustomer_throwsException() {
        when(userRepository.findByUsername("ingenieria")).thenReturn(Optional.of(actingUser));
        when(passwordEncoder.matches("Ingenieria1234*", "$2a$10$encodedSuperAdminPass")).thenReturn(true);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(actingCustomer));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> customerService.deleteCustomerWithCascade(1L, "ingenieria", "Ingenieria1234*"));
        assertTrue(ex.getMessage().toLowerCase().contains("propia") || ex.getMessage().toLowerCase().contains("actual"));
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Eliminar cliente con contraseña válida borra en cascada solicitudes, reportes, equipos, documentos, contrato, usuarios y cliente")
    void deleteCustomerWithCascade_validPassword_deletesAllRelatedEntities() {
        UserEntity clientUser = UserEntity.builder()
                .id(200L)
                .username("admin_demo")
                .customer(targetCustomer)
                .roles(new HashSet<>(Set.of(roleAdmin)))
                .build();

        Product prod = new Product();
        prod.setId(300L);
        prod.setCustomer(99L);

        Contrato contrato = new Contrato();
        contrato.setId(400L);
        contrato.setCliente(targetCustomer);
        contrato.setProductos(new HashSet<>(Set.of(prod)));
        targetCustomer.setContrato(contrato);

        Solicitud sol = Solicitud.builder().idSolicitud(500L).customer(targetCustomer).equipo(prod).build();
        Checkeo checkeo = Checkeo.builder().id(600L).idOrden(500L).build();
        Document doc = new Document();
        doc.setId(700);
        doc.setName("manual.pdf");
        doc.setEquipment(300L);
        Image img = new Image();
        img.setId(800);
        img.setName("foto.png");
        img.setEquipment(300L);
        PlantillaVerificacionData verif = PlantillaVerificacionData.builder().id(900L).equipment("300").build();

        when(userRepository.findByUsername("ingenieria")).thenReturn(Optional.of(actingUser));
        when(passwordEncoder.matches("Ingenieria1234*", "$2a$10$encodedSuperAdminPass")).thenReturn(true);
        when(customerRepository.findById(99L)).thenReturn(Optional.of(targetCustomer));
        when(userRepository.findByCustomerId(99L)).thenReturn(List.of(clientUser));
        when(solicitudRepository.findByCustomerId(99L)).thenReturn(List.of(sol));
        when(checkeoRepository.findByIdOrden(500L)).thenReturn(List.of(checkeo));
        when(productRepository.findByCustomer(99L)).thenReturn(List.of(prod));
        when(documentRepository.findByEquipment(300L)).thenReturn(List.of(doc));
        when(imageRepository.findByEquipment(300L)).thenReturn(List.of(img));
        when(verificationRepository.findByEquipment("300")).thenReturn(List.of(verif));
        when(contratoRepository.findByClienteId(99L)).thenReturn(Optional.of(contrato));
        when(solicitudRepository.findByUsuarioAsignadoId(200L)).thenReturn(Collections.emptyList());

        customerService.deleteCustomerWithCascade(99L, "ingenieria", "Ingenieria1234*");

        // Verificar eliminación en cascada completa
        verify(reportRepository).deleteBySolicitud(500L);
        verify(firmaSolicitudRepository).deleteByIdSolicitud(500L);
        verify(checkeoRepository).deleteAll(List.of(checkeo));
        verify(solicitudRepository).deleteAll(anyList());

        verify(documentRepository).deleteAll(List.of(doc));
        verify(imageRepository).deleteAll(List.of(img));
        verify(scheduleRepository).deleteByDeviceId(300L);
        verify(verificationRepository).deleteAll(List.of(verif));
        verify(productRepository).deleteAll(List.of(prod));

        verify(contratoRepository).delete(contrato);

        verify(firmaSoporteRepository).deleteByIdUsuario(200L);
        verify(userRepository).delete(clientUser);

        verify(customerRepository).delete(targetCustomer);
    }
}
