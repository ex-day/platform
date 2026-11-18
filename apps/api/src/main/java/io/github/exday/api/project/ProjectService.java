package io.github.exday.api.project;

import io.github.exday.api.generated.model.OlderTimelinePage;
import io.github.exday.api.generated.model.ProjectDetail;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S15 の応答を、1つの読み取りの時点（スナップショット）でそろえて作る（apps/api/AGENTS.md「読み取りの一貫性と公開制御」）。
 * 一般のユーザー向けなので、非公開の企画は、ないものとして扱う。
 */
@Service
public class ProjectService {

    private final ProjectQueries queries;
    private final ProjectAssembler assembler;

    public ProjectService(ProjectQueries queries, ProjectAssembler assembler) {
        this.queries = queries;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ProjectDetail> find(String projectId, int timelineLimit) {
        return queries.findProject(projectId).map(project -> assembler.assemble(project, timelineLimit));
    }

    /** 企画がない・見えない、またはカーソルがこの企画のタイムラインのものでなければ空。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<OlderTimelinePage> findOlderTimeline(String projectId, String before, int limit) {
        return queries.findProject(projectId).flatMap(project -> assembler.olderTimeline(project, before, limit));
    }
}
