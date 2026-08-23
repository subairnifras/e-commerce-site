# Member 3 — Cart Service

Own cart retrieval, item addition, quantity change, removal and automatic total calculation. Main endpoints: `GET /carts/{customerId}`, `POST /carts/{customerId}/items`, `PUT /carts/{customerId}/items/{productId}`, `DELETE /carts/{customerId}/items/{productId}`. Container port: `8083`; database: `cartdb`.
