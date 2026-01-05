package com.github.aryanaggarwal.dto.response;

import com.github.aryanaggarwal.domain.enums.RoleType;
import lombok.Data;

/**
 * Data Transfer Object for Role response.
 */
@Data
public class RoleResponse {

    private Long id;
    private RoleType type;
}
