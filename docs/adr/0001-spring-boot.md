# ADR 0001: Java 21 and Spring Boot 3 for the service

## Status

Accepted

## Context

The project needs a backend that demonstrates production-style payment orchestration:
transactional persistence, scheduled jobs, Kafka producers and consumers, health and
metrics endpoints, structured logging and a testable architecture. It should be readable by
engineers who work on payment platforms, most of which run on the JVM.

Alternatives considered: Quarkus (smaller footprint, less familiar to most reviewers),
Kotlin with Spring (adds a language to review), Node/NestJS (weaker transactional and
scheduling story for this shape of problem).

## Decision

Use **Java 21** (records, sealed interfaces, pattern matching, virtual threads available if
needed) and **Spring Boot 3.4** with Maven. The domain layer (`domain` package) is pure Java
with no Spring dependency; Spring is confined to `application` wiring, `infrastructure`
adapters and the `api` layer (DESIGN.md 4.1).

Supporting choices: Spring Data JPA with Hibernate `ddl-auto=validate` and Flyway migrations,
Spring Kafka, Spring Data Redis, Spring Boot Actuator with Micrometer and the Prometheus
registry, springdoc-openapi for Swagger UI, and `ProblemDetail` for RFC 7807 errors.

## Consequences

* Mature ecosystem for every integration in the design; little custom glue.
* Testcontainers gives real PostgreSQL, Kafka and Redis in integration tests on CI.
* JVM memory footprint is the largest in the compose stack (limited to 1 GB); startup takes
  tens of seconds, which the compose healthchecks and `scripts/wait-for-health.sh` absorb.
* Spring's transaction and scheduling abstractions make the outbox and retry patterns
  compact, at the cost of some "magic" that the ADRs and architecture document explain.
