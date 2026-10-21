package com.civictrack.security;

import com.civictrack.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Adapter: translates our User entity into what Spring Security understands. Holds plain values only. */
public class CustomUserDetails implements UserDetails {

    private final String email;
    private final String passwordHash;
    private final String authority;
    private final boolean enabled;
    private final boolean accountNonLocked;

    public CustomUserDetails(User user) {
        this(user, true);
    }

    public CustomUserDetails(User user, boolean accountNonLocked) {
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.authority = user.getRole().authority();
        this.enabled = user.isEnabled();
        this.accountNonLocked = accountNonLocked;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }
}
