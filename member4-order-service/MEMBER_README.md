# Member 4 — Order and Billing Service

Own order creation, retrieval, status changes, tax/total calculation and printable bill generation. Main endpoints: `POST /orders`, `GET /orders/{id}`, `PATCH /orders/{id}/status`, `GET /orders/{id}/bill`. Container port: `8084`; database: `orderdb`. Explain how server-side totals prevent a client from choosing the final bill amount.
