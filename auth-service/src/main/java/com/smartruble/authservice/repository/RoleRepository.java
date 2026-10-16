package com.smartruble.authservice.repository;

import com.smartruble.authservice.entity.Role;
import com.smartruble.authservice.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(RoleName name);

    List<Role> findAllByNameIn(Collection<RoleName> names);
}
