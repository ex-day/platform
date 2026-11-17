package io.github.exday.api.card.project;

import io.github.exday.api.generated.model.ProjectCard;
import java.util.Locale;

/** 企画カード（C29。OpenAPI の ProjectCard）を作る。どの画面の一覧でも同じ見せ方にする（使う画面はコンポーネント一覧の C29）。 */
public final class ProjectCards {

    private ProjectCards() {}

    /**
     * 企画カードの材料。discoveryPosition・placePosition は、その種類の項目のうちいちばん前の項目の順番（なければ null）。
     */
    public record ProjectRow(String publicId, String title, String type, String creatorKind, String creatorName,
            Integer discoveryPosition, Integer placePosition) {}

    /** どちらの種類の項目を優先して matchedBy にするか。 */
    public enum Prefer { DISCOVERY, PLACE }

    /**
     * 企画カード。prefer の種類の項目があればその種類、なければもう一方を matchedBy にする。
     * 順番・順位は、その種類のいちばん前の項目のもので、コース・番付のときだけ返す。
     */
    public static ProjectCard toCard(ProjectRow row, Prefer prefer) {
        boolean byDiscovery = prefer == Prefer.DISCOVERY
            ? row.discoveryPosition() != null
            : row.placePosition() == null;
        var card = new ProjectCard()
            .id(row.publicId())
            .title(row.title())
            .type(ProjectCard.TypeEnum.fromValue(row.type().toLowerCase(Locale.ROOT)))
            .creatorKind(ProjectCard.CreatorKindEnum.fromValue(row.creatorKind().toLowerCase(Locale.ROOT)))
            .creatorName(row.creatorName())
            .matchedBy(byDiscovery ? ProjectCard.MatchedByEnum.DISCOVERY : ProjectCard.MatchedByEnum.PLACE);
        if ("COURSE".equals(row.type()) || "RANKING".equals(row.type())) {
            card.setPosition(byDiscovery ? row.discoveryPosition() : row.placePosition());
        }
        return card;
    }
}
