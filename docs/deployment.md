# Docker deployment

## Host and credentials

1. Install Docker with Compose. Check out both repositories as siblings or set `FRONTEND_CONTEXT` to the frontend checkout.
2. Copy `.env.deploy.example` to `.env.deploy`. Set a unique `POSTGRES_PASSWORD` and a separate `JWT_SECRET` of at least 32 UTF-8 bytes. Keep the populated file private and outside Git.
3. Route the deployment hostname through an HTTPS reverse proxy on the same host. Forward the entire site to `http://127.0.0.1:4200` or the configured `FRONTEND_PORT`, preserving the public request host.

## First startup

| Mode | Required setup |
| --- | --- |
| Academic demo | Enable `DEMO_DATA_ENABLED` and `DEMO_SCENARIOS_ENABLED`, set `DEMO_ACCOUNT_PASSWORD`, and limit access to demo participants. The seeded accounts share this initial password, including the global administrator. Changing the variable later does not reset existing passwords. |
| Clean database | Set `DEMO_DATA_ENABLED=false` and provide `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` on a fresh volume. Use a password of at least 12 characters and remove it from the environment file after checking sign-in. |

The deployment profile disables the test clock. Start the stack from the backend checkout:

```sh
docker compose --env-file .env.deploy -f docker-compose.yml -f docker-compose.deploy.yml up -d --build --wait
```

Through the deployed hostname, open the sign-in page and check `/api/v1/actuator/health`. In demo mode, also open sales history and the collection desk to confirm the fixtures loaded.

## Updates

The default deployment project is `proximity-credit-deploy`. Back up its PostgreSQL volume before updating and keep the same project name and credentials when restarting. Flyway's baseline targets new databases. Test any migration against a copy before deploying over an existing schema.

The PostgreSQL 18 image uses `postgres18_data`, mounted at `/var/lib/postgresql`. An existing PostgreSQL 17 volume is preserved separately. Before switching an existing deployment, take a logical backup with `pg_dump` from PostgreSQL 17, restore it into PostgreSQL 18 before starting the backend, and verify the restored data. A fresh PostgreSQL 18 volume starts with the configured initial data.
