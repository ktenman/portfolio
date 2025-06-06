# Portfolio Management System

[![Build & Test](https://github.com/ktenman/portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/ktenman/portfolio/actions/workflows/ci.yml)

## Introduction

The Portfolio Management System is a comprehensive application designed to help users manage their investment
portfolios. It retrieves financial data from the Alpha Vantage API, stores it in a database, and provides a
user-friendly interface for viewing and managing portfolio transactions, instruments, and performance metrics.

<img src="screenshots/app.png" width="600" alt="Portfolio Management System application home page">

## Key Features

- **Instrument Management**: Add, update, and delete financial instruments in your portfolio.
- **Transaction Tracking**: Record buy and sell transactions for your portfolio.
- **Performance Metrics**: Calculate and display portfolio performance metrics, including XIRR (Extended Internal Rate
  of Return).
- **Data Synchronization**: Automatically fetch and update financial data from Alpha Vantage API.
- **Caching**: Utilize Redis caching for improved performance and reduced API calls.
- **User Interface**: Provide a user-friendly interface for managing and viewing portfolio data.
- **Authentication**: Secure user authentication using OAuth 2.0 with Google and GitHub.
- **Stock Market Tracker**: Real-time price updates for portfolio instruments.

## Technical Stack

### Backend

- Spring Boot v3.3
- Kotlin v2
- Java v21

### Frontend

- Vue.js v3.4
- Bootstrap v5.3
- Vite (Build Tool)

### Database & Caching

- PostgreSQL with Flyway for migrations
- Redis for caching

### Testing

- JUnit, Mockito, AssertJ, Selenide, and Testcontainers

### CI/CD & Containerization

- GitHub Actions
- Docker and Docker Compose

### API Integration

- Alpha Vantage for financial data

### Stock Market Tracker

- Python
- Selenium
- Flask

## Architecture 🏗️

<img src="./screenshots/architecture.svg" width="600" alt="System Architecture">

The system consists of:

- Vue.js frontend
- Spring Boot backend
- PostgreSQL database
- Redis cache
- Authentication service
- Scheduled jobs for data retrieval and XIRR calculation
- Stock market tracker for real-time price updates

### Database 🗄️

PostgreSQL stores portfolio data, including instruments, transactions, and daily price information. The backend performs
CRUD operations using Spring Data JPA.

### Cache 🚀

Redis serves as a caching layer to improve data retrieval performance, storing frequently accessed data like instrument
details and portfolio summaries.

### Data Retrieval Jobs ⚙️

1. **Instrument Data Retrieval Job**: Fetches the latest price data for all instruments from the Alpha Vantage API.
2. **Daily Portfolio XIRR Job**: Calculates the Extended Internal Rate of Return (XIRR) for the portfolio daily.

### Authentication 🔐

The system uses OAuth 2.0 for authentication, integrating with Google's OAuth service.

### Interaction Flow 📊

1. User logs in via the frontend.
2. Frontend redirects to OAuth service for authentication.
3. User is redirected back with an authentication token.
4. Frontend includes this token in backend requests.
5. Backend validates the token with the Auth service.
6. Frontend displays portfolio data to the user.
7. Periodic jobs update the database and calculate metrics.

## Setup and Running Instructions

### Prerequisites

- Kotlin v2
- Java v21
- Gradle v8.8
- Node.js v20.11.1
- npm v10.2.4
- Docker v25.0.2
- Docker Compose v2.24.3

### Local Development Setup

Initialize necessary Docker containers with Docker Compose to ensure the database and Redis services are up before
proceeding:

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

Install frontend dependencies and start the development server:

```bash
npm install
npm run dev
```

You can access it in your web browser at http://localhost:61234

### Running and Updating the Application

To update the application or its services after making changes:

1. Rebuild the services:

```bash
docker-compose -f docker-compose.local.yml build
```

2. Restart the services for the changes to take effect:

```bash
docker-compose -f docker-compose.local.yml up -d
```

Once the application is up and running, you can access it in your web browser at http://localhost

### End-to-End Tests

To run end-to-end tests:

```bash
docker-compose -f docker-compose.yml -f docker-compose.e2e.yml down
docker volume rm portfolio_postgres_data_e2e
docker-compose -f docker-compose.yml -f docker-compose.e2e.yml up -d && sleep 30
export E2E=true && ./gradlew test --info -Pheadless=true
```

### Continuous Integration and Deployment

- A CI pipeline via GitHub Actions in the `.github` folder automates unit and integration tests.
- The Dependabot updates Gradle and GitHub Actions versions, automating dependency management.

## Key Features

- **Instrument Management**: Add, update, and delete financial instruments in your portfolio.
- **Transaction Tracking**: Record buy and sell transactions for your portfolio.
- **Performance Metrics**: Calculate and display portfolio performance metrics, including XIRR.
- **Data Synchronization**: Automatically fetch and update financial data from Alpha Vantage API.
- **Caching**: Utilize Redis caching for improved performance and reduced API calls.
- **User Interface**: Provide a user-friendly interface for managing and viewing portfolio data.

## Database Design

Here's the complete representation of the database schema:

```
+------------------------+      +------------------------+      +------------------------+
|      INSTRUMENT        |      | PORTFOLIO_TRANSACTION  |      |     DAILY_PRICE        |
+------------------------+      +------------------------+      +------------------------+
| id (PK)                |<-----| id (PK)                |      | id (PK)                |
| symbol                 |      | instrument_id (FK)     |      | instrument_id (FK)     |---+
| name                   |      | transaction_type       |      | entry_date             |   |
| instrument_category    |      | quantity               |      | provider_name          |   |
| base_currency          |      | price                  |      | open_price             |   |
| current_price          |      | transaction_date       |      | high_price             |   |
| provider_name          |      | platform               |      | low_price              |   |
| version                |      | realized_profit        |      | close_price            |   |
| created_at             |      | unrealized_profit      |      | volume                 |   |
| updated_at             |      | average_cost           |      | version                |   |
+------------------------+      | version                |      | created_at             |   |
                                | created_at             |      | updated_at             |   |
                                | updated_at             |      +------------------------+   |
                                +------------------------+                                   |
                                                                                            |
+------------------------+      +------------------------+      +------------------------+  |
| PORTFOLIO_DAILY_SUMMARY|      |    JOB_EXECUTION       |      |    USER_ACCOUNT        |  |
+------------------------+      +------------------------+      +------------------------+  |
| id (PK)                |      | id (PK)                |      | id (PK)                |  |
| entry_date (UNIQUE)    |      | job_name               |      | email (UNIQUE)         |  |
| total_value            |      | start_time             |      | session_id (UNIQUE)    |  |
| xirr_annual_return     |      | end_time               |      | version                |  |
| total_profit           |      | duration_in_millis     |      | created_at             |  |
| earnings_per_day       |      | status                 |      | updated_at             |  |
| version                |      | message                |      +------------------------+  |
| created_at             |      | version                |                                   |
| updated_at             |      | created_at             |                                   |
+------------------------+      | updated_at             |                                   |
                                +------------------------+                                   |
                                                                                            |
                                         One-to-Many Relationships -------------------------+
```

### Key Database Features:

**Data Types & Constraints:**

- Primary keys: All tables use `BIGSERIAL` for auto-incrementing IDs
- Numeric precision: Financial values use `NUMERIC(20,10)` for accuracy
- Timestamps: All tables include `TIMESTAMP WITH TIME ZONE` for `created_at` and `updated_at`
- Optimistic locking: All tables have a `version` column (BIGINT DEFAULT 0)

**Indexes for Performance:**

- Text search: GIN indexes on `instrument.symbol` and `instrument.name` using pg_trgm
- Foreign keys: B-tree indexes on all foreign key columns
- Date-based queries: Indexes on `transaction_date`, `entry_date`, and `start_time`
- Composite index: `(instrument_id, entry_date, provider_name)` on `daily_price`

**Supported Values:**

- **Providers**: ALPHA_VANTAGE (stocks/ETFs), BINANCE (crypto), FT (Financial Times)
- **Platforms**: SWEDBANK, BINANCE, TRADING212, LIGHTYEAR
- **Transaction Types**: BUY, SELL
- **Job Status**: SUCCESS, FAILURE, IN_PROGRESS

**Relationships:**

- `instrument` → `portfolio_transaction`: One-to-Many (via instrument_id)
- `instrument` → `daily_price`: One-to-Many (via instrument_id)
- Other tables are independent but interact through business logic

**Data Integrity:**

- UNIQUE constraints on `instrument.symbol`, `daily_price.(instrument_id, entry_date, provider_name)`
- CHECK constraints on `provider_name` and `transaction_type`
- NOT NULL constraints on critical fields like prices, dates, and identifiers

## Deployment

1. Rename the `.env.example` file to `.env` and fill in the necessary information.
2. Create a shell script (e.g., deploy.sh) to deploy the application:

   ```bash
   #!/bin/bash

   cd portfolio
   git pull
   docker-compose -f docker-compose.yml down
   docker-compose -f docker-compose.yml pull
   docker-compose -f docker-compose.yml build
   docker-compose -f docker-compose.yml up -d
   docker rmi $(docker images -f "dangling=true" -q)
   ```

3. Make the shell script executable:

   ```bash
   chmod +x deploy.sh
   ```

4. Run the shell script to deploy the application:
   ```bash
   ./deploy.sh
   ```

### Example .env file needed for deployment

```bash
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
GOOGLE_CLIENT_ID=XXXXXXXXXXXXXXXXXXXXXXXX.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=XXXXXXXXXXXXXXXXXXXXXXXX
GITHUB_CLIENT_ID=XXXXXXXXXXXXXXXXXXXXXXXX
GITHUB_CLIENT_SECRET=XXXXXXXX
```

---

This README provides a comprehensive guide for developers to set up, run, and understand the core functionalities and
technical aspects of the Portfolio Management System.
