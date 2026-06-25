package com.chat.service;

import com.chat.model.Conversation;
import com.chat.model.Message;
import com.chat.repository.ConversationRepository;
import com.chat.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public Conversation createConversation(Long userId, String title, String model) {
        if (title == null || title.isBlank()) {
            title = "新对话";
        }
        Conversation conversation = new Conversation(title, userId, model);
        return conversationRepository.save(conversation);
    }

    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public Conversation getConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("对话不存在"));
    }

    public List<Message> getConversationMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public Message saveMessage(Long conversationId, String role, String content) {
        Message message = new Message(conversationId, role, content);
        Message saved = messageRepository.save(message);

        // 更新对话的更新时间
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setUpdatedAt(java.time.LocalDateTime.now());
            conversationRepository.save(conv);
        });

        return saved;
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    public void updateConversationTitle(Long conversationId, String title) {
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setTitle(title);
            conversationRepository.save(conv);
        });
    }
}
