# Member 5 — Security and Admin Service

Own API-key hashing, MongoDB storage, validation, key generation and revocation. Main endpoints: `POST /security/validate`, `POST /security/keys`, `GET /security/clients`, `DELETE /security/clients/{id}`. Container port: `8085`; database: `securitydb`. Raw keys are returned once; only SHA-256 hashes are stored. Admin operations are protected at the API Gateway.
