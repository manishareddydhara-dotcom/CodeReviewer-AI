# 🤖 CodeReviewer-AI

An automated, AI-driven code review bot built with **Java 17**, **Groq API (Llama 3.1)**, and **GitHub Actions**. Automatically analyzes pull requests, evaluates code diffs, and posts structured feedback directly onto GitHub PR threads.

---

## 🌟 Key Features

- **Automated CI/CD Trigger:** Listens for `pull_request` events (`opened`, `synchronize`, `reopened`) via GitHub Actions.
- **Dynamic Diff Extraction:** Retrieves raw code diffs using the GitHub REST API and `java.net.http.HttpClient`.
- **Fast LLM Analysis:** Uses Groq's high-throughput `llama-3.1-8b-instant` model to analyze code changes in seconds.
- **Structured Markdown Output:** Delivers actionable feedback categorized into **Security**, **Code Quality**, and **Bug Risk**.

---

## 🛠️ Architecture & Tech Stack

- **Language:** Java 17
- **API Integration:** Groq REST API (OpenAI-compatible schema) & GitHub REST API v3
- **Automation:** GitHub Actions Workflows
- **Security:** GitHub Secrets for `GROQ_API_KEY` and ephemeral `GITHUB_TOKEN`

---

## 🚀 How It Works

1. **PR Created/Updated:** Developer opens or updates a Pull Request.
2. **Workflow Execution:** GitHub Actions compiles and runs `CodeReviewerBot.java`.
3. **Diff Fetching:** The bot fetches the PR diff from GitHub's REST API.
4. **AI Prompt Processing:** Diff payload is formatted and sent to Groq's Llama 3.1 model.
5. **Feedback Posting:** Generated review is posted as a comment on the PR thread.

---

## 💻 Local Setup & Development

### Prerequisites

- Java JDK 17+
- Git
- Groq API Key

### Environment Variables

Configure the following secrets in your repository settings (`Settings -> Secrets and variables -> Actions`):

| Variable       | Description                              |
| :------------- | :--------------------------------------- |
| `GROQ_API_KEY` | Your Groq API key                        |
| `GITHUB_TOKEN` | Automatically supplied by GitHub Actions |

---

## 📸 Demo

![CodeReviewer.AI Output](https://github.com/manishareddydhara-dotcom/CodeReviewer-AI/assets/demo-review.png)
