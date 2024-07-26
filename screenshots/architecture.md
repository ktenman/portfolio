```plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml
' Include material icons
!define ICONURL https://raw.githubusercontent.com/tupadr3/plantuml-icon-font-sprites/master/material
!define ICONURL2 https://raw.githubusercontent.com/tupadr3/plantuml-icon-font-sprites/master/devicons2
!include ICONURL/cloud.puml
!include ICONURL2/redis.puml
!include ICONURL2/postgresql.puml
!include ICONURL2/vuejs.puml
!include ICONURL2/kotlin.puml
!include ICONURL2/spring.puml
!include ICONURL2/python.puml
!include ICONURL2/chrome.puml

LAYOUT_WITH_LEGEND()

Person(user, "User", "End user of the Portfolio Management System")

System_Boundary(portfolio_system, "Portfolio Management System") {
  Container(api_gateway, "API Gateway", "Caddy", "Handles authentication and reverse proxy")
  Container(auth, "Auth Service", "Kotlin, Spring Boot", "Handles OAuth authentication and authorization", $sprite="kotlin")
  Container(frontend, "Frontend", "Vue.js, Bootstrap", "Provides the user interface for managing portfolio and viewing data", $sprite="vuejs")
  Container(backend, "Backend", "Kotlin, Spring Boot", "Handles API requests, processes data, and manages business logic", $sprite="kotlin")
  ContainerDb(database, "Database", "PostgreSQL", "Stores portfolio and financial data", $sprite="postgresql")
  ContainerDb(cache, "Cache", "Redis", "Caches financial data and portfolio summaries for improved performance", $sprite="redis")
  Container(market_price_tracker, "Market Price Tracker", "Python, Selenium", "Tracks and updates market prices using web scraping", $sprite="python")
}

System_Ext(alphavantage, "Alpha Vantage API", "Provides financial market data", $sprite="cloud")
System_Ext(google_oauth, "Google OAuth", "Provides OAuth 2.0 authentication", $sprite="cloud")

Container_Ext(browser, "Browser", "Chrome, LocalStorage, Cookies", "Caches some calls and stores cookies", $sprite="chrome")

Rel(user, browser, "Uses", "HTTPS")
Rel(browser, api_gateway, "Accesses", "HTTPS")
Rel(api_gateway, auth, "Handles authentication with", "HTTPS")
Rel(auth, google_oauth, "Authenticates with", "OAuth 2.0")
Rel(api_gateway, frontend, "Forwards to", "HTTPS")
Rel(api_gateway, backend, "Forwards to", "HTTPS")
Rel(frontend, backend, "Sends requests to", "HTTPS")
Rel(backend, database, "Reads from and writes to", "JDBC")
Rel(backend, cache, "Reads from and writes to", "Redis protocol")
Rel(backend, alphavantage, "Retrieves financial data from", "HTTPS")
Rel(market_price_tracker, backend, "Pushes updates to", "HTTP")
@enduml
```
