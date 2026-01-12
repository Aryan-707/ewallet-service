package com.github.aryanaggarwal.service;

import com.github.aryanaggarwal.domain.entity.Type;
import com.github.aryanaggarwal.repository.TypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service used for Type related operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TypeService {

    private final TypeRepository typeRepository;

    /**
     * Fetches a single type reference (entity) by the given id.
     *
     * @param id
     * @return Type
     */
    public Type getReferenceById(long id) {
        return typeRepository.getReferenceById(id);
    }
}
