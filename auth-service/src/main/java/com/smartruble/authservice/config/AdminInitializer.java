package com.smartruble.authservice.config;

import com.smartruble.authservice.entity.Role;
import com.smartruble.authservice.entity.RoleName;
import com.smartruble.authservice.entity.User;
import com.smartruble.authservice.repository.RoleRepository;
import com.smartruble.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Создаёт первого администратора при старте, если пользователя с таким телефоном ещё нет.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminProperties.enabled() || userRepository.existsByPhoneNumber(adminProperties.phoneNumber())) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("В БД нет роли ROLE_ADMIN"));
        User admin = User.builder()
                .phoneNumber(adminProperties.phoneNumber())
                .email(adminProperties.email())
                .passwordHash(passwordEncoder.encode(adminProperties.password()))
                .roles(new HashSet<>(Set.of(adminRole)))
                .build();
        userRepository.save(admin);
        log.warn("Создан администратор по умолчанию: {}. Смените пароль!", adminProperties.phoneNumber());
    }
}
