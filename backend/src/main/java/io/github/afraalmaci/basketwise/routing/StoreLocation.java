package io.github.afraalmaci.basketwise.routing;

/** What the routing module needs to know about a store. */
public record StoreLocation(long id, String chainCode, String name, GeoPoint location) {
}
