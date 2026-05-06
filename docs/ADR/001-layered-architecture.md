# ADR-001: Use Layered Architecture

## Status

Accepted

## Context

This project is a Spring Boot backend application.

As the application grows, the responsibilities of Controller, Service, and Repository can easily become mixed.

When responsibilities are mixed, code becomes harder to change, test, and maintain.

For example, if business logic is placed in Controller, HTTP request handling becomes tightly coupled with domain rules.

If business rules are placed in Repository, persistence logic becomes mixed with application policy.

Service can also become problematic if it only passes data through without clear responsibility, or if it becomes too large and handles every concern.

Therefore, this project needs a clear rule for separating responsibilities between layers from the beginning.

## Decision

This project will use Layered Architecture as the default backend architecture.

The default flow is:

```text
Controller → Service → Repository → Database