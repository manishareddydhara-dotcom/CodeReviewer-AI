package com.codereviewer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CodeReviewerBot {

    public static void main(String[] args) throws Exception {
        String githubToken = System.getenv("GITHUB_TOKEN");
        String groqApiKey = System.getenv("GROQ_API_KEY");
        String repository = System.getenv("GITHUB_REPOSITORY"); 
        String prNumber = System.getenv("PR_NUMBER");

        if (githubToken == null || groqApiKey == null || repository == null || prNumber == null) {
            System.err.println("Error: Missing required environment variables.");
            System.exit(1);
        }

        HttpClient client = HttpClient.newHttpClient();

        // 1. Fetch PR Diff from GitHub (User-Agent added)
        String diffUrl = "https://api.github.com/repos/" + repository + "/pulls/" + prNumber;
        HttpRequest diffRequest = HttpRequest.newBuilder()
                .uri(URI.create(diffUrl))
                .header("Accept", "application/vnd.github.v3.diff")
                .header("Authorization", "Bearer " + githubToken)
                .header("User-Agent", "CodeReviewer-Bot")
                .GET()
                .build();

        HttpResponse<String> diffResponse = client.send(diffRequest, HttpResponse.BodyHandlers.ofString());
        String prDiff = diffResponse.body();

        if (prDiff == null || prDiff.isBlank()) {
            prDiff = "No diff available or empty PR contents.";
        } else if (prDiff.length() > 4000) {
            prDiff = prDiff.substring(0, 4000) + "\n...[diff truncated]";
        }

        // 2. Call Groq API with Llama 3.1
        String groqPayload = """
        {
          "model": "llama-3.1-8b-instant",
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
        String aiReview = extractContent(groqResponse.body());

        // 3. Post Feedback Comment back to GitHub PR
        String commentUrl = "https://api.github.com/repos/" + repository + "/issues/" + prNumber + "/comments";
        String commentPayload = "{\"body\": " + escapeJson("🤖 **CodeReviewer.AI Feedback**\n\n" + aiReview) + "}";

        HttpRequest commentRequest = HttpRequest.newBuilder()
                .uri(URI.create(commentUrl))
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "Bearer " + githubToken)
                .header("User-Agent", "CodeReviewer-Bot")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(commentPayload))
                .build();

        HttpResponse<String> commentResponse = client.send(commentRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("Comment posted with status code: " + commentResponse.statusCode());
    }

    private static String escapeJson(String input) {
        if (input == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String extractContent(String json) {
        String key = "\"content\":";
        int index = json.indexOf(key);
        if (index == -1) return "Failed to retrieve response from AI model.";

        int start = json.indexOf("\"", index + key.length()) + 1;
        StringBuilder result = new StringBuilder();
        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') result.append('\n');
                else if (c == 'r') result.append('\r');
                else if (c == 't') result.append('\t');
                else result.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}