package io.github.exday.api.projectlist;

import io.github.exday.api.generated.api.S17Api;
import io.github.exday.api.generated.model.ProjectCreatorKind;
import io.github.exday.api.generated.model.ProjectListPage;
import io.github.exday.api.generated.model.ProjectListSort;
import io.github.exday.api.generated.model.ProjectType;
import java.beans.PropertyEditorSupport;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

/** S17（企画の一覧）のエンドポイントの実装。 */
@RestController
public class ProjectListController implements S17Api {

    private final ProjectListService service;

    public ProjectListController(ProjectListService service) {
        this.service = service;
    }

    @InitBinder
    void bindEnums(WebDataBinder binder) {
        // 通常の enum 変換は Java の定数名で読むので、OpenAPI の値（course・operator・new 等）だけを受け付ける editor にする
        register(binder, ProjectType.class, ProjectType::fromValue);
        register(binder, ProjectCreatorKind.class, ProjectCreatorKind::fromValue);
        register(binder, ProjectListSort.class, ProjectListSort::fromValue);
    }

    private static <T> void register(WebDataBinder binder, Class<T> type, Function<String, T> fromValue) {
        binder.registerCustomEditor(type, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(fromValue.apply(text));
            }
        });
    }

    @Override
    public ResponseEntity<ProjectListPage> listProjects(ProjectType type, ProjectCreatorKind creator,
            ProjectListSort sort, String after, Integer limit) {
        return ResponseEntity.ok(service.find(type, creator, sort == null ? ProjectListSort.NEW : sort, after,
            limit == null ? 20 : limit));
    }
}
