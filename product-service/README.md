# product-service – Cache chi tiết sản phẩm bằng Redis

- `@Cacheable(value="products", key="#id")`: cache theo `product_id`, key Redis `products::<id>`
- TTL **30 phút** (`app.cache.products-ttl=30m`)
- `@CacheEvict(value="products", key="#id")` khi admin cập nhật / xoá sản phẩm

## Chạy
```bash
docker compose up -d
mvn spring-boot:run
redis-cli FLUSHALL
```

## Kiểm chứng tốc độ
```bash
# Lần 1: Cache MISS -> có SQL, ~200ms
curl -s -o /dev/null -w "%{time_total}s\n" http://localhost:8080/api/products/1
# Lần 2+: Cache HIT -> không SQL, <10ms
curl -s -o /dev/null -w "%{time_total}s\n" http://localhost:8080/api/products/1

redis-cli TTL products::1        # ~1800 giây
```

## Kiểm chứng xoá cache khi admin cập nhật
```bash
curl -X PUT http://localhost:8080/api/products/1 -H "Content-Type: application/json" \
     -d '{"name":"Sản phẩm 1 (bản mới)","imageUrl":"https://cdn.example.com/new.jpg"}'
redis-cli KEYS "products::*"     # products::1 đã biến mất
curl http://localhost:8080/api/products/1   # SQL xuất hiện lại, trả dữ liệu mới
```

> `app.simulate-db-latency-ms=200` giả lập DB chậm khi tải cao (máy local truy vấn theo khoá chính
> thường chỉ vài ms). Đặt `0` để đo tốc độ DB thật.

---

# Flash Sale: khoá phân tán Redisson chống bán vượt tồn kho

- `PurchaseService.purchase()`: `RLock` key `lock:product:{id}`, `tryLock(10s wait, 5s lease)`.
  Giành được khoá mới kiểm tra tồn kho và trừ số lượng. `unlock()` nằm trong `finally`.
- Lease 5s: nếu service chết khi đang giữ khoá, Redis tự xoá khoá sau 5s nên hệ thống không bị treo.

## Thực hành
```bash
# Đặt tồn kho sản phẩm 1 còn 1 món
curl -X PUT localhost:8080/api/products/1 -H "Content-Type: application/json" -d '{"stock":1}'

# 100 người cùng mua (CÓ khoá)
curl -X POST "localhost:8080/api/flash-sale/1/simulate?buyers=100"
```
Kết quả:
```json
{"buyers":100,"stockBefore":1,"success":1,"soldOut":99,"busy":0,"stockAfter":0,"oversold":false,"elapsedMs":...}
```
Console: 1 dòng `Mua hàng thành công`, 99 dòng `Sản phẩm đã hết hàng`.

Thử tồn kho 5: `-d '{"stock":5}'` → `success = 5`, `soldOut = 95`.

## So sánh KHÔNG khoá
```bash
curl -X PUT localhost:8080/api/products/1 -H "Content-Type: application/json" -d '{"stock":1}'
curl -X POST "localhost:8080/api/flash-sale/1/simulate-no-lock?buyers=100"
```
→ `success` > 1, `oversold: true`: nhiều người cùng đọc thấy "còn 1" và đều mua được.

## Một khách đặt mua
```bash
curl -X POST localhost:8080/api/flash-sale/1/buy   # 200 thành công / 409 hết hàng
```
