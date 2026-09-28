package com.shop.product.service;

import com.shop.product.entity.Product;
import com.shop.product.repository.ProductRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.shop.product.config.RedisCacheConfig.PRODUCTS_CACHE;

@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private static final long WAIT_SECONDS = 10;  // chờ tối đa 10s để giành khoá (100 người xếp hàng)
    private static final long LEASE_SECONDS = 5;  // giữ khoá tối đa 5s, quá hạn Redis tự nhả -> không treo

    private final ProductRepository repository;
    private final RedissonClient redissonClient;

    public PurchaseService(ProductRepository repository, RedissonClient redissonClient) {
        this.repository = repository;
        this.redissonClient = redissonClient;
    }

    /**
     * Đặt mua 1 sản phẩm, CÓ khoá phân tán lock:product:{id}.
     *
     * KHÔNG dùng @Transactional ở đây: transaction phải commit TRƯỚC khi unlock,
     * nếu không người kế tiếp vẫn đọc được tồn kho cũ.
     */
    @CacheEvict(value = PRODUCTS_CACHE, key = "#productId")   // tồn kho đổi -> xoá cache chi tiết
    public PurchaseResult purchase(Long productId, String buyer) {
        RLock lock = redissonClient.getLock("lock:product:" + productId);
        boolean acquired = false;
        try {
            // 1. Phải giành được khoá mới được làm tiếp
            acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                return PurchaseResult.BUSY;
            }

            // 2. Vùng găng: kiểm tra tồn kho và trừ số lượng (chỉ 1 người tại 1 thời điểm)
            Product p = repository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            if (p.getStock() == null || p.getStock() <= 0) {
                return PurchaseResult.SOLD_OUT;
            }
            p.setStock(p.getStock() - 1);
            repository.save(p);        // commit ngay trong lúc còn giữ khoá
            log.info("[{}] MUA THÀNH CÔNG sản phẩm {} -> tồn kho còn {}", buyer, productId, p.getStock());
            return PurchaseResult.SUCCESS;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PurchaseResult.BUSY;
        } finally {
            // 3. LUÔN nhả khoá trong finally (kể cả khi có exception) -> tránh treo hệ thống.
            //    Kiểm tra isHeldByCurrentThread: tránh lỗi khi khoá đã tự hết hạn lease
            //    hoặc thread này chưa từng giành được khoá.
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** Phiên bản KHÔNG khoá – chỉ để so sánh, sẽ bán vượt tồn kho (overselling). */
    @CacheEvict(value = PRODUCTS_CACHE, key = "#productId")
    public PurchaseResult purchaseWithoutLock(Long productId, String buyer) {
        Product p = repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (p.getStock() == null || p.getStock() <= 0) {
            return PurchaseResult.SOLD_OUT;
        }
        p.setStock(p.getStock() - 1);
        repository.save(p);
        return PurchaseResult.SUCCESS;
    }
}
