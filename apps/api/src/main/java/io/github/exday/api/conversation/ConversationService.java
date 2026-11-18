package io.github.exday.api.conversation;

import io.github.exday.api.generated.model.ConversationDetail;
import io.github.exday.api.generated.model.FindingPage;
import io.github.exday.api.generated.model.OlderPostPage;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S14 の応答を、1つの読み取りの時点（スナップショット）でそろえて作る（apps/api/AGENTS.md「読み取りの一貫性と公開制御」）。
 * 一般のユーザー向けなので、非公開の会話は、ないものとして扱う。
 */
@Service
public class ConversationService {

    private final ConversationQueries queries;
    private final ConversationAssembler assembler;

    public ConversationService(ConversationQueries queries, ConversationAssembler assembler) {
        this.queries = queries;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ConversationDetail> find(String conversationId, int postLimit, int findingLimit) {
        return queries.findConversation(conversationId)
            .map(conversation -> assembler.assemble(conversation, postLimit, findingLimit));
    }

    /** 会話がない・見えない、またはカーソルがこの会話の Post でなければ空。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<OlderPostPage> findOlderPosts(String conversationId, String before, int limit) {
        return queries.findConversation(conversationId)
            .flatMap(conversation -> assembler.olderPosts(conversation.id(), before, limit));
    }

    /** 会話がない・見えない、またはカーソルがこの会話のわかってきたことでなければ空。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<FindingPage> findFindings(String conversationId, String after, int limit) {
        return queries.findConversation(conversationId)
            .flatMap(conversation -> queries.findFindingKey(conversation.id(), after)
                .map(key -> assembler.findings(conversation.id(), key, limit)));
    }
}
