package com.evo.commerce.global.exception.support;

import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThrowingController {

    @GetMapping("/test/business-error")
    public void throwBusinessError() {
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    @GetMapping("/test/unexpected-error")
    public void throwUnexpectedError() {
        throw new IllegalStateException("잔액 부족");
    }

    @PostMapping("/test/validation-error")
    public void throwValidationError(@Valid @RequestBody ValidationTestRequest request) {
    }

}
