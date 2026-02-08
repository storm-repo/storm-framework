# Contributing to Storm

Thank you for your interest in contributing to Storm! This document provides guidelines and information for contributors.

## Ways to Contribute

- **Report bugs:** Open an issue describing the bug, including steps to reproduce
- **Suggest features:** Open an issue describing your idea and use case
- **Improve documentation:** Fix typos, clarify explanations, add examples
- **Submit code:** Bug fixes, new features, performance improvements

## Development Setup

### Prerequisites

- JDK 21 or later
- Kotlin 2.0 or later (for Kotlin modules)
- Maven 3.9+

### Building the Project

```bash
# Clone the repository
git clone https://github.com/storm-repo/storm-framework.git
cd storm-framework

# Build all modules
mvn clean install
```

### Project Structure

```
storm-framework/
├── storm-core/           # Core library (no preview dependencies)
├── storm-java21/         # Java 21+ API (uses String Templates)
├── storm-kotlin/         # Kotlin API
├── storm-spring/         # Spring integration (Java)
├── storm-kotlin-spring/  # Spring integration (Kotlin)
├── storm-oracle/         # Oracle dialect
├── storm-mysql/          # MySQL dialect
├── storm-mariadb/        # MariaDB dialect
├── storm-postgresql/     # PostgreSQL dialect
├── storm-mssqlserver/    # MS SQL Server dialect
├── storm-jackson/        # Jackson JSON support
├── storm-kotlinx-serialization/  # Kotlinx serialization support
└── storm-metamodel-processor/    # Annotation processor
```

## Code Guidelines

### General

- Follow existing code style and conventions
- Write clear, self-documenting code
- Keep changes focused—one feature or fix per PR
- Add tests for new functionality

### Kotlin

- Use idiomatic Kotlin (data classes, extension functions, etc.)
- Prefer immutability
- Use coroutines for async operations
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)

### Java

- Use records for immutable data
- Leverage pattern matching where appropriate
- Follow standard Java naming conventions
- Use `Optional` for nullable return values

### Testing

- Write unit tests for new functionality
- Ensure existing tests pass before submitting
- Use meaningful test names that describe the scenario

```bash
# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl storm-kotlin
```

## Submitting Changes

### Pull Request Process

1. **Fork** the repository
2. **Create a branch** for your changes
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes** with clear, atomic commits
4. **Test** your changes thoroughly
5. **Push** to your fork
6. **Open a Pull Request** against the `main` branch

### PR Guidelines

- Provide a clear description of the changes
- Reference any related issues
- Keep PRs focused and reasonably sized
- Respond to review feedback promptly

### Commit Messages

Write clear commit messages that explain *what* and *why*:

```
Add support for composite foreign keys

- Handle multiple column FKs in entity mapping
- Update metamodel generator for composite keys
- Add tests for various composite key scenarios

Fixes #123
```

## Reporting Issues

When reporting bugs, please include:

- Storm version
- Java/Kotlin version
- Database and JDBC driver version
- Minimal code example reproducing the issue
- Expected vs actual behavior
- Stack trace (if applicable)

## Feature Requests

When suggesting features:

- Describe the use case and problem being solved
- Explain how you envision the feature working
- Consider providing API examples
- Note any alternatives you've considered

## Questions?

- Check the [FAQ](docs/faq.md) first
- Open a discussion for general questions
- Open an issue for bugs or feature requests

## License

By contributing to Storm, you agree that your contributions will be licensed under the Apache 2.0 License.
