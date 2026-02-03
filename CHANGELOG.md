# Changelog

All notable changes to Storm are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Repository lookups and `Ref.fetch()` check the cache before querying the database
- Select operations only query the database for cache misses
- Raw INSERT/UPDATE/DELETE queries automatically invalidate the cache for the affected entity type
- New `isRepeatableRead()` method on `TransactionContext` to check if isolation level is REPEATABLE_READ or higher
- Revamped documentation and examples

## [1.8.1] - 2025-01-29

### Added
- Entity cache enabled in read-only transactions
- Queries within the same transaction share cached entities
- Primary key lookup in cache before construction, avoiding full entity equality checks

### Changed
- Entity cache disabled for READ_UNCOMMITTED to respect isolation semantics
- Interner optimization using primary key-based lookups

## [1.8.0] - 2025-01-28

### Added
- SQL template caching
- SQL template generation metric logging

### Changed
- Separated SQL template compilation and binding stages
- Query model improvements for fast table and column lookups
- Simplified template processing logic

## [1.7.2] - 2025-01-09

### Fixed
- Entity cache cleanup after nested transaction rollback

### Changed
- Cleaned up extension functions API

## [1.7.1] - 2024-12-30

### Added
- Custom field type support
- Kotlinx serialization integration
- Entity and field-level dirty checks
- KSP mirror support

### Changed
- Removed @JvmRecord requirement for Kotlin
- Record mapper optimization
- Caching repository proxies for improved lookup performance

## [1.6.2] - 2024-11-02

### Added
- Primary keys can now be Ref types

## [1.6.1] - 2024-10-04

### Fixed
- WhereProcessor for PK/FK combinations
- Join inclusion logic
- Time-based JDBC type handling
- SQL dialect keyword handling

## [1.6.0] - 2024-09-13

### Added
- Sequence-based ID generation strategy support
- Entity-to-ref conversion functions
- Null Ref deserialization to Ref.ofNull()
- Transaction options configurable globally and per call stack

### Changed
- Replaced org.reflections with Spring classpath scanning

## [1.5.0] - 2024-08-19

### Added
- Kotlin coroutine support
- Programmatic transaction support
- Flow support for streaming results

### Changed
- Replaced JUL with SLF4J logging

### Removed
- Legacy callback and slice helper methods

## [1.4.0] - 2024-08-11

### Added
- Kotlin elevated to first-class citizen status
- Template support in predicate builder
- whereExists queries
- Kotlin validator project
- SqlLogger for query logging
- Ref serializer/deserializer for JSON handling

### Changed
- Updated to Kotlin 2.0.21

## [1.3.8] - 2024-07-06

### Changed
- Performance improvements through reflection optimization
- Enhanced template function methods
- Limited alias resolution to scope when available

## [1.3.7] - 2024-06-29

### Added
- Parameter inlining for SqlTemplate
- Literal conversion for database values
- SqlTemplate customizer
- Kotlin extension methods for simplified queries
- Sequence and stream support with closeable sequences for repositories
- Infix operators

## [1.3.6] - 2024-05-31

### Added
- Kotlin extension functions for convenient repository access

## [1.3.5] - 2024-05-26

### Added
- Nullable reference method

### Changed
- Eliminated annotations from metamodel
- Documented and cleaned up conversion logic

## [1.3.4] - 2024-05-18

### Added
- Full support for compound primary keys

### Changed
- Improved nullability checks
- Improved order by handling

## [1.3.3] - 2024-05-05

### Changed
- Refactored SqlInterceptor to use ScopedValue

## [1.3.2] - 2024-05-03

### Added
- Jackson module auto-registration

### Changed
- Aligned entity and projection repositories with existing persistence APIs

## [1.3.1] - 2024-04-22

### Changed
- Query builder API refactoring
- Improved subquery handling
- Improved alias resolution error handling

## [1.2.2] - 2024-03-31

### Added
- Short-metamodel support for joins and nested entity graph columns
- Entity usage validation in where clauses

### Removed
- Metamodel alias support

## [1.2.1] - 2024-03-16

### Fixed
- Bug fixes and improvements

## [1.2.0] - 2024-03-16

### Added
- Initial public release

---

For detailed release notes, see the [GitHub Releases](https://github.com/storm-repo/storm-framework/releases) page.
