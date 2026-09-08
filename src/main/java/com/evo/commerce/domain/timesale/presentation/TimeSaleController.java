package com.evo.commerce.domain.timesale.presentation;

import com.evo.commerce.domain.timesale.application.TimeSaleFacade;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventCreateRequest;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventResponse;
import com.evo.commerce.domain.timesale.dto.TimeSaleParticipationResponse;
import com.evo.commerce.global.auth.JwtAuthenticationFilter;
import com.evo.commerce.global.auth.RequireRole;
import com.evo.commerce.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/timesales")
@RequiredArgsConstructor
public class TimeSaleController {

    private final TimeSaleFacade timeSaleFacade;

    @GetMapping
    public ApiResponse<List<TimeSaleEventResponse>> getEvents() {
        return ApiResponse.success(timeSaleFacade.getEvents());
    }

    @GetMapping("/{eventId}")
    public ApiResponse<TimeSaleEventResponse> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success(timeSaleFacade.getEvent(eventId));
    }

    @PostMapping
    @RequireRole("MASTER")
    public ApiResponse<TimeSaleEventResponse> createEvent(@Valid @RequestBody TimeSaleEventCreateRequest request) {
        return ApiResponse.success(timeSaleFacade.createEvent(request));
    }

    @PostMapping("/{eventId}/participate")
    public ApiResponse<TimeSaleParticipationResponse> participate(HttpServletRequest request, @PathVariable Long eventId) {
        Long userId = (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
        return ApiResponse.success(timeSaleFacade.participate(userId, eventId));
    }
}
