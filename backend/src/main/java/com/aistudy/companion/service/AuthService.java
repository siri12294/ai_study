package com.aistudy.companion.service;

import com.aistudy.companion.dto.AuthDtos.*;
import com.aistudy.companion.entity.User;
import com.aistudy.companion.exception.ConflictException;
import com.aistudy.companion.exception.NotFoundException;
import com.aistudy.companion.repository.UserRepository;
import com.aistudy.companion.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = User.builder()
                .email(request.email().toLowerCase())
                .displayName(request.displayName())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(User.Role.USER)
                .build();
        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new NotFoundException("Invalid email or password."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new NotFoundException("Invalid email or password.");
        }
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }
}
