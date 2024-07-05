# Portfolio Management System

[![Build & Test](https://github.com/ktenman/portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/ktenman/portfolio/actions/workflows/ci.yml)

## Introduction

The Portfolio Management System is a comprehensive application designed to help users manage their investment portfolios. It retrieves financial data from the Alpha Vantage API, stores it in a database, and provides a user-friendly interface for viewing and managing portfolio transactions, instruments, and performance metrics.

## Prerequisites

Before you begin, ensure your system meets the following requirements:

- Kotlin: v1.9.0
- Java: v21 (for running Kotlin)
- Gradle: v8.8
- Node.js: v20.11.1
- npm: v10.2.4
- Docker: v25.0.2
- Docker Compose: v2.24.3

## Technical Stack

- **Backend**: Spring Boot v3.3
- **Frontend**:
  - **Build Tool**: Vite
  - Vue.js v3.4
  - Bootstrap v5.3
- **Database**: PostgreSQL for data persistence and Flyway for database migration management
- **Caching**: Redis, utilized for caching financial data and portfolio summaries
- **Testing**: JUnit, Mockito, AssertJ, Selenide, and Testcontainers for robust testing coverage

## Architecture 🏗️

The Portfolio Management System is built with a modular architecture, comprising several key components that work together to deliver a comprehensive portfolio management experience.

![System Architecture](./screenshots/architecture.svg)

### Frontend 🌐

The frontend, built with Vue.js and Bootstrap, provides a responsive user interface. It communicates with the backend via HTTP to retrieve and display portfolio data, transactions, and performance metrics.

### Backend 🧠

Developed using Spring Boot, the backend handles API requests, processes data, and interacts with the database and cache. It exposes RESTful endpoints for the frontend to consume and manages the business logic for portfolio calculations.

### Database 🗄️

PostgreSQL, a reliable and scalable relational database, stores the portfolio data, including instruments, transactions, and daily price information. The backend performs CRUD operations using Spring Data JPA.

### Cache 🚀

Redis, an in-memory data store, is used as a caching layer to improve data retrieval performance. Frequently accessed data, such as instrument details and portfolio summaries, is stored in the cache, reducing database queries and enhancing responsiveness.

### Data Retrieval Jobs ⚙️

Two main jobs run periodically to keep the system updated:

1. **Instrument Data Retrieval Job**: Fetches the latest price data for all instruments in the portfolio from the Alpha Vantage API.
2. **Daily Portfolio XIRR Job**: Calculates the Extended Internal Rate of Return (XIRR) for the portfolio on a daily basis, providing up-to-date performance metrics.

### Interaction Flow 📊

1. User accesses the frontend, triggering HTTP requests to the backend for portfolio data.
2. Backend checks the Redis cache for the requested data.
3. If data is cached, the backend retrieves it and sends it to the frontend.
4. If data is not cached, the backend queries the PostgreSQL database.
5. Retrieved data is cached in Redis for future requests and sent to the frontend.
6. Frontend receives and displays the portfolio data to the user.
7. Periodic jobs update the database with the latest financial data and calculate performance metrics.

## Setup and Running Instructions

### Docker Containers Setup

Initialize necessary Docker containers with Docker Compose to ensure the database and Redis services are up before proceeding:

```bash
docker-compose -f compose.yaml up -d
```

### Backend Setup

Navigate to the root directory and compile the Java application using Gradle:

```bash
./gradlew clean build
./gradlew bootRun
```

### Frontend Setup

Install frontend dependencies, and start the development server:

```bash
npm install
npm run dev
```

You can access it in your web browser at http://localhost:61234

### Running in Production

To run the application in production, use Docker Compose:

```bash
docker-compose -f docker-compose.yml up -d
```

Once the application is up and running, you can access it in your web browser at http://localhost

### Updating the Application

To update the application or its services after making changes:

1. Rebuild the services:

```bash
docker-compose -f docker-compose.yml build
```

2. Restart the services for the changes to take effect:

```bash
docker-compose -f docker-compose.yml up -d
```

### End-to-End Tests

To run end-to-end tests:

```bash
export E2E=true
./gradlew test --info -Pheadless=true
```

### Continuous Integration and Deployment

- A CI pipeline via GitHub Actions in the `.github` folder automates unit and integration tests.
- Dependabot keeps Gradle and GitHub Actions versions up-to-date, automating dependency management.

## Key Features

- **Instrument Management**: Add, update, and delete financial instruments in your portfolio.
- **Transaction Tracking**: Record buy and sell transactions for your portfolio.
- **Performance Metrics**: Calculate and display portfolio performance metrics, including XIRR.
- **Data Synchronization**: Automatically fetch and update financial data from Alpha Vantage API.
- **Caching**: Utilize Redis caching for improved performance and reduced API calls.
- **User Interface**: Provide a user-friendly interface for managing and viewing portfolio data.

## Database Design

Here's a simplified representation of the main database tables:

```
+------------------+      +------------------------+      +-------------------+
|    Instrument    |      |  PortfolioTransaction  |      |    DailyPrice     |
+------------------+      +------------------------+      +-------------------+
| id               |      | id                     |      | id                |
| symbol           |      | instrument_id          |      | instrument_id     |
| name             |      | transaction_type       |      | entry_date        |
| category         |      | quantity               |      | provider_name     |
| base_currency    |      | price                  |      | open_price        |
+------------------+      | transaction_date       |      | high_price        |
                          +------------------------+      | low_price         |
                                                          | close_price       |
                                                          | volume            |
                                                          +-------------------+

+------------------------+
| PortfolioDailySummary  |
+------------------------+
| id                     |
| entry_date             |
| total_value            |
| xirr_annual_return     |
| total_profit           |
| earnings_per_day       |
+------------------------+
```

This README provides a comprehensive guide for developers to set up, run, and understand the core functionalities and technical aspects of the Portfolio Management System.
