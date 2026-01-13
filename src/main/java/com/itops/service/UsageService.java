package com.itops.service;

import com.itops.repository.ProjectRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageService {
    
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    
    @Transactional(readOnly = true)
    public int countActiveUsers(UUID companyId) {
        return (int) userRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
            .stream()
            .filter(user -> user.getIsActive() != null && user.getIsActive())
            .count();
    }
    
    @Transactional(readOnly = true)
    public int countActiveProjects(UUID companyId) {
        return (int) projectRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
            .size();
    }
}
