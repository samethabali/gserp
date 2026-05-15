package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.model.Resource;
import com.gserp.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Resource>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(resourceService.getAll()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Resource>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(resourceService.getActive()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Resource>> create(@RequestBody Resource resource) {
        return ResponseEntity.ok(ApiResponse.ok("Kaynak eklendi", resourceService.create(resource)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Resource>> update(@PathVariable Long id, @RequestBody Resource resource) {
        return ResponseEntity.ok(ApiResponse.ok("Kaynak güncellendi", resourceService.update(id, resource)));
    }
}
