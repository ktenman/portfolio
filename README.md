# Portfolio Management System

[![Build & Test](https://github.com/ktenman/portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/ktenman/portfolio/actions/workflows/ci.yml)

## Introduction

The Portfolio Management System is a comprehensive application designed to help users manage their investment
portfolios. It retrieves financial data from the Alpha Vantage API, stores it in a database, and provides a
user-friendly interface for viewing and managing portfolio transactions, instruments, and performance metrics.

<img src="screenshots2/app.png" width="600" alt="Portfolio Management System application home page">

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

Here's a simplified representation of the primary database tables:

```
+------------------+      +------------------------+      +-------------------+
|    Instrument    |      |  PortfolioTransaction  |      |    DailyPrice     |
+------------------+      +------------------------+      +-------------------+
| id               |      | id                     |      | id                |
| symbol           |      | instrument_id          |      | instrument_id     |
| name             |      | transaction_type       |      | entry_date        |
| category         |      | quantity               |      | provider_name     |
| base_currency    |      | price                  |      | open_price        |
| current_price    |      | transaction_date       |      | high_price        |
| created_at       |      | created_at             |      | low_price         |
| updated_at       |      | updated_at             |      | close_price       |
+------------------+      +------------------------+      | volume            |
                                                          | created_at        |
                                                          | updated_at        |
                                                          +-------------------+

+------------------------+      +------------------------+
| PortfolioDailySummary  |      |    JobExecution        |
+------------------------+      +------------------------+
| id                     |      | id                     |
| entry_date             |      | job_name               |
| total_value            |      | start_time             |
| xirr_annual_return     |      | end_time               |
| total_profit           |      | duration_in_millis     |
| earnings_per_day       |      | status                 |
| created_at             |      | message                |
| updated_at             |      | created_at             |
+------------------------+      | updated_at             |
                                +------------------------+

+------------------+
|   UserAccount    |
+------------------+
| id               |
| email            |
| session_id       |
| created_at       |
| updated_at       |
+------------------+
```

Key points:

- All tables include `id,` `created_at,` and `updated_at` fields for tracking creation and modifications.
- The `Instrument` table stores information about financial instruments.
- `PortfolioTransaction` table records buy and sell transactions linked to instruments.
- The `DailyPrice` table stores daily price data for instruments, including the data provider.
- The `PortfolioDailySummary` table keeps track of daily portfolio performance metrics.
- The `JobExecution` table logs the execution of scheduled jobs, including their status and duration.

Relationships:

- `PortfolioTransaction` and `DailyPrice` have a many-to-one relationship with `Instrument.`
- `PortfolioDailySummary` is independent but calculated based on transactions and prices.
- `JobExecution` is independent and used to monitor and audit system jobs.

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
