package com.github.aryanaggarwal.service;

import com.github.aryanaggarwal.domain.entity.User;
import com.github.aryanaggarwal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service used for User related operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Fetches a single user reference (entity) by the given id.
     *
     * @param id
     * @return User
     */
    public User getReferenceById(long id) {
        return userRepository.getReferenceById(id);
    }
}
