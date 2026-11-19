package io.github.exday.api.top;

/** TOP の起点（{@code from}）の場所がない、または統合の先を決められない（404）。 */
public class TopOriginNotFoundException extends RuntimeException {

    public TopOriginNotFoundException() {
        super("top origin place not found");
    }
}
