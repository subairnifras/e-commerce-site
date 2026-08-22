package com.soc.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@SpringBootApplication
public class ProductServiceApplication {
 public static void main(String[] args){SpringApplication.run(ProductServiceApplication.class,args);}
}

@Document("products") record Product(@Id String id,@NotBlank String name,@NotBlank String description,@NotNull @Positive BigDecimal price,@Min(0) int stock,String imageUrl,boolean active){}
interface ProductRepository extends MongoRepository<Product,String>{List<Product> findByActiveTrue();}

@RestController @RequestMapping("/products")
class ProductController{
 private final ProductRepository repo; ProductController(ProductRepository repo){this.repo=repo;}
 @GetMapping List<Product> all(){return repo.findByActiveTrue();}
 @GetMapping("/{id}") Product one(@PathVariable String id){return repo.findById(id).orElseThrow(()->new NoSuchElementException("Product not found"));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Product create(@Valid @RequestBody Product p){return repo.save(new Product(null,p.name(),p.description(),p.price(),p.stock(),p.imageUrl(),true));}
 @PutMapping("/{id}") Product update(@PathVariable String id,@Valid @RequestBody Product p){one(id);return repo.save(new Product(id,p.name(),p.description(),p.price(),p.stock(),p.imageUrl(),p.active()));}
 @DeleteMapping("/{id}") void delete(@PathVariable String id){repo.deleteById(id);}
 @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Map<String,String>> notFound(Exception e){return ResponseEntity.status(404).body(Map.of("error",e.getMessage()));}
}

