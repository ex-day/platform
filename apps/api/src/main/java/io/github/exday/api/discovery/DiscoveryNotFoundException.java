package io.github.exday.api.discovery;

/** 指定した Discovery がない、または一般のユーザーに見えない（非公開・成立の取り消し）とき投げる。 */
public class DiscoveryNotFoundException extends RuntimeException {

    public DiscoveryNotFoundException(String discoveryId) {
        super("Discovery not found: " + discoveryId);
    }
}
