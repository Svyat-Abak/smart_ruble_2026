package com.smartruble.authservice.config;

import com.smartruble.authservice.entity.*;
import com.smartruble.authservice.repository.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit opt-in for a local exhibition setup. Existing users are never overwritten. */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoUsers implements ApplicationRunner {
  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder encoder;

  public DemoUsers(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    var names =
        List.of(
            RoleName.ROLE_MANAGER,
            RoleName.ROLE_FINANCE_DIRECTOR,
            RoleName.ROLE_CONTRACTOR,
            RoleName.ROLE_EMPLOYEE);
    for (int i = 0; i < names.size(); i++) {
      String phone = "+7000000000" + (i + 1);
      if (users.existsByPhoneNumber(phone)) continue;
      Role role = roles.findByName(names.get(i)).orElseThrow();
      users.save(
          User.builder()
              .phoneNumber(phone)
              .passwordHash(encoder.encode("Demo12345!"))
              .roles(new HashSet<>(Set.of(role)))
              .build());
    }
  }
}
