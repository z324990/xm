package com.chat.dto;

public class ChatResponse {
    private String type;
    private String content;
    private Long conversationId;
    private Long messageId;

    public ChatResponse() {}

    public ChatResponse(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
}
