package com.soc.cart;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.data.annotation.Id;import org.springframework.data.mongodb.core.mapping.Document;import org.springframework.data.mongodb.repository.MongoRepository;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.math.BigDecimal;import java.util.*;
@SpringBootApplication public class CartServiceApplication{public static void main(String[]a){SpringApplication.run(CartServiceApplication.class,a);}}
record CartItem(@NotBlank String productId,@NotBlank String productName,@Min(1) int quantity,@NotNull @Positive BigDecimal unitPrice){}
@Document("carts") record Cart(@Id String id,String customerId,List<CartItem> items,BigDecimal total){}
interface CartRepository extends MongoRepository<Cart,String>{Optional<Cart> findByCustomerId(String customerId);}
@RestController @RequestMapping("/carts") class CartController{
 private final CartRepository repo;CartController(CartRepository r){repo=r;}
 @GetMapping("/{customerId}") Cart get(@PathVariable String customerId){return repo.findByCustomerId(customerId).orElse(new Cart(null,customerId,new ArrayList<>(),BigDecimal.ZERO));}
 @PostMapping("/{customerId}/items") Cart add(@PathVariable String customerId,@Valid @RequestBody CartItem item){Cart c=get(customerId);List<CartItem> items=new ArrayList<>(c.items());items.add(item);return save(c.id(),customerId,items);}
 @PutMapping("/{customerId}/items/{productId}") Cart quantity(@PathVariable String customerId,@PathVariable String productId,@RequestParam @Min(1) int quantity){Cart c=get(customerId);List<CartItem> items=c.items().stream().map(i->i.productId().equals(productId)?new CartItem(i.productId(),i.productName(),quantity,i.unitPrice()):i).toList();return save(c.id(),customerId,items);}
 @DeleteMapping("/{customerId}/items/{productId}") Cart remove(@PathVariable String customerId,@PathVariable String productId){Cart c=get(customerId);return save(c.id(),customerId,c.items().stream().filter(i->!i.productId().equals(productId)).toList());}
 @DeleteMapping("/{customerId}") void clear(@PathVariable String customerId){repo.findByCustomerId(customerId).ifPresent(repo::delete);}
 private Cart save(String id,String customerId,List<CartItem> items){BigDecimal t=items.stream().map(i->i.unitPrice().multiply(BigDecimal.valueOf(i.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);return repo.save(new Cart(id,customerId,items,t));}
}
