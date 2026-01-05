package com.github.aryanaggarwal.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {

    PENDING("Pending"),
    COMPLETED("Completed"),
    FAILED("Failed");

    private String label;
}
