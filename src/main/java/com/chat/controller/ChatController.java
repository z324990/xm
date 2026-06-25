package com.chat.controller;

import com.chat.dto.ApiResult;
import com.chat.dto.ChatRequest;
import com.chat.dto.ChatResponse;
import com.chat.model.Conversation;
import com.chat.model.Message;
import com.chat.service.AIService;
import com.chat.service.ChatService;
import com.chat.service.WebSearchService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final AIService aiService;
    private final WebSearchService webSearchService;

    public ChatController(ChatService chatService, AIService aiService,
                           WebSearchService webSearchService) {
        this.chatService = chatService;
        this.aiService = aiService;
        this.webSearchService = webSearchService;
    }

    // ====== 对话管理 ======

    @PostMapping("/conversations")
    public ApiResult<?> createConversation(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String title = body.getOrDefault("title", "新对话");
        String model = body.get("model");
        Conversation conv = chatService.createConversation(userId, title, model);
        return ApiResult.success(conversationToMap(conv));
    }

    @GetMapping("/conversations")
    public ApiResult<?> listConversations(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        List<Conversation> conversations = chatService.getUserConversations(userId);
        return ApiResult.success(conversations.stream()
                .map(this::conversationToMap)
                .toList());
    }

    @GetMapping("/conversations/{id}")
    public ApiResult<?> getConversation(@PathVariable Long id) {
        Conversation conv = chatService.getConversation(id);
        List<Message> messages = chatService.getConversationMessages(id);
        return ApiResult.success(Map.of(
                "conversation", conversationToMap(conv),
                "messages", messages.stream().map(msg -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", msg.getId());
                    m.put("role", msg.getRole());
                    m.put("content", msg.getContent());
                    m.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
                    return m;
                }).toList()
        ));
    }

    private java.util.Map<String, Object> conversationToMap(Conversation conv) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", conv.getId());
        map.put("title", conv.getTitle());
        map.put("model", conv.getModel());
        map.put("createdAt", conv.getCreatedAt() != null ? conv.getCreatedAt().toString() : null);
        map.put("updatedAt", conv.getUpdatedAt() != null ? conv.getUpdatedAt().toString() : null);
        return map;
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResult<?> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return ApiResult.success(null);
    }

    @PutMapping("/conversations/{id}/title")
    public ApiResult<?> updateTitle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title != null && !title.isBlank()) {
            chatService.updateConversationTitle(id, title);
        }
        return ApiResult.success(null);
    }

    // ====== 对话消息（非流式·返回完整回复） ======

    @PostMapping("/chat")
    public ApiResult<?> sendMessage(@RequestBody ChatRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        try {
            Long conversationId = request.getConversationId();
            if (conversationId == null) {
                Conversation conv = chatService.createConversation(userId, null, request.getModel());
                conversationId = conv.getId();
            }

            // 保存用户消息
            chatService.saveMessage(conversationId, "user", request.getMessage());

            // 获取历史消息
            List<Message> history = chatService.getConversationMessages(conversationId);

            // 联网搜索，注入搜索结果作为上下文
            List<Message> augmentedHistory = augmentWithSearchResults(history, request.getMessage());

            // 调用 AI（同步获取完整回复）
            String reply = aiService.chat(augmentedHistory, request.getModel());

            // 保存 AI 回复
            chatService.saveMessage(conversationId, "assistant", reply);

            // 自动生成标题
            long userMsgCount = history.stream().filter(m -> "user".equals(m.getRole())).count();
            if (userMsgCount <= 1) {
                String title = request.getMessage().trim();
                if (title.length() > 30) title = title.substring(0, 30) + "...";
                chatService.updateConversationTitle(conversationId, title);
            }

            return ApiResult.success(Map.of(
                    "conversationId", conversationId,
                    "content", reply
            ));

        } catch (Exception e) {
            log.error("发送消息失败", e);
            return ApiResult.error(500, "消息处理失败: " + e.getMessage());
        }
    }

    // ====== SSE 流式聊天（支持联网搜索） ======

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void streamChat(@RequestBody ChatRequest request, HttpSession session,
                           HttpServletResponse response) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            response.setStatus(401);
            return;
        }

        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try {
            Long conversationId = request.getConversationId();
            if (conversationId == null) {
                Conversation conv = chatService.createConversation(userId, null, request.getModel());
                conversationId = conv.getId();
            }

            final Long finalConvId = conversationId;

            // 保存用户消息
            chatService.saveMessage(finalConvId, "user", request.getMessage());

            // 获取历史消息
            List<Message> history = chatService.getConversationMessages(finalConvId);

            // 联网搜索，注入搜索结果作为上下文
            List<Message> augmentedHistory = augmentWithSearchResults(history, request.getMessage());

            PrintWriter writer = response.getWriter();

            // 先发送 meta 事件（包含 conversationId）
            sendSSE(writer, "meta", "{\"conversationId\":" + finalConvId + "}");
            response.flushBuffer();

            // 如果注入了搜索内容，发送提示给前端
            if (augmentedHistory.size() > history.size()) {
                sendSSE(writer, "notice", "🔍 已搜索网络信息，AI 将结合搜索结果回答");
            }

            StringBuilder fullContent = new StringBuilder();

            aiService.streamChat(augmentedHistory, request.getModel(),
                    chunk -> {
                        try {
                            fullContent.append(chunk);
                            sendSSE(writer, "message", chunk);
                        } catch (IOException e) {
                            log.warn("SSE 发送失败", e);
                        }
                    },
                    () -> {
                        try {
                            // 保存 AI 完整回复
                            chatService.saveMessage(finalConvId, "assistant", fullContent.toString());

                            // 如果这是第一条消息，用内容自动生成对话标题
                            List<Message> msgs = chatService.getConversationMessages(finalConvId);
                            if (msgs.stream().filter(m -> "user".equals(m.getRole())).count() <= 1) {
                                String title = generateTitle(request.getMessage());
                                chatService.updateConversationTitle(finalConvId, title);
                                sendSSE(writer, "title", title);
                            }

                            sendSSE(writer, "done", "");
                            writer.flush();
                        } catch (IOException e) {
                            log.warn("SSE 完成事件发送失败", e);
                        }
                    },
                    errorMsg -> {
                        try {
                            sendSSE(writer, "error", errorMsg);
                            sendSSE(writer, "done", "");
                            writer.flush();
                        } catch (IOException e) {
                            log.warn("SSE 错误事件发送失败", e);
                        }
                    });

        } catch (Exception e) {
            log.error("流式聊天处理失败", e);
            try {
                sendSSE(response.getWriter(), "error", "服务器内部错误");
                sendSSE(response.getWriter(), "done", "");
            } catch (IOException ignored) {}
        }
    }

    /**
     * 联网搜索并注入结果到对话上下文中（作为 system 消息前置）
     */
    private List<Message> augmentWithSearchResults(List<Message> history, String userMessage) {
        // 搜索网络
        List<com.chat.model.SearchResult> results = webSearchService.search(userMessage, 5);

        if (results.isEmpty()) {
            return history;
        }

        // 构建搜索上下文文本
        String searchContext = webSearchService.formatResultsAsContext(results, userMessage);

        // 创建一个 system 消息注入到历史最前面
        Message systemMsg = new Message();
        systemMsg.setRole("system");
        systemMsg.setContent(searchContext);

        List<Message> augmented = new java.util.ArrayList<>();
        augmented.add(systemMsg);
        augmented.addAll(history);

        log.info("已注入 {} 条搜索结果到对话上下文", results.size());
        return augmented;
    }

    private void sendSSE(PrintWriter writer, String event, String data) throws IOException {
        writer.write("event: " + event + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();
    }

    private String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) {
            return "新对话";
        }
        String clean = firstMessage.trim();
        if (clean.length() > 30) {
            return clean.substring(0, 30) + "...";
        }
        return clean;
    }
}
