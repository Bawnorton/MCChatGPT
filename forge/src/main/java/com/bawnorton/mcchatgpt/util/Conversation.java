package com.bawnorton.mcchatgpt.util;

import com.theokanning.openai.completion.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private final List<ChatMessage> messageList;
    private ChatMessage previewMessage;
    private int contextIndex;

    public Conversation() {
        this.messageList = new ArrayList<>();
        this.contextIndex = -1;
    }

    public void addMessage(ChatMessage message) {
        messageList.add(message);
    }

    public void setPreviewMessage(ChatMessage message) {
        previewMessage = message;
    }

    public ChatMessage getPreviewMessage() {
        return previewMessage;
    }

    public List<ChatMessage> getMessages() {
        return messageList;
    }

    public void resetContext() {
        if(contextIndex != -1) messageList.remove(contextIndex);
        contextIndex = -1;
    }

    public void setContext(ChatMessage message) {
        resetContext();
        messageList.add(message);
        contextIndex = messageList.size() - 1;
    }

    public int messageCount() {
        return messageList.size();
    }

    public void removeMessage(int i) {
        messageList.remove(i);
    }
}
