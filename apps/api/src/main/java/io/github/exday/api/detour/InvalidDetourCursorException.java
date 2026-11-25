package io.github.exday.api.detour;

/** 寄れる場所のカーソルが読めない、条件が違う、または到達圏のデータの版・計算の決まりの値が切り替わったあとのものとき投げる（400）。 */
public class InvalidDetourCursorException extends RuntimeException {

    public InvalidDetourCursorException() {
        super("Invalid detour cursor.");
    }
}
