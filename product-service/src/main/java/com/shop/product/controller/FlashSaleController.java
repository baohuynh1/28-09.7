package com.shop.product.controller;

import com.shop.product.service.PurchaseResult;
import com.shop.product.service.PurchaseService;
import com.shop.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiFunction;

@RestController
@RequestMapping("/api/flash-sale")
public class FlashSaleController {

    private final PurchaseService purchaseService;
    private final ProductService productService;

    public FlashSaleController(PurchaseService purchaseService, ProductService productService) {
        this.purchaseService = purchaseService;
        this.productService = productService;
    }

    /** 1 khách đặt mua: POST /api/flash-sale/1/buy */
    @PostMapping("/{id}/buy")
    public ResponseEntity<Map<String, String>> buy(@PathVariable Long id) {
        PurchaseResult r = purchaseService.purchase(id, "khach-le");
        return ResponseEntity.status(r == PurchaseResult.SUCCESS ? 200 : 409)
                .body(Map.of("result", r.name(), "message", r.getMessage()));
    }

    /** Mô phỏng N người cùng bấm mua (CÓ khoá): POST /api/flash-sale/1/simulate?buyers=100 */
    @PostMapping("/{id}/simulate")
    public Map<String, Object> simulate(@PathVariable Long id,
                                        @RequestParam(defaultValue = "100") int buyers) throws Exception {
        return runFlashSale(id, buyers, purchaseService::purchase);
    }

    /** So sánh KHÔNG khoá: POST /api/flash-sale/1/simulate-no-lock?buyers=100 */
    @PostMapping("/{id}/simulate-no-lock")
    public Map<String, Object> simulateNoLock(@PathVariable Long id,
                                              @RequestParam(defaultValue = "100") int buyers) throws Exception {
        return runFlashSale(id, buyers, purchaseService::purchaseWithoutLock);
    }

    private Map<String, Object> runFlashSale(Long id, int buyers,
                                             BiFunction<Long, String, PurchaseResult> buyFn) throws Exception {
        int stockBefore = productService.getProductById(id).getStock();

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<PurchaseResult>> futures = new ArrayList<>();

        for (int i = 1; i <= buyers; i++) {
            String buyer = "khach-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();                      // tất cả cùng bấm "Mua" một lúc
                PurchaseResult r = buyFn.apply(id, buyer);
                System.out.println(buyer + " : " + r.getMessage());
                return r;
            }));
        }
        ready.await();
        long start = System.currentTimeMillis();
        go.countDown();

        Map<PurchaseResult, Integer> count = new EnumMap<>(PurchaseResult.class);
        for (Future<PurchaseResult> f : futures) count.merge(f.get(), 1, Integer::sum);
        pool.shutdown();

        int stockAfter = productService.getProductById(id).getStock();
        int success = count.getOrDefault(PurchaseResult.SUCCESS, 0);
        return Map.of(
                "buyers", buyers,
                "stockBefore", stockBefore,
                "success", success,
                "soldOut", count.getOrDefault(PurchaseResult.SOLD_OUT, 0),
                "busy", count.getOrDefault(PurchaseResult.BUSY, 0),
                "stockAfter", stockAfter,
                "oversold", success > stockBefore,
                "elapsedMs", System.currentTimeMillis() - start);
    }
}
