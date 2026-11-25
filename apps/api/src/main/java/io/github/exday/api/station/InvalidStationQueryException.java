package io.github.exday.api.station;

/** 駅の名前での検索の文字が、前後の空白を除くと空のとき投げる。 */
public class InvalidStationQueryException extends RuntimeException {

    public InvalidStationQueryException() {
        super("Station query is blank.");
    }
}
