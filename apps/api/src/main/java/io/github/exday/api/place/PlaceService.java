package io.github.exday.api.place;

import io.github.exday.api.generated.model.PlaceDetail;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * S13 の応答を、1つの読み取りの時点（スナップショット）でそろえて作る（apps/api/AGENTS.md「読み取りの一貫性と公開制御」）。
 */
@Service
public class PlaceService {

    private final PlaceQueries queries;
    private final PlaceAssembler assembler;

    public PlaceService(PlaceQueries queries, PlaceAssembler assembler) {
        this.queries = queries;
        this.assembler = assembler;
    }

    /** ない場所は空。場所は公開状態を持たないので、統合済み・廃止の場所も返す。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<PlaceDetail> find(String placeId) {
        return queries.findPlace(placeId).map(assembler::assemble);
    }
}
