# Dart Tenants

REST service for managing DART's tenants

[![build and publish](https://github.com/twosixlabs-dart/dart-tenants/actions/workflows/build-and-publish.yml/badge.svg)](https://github.com/twosixlabs-dart/dart-tenants/actions/workflows/build-and-publish.yml)

## Modules

| Module Name          | Description                                             |
|----------------------|---------------------------------------------------------|
| tenants-api          | REST API definition using Tapir                         |
| tenants-controllers  | Scalatra controllers implementing dart-tenants REST API |
| tenants-client       | REST client library (unimplemented)                     |
| tenants-microservice | Scalatra microservice serving dart-tenants API          |

## Building
This project is built using SBT. For more information on installation and configuration of SBT please [see their documentation](https://www.scala-sbt.org/1.x/docs/)

To build and test the code:
```bash
sbt clean test
````

To create a runnable JAR of tenants-microservice:
```bash
sbt clean assembly
```

To create a Docker image of the runnable application:
```bash
make docker-build
```


## Configuration

The microservice's configuration is defined in `tenants-microservice/sr/main/resources/application.conf`. Most properties can be overridden
by environment variables:

| Name	                        | Description	                                                                 | Example Values                          |
|------------------------------|------------------------------------------------------------------------------|-----------------------------------------|
| TENANTS_HTTP_PORT            | Port where tenants REST service will be served                               | `8080` (default)                        |
| INDEX_MASTER                 | Type id of main tenant index                                                 | `arango` (default) / `in-memory`        |
| INDEX_1                      | Type id of secondary tenant index                                            | `elasticsearch` (default) / `none`      |
| INDEX_2                      | Type id of secondary tenant index                                            | `keycloak` (default) / `none`           |
| CORS_ALLOWED_ORIGINS         | Allowed origins for cross-origin requests                                    | `*` (default)                           |
| KEYCLOAK_SCHEME              | Protocol for keycloak connection                                             | `https` (default)                       |
| KEYCLOAK_HOST                | Hostname or IP of keycloak instance                                          | `localhost` (default)                   |
| KEYCLOAK_PORT                | Port for keycloak connection                                                 | `8090` (default)                        |
| KEYCLOAK_BASE_PATH           | Base path for keycloak requests                                              | `auth` (default)                        | 
| KEYCLOAK_REALM               | Keycloak realm used for REST auth                                            | `dart` (default)                        |
| KEYCLOAK_ADMIN_REALM         | Name of realm used by keycloak tenant index's auth client                    | `dart` (default)                        |
| KEYCLOAK_ADMIN_CLIENT_ID     | Admin client id used by keycloak tenant index (if enabled)                   | `dart-admin` (default)                  |
| KEYCLOAK_ADMIN_CLIENT_SECRET | Secret used by keycloak admin client                                         | `a5dd2106-326b-4b2b-bf0a-c4afd5e86851`  |
| ARANGODB_DATABASE            | Database Name for Arango CDR datastore                                       | `dart` (default)                        |
| ARANGODB_HOST                | Hostname or IP of Arango database instance                                   | `localhost` (default) / `dart-arangodb` |
| ARANGODB_PORT                | Arango database port                                                         | `8529` (default)                        |
| ELASTICSEARCH_SCHEME         | Protocol used for Elasticsearch connection                                   | `http` (default)                        |
| ELASTICSEARCH_HOST           | Hostname or IP address of Elasticsearch instance                             | `localhost` (default)                   |
| ELASTICSEARCH_PORT           | Elasticsearch port number                                                    | `9200` (default)                        |
| DART_AUTH_SECRET             | Auth token secret for keycloak integration                                   | `xxyyzz` (no default)                   |
| DART_AUTH_BYPASS             | If true do not use tokens to authenticate/authorize                          | `"true"` or `"false"`                   |
| DART_AUTH_BASIC_CREDENTIALS  | Use these credentials for basic auth authentication if DART_AUTH_BYPASS=true | `user1:pass1,user2:pass2`               |

## Funding
This software was developed with funding from the following sources.

| Agency | Program(s)                         | Grant #          |
|--------|------------------------------------|------------------|
| DARPA  | Causal Exploration, World Modelers | W911NF-19-C-0080 |
