package io.github.exday.api.detour;

/** 移動を始める駅・到着の駅が、駅の場所（ACTIVE で種類が「駅」）でないとき、または鉄道で着けないとき投げる（400）。 */
public class InvalidDetourStationException extends RuntimeException {

    public InvalidDetourStationException(String message) {
        super(message);
    }
}
