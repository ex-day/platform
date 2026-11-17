package io.github.exday.api.place;

/** 指定した場所がないとき投げる。 */
public class PlaceNotFoundException extends RuntimeException {

    public PlaceNotFoundException(String placeId) {
        super("Place not found: " + placeId);
    }
}
