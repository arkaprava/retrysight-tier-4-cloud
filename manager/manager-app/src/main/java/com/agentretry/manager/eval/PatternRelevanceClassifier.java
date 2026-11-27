/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.eval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Heuristic classifier that flags topic clusters unlikely to be software-engineering work.
 */
@Component
public class PatternRelevanceClassifier {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "to", "for", "of", "in", "on", "with", "at", "by", "from",
            "is", "are", "was", "were", "be", "been", "this", "that", "it", "as", "my", "me", "i",
            "you", "we", "they", "please", "help", "need", "want", "can", "how", "what", "when", "where", "why"
    );

    private static final Set<String> SE_KEYWORDS = Set.of(
            "api", "app", "async", "auth", "await", "backend", "bug", "build", "class", "cli", "code",
            "commit", "compile", "component", "config", "css", "cursor", "database", "db", "debug",
            "deploy", "docker", "endpoint", "error", "exception", "feature", "fix", "frontend", "function",
            "git", "graphql", "hook", "html", "http", "ide", "implement", "import", "java", "javascript",
            "json", "kubernetes", "lint", "log", "merge", "method", "migrate", "migration", "module",
            "npm", "null", "patch", "pipeline", "pr", "python", "react", "refactor", "repo", "request",
            "rest", "retry", "route", "schema", "script", "server", "service", "spring", "sql", "stack",
            "test", "tests", "typescript", "ui", "unit", "variable", "vite", "webpack", "yaml",
            "tsx", "jsx", "gradle", "maven", "kotlin", "rust", "go", "golang", "node",
            "express", "fastapi", "django", "flask", "lambda", "aws", "gcp", "azure", "s3", "redis",
            "postgres", "mysql", "mongodb", "websocket", "grpc", "oauth", "jwt", "token",
            "runtime", "syntax", "formatter", "coverage", "ci", "cd", "github",
            "gitlab", "jira", "ticket", "issue", "branch", "rebase", "cherry", "pick", "conflict",
            "interface", "struct", "enum", "generic", "promise", "callback", "middleware", "controller",
            "repository", "dependency", "inject", "mock", "stub", "fixture", "assert", "spec", "e2e",
            "integration", "regression", "hotfix", "release", "version", "semver", "changelog",
            "dockerfile", "helm", "terraform", "ansible", "nginx", "proxy", "cache", "queue", "kafka",
            "span", "trace", "metric", "observability", "agent", "collector", "ingest", "sdk"
    );

    private static final Set<String> NON_SE_KEYWORDS = Set.of(
            "recipe", "cooking", "meal", "diet", "workout", "fitness", "vacation", "travel", "hotel",
            "flight", "itinerary", "wedding", "birthday", "gift", "shopping", "fashion", "makeup",
            "movie", "film", "song", "music", "lyrics", "poem", "poetry", "story", "novel", "fiction",
            "essay", "homework", "assignment", "worksheet", "exam", "quiz", "algebra", "calculus",
            "geometry", "physics", "chemistry", "biology", "history", "geography", "capitals", "translate",
            "translation", "language", "spanish", "french", "german", "japanese", "korean", "chinese",
            "email", "letter", "cover", "resume", "cv", "interview", "salary", "negotiation", "marketing",
            "sales", "advertisement", "seo", "blog", "instagram", "tiktok", "twitter", "facebook",
            "linkedin", "caption", "hashtag", "medical", "doctor", "symptom", "diagnosis", "therapy",
            "legal", "lawyer", "contract", "lawsuit", "horoscope", "astrology", "joke", "meme", "trivia",
            "fanfic", "roleplay", "dnd", "minecraft", "fortnite", "betting", "gambling", "lottery",
            "crypto", "bitcoin", "stock", "trading", "investment", "portfolio", "politics", "election",
            "debate", "religion", "philosophy", "relationship", "dating", "breakup", "parenting", "pet",
            "dog", "cat", "garden", "plant", "weather", "forecast", "news", "headline", "summarize",
            "summary", "article", "book", "chapter", "write", "rewrite", "proofread", "grammar",
            "chicken", "pasta", "pizza", "sushi", "steak", "salad", "soup", "sandwich", "burger",
            "dinner", "lunch", "breakfast", "brunch", "snack", "dessert", "baking", "bake", "grill",
            "roast", "fry", "boil", "simmer", "marinate", "ingredient", "spice", "sauce", "noodle",
            "rice", "bread", "flour", "oven", "kitchen", "food", "restaurant", "menu", "takeout"
    );

    public record Assessment(double relevanceScore, boolean suspicious, String flagReason) {
        public static Assessment engineering(double score) {
            return new Assessment(score, false, null);
        }

        public static Assessment flagged(double score, String reason) {
            return new Assessment(score, true, reason);
        }
    }

    public Assessment assess(String label, List<String> sampleDescriptions) {
        String corpus = buildCorpus(label, sampleDescriptions);
        if (corpus.isBlank()) {
            return Assessment.flagged(0, "Empty or unknown topic label");
        }

        List<String> tokens = tokenize(corpus);
        if (tokens.isEmpty()) {
            return Assessment.flagged(0.1, "No meaningful tokens in topic cluster");
        }

        int seHits = 0;
        int nonSeHits = 0;
        for (String token : tokens) {
            if (SE_KEYWORDS.contains(token)) {
                seHits++;
            }
            if (NON_SE_KEYWORDS.contains(token)) {
                nonSeHits++;
            }
        }

        double score = 0.45;
        score += Math.min(0.45, seHits * 0.08);
        score -= Math.min(0.55, nonSeHits * 0.14);

        if (seHits == 0 && nonSeHits >= 1) {
            return Assessment.flagged(clamp(score), "Non-engineering topic keywords detected");
        }
        if (seHits == 0 && tokens.size() <= 3 && nonSeHits == 0) {
            return Assessment.flagged(clamp(score), "No software engineering keywords detected");
        }
        if (nonSeHits >= 2 && seHits <= nonSeHits) {
            return Assessment.flagged(clamp(score), "Topic appears unrelated to software engineering");
        }
        if (score < 0.35) {
            return Assessment.flagged(clamp(score), "Low software engineering relevance score");
        }

        return Assessment.engineering(clamp(score));
    }

    private static String buildCorpus(String label, List<String> sampleDescriptions) {
        StringBuilder builder = new StringBuilder();
        if (label != null && !label.isBlank()) {
            builder.append(label).append(' ');
        }
        if (sampleDescriptions != null) {
            for (String description : sampleDescriptions) {
                if (description != null && !description.isBlank()) {
                    builder.append(description).append(' ');
                }
            }
        }
        return builder.toString().trim();
    }

    private static List<String> tokenize(String text) {
        String[] raw = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String word : raw) {
            if (word.length() < 2 || STOP_WORDS.contains(word)) {
                continue;
            }
            tokens.add(word);
        }
        return tokens;
    }

    private static double clamp(double score) {
        return Math.max(0, Math.min(1, score));
    }
}
