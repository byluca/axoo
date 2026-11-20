# Axoo Server

Axoo’s Go API uses PostgreSQL and Supabase Auth and Storage. Firebase push notifications are optional.

## Run locally

Use the Go version in [go.mod](go.mod) and a Supabase development project.
From `axoo-server/`, apply the [schema](schema.sql) as the database owner on a fresh database:

```sh
psql 'YOUR_DEVELOPMENT_POSTGRES_URL' -v ON_ERROR_STOP=1 -f schema.sql
```

Add cities and university email domains to `cities` and `universities`. Enable
email sign-in in Supabase Auth. For memes and stickers, create a `memes` storage
bucket with public reads and backend-only writes.

```sh
cp .env.example .env
# Set SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, SUPABASE_DB_URL, and EMAIL_SALT.
go run ./cmd/api
```

In another terminal, run `curl http://localhost:8080/health`.
See [.env.example](.env.example) for optional integrations.

## Test

```sh
go test -race ./...
```

For integration tests, use a dedicated local PostgreSQL database and the
environment variables listed in the test files.

## Tools

- [Content Desk](cmd/utility/contentdesk/README.md) for content and activity reports
- [Maintenance scripts](scripts/README.md)
- [API notes](docs/)

## License

Axoo’s original code is licensed under [AGPL-3.0-only](LICENSE).
See the [third-party notices](THIRD_PARTY_NOTICES.md).
If users interact with your modified version over a network, offer them its
corresponding source as required by [Section 13](LICENSE).
