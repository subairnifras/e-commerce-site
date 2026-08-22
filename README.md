# SOC Shop — Spring Boot Microservices E-Commerce

This repository is a complete group project for five members. Every member owns one independently buildable Spring Boot REST API with MongoDB persistence, at least three endpoints, and a separate Docker container. A Spring Cloud API Gateway is the single public API entry point. The customer/admin web client is a separate Nginx container.

## Architecture and member work

| Member | Container / API | Main responsibility | Important endpoints |
|---|---|---|---|
| Member 1 | `product-service` | Product catalogue and inventory | `GET /api/products`, `GET /api/products/{id}`, `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}` |
| Member 2 | `customer-service` | Customer registration and profiles | `GET /api/customers`, `GET /api/customers/{id}`, `POST /api/customers`, `PUT /api/customers/{id}`, `DELETE /api/customers/{id}` |
| Member 3 | `cart-service` | Shopping-cart lifecycle | `GET /api/carts/{customerId}`, `POST /api/carts/{customerId}/items`, `PUT/DELETE /api/carts/{customerId}/items/{productId}` |
| Member 4 | `order-service` | Orders, status, calculations and bill | `POST /api/orders`, `GET /api/orders/{id}`, `PATCH /api/orders/{id}/status`, `GET /api/orders/{id}/bill` |
| Member 5 | `security-service` | API-key creation, validation and revocation | `POST /api/security/validate`, `POST /api/security/keys`, `GET /api/security/clients`, `DELETE /api/security/clients/{id}` |

Additional shared work: `api-gateway`, `client`, `docker-compose.yml`, and `.github/workflows/ci.yml`.

## Security design

- A raw API key is shown only once when it is generated.
- Only its SHA-256 hash is saved in the MongoDB `api_clients` collection.
- The Gateway requires `X-API-KEY`, asks Security Service to validate the hash, and rejects missing/invalid keys.
- Product mutations and API-key administration require an `ADMIN` role.
- Secrets are environment variables. Commit `.env.example`, but never commit `.env`.

For a production system, add TLS, rate limits, audit logs, key rotation, a secrets manager, and user login/JWT. SHA-256 is suitable for high-entropy generated keys; passwords require BCrypt/Argon2 instead.

## Run all containers

Requirements: Docker Desktop with Docker Compose.

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
docker compose logs -f api-gateway
```

Open `http://localhost:3000`. Enter the initial admin key defined by `INITIAL_ADMIN_KEY` in `.env`. All browser calls go through the gateway at `http://localhost:8080`.

Stop without deleting data:

```powershell
docker compose down
```

Delete containers and Mongo volume (erases database data):

```powershell
docker compose down -v
```

## Build one member container separately

Each service is self-contained. Example for Member 1:

```powershell
docker build -t soc-product-service:1.0 .\member1-product-service
docker run --name product-service -p 8081:8081 `
  -e MONGODB_URI="mongodb://host.docker.internal:27017/productdb" `
  soc-product-service:1.0
```

For the other members, change the folder/image/port: Member 2 `8082`, Member 3 `8083`, Member 4 `8084`, Member 5 `8085`. Running through `docker compose` is easier because it creates the internal network and supplies correct service hostnames automatically.

## Quick API test

```powershell
$key = "soc-admin-change-me"
curl.exe -H "X-API-KEY: $key" http://localhost:8080/api/products
curl.exe -X POST http://localhost:8080/api/products `
  -H "X-API-KEY: $key" -H "Content-Type: application/json" `
  -d '{"name":"Laptop","description":"Student laptop","price":185000,"stock":10,"imageUrl":"https://placehold.co/600x400"}'
```

## Bill generation

Create an order with `POST /api/orders`, then request `GET /api/orders/{id}/bill`. The returned printable HTML bill includes line items, subtotal, 8% tax, total, customer and date. The Admin portal has an **Open bill** button and the browser can print/save it as PDF.

## CI workflow

On every push to `main`/`develop` and every pull request, GitHub Actions:

1. checks out the repository;
2. installs Java 17;
3. tests each Spring Boot service in a matrix;
4. builds each service Docker image;
5. validates `docker-compose.yml`.

## GitHub group workflow

Each member extracts their ZIP, creates a branch, commits only their service, and pushes:

```powershell
git checkout -b member1-product-api
git add member1-product-service
git commit -m "Add product microservice"
git push -u origin member1-product-api
```

Then create a Pull Request into `develop`. After CI passes and code review is complete, merge it. The integrator adds gateway/client/Compose files and finally merges `develop` into `main`.

## Notes

- Never expose service ports publicly in production; expose only Gateway and Client.
- Compose exposes only Client (`3000`) and Gateway (`8080`); all member services and MongoDB stay inside the private Compose network.
- MongoDB is one container with a separate logical database per service. No service directly reads another service's database.
