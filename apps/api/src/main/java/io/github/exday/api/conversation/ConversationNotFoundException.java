package io.github.exday.api.conversation;

/** 会話がない・見られない（非公開）、またはカーソルがその会話のものでない。 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(String conversationId) {
        super("conversation not found: " + conversationId);
    }
}
