package io.github.exday.api.detour;

import io.github.exday.api.ApiProblem;

/**
 * 移動を始める駅・到着の駅が、ない（404）、駅の場所（ACTIVE で種類が「駅」）でない、使っている到達圏のデータ（版）に
 * 入っていない、または鉄道で着けないとき（400）に投げる。どれかと HTTP の状態は {@link #problem()} で決まる（画面が直す駅を出せるように）。
 */
public class InvalidDetourStationException extends RuntimeException {

    private final ApiProblem problem;

    public InvalidDetourStationException(ApiProblem problem) {
        super(problem.code());
        this.problem = problem;
    }

    public ApiProblem problem() {
        return problem;
    }
}
