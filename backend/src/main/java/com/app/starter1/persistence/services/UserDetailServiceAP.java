package com.app.starter1.persistence.services;

import com.app.starter1.dto.AuthCreateUserRequest;
import com.app.starter1.dto.AuthResponse;
import com.app.starter1.dto.LoginRequest;
import com.app.starter1.dto.UserCreateUpdateRequest;
import com.app.starter1.persistence.entity.Customer;
import com.app.starter1.persistence.entity.RoleEntity;
import com.app.starter1.persistence.entity.RoleEnum;
import com.app.starter1.persistence.entity.Solicitud;
import com.app.starter1.persistence.entity.UserEntity;
import com.app.starter1.persistence.exeptions.UserNotFoundException;
import com.app.starter1.persistence.repository.CustomerRepository;
import com.app.starter1.persistence.repository.RoleRepository;
import com.app.starter1.persistence.repository.SolicitudRepository;
import com.app.starter1.persistence.repository.UserRepository;
import jakarta.transaction.Transactional;
import com.app.starter1.util.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserDetailServiceAP implements UserDetailsService {

        private static final Logger logger = LoggerFactory.getLogger(UserDetailServiceAP.class);

        public String FRONT_URL = System.getenv("NEXT_FRONTEND_URL");

        @Autowired
        @Lazy
        PasswordEncoder passwordEncoder;

        @Autowired
        private JwtUtils jwtUtils;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private ApplicationEventPublisher eventPublisher;

        @Autowired
        private final NotificationProducerService producerService;

        @Autowired
        private CustomerRepository customerRepository; // Para obtener el cliente si es necesario

        @Autowired
        private SolicitudRepository solicitudRepository;

        public UserDetailServiceAP(NotificationProducerService producerService) {
                this.producerService = producerService;
        }

        // Crear o actualizar un usuario
        @Transactional
        public Map<String, Object> createOrUpdateUser(UserCreateUpdateRequest request) throws IllegalAccessException {

                boolean isNew = (request.getId() == null || request.getId() == 0L);
                boolean hasNewPassword = (request.getPassword() != null && !request.getPassword().isBlank());

                if (isNew) {
                        // En creación la contraseña es obligatoria
                        if (!hasNewPassword || !request.getPassword().equals(request.getConfirmPassword())) {
                                throw new IllegalArgumentException("Las contraseñas no coinciden o están vacías.");
                        }

                        if (userRepository.existsByUsername(request.getUsername())) {
                                throw new IllegalArgumentException(
                                                "The username '" + request.getUsername() + "' is already taken.");
                        }

                        if (userRepository.existsByEmail(request.getEmail())) {
                                throw new IllegalArgumentException("The email '" + request.getEmail() + "' is already in use.");
                        }
                } else if (hasNewPassword) {
                        // En edición la contraseña es opcional, pero si se envía debe coincidir
                        if (!request.getPassword().equals(request.getConfirmPassword())) {
                                throw new IllegalArgumentException("Las contraseñas no coinciden.");
                        }
                }

                // Buscar el rol
                RoleEntity roleEntity = roleRepository.findById(Long.valueOf(request.getRole()))
                                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

                // Buscar el cliente
                Customer customer = customerRepository.findById(Long.valueOf(request.getCustomer()))
                                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

                // Crear o actualizar el usuario
                UserEntity userEntity;
                if (isNew) {
                        userEntity = new UserEntity();
                        userEntity.setUsername(request.getUsername());
                        userEntity.setEmail(request.getEmail());
                        userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
                        userEntity.setEnabled(true);
                        userEntity.setAccountNoExpired(true);
                        userEntity.setAccountNoLocked(true);
                        userEntity.setCredentialNoExpired(true);
                        userEntity.setVerificationToken("");
                } else {
                        userEntity = userRepository.findById(request.getId())
                                        .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                        // Si el usuario es actualmente ADMIN y se intenta cambiar a otro rol, validar que quede al menos un ADMIN en la empresa
                        boolean wasAdmin = userEntity.getRoles() != null && userEntity.getRoles().stream()
                                        .anyMatch(r -> r.getRoleEnum() == RoleEnum.ADMIN);
                        boolean willBeAdmin = roleEntity.getRoleEnum() == RoleEnum.ADMIN;
                        if (wasAdmin && !willBeAdmin && userEntity.getCustomer() != null) {
                                long adminCount = userRepository.countByCustomerIdAndRoles_RoleEnum(
                                                userEntity.getCustomer().getId(), RoleEnum.ADMIN);
                                if (adminCount <= 1) {
                                        throw new IllegalArgumentException(
                                                        "No se puede cambiar el rol de este administrador porque la empresa debe tener al menos un usuario con rol ADMIN.");
                                }
                        }

                        // En actualización NO se modifican username ni email (datos de cuenta protegidos)
                        if (hasNewPassword) {
                                userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
                        }
                }

                // Usar Set mutable para que Hibernate pueda gestionar la colección @ManyToMany sin UnsupportedOperationException
                userEntity.setRoles(new HashSet<>(Set.of(roleEntity)));
                userEntity.setCustomer(customer);
                userEntity.setApellidos(request.getApellidos() != null ? request.getApellidos() : "");
                userEntity.setNombres(request.getNombres() != null ? request.getNombres() : "");

                UserEntity savedUser = userRepository.save(userEntity);

                if (isNew) {
                        // Si es un nuevo usuario, generar un token de verificación
                        String token = UUID.randomUUID().toString();
                        userEntity.setVerificationToken(token);
                        userRepository.save(userEntity);

                        String activationLink = this.FRONT_URL + "/verificate/" + userEntity.getVerificationToken();

                        String to = userEntity.getEmail();
                        String subject = "Registro exitoso! " + "Bienvenido a GM2, " + request.getUsername();
                        String body = activationLink;
                        String type = "register";

                        String message = String.format(
                                        "{\"to\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\",\"type\":\"%s\",\"username\":\"%s\"}",
                                        to,
                                        subject, body, type, request.getUsername());
                        producerService.sendMessage("email-notifications", message);
                        System.out.println("Notificación enviada a la cola.");

                }

                // Construir la respuesta con el formato requerido
                Map<String, Object> response = new HashMap<>();
                response.put("result", "success");
                response.put("message", isNew ? "Usuario creado correctamente." : "Usuario actualizado correctamente.");
                response.put("user", savedUser);

                return response;

        }

        // Eliminar un usuario respetando aislamiento por empresa y protección del último ADMIN / SUPERADMIN
        @Transactional
        public void deleteUser(Long id, String actingUsername) throws UserNotFoundException {
                UserEntity targetUser = userRepository.findById(id)
                                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado."));

                UserEntity actingUser = actingUsername != null
                                ? userRepository.findByUsername(actingUsername).orElse(null)
                                : null;

                boolean actingIsSuperAdmin = actingUser != null && actingUser.getRoles() != null
                                && actingUser.getRoles().stream().anyMatch(r -> r.getRoleEnum() == RoleEnum.SUPERADMIN);

                boolean targetIsSuperAdmin = targetUser.getRoles() != null
                                && targetUser.getRoles().stream().anyMatch(r -> r.getRoleEnum() == RoleEnum.SUPERADMIN);
                boolean targetIsAdmin = targetUser.getRoles() != null
                                && targetUser.getRoles().stream().anyMatch(r -> r.getRoleEnum() == RoleEnum.ADMIN);
                boolean targetIsBiomedical = targetUser.getRoles() != null
                                && targetUser.getRoles().stream().anyMatch(r -> r.getRoleEnum() == RoleEnum.BIOMEDICAL);

                // Validar permisos por empresa si el usuario que ejecuta no es SUPERADMIN
                if (!actingIsSuperAdmin) {
                        if (targetIsSuperAdmin || targetIsBiomedical) {
                                throw new IllegalArgumentException("No tienes permisos para eliminar un usuario con ese rol.");
                        }
                        Long actingCustomerId = (actingUser != null && actingUser.getCustomer() != null)
                                        ? actingUser.getCustomer().getId()
                                        : null;
                        Long targetCustomerId = targetUser.getCustomer() != null
                                        ? targetUser.getCustomer().getId()
                                        : null;
                        if (actingCustomerId == null || !actingCustomerId.equals(targetCustomerId)) {
                                throw new IllegalArgumentException("No puedes eliminar usuarios de otra empresa.");
                        }
                }

                // Proteger el último SUPERADMIN del sistema
                if (targetIsSuperAdmin) {
                        long superAdminCount = userRepository.countByRoles_RoleEnum(RoleEnum.SUPERADMIN);
                        if (superAdminCount <= 1) {
                                throw new IllegalArgumentException("No se puede eliminar el último SUPERADMIN del sistema.");
                        }
                }

                // Proteger el último ADMIN de la empresa
                if (targetIsAdmin && targetUser.getCustomer() != null) {
                        long adminCount = userRepository.countByCustomerIdAndRoles_RoleEnum(
                                        targetUser.getCustomer().getId(), RoleEnum.ADMIN);
                        if (adminCount <= 1) {
                                throw new IllegalArgumentException(
                                                "No se puede eliminar este administrador porque la empresa debe tener al menos un usuario con rol ADMIN.");
                        }
                }

                // Desvincular solicitudes asignadas para evitar error de llave foránea
                List<Solicitud> assignedSolicitudes = solicitudRepository.findByUsuarioAsignadoId(id);
                if (assignedSolicitudes != null && !assignedSolicitudes.isEmpty()) {
                        for (Solicitud solicitud : assignedSolicitudes) {
                                solicitud.setUsuarioAsignado(null);
                        }
                        solicitudRepository.saveAll(assignedSolicitudes);
                }

                // Limpiar roles y eliminar usuario
                if (targetUser.getRoles() != null) {
                        targetUser.getRoles().clear();
                }
                userRepository.delete(targetUser);
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                UserEntity userEntity = userRepository.findUserEntityByUsername(username)
                                .orElseThrow(() -> new UsernameNotFoundException(
                                                "Th username " + username + " not exists!"));

                List<SimpleGrantedAuthority> autorityList = new ArrayList<>();

                // registrar roles en lista de autorizacion sprng security
                userEntity.getRoles().forEach(
                                role -> autorityList.add(
                                                new SimpleGrantedAuthority("ROLE_".concat(role.getRoleEnum().name()))));

                userEntity.getRoles().stream().flatMap(role -> role.getPermissionList().stream())
                                .forEach(permission -> autorityList
                                                .add(new SimpleGrantedAuthority(permission.getName())));
                return new User(userEntity.getUsername(),
                                userEntity.getPassword(),
                                userEntity.isEnabled(),
                                userEntity.isAccountNoExpired(),
                                userEntity.isCredentialNoExpired(),
                                userEntity.isAccountNoLocked(), autorityList);
        }

        public AuthResponse loginUser(LoginRequest authloginRequest) {

                String username = authloginRequest.username();
                String password = authloginRequest.password();

                // Intentamos autenticar al usuario
                Authentication authentication = this.authenticate(username, password);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                String accessToken = jwtUtils.CreateToken(authentication);

                // Buscamos el usuario en la base de datos
                Optional<UserEntity> userEntityOptional = userRepository.findByUsername(username);

                // Si no se encuentra el usuario, lanzamos BadCredentialsException con mensaje
                // adecuado
                UserEntity userEntity = userEntityOptional
                                .orElseThrow(() -> new BadCredentialsException(
                                                "The username " + username + " not exists!"));

                // Devolvemos la respuesta con el usuario y el token
                AuthResponse authResponse = new AuthResponse(username, "User logged in successfully", accessToken, true,
                                userEntity);

                return authResponse;
        }

        public Authentication authenticate(String username, String password) {
                // Intentamos cargar los detalles del usuario
                UserDetails userDetails = this.loadUserByUsername(username);

                // Si no se encuentra el usuario, lanzamos BadCredentialsException con mensaje
                // adecuado
                if (userDetails == null) {
                        throw new BadCredentialsException("Invalid username or password");
                }

                // Comparamos las contraseñas
                if (!passwordEncoder.matches(password, userDetails.getPassword())) {
                        throw new BadCredentialsException("Invalid password");
                }

                // Si todo es correcto, devolvemos el token de autenticación
                return new UsernamePasswordAuthenticationToken(username, userDetails.getPassword(),
                                userDetails.getAuthorities());
        }

        public AuthResponse createUser(AuthCreateUserRequest authCreateUserRequest) throws IllegalAccessException {

                String nombres = authCreateUserRequest.nombres();
                String apellidos = authCreateUserRequest.apellidos();
                String username = authCreateUserRequest.username();
                String password = authCreateUserRequest.password();
                String email = authCreateUserRequest.email();
                List<String> rolesRequest = authCreateUserRequest.roleRequest().roleListName();

                // Verificar si el username ya existe
                if (userRepository.existsByUsername(username)) {
                        throw new IllegalArgumentException("The username '" + username + "' is already taken.");
                }

                // Verificar si el email ya existe
                if (userRepository.existsByEmail(email)) {
                        throw new IllegalArgumentException("The email '" + email + "' is already in use.");
                }

                Set<RoleEntity> roleEntityList = roleRepository.findRoleEntitiesByRoleEnumIn(rolesRequest).stream()
                                .collect(Collectors.toSet());

                if (roleEntityList.isEmpty()) {
                        throw new IllegalAccessException("The roles specified do not exist.");
                }

                UserEntity userEntity = UserEntity.builder()
                                .nombres(nombres)
                                .apellidos(apellidos)
                                .username(username)
                                .password(passwordEncoder.encode(password))
                                .roles(roleEntityList)
                                .email(email)
                                .isEnabled(false) // Usuario inicialmente deshabilitado
                                .accountNoLocked(true)
                                .credentialNoExpired(true)
                                .credentialNoExpired(true)
                                .build();

                UserEntity userCreated = userRepository.save(userEntity);

                // Generar token de verificación y guardar
                String token = UUID.randomUUID().toString();
                userEntity.setVerificationToken(token);
                userRepository.save(userEntity);

                // String confirmationUrl = "http://localhost:8080/verify-email?token=" + token;
                // emailService.sendEmail(userEntity.getEmail(), "Email Verification", "Click
                // the link to verify your email: " + confirmationUrl);

                String activationLink = this.FRONT_URL + "/verificate/" + userEntity.getVerificationToken();

                String to = userEntity.getEmail();
                String subject = "Registro exitoso! " + "Bienvenido a GM2, " + nombres + ' ' + apellidos;
                String body = activationLink;
                String type = "register";

                String message = String.format(
                                "{\"to\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\",\"type\":\"%s\",\"username\":\"%s\"}",
                                to, subject,
                                body, type, username);
                producerService.sendMessage("email-notifications", message);
                System.out.println("Notificación enviada a la cola.");

                ArrayList<SimpleGrantedAuthority> authorityList = new ArrayList<>();

                // Roles
                userCreated.getRoles().forEach(
                                role -> authorityList.add(
                                                new SimpleGrantedAuthority("ROLE_".concat(role.getRoleEnum().name()))));

                // Permisos
                userCreated.getRoles()
                                .stream()
                                .flatMap(role -> role.getPermissionList().stream())
                                .forEach(permission -> authorityList
                                                .add(new SimpleGrantedAuthority(permission.getName())));

                SecurityContext context = SecurityContextHolder.getContext();
                Authentication authentication = new UsernamePasswordAuthenticationToken(userCreated.getUsername(),
                                userCreated.getPassword(), authorityList);

                String accessToken = jwtUtils.CreateToken(authentication);

                AuthResponse authResponse = new AuthResponse(userCreated.getUsername(),
                                "User created successfully. Please verify your email.", accessToken, true, userCreated);

                return authResponse;
        }

        public String validateVerificationToken(String token) {
                UserEntity user = userRepository.findByVerificationToken(token).orElse(null);
                if (user == null) {
                        return "invalid";
                }

                user.setEnabled(true);
                userRepository.save(user);
                return "valid";
        }

        // Obtener un usuario por su ID
        public UserEntity getUserById(Long id) throws UserNotFoundException {
                return userRepository.findById(id)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));
        }

        public void sendResetPasswordEmail(String email) {
                // Buscar usuario por email
                UserEntity user = userRepository.findUserEntityByEmail(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "Usuario no encontrado con el correo: " + email));

                // Generar token único (UUID o similar)
                String token = UUID.randomUUID().toString();
                user.setRecoveryToken(token);

                // Guardar el token en la base de datos
                userRepository.save(user);

                // Lógica para enviar el correo con el token
                String resetLink = "http://localhost:3000/reset-password/" + token;
                // Enviar correo con el enlace

                String to = user.getEmail();
                String subject = "GM2 Recuperación de contraseña";
                String body = resetLink;
                String type = "recover-password";
                String username = user.getUsername();

                String message = String.format(
                                "{\"to\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\",\"type\":\"%s\",\"username\":\"%s\"}",
                                to, subject,
                                body, type, username);

                producerService.sendMessage("email-notifications", message);
                System.out.println("Notificación enviada a la cola.");

        }

        public void resetPassword(String token, String newPassword) {
                // Buscar usuario por el token
                UserEntity user = userRepository.findByRecoveryToken(token)
                                .orElseThrow(() -> new RuntimeException("Token inválido o expirado."));

                // Actualizar la contraseña
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setRecoveryToken(""); // Eliminar el token después de usarlo
                userRepository.save(user);
        }
}
