package io.github.afraalmaci.basketwise.catalog;

import java.util.List;

/** Response shapes for the catalog API. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record ChainDto(String code, String name, String color, int storeCount) {
    }

    public record StoreDto(long id, String chainCode, String chainName, String name,
                           double latitude, double longitude) {
    }

    /**
     * @param unit "ml", "g" or "pc"; unit prices are per liter, per kilogram or per piece
     */
    public record ProductDto(long id, String slug, String name, String category, String unit,
                             List<OfferDto> offers) {
    }

    public record OfferDto(String chainCode, String displayName, String quantityText,
                           long quantityAmount, long priceCents, long unitPriceCents) {
    }
}
