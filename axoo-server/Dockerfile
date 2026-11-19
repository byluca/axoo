ARG GO_VERSION=1
FROM golang:${GO_VERSION}-bookworm AS builder

WORKDIR /usr/src/app
COPY go.mod go.sum ./
RUN go mod download && go mod verify
COPY cmd/api ./cmd/api
COPY internal ./internal
COPY pkg ./pkg
RUN go build -v -o /run-app ./cmd/api/


FROM debian:bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /run-app /usr/local/bin/
COPY LICENSE THIRD_PARTY_NOTICES.md /usr/local/share/doc/axoo-server/
CMD ["run-app"]
