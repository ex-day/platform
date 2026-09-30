package io.github.exday.api.discovery;

/** 指定した Discovery がない、または公開状態が HIDDEN のとき投げる。 */
public class DiscoveryNotFoundException extends RuntimeException {

    public DiscoveryNotFoundException(String discoveryId) {
        super("Discovery not found: " + discoveryId);
    }
}
