package com.wangji92.retry.springretryexample.dto;

import java.util.Objects;

/**
 * @author 汪小哥
 * @date 03-04-2021
 */
public class TextMessageDto {
    private String messageId;
    private String messageBody;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }


    /**
     * 有状态 保证 key 唯一 通过  messageId
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextMessageDto that = (TextMessageDto) o;
        return Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }
}
