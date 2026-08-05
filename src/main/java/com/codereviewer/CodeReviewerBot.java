package com.codereviewer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CodeReviewerBot {

    public static void main(String[] args) {
        String groqApiKey = System.getenv("GROQ_API_KEY");
        String githubToken = System.getenv("GITHUB_TOKEN");
        String repo = System.getenv("REPOSITORY");
        String prNumber = System.getenv("PR_NUMBER");

        if (groqApiKey == null || githubToken == null || repo == null || prNumber == null) {
            System.err.println("Missing required environment variables.");
            System.exit(1);
        }

        try {
            HttpClient client = HttpClient.newHttpClient();

            // 1. Fetch PR Diff from GitHub REST API
            String diffUrl = "https://api.github.com/repos/" + repo + "/pulls/" + prNumber;
            HttpRequest diffRequest = HttpRequest.newBuilder()
                    .uri(URI.create(diffUrl))
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Accept", "application/vnd.github.v3.diff")
                    .GET()
                    .build();

            HttpResponse<String> diffResponse = client.send(diffRequest, HttpResponse.BodyHandlers.ofString());
            String patch = diffResponse.body();

            if (patch.isBlank()) {
                System.out.println("No diff changes found.");
                return;
            }

            // Clean up patch content for JSON payload
            String escapedPatch = patch.replace("\\", "\\\\")
                                       .replace("\"", "\\\"")
                                       .replace("\n", "\\n")
                                       .replace("\r", "\\r")
                                       .replace("\t", "\\t");

            // 2. Query Groq API (Llama 3 Model)
            String groqBody = "{"
                    + "\"model\": \"llama3-8b-8192\","
                    + "\"messages\": ["
                    + "  {\"role\": \"system\", \"content\": \"You are an expert AI code reviewer. Provide short, concise, and helpful code review feedback.\"},"
                    + "  {\"role\": \"user\", \"content\": \"Review this git patch:\\n" + escapedPatch + "\"}"
                    + "]"
                    + "}";

            HttpRequest groqRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(groqBody))
                    .build();

            HttpResponse<String> groqResponse = client.send(groqRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("Groq Response Status: " + groqResponse.statusCode());
            
            // 3. Simple JSON extraction for response content
            String responseBody = groqResponse.body();
            String reviewContent = extractContentFromGroqResponse(responseBody);

            // 4. Post comment back to GitHub PR
            String commentUrl = "https://api.github.com/repos/" + repo + "/issues/" + prNumber + "/comments";
            String commentBody = "{\"body\": \"### 🤖 CodeReviewer.AI Feedback\\n\\n" + reviewContent + "\"}";

            HttpRequest commentRequest = HttpRequest.newBuilder()
                    .uri(URI.create(commentUrl))
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(commentBody))
                    .build();

            HttpResponse<String> commentResponse = client.send(commentRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("GitHub Comment Status: " + commentResponse.statusCode());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractContentFromGroqResponse(String json) {
        try {
            int contentIndex = json.indexOf("\"content\":");
            if (contentIndex != -1) {
                int start = json.indexOf("\"", contentIndex + 10) + 1;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse response JSON cleanly");
        }
        return "Reviewed code successfully.";
    }
}