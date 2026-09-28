package com.shop.product.controller;

import com.shop.product.entity.Product;
import com.shop.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** Trang chi tiết sản phẩm (public). */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        long start = System.nanoTime();
        Product p = service.getProductById(id);
        String ms = String.format("%.2f", (System.nanoTime() - start) / 1_000_000.0);
        log.info("GET /api/products/{} -> {} ms", id, ms);
        return ResponseEntity.ok().header("X-Response-Time-ms", ms).body(p);
    }

    @PostMapping
    public Product create(@RequestBody Product p) {
        return service.create(p);
    }

    /** Quản trị viên cập nhật sản phẩm (tên, mô tả, ảnh, giá). */
    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @RequestBody Product p) {
        return service.updateProduct(id, p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
