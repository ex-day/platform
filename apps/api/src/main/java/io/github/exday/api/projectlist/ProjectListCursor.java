package io.github.exday.api.projectlist;

import io.github.exday.api.generated.model.ProjectCreatorKind;
import io.github.exday.api.generated.model.ProjectListSort;
import io.github.exday.api.generated.model.ProjectType;
import java.util.Map;

/**
 * 企画の一覧のカーソル（OpenAPI の ProjectListCursor）。並べ方・型・作った人の1文字ずつと、並びのキーの企画の識別子。
 * 例：{@code naa3b99200e12f7}（新しい順・型すべて・作った人すべて・その企画より後）。
 */
record ProjectListCursor(ProjectListSort sort, ProjectType type, ProjectCreatorKind creator, String projectId) {

    private static final Map<ProjectType, Character> TYPES = Map.of(
        ProjectType.COURSE, 'c', ProjectType.RANKING, 'r', ProjectType.COLLECTION, 'l', ProjectType.MEMORY, 'm');
    private static final Map<ProjectCreatorKind, Character> CREATORS = Map.of(
        ProjectCreatorKind.OPERATOR, 'o', ProjectCreatorKind.USER, 'u');

    String encode() {
        return "n" + (type == null ? 'a' : TYPES.get(type)) + (creator == null ? 'a' : CREATORS.get(creator)) + projectId;
    }

    /** 渡された条件で作ったカーソルなら、並びのキーの企画の識別子。違う条件なら例外（400）。 */
    static String keyFor(String cursor, ProjectListSort sort, ProjectType type, ProjectCreatorKind creator) {
        var expected = new ProjectListCursor(sort, type, creator, "").encode();
        if (!cursor.startsWith(expected) || cursor.length() != expected.length() + 12) {
            throw new InvalidProjectListCursorException();
        }
        return cursor.substring(expected.length());
    }
}
