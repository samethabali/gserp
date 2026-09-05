package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.model.ActivityEvent;
import com.gscrm.service.ActivityEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * İşlem kütüğü.
 *
 * <p>Uç eskiden yalnızca "son N kayıt" döndürüyordu; sayfa da onu bellekte ters
 * çevirip gösteriyordu. Tarih aralığı, kullanıcı veya işlem türüne göre arama
 * yapmak mümkün değildi, dolayısıyla kütük bir olayı incelemek için kullanılamıyordu.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER','ORG_OWNER','PLATFORM_ADMIN')")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int CSV_MAX_ROWS = 5000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityEventService activityEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String q) {

        Page<ActivityEvent> result = activityEventService.search(from, to, action, user, q, pageable(page, size));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    /** Sayfanın filtre açılırlarını doldurur; sabit liste kullanmak yeni işlem türlerini gizlerdi. */
    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<String>>> actions() {
        return ResponseEntity.ok(ApiResponse.ok(activityEventService.distinctActions()));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String q) {

        List<ActivityEvent> rows = activityEventService
                .search(from, to, action, user, q, PageRequest.of(0, CSV_MAX_ROWS, sort()))
                .getContent();

        StringBuilder csv = new StringBuilder();
        // Excel dosyayı UTF-8 olarak tanısın diye BOM; aksi hâlde Türkçe karakterler bozuluyor.
        csv.append('﻿');
        csv.append("Zaman;İşlem;Sonuç;Tür;Özet;Detay;Kullanıcı;IP;HTTP\n");
        for (ActivityEvent e : rows) {
            csv.append(cell(e.getCreatedAt() != null ? e.getCreatedAt().format(CSV_TIME) : ""))
               .append(';').append(cell(e.getAction()))
               .append(';').append(cell(e.getOutcome()))
               .append(';').append(cell(e.getEntityType()))
               .append(';').append(cell(e.getSummary()))
               .append(';').append(cell(e.getDetail()))
               .append(';').append(cell(e.getActorUsername()))
               .append(';').append(cell(e.getIp()))
               .append(';').append(cell(e.getHttpStatus() != null ? String.valueOf(e.getHttpStatus()) : ""))
               .append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"islem-kutugu.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort());
    }

    /** En yeni üstte; sayfa bunu daha önce istemcide {@code reverse()} ile taklit ediyordu. */
    private Sort sort() {
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ');
        return '"' + escaped + '"';
    }
}
