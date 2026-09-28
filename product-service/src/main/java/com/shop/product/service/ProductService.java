package com.shop.product.service;

import com.shop.product.entity.Product;
import com.shop.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.shop.product.config.RedisCacheConfig.PRODUCTS_CACHE;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;
    private final long simulatedDbLatencyMs;

    public ProductService(ProductRepository repository,
                          @Value("${app.simulate-db-latency-ms:0}") long simulatedDbLatencyMs) {
        this.repository = repository;
        this.simulatedDbLatencyMs = simulatedDbLatencyMs;
    }

    /**
     * Lấy chi tiết sản phẩm theo product_id.
     * - Có key "products::<id>" trong Redis -> trả về ngay, không chạy hàm (không SQL).
     * - Chưa có -> đọc DB rồi lưu vào Redis, TTL 30 phút.
     */
    @Cacheable(value = PRODUCTS_CACHE, key = "#id")
    public Product getProductById(Long id) {
        log.info(">>> Cache MISS - đọc DATABASE product_id = {}", id);
        simulateDbLatency();
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Quản trị viên cập nhật sản phẩm -> xoá cache "products::<id>".
     * Lần xem tiếp theo sẽ đọc DB và nạp lại thông tin mới vào Redis.
     */
    @CacheEvict(value = PRODUCTS_CACHE, key = "#id")
    public Product updateProduct(Long id, Product input) {
        Product p = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        if (input.getName() != null)        p.setName(input.getName());
        if (input.getDescription() != null) p.setDescription(input.getDescription());
        if (input.getImageUrl() != null)    p.setImageUrl(input.getImageUrl());
        if (input.getPrice() != null)       p.setPrice(input.getPrice());
        if (input.getStock() != null)       p.setStock(input.getStock());
        Product saved = repository.save(p);
        log.info(">>> Admin cập nhật product_id = {} -> XOÁ cache products::{}", id, id);
        return saved;
    }

    /** Xoá sản phẩm -> xoá luôn cache. */
    @CacheEvict(value = PRODUCTS_CACHE, key = "#id")
    public void deleteProduct(Long id) {
        repository.deleteById(id);
    }

    public Product create(Product p) {
        return repository.save(p);
    }

    private void simulateDbLatency() {
        if (simulatedDbLatencyMs <= 0) return;
        try {
            Thread.sleep(simulatedDbLatencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
