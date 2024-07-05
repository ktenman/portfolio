```
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml

LAYOUT_WITH_LEGEND()

Person(user, "User", "End user of the Portfolio Management System")

Container(frontend, "Frontend", "Vue.js, Bootstrap", "Provides the user interface for managing portfolio and viewing data")
Container(backend, "Backend", "Spring Boot, Kotlin", "Handles API requests, processes data, and manages business logic")
Container(database, "Database", "PostgreSQL", "Stores portfolio data, transactions, and financial information")
Container(cache, "Cache", "Redis", "Caches financial data and portfolio summaries for improved performance")
Container(jobs, "Data Retrieval Jobs", "Kotlin", "Fetches financial data and calculates portfolio metrics")

System_Ext(alphavantage, "Alpha Vantage API", "Provides financial market data")

Rel(user, frontend, "Uses", "HTTPS")
Rel(frontend, backend, "Sends requests to", "HTTPS")
Rel(backend, database, "Reads from and writes to", "JDBC")
Rel(backend, cache, "Reads from and writes to", "Redis protocol")
Rel(jobs, alphavantage, "Retrieves financial data from", "HTTPS")
Rel(jobs, database, "Writes financial data to", "JDBC")
Rel(jobs, backend, "Triggers calculations", "Internal")
@enduml
```