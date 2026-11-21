package com.betanalyzer.esoccer.controller;

import com.betanalyzer.esoccer.service.ESoccerImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/esoccer/import")
@RequiredArgsConstructor
public class ESoccerImportController {

    private final ESoccerImportService importService;

    @PostMapping("/csv")
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        try {
            int count = importService.importCsv(file);
            return ResponseEntity.ok().body("Imported " + count + " matches from CSV");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("CSV import failed: " + e.getMessage());
        }
    }

    @PostMapping("/json")
    public ResponseEntity<?> importJson(@RequestParam("file") MultipartFile file) {
        try {
            int count = importService.importJson(file);
            return ResponseEntity.ok().body("Imported " + count + " matches from JSON");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("JSON import failed: " + e.getMessage());
        }
    }
}
