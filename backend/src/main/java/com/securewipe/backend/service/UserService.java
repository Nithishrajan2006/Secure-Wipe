package com.securewipe.backend.service;

import com.securewipe.backend.dto.response.UserDto;
import com.securewipe.backend.entity.User;
import com.securewipe.backend.exception.ResourceNotFoundException;
import com.securewipe.backend.repository.UserRepository;
import com.securewipe.backend.repository.WipeJobRepository;
import com.securewipe.backend.enums.WipeStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WipeJobRepository wipeJobRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional
    public UserDto toggleUserStatus(Long userId, User adminUser, HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.setActive(!user.isActive());
        user = userRepository.save(user);

        auditService.log(adminUser, user.isActive() ? "USER_ENABLED" : "USER_DISABLED",
                "USER", userId.toString(),
                "User " + user.getUsername() + " status changed to: " + (user.isActive() ? "ACTIVE" : "INACTIVE"),
                request);

        return toDto(user);
    }

    private UserDto toDto(User user) {
        long totalWipes = wipeJobRepository.countByUserAndStatus(user, WipeStatus.COMPLETED);
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .totalWipes(totalWipes)
                .build();
    }
}
