package com.gscrm.controller;

import com.gscrm.dto.request.AppointmentMoveRequest;
import com.gscrm.dto.response.AppointmentResponse;
import com.gscrm.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CalendarWebSocketController {

    private final AppointmentService appointmentService;

    @MessageMapping("/appointment/move")
    @SendTo("/topic/appointments")
    public Map<String, Object> handleMove(AppointmentMoveRequest request) {
        AppointmentResponse response = appointmentService.move(request);
        return Map.of("action", "MOVE", "appointment", response);
    }
}
