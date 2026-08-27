package com.evo.commerce.global.exception.support;

import jakarta.validation.constraints.NotBlank;

public record ValidationTestRequest(@NotBlank String name) {
}
