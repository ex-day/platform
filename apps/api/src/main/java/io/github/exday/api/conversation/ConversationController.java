package io.github.exday.api.conversation;

import io.github.exday.api.generated.api.S14Api;
import io.github.exday.api.generated.model.ConversationDetail;
import io.github.exday.api.generated.model.FindingPage;
import io.github.exday.api.generated.model.OlderPostPage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** S14（会話のページ）のエンドポイントの実装。 */
@RestController
public class ConversationController implements S14Api {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ConversationDetail> getConversation(String conversationId, Integer postLimit,
            Integer findingLimit) {
        return ResponseEntity.ok(service.find(conversationId, postLimit, findingLimit)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId)));
    }

    @Override
    public ResponseEntity<OlderPostPage> getConversationPosts(String conversationId, String before, Integer limit) {
        return ResponseEntity.ok(service.findOlderPosts(conversationId, before, limit)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId)));
    }

    @Override
    public ResponseEntity<FindingPage> getConversationFindings(String conversationId, String after, Integer limit) {
        return ResponseEntity.ok(service.findFindings(conversationId, after, limit)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId)));
    }
}
