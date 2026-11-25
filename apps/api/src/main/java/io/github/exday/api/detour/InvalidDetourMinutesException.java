package io.github.exday.api.detour;

/**
 * 使える時間が15分刻みでないとき投げる（400）。範囲（30〜480分）は生成した interface の検証で弾くが、
 * OpenAPI の {@code multipleOf} は生成したコードで検証されないので、ここで確かめる。
 */
public class InvalidDetourMinutesException extends RuntimeException {

    public InvalidDetourMinutesException() {
        super("Detour minutes must be a multiple of 15.");
    }
}
