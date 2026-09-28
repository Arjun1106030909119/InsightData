package com.project.InsightData;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class ResearchService {

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ResearchService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String processContent(ResearchRequest request) {

        if (request == null || request.getContent() == null
                || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        if (request.getOperation() == null
                || request.getOperation().isBlank()) {
            throw new IllegalArgumentException("Operation cannot be empty");
        }

        String prompt = buildPrompt(request);

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return "Gemini API key is not configured. Please set the GEMINI_API_KEY environment variable or configure gemini.api.key in application.properties.";
        }

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of(
                                "parts", new Object[]{
                                        Map.of("text", prompt)
                                }
                        )
                }
        );

        try {
            String effectiveKey = geminiApiKey.trim();
            String url = normalizeUrl(geminiApiUrl);

            WebClient.RequestHeadersSpec<?> requestSpec;
            if (url.contains("key=")) {
                requestSpec = webClient.post()
                        .uri(url.endsWith("=") ? url + effectiveKey : url + "&key=" + effectiveKey)
                        .header("x-goog-api-key", effectiveKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody);
            } else {
                requestSpec = webClient.post()
                        .uri(url)
                        .header("x-goog-api-key", effectiveKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody);
            }

            String response = requestSpec
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            formatErrorMessage(clientResponse.statusCode().value(), body)
                                    )))
                    )
                    .bodyToMono(String.class)
                    .block();

            return extractTextFromResponse(response);

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return "API Error: " + cause.getMessage();
        }
    }

    private String formatErrorMessage(int statusCode, String errorBody) {
        if (errorBody != null) {
            if (errorBody.contains("CREDENTIALS_MISSING") || errorBody.contains("UNAUTHENTICATED")) {
                return "Gemini API authentication failed (401). Please check that your GEMINI_API_KEY is valid.";
            }
            if (errorBody.contains("API_KEY_INVALID")) {
                return "The provided Gemini API key is invalid. Please verify your GEMINI_API_KEY.";
            }
            if (statusCode == 429 || errorBody.contains("RESOURCE_EXHAUSTED")) {
                return "Gemini API rate limit or quota exceeded. Please try again later.";
            }
        }
        return "Gemini API request failed (" + statusCode + "): " + errorBody;
    }

    private String extractTextFromResponse(String response) {

        try {
            GeminiResponse geminiResponse =
                    objectMapper.readValue(response, GeminiResponse.class);

            if (geminiResponse.getCandidates() != null
                    && !geminiResponse.getCandidates().isEmpty()) {

                GeminiResponse.Candidate candidate =
                        geminiResponse.getCandidates().get(0);

                if (candidate.getContent() != null
                        && candidate.getContent().getParts() != null
                        && !candidate.getContent().getParts().isEmpty()) {

                    return candidate.getContent()
                            .getParts()
                            .get(0)
                            .getText();
                }
            }

            return "No content found in Gemini response";

        } catch (Exception e) {
            return "Failed to parse Gemini response: " + e.getMessage();
        }
    }

    private String buildPrompt(ResearchRequest request) {

        StringBuilder prompt = new StringBuilder();

        switch (request.getOperation().toLowerCase()) {

            case "summarize":
                prompt.append(
                        "Provide a clear and concise summary " +
                                "of the following text in a few sentences:\n\n"
                );
                break;

            case "suggest":
                prompt.append(
                        "Based on the following content: suggest related topics and further reading. " +
                                "Format the response with clear headings and bullet points:\n\n"
                );
                break;

            default:
                throw new IllegalArgumentException(
                        "Invalid operation: " + request.getOperation()
                );
        }

        prompt.append(request.getContent());

        return prompt.toString();
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
        }
        String url = rawUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.contains(":generateContent")) {
            if (url.contains("/models/")) {
                url += ":generateContent";
            } else if (url.contains("/v1beta")) {
                url += "/models/gemini-2.0-flash:generateContent";
            } else {
                url += "/v1beta/models/gemini-2.0-flash:generateContent";
            }
        }
        return url;
    }
}
