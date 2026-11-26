package io.github.exday.api.detour;

/** 使っている到達圏のデータ（ACTIVE の版）がないとき投げる（503。最初の版を作る前など）。 */
public class DetourDataUnavailableException extends RuntimeException {

    public DetourDataUnavailableException() {
        super("到達圏のデータの版（ACTIVE）がない");
    }
}
