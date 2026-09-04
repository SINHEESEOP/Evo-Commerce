package com.evo.commerce.domain.product.presentation;

import com.evo.commerce.domain.product.application.ProductService;
import com.evo.commerce.domain.product.dto.ProductCreateRequest;
import com.evo.commerce.domain.product.dto.ProductResponse;
import com.evo.commerce.global.auth.RequireRole;
import com.evo.commerce.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<List<ProductResponse>> getProducts() {
        return ApiResponse.success(productService.getProducts());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.success(productService.getProduct(id));
    }

    @PostMapping
    @RequireRole("MASTER")
    public ApiResponse<ProductResponse> registerProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.success(productService.registerProduct(request));
    }
}
