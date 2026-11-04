package com.betanalyzer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betanalyzer.dto.SlipAnalysisRequestDto;
import com.betanalyzer.service.SlipAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the /api/slips/analyze contract: empty input is rejected with a
 * 400 (never a misleading 100% probability) and errors follow the standard
 * JSON problem shape produced by {@code GlobalExceptionHandler}.
 */
@WebMvcTest(SlipController.class)
class SlipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SlipAnalysisService slipAnalysisService;

    @Test
    void emptyRawTextReturns400() throws Exception {
        when(slipAnalysisService.analyze(any()))
                .thenThrow(new IllegalArgumentException("No selections to analyze."));

        SlipAnalysisRequestDto request = new SlipAnalysisRequestDto();
        request.setRawText("");

        mockMvc.perform(post("/api/slips/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("No selections to analyze."))
                .andExpect(jsonPath("$.path").value("/api/slips/analyze"));
    }

    @Test
    void validSlipReturns200() throws Exception {
        when(slipAnalysisService.analyze(any()))
                .thenReturn(new com.betanalyzer.dto.SlipAnalysisResponseDto(
                        java.util.List.of(), 0.0, 0.0, 0.0, "LOW", java.util.List.of(), java.util.List.of()));

        SlipAnalysisRequestDto request = new SlipAnalysisRequestDto();
        request.setRawText("Kaizer Chiefs vs Orlando Pirates - Home Win @ 2.10");

        mockMvc.perform(post("/api/slips/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
