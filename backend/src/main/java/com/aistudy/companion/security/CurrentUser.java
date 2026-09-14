package com.aistudy.companion.security;

import com.aistudy.companion.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Small helper so services/controllers don't repeat SecurityContext plumbing. */
@Component
public class CurrentUser {
    public User get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User u) return u;
        throw new IllegalStateException("No authenticated user in context");
    }
}
