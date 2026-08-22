package com.soc.customer;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.data.annotation.Id;import org.springframework.data.mongodb.core.mapping.Document;import org.springframework.data.mongodb.repository.MongoRepository;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.*;
@SpringBootApplication public class CustomerServiceApplication{public static void main(String[]a){SpringApplication.run(CustomerServiceApplication.class,a);}}
@Document("customers") record Customer(@Id String id,@NotBlank String name,@Email @NotBlank String email,@NotBlank String phone,@NotBlank String address,boolean active){}
interface CustomerRepository extends MongoRepository<Customer,String>{Optional<Customer> findByEmail(String email);}
@RestController @RequestMapping("/customers") class CustomerController{
 private final CustomerRepository repo;CustomerController(CustomerRepository r){repo=r;}
 @GetMapping List<Customer> all(){return repo.findAll();}
 @GetMapping("/{id}") Customer one(@PathVariable String id){return repo.findById(id).orElseThrow(()->new NoSuchElementException("Customer not found"));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Customer create(@Valid @RequestBody Customer c){if(repo.findByEmail(c.email()).isPresent())throw new IllegalArgumentException("Email already exists");return repo.save(new Customer(null,c.name(),c.email(),c.phone(),c.address(),true));}
 @PutMapping("/{id}") Customer update(@PathVariable String id,@Valid @RequestBody Customer c){one(id);return repo.save(new Customer(id,c.name(),c.email(),c.phone(),c.address(),c.active()));}
 @DeleteMapping("/{id}") void delete(@PathVariable String id){repo.deleteById(id);}
 @ExceptionHandler({NoSuchElementException.class,IllegalArgumentException.class}) ResponseEntity<Map<String,String>> error(RuntimeException e){return ResponseEntity.status(e instanceof NoSuchElementException?404:400).body(Map.of("error",e.getMessage()));}
}

