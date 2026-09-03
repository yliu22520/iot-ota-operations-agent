package com.yliu22520.iotota.diagnosis;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.ProxyOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.retry.support.RetryTemplate;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Creates the Google transport in one adapter boundary without exposing credentials to the domain. */
final class GoogleGenAiDiagnosticModelClientFactory {

    static final String API_KEY_ENVIRONMENT_VARIABLE = "GEMINI_API_KEY";

    private GoogleGenAiDiagnosticModelClientFactory() {
    }

    static DiagnosticModelClient fromEnvironment(DiagnosticModelConfiguration configuration) {
        return fromApiKey(System.getenv(API_KEY_ENVIRONMENT_VARIABLE), configuration);
    }

    static DiagnosticModelClient fromApiKey(String apiKey, DiagnosticModelConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (apiKey == null || apiKey.isBlank()) {
            return request -> {
                throw new DiagnosticModelUnavailableException(DiagnosticModelUnavailableException.API_KEY_MISSING);
            };
        }

        Client api = null;
        try {
            Client.Builder clientBuilder = Client.builder()
                    .apiKey(apiKey)
                    .httpOptions(httpOptions(configuration));
            ClientOptions clientOptions = clientOptionsFromEnvironment();
            if (clientOptions != null) {
                clientBuilder.clientOptions(clientOptions);
            }
            api = clientBuilder.build();
            var options = GoogleGenAiDiagnosticModelOptions.from(configuration);
            ChatModel chatModel = GoogleGenAiChatModel.builder()
                    .genAiClient(api)
                    .defaultOptions(options)
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build();
            return new SpringAiGoogleGenAiModelClient(chatModel, api);
        } catch (RuntimeException exception) {
            try {
                if (api != null) {
                    api.close();
                }
            } catch (Exception ignored) {
                // Preserve the original configuration failure without leaking credential details.
            }
            throw exception;
        }
    }

    static ClientOptions clientOptionsFromEnvironment() {
        String proxy = environmentValue("HTTPS_PROXY", "ALL_PROXY");
        return proxyClientOptions(proxy);
    }

    static HttpOptions httpOptions(DiagnosticModelConfiguration configuration) {
        return HttpOptions.builder()
                .timeout(Math.toIntExact(configuration.limits().maxDuration().toMillis()))
                // The evaluation runner owns the three-run policy. Do not multiply its budget with SDK retries.
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build();
    }

    static ClientOptions proxyClientOptions(String proxyValue) {
        if (proxyValue == null || proxyValue.isBlank()) {
            return null;
        }

        String normalized = proxyValue.contains("://") ? proxyValue.trim() : "http://" + proxyValue.trim();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Configured Gemini proxy must be a valid HTTP proxy URI", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Configured Gemini proxy must use HTTP or HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Configured Gemini proxy must include a host");
        }
        int port = uri.getPort() < 0 ? 80 : uri.getPort();
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Configured Gemini proxy port is invalid");
        }

        ProxyOptions.Builder proxyBuilder = ProxyOptions.builder()
                .type("HTTP")
                .host(uri.getHost())
                .port(port);
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int separator = userInfo.indexOf(':');
            if (separator <= 0 || separator == userInfo.length() - 1) {
                throw new IllegalArgumentException("Configured Gemini proxy credentials must include a username and password");
            }
            proxyBuilder.username(userInfo.substring(0, separator))
                    .password(userInfo.substring(separator + 1));
        }
        return ClientOptions.builder().proxyOptions(proxyBuilder.build()).build();
    }

    private static String environmentValue(String... names) {
        for (String name : names) {
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null
                        && !entry.getValue().isBlank()) {
                    return entry.getValue().trim();
                }
            }
        }
        return null;
    }
}
