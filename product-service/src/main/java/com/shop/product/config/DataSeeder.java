package com.shop.product.config;

import com.shop.product.entity.Product;
import com.shop.product.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.stream.IntStream;

/** Tạo 1.000 sản phẩm mẫu khi DB trống. */
@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seedProducts(ProductRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            repo.saveAll(IntStream.rangeClosed(1, 1000).mapToObj(i -> new Product(
                    "Sản phẩm " + i,
                    "Mô tả chi tiết cho sản phẩm " + i,
                    "https://cdn.example.com/images/product-" + i + ".jpg",
                    BigDecimal.valueOf(10000L + i * 1000L), 100)).toList());
        };
    }
}
