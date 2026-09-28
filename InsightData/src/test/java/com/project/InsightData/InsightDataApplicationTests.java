package com.project.InsightData;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InsightDataApplicationTests {

    @Autowired
    private ResearchService researchService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        assertNotNull(researchService);
        assertNotNull(objectMapper);
    }

    @Test
    void testMissingApiKeyReturnsFriendlyMessage() {
        ResearchRequest request = new ResearchRequest();
        request.setContent("Spring Boot is a framework for building Java applications.");
        request.setOperation("summarize");

        String result = researchService.processContent(request);
        assertTrue(result.contains("Gemini API key is not configured")
                || result.contains("API Error"));
    }

    @Test
    void testGeminiResponseParsing() throws Exception {
        String json = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Spring Boot makes it easy to create stand-alone applications."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 15,
                    "candidatesTokenCount": 10,
                    "totalTokenCount": 25
                  },
                  "modelVersion": "gemini-2.0-flash"
                }
                """;

        GeminiResponse response = objectMapper.readValue(json, GeminiResponse.class);
        assertNotNull(response);
        assertNotNull(response.getCandidates());
        assertFalse(response.getCandidates().isEmpty());

        GeminiResponse.Candidate candidate = response.getCandidates().get(0);
        assertNotNull(candidate.getContent());
        assertNotNull(candidate.getContent().getParts());
        assertFalse(candidate.getContent().getParts().isEmpty());
        assertEquals("Spring Boot makes it easy to create stand-alone applications.", candidate.getContent().getParts().get(0).getText());
    }

    @Test
    void testValidationOnEmptyContent() {
        ResearchRequest request = new ResearchRequest();
        request.setContent("");
        request.setOperation("summarize");

        assertThrows(IllegalArgumentException.class, () -> researchService.processContent(request));
    }

    @Test
    void testValidationOnInvalidOperation() {
        ResearchRequest request = new ResearchRequest();
        request.setContent("Some text");
        request.setOperation("invalid_op");

        assertThrows(IllegalArgumentException.class, () -> researchService.processContent(request));
    }
}
