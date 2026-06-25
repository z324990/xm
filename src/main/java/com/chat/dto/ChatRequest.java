package com.chat.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private String model;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
