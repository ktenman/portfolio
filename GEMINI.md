
This file provides instructions for Gemini to interact with the project.

## Project Overview

This is a portfolio management application with a Spring Boot backend, a Vue.js frontend, and a Python-based market price tracker. The project is a monorepo, with the backend, frontend, and market price tracker in the `src`, `ui`, and `market-price-tracker` directories, respectively.

## Tech Stack

- **Backend:** Spring Boot (Kotlin)
- **Frontend:** Vue.js (TypeScript)
- **Market Price Tracker:** Python
- **Build Tool:** Gradle
- **Package Manager:** npm
- **Database:** PostgreSQL
- **Testing:** JUnit, Kotest, Vitest

## Commands

- **Run backend tests:** `./gradlew test`
- **Run frontend tests:** `npm test`
- **Run linter:** `./gradlew ktlintCheck` and `npm run lint`
- **Format code:** `./gradlew ktlintFormat` and `npm run format`
- **Build project:** `./gradlew build`
