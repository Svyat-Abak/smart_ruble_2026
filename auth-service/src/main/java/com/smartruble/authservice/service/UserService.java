package com.smartruble.authservice.service;

import com.smartruble.authservice.dto.UserResponse;
import com.smartruble.authservice.entity.Role;
import com.smartruble.authservice.entity.RoleName;
import com.smartruble.authservice.entity.User;
import com.smartruble.authservice.exception.ApiException;
import com.smartruble.authservice.repository.RoleRepository;
import com.smartruble.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return UserResponse.from(getUser(id));
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<RoleName> roleNames) {
        User user = getUser(id);
        List<Role> roles = roleRepository.findAllByNameIn(roleNames);
        if (roles.size() != roleNames.size()) {
            throw ApiException.badRequest("Часть ролей не найдена в БД");
        }
        user.setRoles(new HashSet<>(roles));
        // новые роли попадут в токен только после повторного входа
        refreshTokenService.revokeAll(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, boolean active) {
        User user = getUser(id);
        user.setActive(active);
        if (!active) {
            refreshTokenService.revokeAll(user);
        }
        return UserResponse.from(user);
    }

    private User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }
}
