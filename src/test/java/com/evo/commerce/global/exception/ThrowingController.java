package com.evo.commerce.global.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThrowingController {

    @GetMapping("/test/error")
    public void throwError() {
        throw new IllegalStateException("잔액 부족");
    }
}
