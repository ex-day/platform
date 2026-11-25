package io.github.exday.api.detour;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 寄れる場所の続きを読むカーソル（OpenAPI の DetourPlaceCursor）。到達圏のデータの版・計算の決まりの値の要約・条件（from・t・to）・
 * 並びのキー（過ごせる時間・場所の識別子）を、区切り文字でつないで URL で使える Base64 にする。画面は中身を解釈しない。
 *
 * @param build 到達圏のデータの版（reach.build.version）
 * @param settings 計算の決まりの値の要約（{@link DetourProperties#digest()}）
 * @param to 終わり方（{@code to} の値。省いたときは空）
 */
record DetourCursor(String build, String settings, String from, int minutes, String to, int stay, String placeId) {

    private static final String VERSION = "d1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    String encode() {
        String text = String.join("|", VERSION, build, settings, from, Integer.toString(minutes), to,
            Integer.toString(stay), placeId);
        return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 読めない値は {@link InvalidDetourCursorException}（400）。 */
    static DetourCursor decode(String cursor) {
        try {
            String[] parts = new String(DECODER.decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 8 || !VERSION.equals(parts[0])) {
                throw new InvalidDetourCursorException();
            }
            return new DetourCursor(parts[1], parts[2], parts[3], Integer.parseInt(parts[4]), parts[5],
                Integer.parseInt(parts[6]), parts[7]);
        } catch (IllegalArgumentException e) {
            throw new InvalidDetourCursorException();
        }
    }
}
