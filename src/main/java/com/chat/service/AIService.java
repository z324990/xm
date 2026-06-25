package com.chat.service;

import com.chat.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.model:gpt-3.5-turbo}")
    private String defaultModel;

    // 国内可用的备选 API 地址（自动兜底）
    private static final String[] FALLBACK_URLS = {
            "https://api.deepseek.com/chat/completions",          // DeepSeek
            "https://api.siliconflow.cn/v1/chat/completions",    // SiliconFlow
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" // 通义千问
    };

    private final OkHttpClient client;

    public AIService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)    // 连接超时 6s
                .readTimeout(90, TimeUnit.SECONDS)      // 读取超时 90s
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 构建 AI 请求消息体（兼容 OpenAI API 格式）
     */
    public String buildRequestBody(List<Message> messages, String model) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"model\": \"").append(escapeJson(model != null ? model : defaultModel)).append("\",\n");
        json.append("  \"stream\": true,\n");
        json.append("  \"messages\": [\n");

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            json.append("    {\"role\": \"")
                    .append(escapeJson(msg.getRole()))
                    .append("\", \"content\": \"")
                    .append(escapeJson(msg.getContent()))
                    .append("\"}");
            if (i < messages.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * 调用 AI API 并同步流式获取返回（同步阻塞，适用于 SSE 场景）
     */
    public void streamChat(List<Message> messages, String model,
                           Consumer<String> onChunk,
                           Runnable onComplete,
                           Consumer<String> onError) {

        // 未配置 API Key 时使用模拟回复
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI API Key 未配置，使用模拟回复模式");
            mockStreamReply(messages, onChunk, onComplete, onError);
            return;
        }

        String host = extractHost(apiUrl);
        log.info("AI API 配置: host={}, model={}", host, model != null ? model : defaultModel);

        boolean success = tryStreamChat(apiUrl, apiKey, messages, model, onChunk, onComplete, onError);

        if (success) return;

        log.info("配置的 API 不可达，尝试备选地址...");
        for (String fallbackUrl : FALLBACK_URLS) {
            if (fallbackUrl.equals(apiUrl)) continue;
            log.info("尝试备选: {}", fallbackUrl);
            boolean fallbackSuccess = tryStreamChat(fallbackUrl, apiKey, messages, model, onChunk, onComplete, onError);
            if (fallbackSuccess) return;
        }

        log.error("所有 AI API 均不可达");
        onError.accept("无法连接到任何 AI API 服务。请检查网络连接或配置正确的 API 地址和 Key。"
                + "\n当前配置: " + apiUrl
                + "\n常见国内可用 API: https://api.deepseek.com（DeepSeek）、https://api.siliconflow.cn（SiliconFlow）");
    }

    /**
     * 非流式调用：返回完整的 AI 回复文本（同步阻塞）
     */
    public String chat(List<Message> messages, String model) {
        // 未配置 API Key 时返回模拟回复
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI API Key 未配置，返回模拟回复");
            if (messages == null || messages.isEmpty() || messages.stream().noneMatch(m -> "user".equals(m.getRole()))) {
                return "你好！我是 AI 聊天助手。";
            }
            return "这是一个模拟回复。请在 application.yml 中配置 AI API Key。";
        }

        StringBuilder fullResponse = new StringBuilder();
        final boolean[] succeeded = {false};
        final String[] errorMsg = {null};
        Object lock = new Object();

        streamChat(messages, model,
            chunk -> {
                synchronized (lock) {
                    fullResponse.append(chunk);
                }
            },
            () -> {
                synchronized (lock) {
                    succeeded[0] = true;
                    lock.notifyAll();
                }
            },
            err -> {
                synchronized (lock) {
                    errorMsg[0] = err;
                    succeeded[0] = true; // mark as done even on error
                    lock.notifyAll();
                }
            }
        );

        // Wait for completion (streamChat is already synchronous now, so this is just a safety)
        if (fullResponse.length() > 0) {
            return fullResponse.toString();
        }
        if (errorMsg[0] != null) {
            throw new RuntimeException(errorMsg[0]);
        }
        return "";
    }

    /**
     * 尝试用指定 URL 发起一次流式聊天请求
     * @return true 如果请求成功（或部分成功），false 如果完全不可达
     */
    private boolean tryStreamChat(String url, String key,
                                   List<Message> messages, String model,
                                   Consumer<String> onChunk,
                                   Runnable onComplete,
                                   Consumer<String> onError) {
        String requestBody = buildRequestBody(messages, model);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Connection", "keep-alive")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "未知错误";
                log.warn("AI API {} 返回错误: {} {}", url, response.code(), errorBody);
                // 4xx 错误（如认证失败）不再重试
                if (response.code() >= 400 && response.code() < 500) {
                    String msg = "API 认证失败 (" + response.code() + "): " + errorBody;
                    if (response.code() == 401 || response.code() == 403) {
                        msg = "API Key 无效或未授权，请检查 application.yml 中的 ai.api.key 配置。";
                    }
                    onError.accept(msg);
                    return true; // 这是"成功失败"，不需要重试
                }
                return false; // 5xx 可以重试
            }

            // 成功获取响应，开始流式读取
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                boolean hasContent = false;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        String content = parseDeltaContent(data);
                        if (content != null && !content.isEmpty()) {
                            hasContent = true;
                            onChunk.accept(content);
                        }
                    }
                }

                if (!hasContent) {
                    log.warn("AI 返回了空内容（URL: {}）", url);
                }
                onComplete.run();
                return true;
            }
        } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
            log.warn("连接 {} 超时/拒绝: {}", url, e.getMessage());
            return false; // 网络不可达，可以重试其他地址
        } catch (java.net.UnknownHostException e) {
            log.warn("DNS 解析失败 {}: {}", url, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("AI API 调用异常 ({}): {}", url, e.getMessage());
            onError.accept("AI 服务调用异常: " + e.getMessage());
            return true;
        }
    }

    /**
     * 从 URL 提取主机名
     */
    private String extractHost(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 模拟回复（API Key 未配置或全部 API 不可用时使用）
     */
    private void mockStreamReply(List<Message> messages,
                                  Consumer<String> onChunk,
                                  Runnable onComplete,
                                  Consumer<String> onError) {
        try {
            String reply;
            if (messages == null || messages.isEmpty() || messages.stream().noneMatch(m -> "user".equals(m.getRole()))) {
                reply = "你好！我是 AI 聊天助手。";
            } else {
                // 取用户最后一条消息，做个简单回复
                String lastUserMsg = "";
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if ("user".equals(messages.get(i).getRole())) {
                        lastUserMsg = messages.get(i).getContent();
                        break;
                    }
                }
                if (lastUserMsg.length() > 30) {
                    lastUserMsg = lastUserMsg.substring(0, 30) + "...";
                }
                reply = "你刚才说: \"" + lastUserMsg + "\"\n\n"
                      + "---\n"
                      + "⚠️ **这是模拟回复**\n\n"
                      + "要获得真实的 AI 回复，请在 `application.yml` 中配置：\n\n"
                      + "```yaml\n"
                      + "ai:\n"
                      + "  api:\n"
                      + "    url: https://api.deepseek.com/chat/completions  # 你的 API 地址\n"
                      + "    key: sk-your-key-here                           # 你的 API Key\n"
                      + "    model: deepseek-chat                            # 模型名称\n"
                      + "```";
            }

            // 模拟流式输出：逐字发送
            for (int i = 0; i < reply.length(); i++) {
                onChunk.accept(String.valueOf(reply.charAt(i)));
                Thread.sleep(12);
            }
            onComplete.run();
        } catch (Exception e) {
            onError.accept("模拟回复失败: " + e.getMessage());
        }
    }

    /**
     * 从 OpenAI 流式响应的 JSON chunk 中提取 delta.content 字段
     * 格式: {"choices":[{"delta":{"content":"Hello"},"index":0}]}
     */
    private String parseDeltaContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull()) {
                        return content.asText();
                    }
                }
            }
        } catch (Exception e) {
            log.trace("解析流数据JSON失败: {}", data);
        }
        return "";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
