package com.codereviewer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CodeReviewerBot {

    public static void main(String[] args) throws Exception {
        String githubToken = System.getenv("GITHUB_TOKEN");
        String groqApiKey = System.getenv("GROQ_API_KEY");
        String repository = System.getenv("GITHUB_REPOSITORY"); // format: owner/repo
        String prNumber = System.getenv("PR_NUMBER");

        if (githubToken == null || groqApiKey == null || repository == null || prNumber == null) {
            System.err.println("Error: Missing required environment variables.");
            System.exit(1);
        }

        HttpClient client = HttpClient.newHttpClient();

        // 1. Fetch PR Diff from GitHub
        String diffUrl = "https://api.github.com/repos/" + repository + "/pulls/" + prNumber;
        HttpRequest diffRequest = HttpRequest.newBuilder()
                .uri(URI.create(diffUrl))
                .header("Accept", "application/vnd.github.v3.diff")
                .header("Authorization", "Bearer " + githubToken)
                .GET()
                .build();

        HttpResponse<String> diffResponse = client.send(diffRequest, HttpResponse.BodyHandlers.ofString());
        String prDiff = diffResponse.body();

        // Truncate diff if it exceeds token safety limits (~4000 chars)
        if (prDiff.length() > 4000) {
            prDiff = prDiff.substring(0, 4000) + "\n...[diff truncated]";
        }

        // 2. Call Groq API with Llama 3
        String groqPayload = """
        {
          "model": "llama3-8b-8192",
          "messages": [
            {
              "role": "system",
              "content": "You are an expert Java code reviewer. Analyze the provided Git diff and provide concise, actionable feedback covering Security, Code Quality, and Bug Risk. Format using Markdown bullet points."
            },
            {
              "role": "user",
              "content": %s
            }
          ]
        }
        """.formatted(escapeJson(prDiff));

        HttpRequest groqRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(groqPayload))
                .build();

        HttpResponse<String> groqResponse = client.send(groqRequest, HttpResponse.BodyHandlers.ofString());
        String aiReview = parseGroqResponse(groqResponse.body());

        // 3. Post Feedback Comment back to GitHub PR
        String commentUrl = "https://api.github.com/repos/" + repository + "/issues/" + prNumber + "/comments";
        String commentPayload = "{\"body\": " + escapeJson("🤖 **CodeReviewer.AI Feedback**\n\n" + aiReview) + "}";

        HttpRequest commentRequest = HttpRequest.newBuilder()
                .uri(URI.create(commentUrl))
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "Bearer " + githubToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(commentPayload))
                .build();

        HttpResponse<String> commentResponse = client.send(commentRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("Comment posted with status code: " + commentResponse.statusCode());
    }

    private static String escapeJson(String input) {
        if (input == null) return "\"\"";
        return "\"" + input.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\n", "\\n")
                           .replace("\r", "\\r")
                           .replace("\t", "\\t") + "\"";
    }

    private static String parseGroqResponse(String responseBody) {
        int contentStart = responseBody.indexOf("\"content\":");
        if (contentStart == -1) return "Unable to generate review output.";
        int start = responseBody.indexOf("\"", contentStart + 10) + 1;
        int end = responseBody.indexOf("\"", start);
        while (end > 0 && responseBody.charAt(end - 1) == '\\') {
            end = responseBody.indexOf("\"", end + 1);
        }
        if (start > 0 && end > start) {
            return responseBody.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");
        }
        return "Review generated successfully.";
    }
}