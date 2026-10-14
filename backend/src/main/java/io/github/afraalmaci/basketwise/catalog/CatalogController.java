package io.github.afraalmaci.basketwise.catalog;

import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ChainDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.ProductDto;
import io.github.afraalmaci.basketwise.catalog.CatalogDtos.StoreDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_LIMIT = 25;

    private final CatalogRepository repository;

    public CatalogController(CatalogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/chains")
    public List<ChainDto> chains() {
        return repository.findChains();
    }

    @GetMapping("/stores")
    public List<StoreDto> stores() {
        return repository.findStores();
    }

    /**
     * Product search. Queries shorter than two characters return an empty list rather
     * than an error, so a search box can call this on every keystroke.
     */
    @GetMapping("/products/search")
    public List<ProductDto> searchProducts(@RequestParam(name = "q", defaultValue = "") String query,
                                           @RequestParam(name = "limit", defaultValue = "10") int limit) {
        String trimmed = query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return repository.searchProducts(trimmed, Math.clamp(limit, 1, MAX_LIMIT));
    }
}
