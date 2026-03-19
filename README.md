# AI Create / ERP

A modern AI tool and ERP backend.

## IAM module (Identity and Access Management)

The **iam** module is a multi-tenant IAM service built with **Java 17**, **Spring Boot**, and **Gradle (Groovy)**. It uses CockroachDB and JWT for future ERP modules.

- **Requirements:** Java 17+, CockroachDB (or PostgreSQL)
- **Build:** `./gradlew :iam:build`
- **Run (with DevTools auto-reload):** `./gradlew :iam:bootRun`
- **Details:** See [iam/README.md](iam/README.md)
- **CockroachDB local setup:** See [cockroachdb/README.md](cockroachdb/README.md)

## API gateway (BFF)

**api-gateway** is a **Spring Cloud Gateway** reverse proxy (port **8000** by default) that routes to IAM (**8080**) and entity-builder (**8081**). Use this on Windows without Docker; see [api-gateway/README.md](api-gateway/README.md). For a Docker-based gateway, see [kong/README.md](kong/README.md).

## Overview

This project provides AI capabilities for generating and creating content.

## Features

- AI-powered content generation
- Modern and intuitive interface
- Extensible architecture
- Easy to configure and customize

## Installation

```bash
# Clone the repository
git clone <repository-url>
cd ai-create

# Install dependencies
npm install
```

## Usage

```bash
# Start the application
npm start
```

## Configuration

Configuration options can be set in the `.env` file:

```env
# Add your configuration here
API_KEY=your_api_key_here
```

## Development

```bash
# Run in development mode
npm run dev

# Run tests
npm test

# Build for production
npm run build
```

## Project Structure

```
ai-create/
├── src/           # Source code
├── tests/         # Test files
├── docs/          # Documentation
└── README.md      # This file
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For questions or support, please open an issue on GitHub.

## Acknowledgments

- Thanks to all contributors
- Built with modern AI technologies

