package com.app.starter1.persistence.services;

import com.app.starter1.dto.LoginRequest;
import com.app.starter1.dto.UserCreateUpdateRequest;
import com.app.starter1.persistence.entity.Customer;
import com.app.starter1.persistence.entity.RoleEntity;
import com.app.starter1.persistence.entity.RoleEnum;
import com.app.starter1.persistence.entity.Solicitud;
import com.app.starter1.persistence.entity.UserEntity;
import com.app.starter1.persistence.repository.CustomerRepository;
import com.app.starter1.persistence.repository.RoleRepository;
import com.app.starter1.persistence.repository.SolicitudRepository;
import com.app.starter1.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailServiceAPTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SolicitudRepository solicitudRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationProducerService producerService;

    @InjectMocks
    private UserDetailServiceAP userService;

    private Customer empresaA;
    private Customer empresaB;
    private RoleEntity roleAdmin;
    private RoleEntity roleUser;
    private RoleEntity roleSuperAdmin;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
        ReflectionTestUtils.setField(userService, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(userService, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(userService, "solicitudRepository", solicitudRepository);

        empresaA = new Customer();
        empresaA.setId(1L);
        empresaA.setName("Empresa A");

        empresaB = new Customer();
        empresaB.setId(2L);
        empresaB.setName("Empresa B");

        roleSuperAdmin = RoleEntity.builder().id(1L).roleEnum(RoleEnum.SUPERADMIN).build();
        roleAdmin = RoleEntity.builder().id(2L).roleEnum(RoleEnum.ADMIN).build();
        roleUser = RoleEntity.builder().id(4L).roleEnum(RoleEnum.USER).build();
    }

    @Test
    @DisplayName("Actualizar usuario sin contraseña mantiene contraseña actual, ignora cambios de email/username y usa Set mutable")
    void updateUser_withoutPassword_keepsPasswordAndIgnoresAccountFields() throws Exception {
        UserEntity existing = UserEntity.builder()
                .id(10L)
                .nombres("Juan")
                .apellidos("Perez")
                .username("juan_original")
                .email("juan@original.com")
                .password("$2a$10$encodedExistingPassword")
                .roles(new HashSet<>(Set.of(roleUser)))
                .customer(empresaA)
                .isEnabled(true)
                .accountNoExpired(true)
                .accountNoLocked(true)
                .credentialNoExpired(true)
                .build();

        UserCreateUpdateRequest req = new UserCreateUpdateRequest();
        req.setId(10L);
        req.setNombres("Juan Carlos");
        req.setApellidos("Perez Gomez");
        req.setUsername("intento_cambio_user");
        req.setEmail("intento@cambio.com");
        req.setPassword("");
        req.setConfirmPassword("");
        req.setRole("4");
        req.setCustomer("1");

        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(4L)).thenReturn(Optional.of(roleUser));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(empresaA));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = userService.createOrUpdateUser(req);

        assertEquals("success", result.get("result"));
        UserEntity saved = (UserEntity) result.get("user");
        assertEquals("Juan Carlos", saved.getNombres());
        assertEquals("Perez Gomez", saved.getApellidos());
        // No debe permitir cambiar username ni email
        assertEquals("juan_original", saved.getUsername());
        assertEquals("juan@original.com", saved.getEmail());
        // Debe mantener la contraseña original sin re-encriptar
        assertEquals("$2a$10$encodedExistingPassword", saved.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
        // La colección de roles debe ser mutable para Hibernate
        assertDoesNotThrow(() -> saved.getRoles().clear());
    }

    @Test
    @DisplayName("Actualizar usuario con nueva contraseña válida actualiza y encripta contraseña")
    void updateUser_withNewPassword_encodesPassword() throws Exception {
        UserEntity existing = UserEntity.builder()
                .id(11L)
                .nombres("Ana")
                .apellidos("Lopez")
                .username("ana_lopez")
                .email("ana@empresa.com")
                .password("$2a$10$oldHash")
                .roles(new HashSet<>(Set.of(roleUser)))
                .customer(empresaA)
                .isEnabled(true)
                .build();

        UserCreateUpdateRequest req = new UserCreateUpdateRequest();
        req.setId(11L);
        req.setNombres("Ana Maria");
        req.setApellidos("Lopez");
        req.setUsername("ana_lopez");
        req.setEmail("ana@empresa.com");
        req.setPassword("NuevaClave1!");
        req.setConfirmPassword("NuevaClave1!");
        req.setRole("4");
        req.setCustomer("1");

        when(userRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(4L)).thenReturn(Optional.of(roleUser));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(empresaA));
        when(passwordEncoder.encode("NuevaClave1!")).thenReturn("$2a$10$newHash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = userService.createOrUpdateUser(req);
        UserEntity saved = (UserEntity) result.get("user");

        assertEquals("$2a$10$newHash", saved.getPassword());
        assertEquals("Ana Maria", saved.getNombres());
    }

    @Test
    @DisplayName("No permite degradar el rol del último ADMIN de una empresa a USER")
    void updateUser_demotingLastAdminInCompany_throwsException() {
        UserEntity lastAdmin = UserEntity.builder()
                .id(20L)
                .nombres("Admin")
                .apellidos("Unico")
                .username("admin_unico")
                .email("admin@empresaa.com")
                .password("$2a$10$hash")
                .roles(new HashSet<>(Set.of(roleAdmin)))
                .customer(empresaA)
                .isEnabled(true)
                .build();

        UserCreateUpdateRequest req = new UserCreateUpdateRequest();
        req.setId(20L);
        req.setNombres("Admin");
        req.setApellidos("Unico");
        req.setUsername("admin_unico");
        req.setEmail("admin@empresaa.com");
        req.setPassword("");
        req.setConfirmPassword("");
        req.setRole("4"); // USER
        req.setCustomer("1");

        when(userRepository.findById(20L)).thenReturn(Optional.of(lastAdmin));
        when(roleRepository.findById(4L)).thenReturn(Optional.of(roleUser));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(empresaA));
        when(userRepository.countByCustomerIdAndRoles_RoleEnum(1L, RoleEnum.ADMIN)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createOrUpdateUser(req));
        assertTrue(ex.getMessage().toLowerCase().contains("admin"));
    }

    @Test
    @DisplayName("Eliminar usuario con rol USER desvincula solicitudes y elimina el usuario")
    void deleteUser_roleUser_deletesSuccessfully() throws Exception {
        UserEntity targetUser = UserEntity.builder()
                .id(30L)
                .username("usuario_prueba")
                .roles(new HashSet<>(Set.of(roleUser)))
                .customer(empresaA)
                .build();

        UserEntity actingSuperAdmin = UserEntity.builder()
                .id(1L)
                .username("ingenieria")
                .roles(new HashSet<>(Set.of(roleSuperAdmin)))
                .build();

        Solicitud sol = Solicitud.builder().idSolicitud(100L).usuarioAsignado(targetUser).build();

        when(userRepository.findById(30L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findByUsername("ingenieria")).thenReturn(Optional.of(actingSuperAdmin));
        when(solicitudRepository.findByUsuarioAsignadoId(30L)).thenReturn(List.of(sol));

        userService.deleteUser(30L, "ingenieria");

        assertNull(sol.getUsuarioAsignado());
        verify(solicitudRepository).saveAll(anyList());
        verify(userRepository).delete(targetUser);
    }

    @Test
    @DisplayName("No permite eliminar el último ADMIN de una empresa")
    void deleteUser_lastAdminInCompany_throwsException() {
        UserEntity lastAdmin = UserEntity.builder()
                .id(40L)
                .username("admin_empresa_a")
                .roles(new HashSet<>(Set.of(roleAdmin)))
                .customer(empresaA)
                .build();

        UserEntity actingSuperAdmin = UserEntity.builder()
                .id(1L)
                .username("ingenieria")
                .roles(new HashSet<>(Set.of(roleSuperAdmin)))
                .build();

        when(userRepository.findById(40L)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.findByUsername("ingenieria")).thenReturn(Optional.of(actingSuperAdmin));
        when(userRepository.countByCustomerIdAndRoles_RoleEnum(1L, RoleEnum.ADMIN)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(40L, "ingenieria"));
        assertTrue(ex.getMessage().toLowerCase().contains("admin"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Permite eliminar un ADMIN si la empresa tiene al menos otro ADMIN")
    void deleteUser_adminWithBackupAdminInCompany_deletesSuccessfully() throws Exception {
        UserEntity adminToDelete = UserEntity.builder()
                .id(41L)
                .username("admin_secundario")
                .roles(new HashSet<>(Set.of(roleAdmin)))
                .customer(empresaA)
                .build();

        UserEntity actingSuperAdmin = UserEntity.builder()
                .id(1L)
                .username("ingenieria")
                .roles(new HashSet<>(Set.of(roleSuperAdmin)))
                .build();

        when(userRepository.findById(41L)).thenReturn(Optional.of(adminToDelete));
        when(userRepository.findByUsername("ingenieria")).thenReturn(Optional.of(actingSuperAdmin));
        when(userRepository.countByCustomerIdAndRoles_RoleEnum(1L, RoleEnum.ADMIN)).thenReturn(2L);
        when(solicitudRepository.findByUsuarioAsignadoId(41L)).thenReturn(Collections.emptyList());

        userService.deleteUser(41L, "ingenieria");

        verify(userRepository).delete(adminToDelete);
    }

    @Test
    @DisplayName("Un ADMIN no puede eliminar usuarios de otra empresa")
    void deleteUser_adminDeletingUserFromDifferentCompany_throwsException() {
        UserEntity userEmpresaB = UserEntity.builder()
                .id(50L)
                .username("user_b")
                .roles(new HashSet<>(Set.of(roleUser)))
                .customer(empresaB)
                .build();

        UserEntity adminEmpresaA = UserEntity.builder()
                .id(40L)
                .username("admin_a")
                .roles(new HashSet<>(Set.of(roleAdmin)))
                .customer(empresaA)
                .build();

        when(userRepository.findById(50L)).thenReturn(Optional.of(userEmpresaB));
        when(userRepository.findByUsername("admin_a")).thenReturn(Optional.of(adminEmpresaA));

        assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(50L, "admin_a"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("No permite eliminar el último SUPERADMIN del sistema")
    void deleteUser_lastSuperAdmin_throwsException() {
        UserEntity lastSuper = UserEntity.builder()
                .id(1L)
                .username("ingenieria")
                .roles(new HashSet<>(Set.of(roleSuperAdmin)))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(lastSuper));
        when(userRepository.findByUsername("ingenieria")).thenReturn(Optional.of(lastSuper));
        when(userRepository.countByRoles_RoleEnum(RoleEnum.SUPERADMIN)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(1L, "ingenieria"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Bloquea login si la empresa (Customer) del usuario está inactiva con mensaje 'Cuenta desactivada'")
    void loginUser_whenCustomerInactive_throwsCuentaDesactivada() {
        Customer inactiveCompany = Customer.builder().id(88L).name("Empresa Inactiva").status(false).build();
        RoleEntity roleUserWithPerms = RoleEntity.builder()
                .id(4L)
                .roleEnum(RoleEnum.USER)
                .permissionList(new HashSet<>())
                .build();

        UserEntity user = UserEntity.builder()
                .id(77L)
                .username("user_inactivo")
                .password("$2a$10$hash")
                .isEnabled(true)
                .accountNoExpired(true)
                .accountNoLocked(true)
                .credentialNoExpired(true)
                .roles(new HashSet<>(Set.of(roleUserWithPerms)))
                .customer(inactiveCompany)
                .build();

        when(userRepository.findUserEntityByUsername("user_inactivo")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Clave123!", "$2a$10$hash")).thenReturn(true);
        when(userRepository.findByUsername("user_inactivo")).thenReturn(Optional.of(user));

        DisabledException ex = assertThrows(DisabledException.class,
                () -> userService.loginUser(new LoginRequest("user_inactivo", "Clave123!")));
        assertEquals("Cuenta desactivada", ex.getMessage());
    }
}
