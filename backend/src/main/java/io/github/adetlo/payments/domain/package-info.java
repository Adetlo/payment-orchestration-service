/**
 * Core domain model: money, accounts, the payment aggregate with its explicit state table, and the
 * records (events, audit, outbox, idempotency, reconciliation) that surround it. This package has
 * no Spring dependency; aggregates carry JPA mapping annotations only.
 */
package io.github.adetlo.payments.domain;
