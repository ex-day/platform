package io.github.exday.api.project;

import io.github.exday.api.generated.api.S15Api;
import io.github.exday.api.generated.model.OlderTimelinePage;
import io.github.exday.api.generated.model.ProjectDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** S15（企画のページ）のエンドポイントの実装。 */
@RestController
public class ProjectController implements S15Api {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ProjectDetail> getProject(String projectId, Integer timelineLimit) {
        return ResponseEntity.ok(service.find(projectId, timelineLimit)
            .orElseThrow(() -> new ProjectNotFoundException(projectId)));
    }

    @Override
    public ResponseEntity<OlderTimelinePage> getProjectTimeline(String projectId, String before, Integer limit) {
        return ResponseEntity.ok(service.findOlderTimeline(projectId, before, limit)
            .orElseThrow(() -> new ProjectNotFoundException(projectId)));
    }
}
