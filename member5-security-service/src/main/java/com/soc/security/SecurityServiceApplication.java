package com.soc.security;
import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.context.annotation.Bean;import org.springframework.data.annotation.Id;import org.springframework.data.mongodb.core.mapping.Document;import org.springframework.data.mongodb.repository.MongoRepository;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.time.*;import java.util.*;
@SpringBootApplication public class SecurityServiceApplication{
 public static void main(String[]a){SpringApplication.run(SecurityServiceApplication.class,a);}
 @Bean CommandLineRunner seed(ApiClientRepository repo,@Value("${app.initial-admin-key}")String key){return a->{if(repo.count()==0)repo.save(new ApiClient(null,"Initial Admin",hash(key),"ADMIN",true,Instant.now()));};}
 static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
@Document("api_clients") record ApiClient(@Id String id,String name,String keyHash,String role,boolean active,Instant createdAt){}
interface ApiClientRepository extends MongoRepository<ApiClient,String>{Optional<ApiClient> findByKeyHashAndActiveTrue(String hash);}
record CreateKeyRequest(String name,String role){} record KeyCreated(String id,String apiKey,String role){} record Validation(boolean valid,String role){}
@RestController @RequestMapping("/security") class SecurityController{
 private final ApiClientRepository repo;SecurityController(ApiClientRepository r){repo=r;}
 @PostMapping("/validate") Validation validate(@RequestHeader(value="X-API-KEY",required=false)String key){if(key==null||key.isBlank())return new Validation(false,null);return repo.findByKeyHashAndActiveTrue(SecurityServiceApplication.hash(key)).map(c->new Validation(true,c.role())).orElse(new Validation(false,null));}
 @GetMapping("/clients") List<Map<String,Object>> clients(){return repo.findAll().stream().map(c->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",c.id());m.put("name",c.name());m.put("role",c.role());m.put("active",c.active());m.put("createdAt",c.createdAt());return m;}).toList();}
 @PostMapping("/keys") @ResponseStatus(HttpStatus.CREATED) KeyCreated create(@RequestBody CreateKeyRequest r){String raw="soc_"+UUID.randomUUID().toString().replace("-","");ApiClient c=repo.save(new ApiClient(null,r.name(),SecurityServiceApplication.hash(raw),r.role()==null?"CUSTOMER":r.role().toUpperCase(),true,Instant.now()));return new KeyCreated(c.id(),raw,c.role());}
 @DeleteMapping("/clients/{id}") void revoke(@PathVariable String id){ApiClient c=repo.findById(id).orElseThrow();repo.save(new ApiClient(c.id(),c.name(),c.keyHash(),c.role(),false,c.createdAt()));}
}
