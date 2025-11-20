# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is a collection of Eclipse Dataspace Components (EDC) connector extensions and utilities for building data space connectors. The repository contains:

1. **Java connector extensions** (in `connector/`) that extend EDC functionality
2. **Python SDK** (`edcpy/`) for interacting with EDC connectors
3. **Mock backend API** (`mock-backend/`) demonstrating OpenAPI-based data exposure
4. **Development configurations and examples** (`dev-config/`, `example/`)

## Build and Development Commands

### Building the Connector

The project uses Gradle with conditional compilation for different features:

```bash
# Build with default settings (no SSI, no SQL, no Catalog)
cd connector && gradle build

# Build with SSI (Self-Sovereign Identity) extension
cd connector && ORG_GRADLE_PROJECT_useSSI=true gradle build

# Build with SQL storage backend
cd connector && ORG_GRADLE_PROJECT_useSQLStore=true gradle build

# Build with Federated Catalog enabled
cd connector && ORG_GRADLE_PROJECT_enableFederatedCatalog=true gradle build
```

### Using Taskfile

The project uses [Taskfile](https://taskfile.dev/) for common operations:

```bash
# Build connector with features (CLEAN, SSI, SQL, CATALOG vars)
task build-connector SSI=true SQL=true

# Start full development environment
task dev-up

# Start development with SSI extension enabled
task dev-up SSI=true

# Stop development environment
task dev-down

# Clean all artifacts
task clean

# Run example scripts
task run-example-pull
task run-example-push
task run-example-parallel

# Provision wallets for SSI (requires dev environment running)
task dev-provision-wallets
```

### Python SDK (edcpy)

```bash
# Install dependencies
cd edcpy && poetry install

# Run tests
cd edcpy && poetry run pytest

# Build package
cd edcpy && poetry build
```

## Architecture

### Connector Extensions

The connector is built as a multi-module Gradle project with three main extensions:

1. **openapi-connector**: Core extension that reads an OpenAPI schema and auto-generates EDC assets, policies, and contract definitions
2. **iam**: Self-Sovereign Identity extension implementing W3C Verifiable Credentials authentication
3. **federated-catalog**: Extension for federated catalog functionality across multiple connectors

#### Key Extension Classes

**OpenAPICoreExtension** (`connector/openapi-connector/src/main/java/eu/datacellar/connector/OpenAPICoreExtension.java`)
- Main extension entry point
- Reads OpenAPI schema from configured URL (`dataspace.openapi.url`)
- Creates EDC assets for each OpenAPI operation (endpoint + HTTP method)
- Generates policies and contract definitions automatically
- Registers HTTP params decorators for contract details and backend authentication

**VCIdentityExtension** (`connector/iam/src/main/java/eu/datacellar/iam/VCIdentityExtension.java`)
- Implements W3C Verifiable Credentials-based authentication
- Integrates with Walt.ID wallet for credential management
- Provides custom IdentityService implementation

**PolicyBuilder** (`connector/openapi-connector/src/main/java/eu/datacellar/connector/PolicyBuilder.java`)
- Builds EDC policies from OpenAPI presentation definitions
- Supports authorization constraints and credential constraints
- Handles the `x-connector-presentation-definition` OpenAPI extension

### Configuration Parameters

All custom configuration parameters use the `dataspace.*` prefix (not `eu.datacellar.*` which was the old project-specific naming):

**OpenAPI Extension:**
- `dataspace.openapi.url` - URL to OpenAPI schema
- `dataspace.openapi.validation.continue.on.failure` - Continue on schema validation errors
- `dataspace.base.url` - Base URL for backend API
- `dataspace.http.scheme` - HTTP scheme (http/https)
- `dataspace.omegax.decoration.enabled` - Enable Omega-X metadata decoration
- `dataspace.omegax.decoration.default.creator.name` - Creator name for assets
- `dataspace.omegax.decoration.default.publisher.homepage` - Publisher homepage

**IAM Extension:**
- `dataspace.wallet.url` - Walt.ID wallet API URL
- `dataspace.wallet.email` - Wallet admin email
- `dataspace.wallet.password` - Wallet admin password
- `dataspace.wallet.id` - Wallet ID (optional, auto-discovered if not set)
- `dataspace.trust.did` - DID of the trust anchor
- `dataspace.uniresolver.url` - Universal DID resolver URL
- `dataspace.vc.type` - Verifiable Credential type to use (default: "OpenTunityCredential")
- `dataspace.token.ttl.minutes` - Wallet token TTL in minutes

**Legacy CTIC-specific parameters** (still using `es.ctic.*` prefix):
- `es.ctic.backend.auth.key.header` - Backend API auth header name
- `es.ctic.backend.auth.key.envvar` - Environment variable containing backend API key
- `es.ctic.enable.authorization.constraint` - Enable authorization constraints
- `es.ctic.implicitly.trusted.dids` - Comma-separated list of trusted DIDs
- `es.ctic.policy.decision.point.api.url` - Policy Decision Point API URL
- `es.ctic.policy.decision.point.api.key` - Policy Decision Point API key

### Python SDK (edcpy)

The `edcpy` package provides:

- **EDC API client** (`edcpy/edc_api.py`) - High-level wrapper around EDC Management and Control APIs
- **Consumer backend** (`edcpy/backend.py`) - HTTP server that receives EndpointDataReference objects or push data transfers
- **Message handling** (`edcpy/message_handler.py`, `edcpy/messaging.py`) - Integration with RabbitMQ for async message processing
- **Models** (`edcpy/models/`) - Pydantic models for EDC entities (assets, contracts, transfers, etc.)

### OpenAPI Extension Mechanism

The OpenAPI connector works by:

1. Fetching the OpenAPI schema from the configured URL on startup
2. Iterating through all paths and operations in the schema
3. For each operation (e.g., GET /users, POST /data):
   - Creating an EDC Asset with an HttpDataAddress pointing to the backend endpoint
   - Extracting any `x-connector-presentation-definition` extension data
   - Building a PolicyDefinition with credential constraints if presentation definition exists
   - Creating a ContractDefinition linking the asset and policy
4. Registering HTTP params decorators to inject contract details and authentication into proxied requests

### Verifiable Credentials Flow

When SSI extension is enabled:

1. Consumer sends catalog request to provider with its DID
2. Provider requests a Verifiable Presentation from consumer
3. Consumer builds VP from credentials in its Walt.ID wallet matching the presentation definition
4. Consumer sends VP in the authentication header
5. Provider validates VP signature, checks issuer is trusted anchor, verifies credential type
6. If valid, provider allows access to the requested asset

### Development Environment

The `docker-compose-dev.yml` sets up:
- Provider connector with OpenAPI extension pointing to mock backend
- Consumer connector
- Mock backend HTTP API exposing OpenAPI schema
- RabbitMQ message broker for consumer backend
- Consumer backend (edcpy HTTP server)
- PostgreSQL databases for both connectors (when SQL=true)
- Walt.ID wallets for anchor, provider, and consumer (when SSI=true)

### Example Scripts

All examples in `example/` demonstrate different transfer patterns:

- **example_catalogue.py**: Fetch and display provider's catalog
- **example_pull.py**: Consumer Pull pattern - consumer initiates and pulls data
- **example_push.py**: Provider Push pattern - provider pushes data to consumer
- **example_parallel.py**: Multiple parallel transfers to same connector
- **example_pull_sse.py** / **example_push_sse.py**: Using Server-Sent Events endpoints

## Important Notes

- The connector extensions use EDC's ServiceExtension mechanism - each extension must have a corresponding entry in `META-INF/services/org.eclipse.edc.spi.system.ServiceExtension`
- Asset IDs are auto-generated as slugified versions of "METHOD-path" (e.g., "POST-consumption-prediction")
- The OpenAPI extension supports the custom `x-connector-presentation-definition` field for declaring credential requirements per endpoint
- Configuration files in `dev-config/` use `.properties` format, environment files use `.env` format
- Keystore must be in PKCS12 format with specific aliases defined in vault.properties
- When updating configuration parameter names, search for both Java @Setting annotations and .properties files
