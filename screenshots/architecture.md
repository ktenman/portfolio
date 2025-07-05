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
  Container(caddy, "Caddy", "Web Server", "Reverse proxy on port 80")
  Container(oauth2_proxy, "OAuth2-Proxy", "Go", "Session management, authentication proxy, email whitelist")
  Container(app_internal, "App Internal Router", "Caddy", "Routes /api/* to backend, rest to frontend")
  Container(keycloak, "Keycloak", "Java", "Identity provider, OIDC authentication")
  ContainerDb(keycloak_db, "Keycloak DB", "PostgreSQL", "Stores Keycloak users and configuration", $sprite="postgresql")
  Container(frontend, "Frontend", "Vue.js, Bootstrap", "Provides the user interface for managing portfolio and viewing data", $sprite="vuejs")
  Container(backend, "Backend", "Kotlin, Spring Boot", "Handles API requests, processes data, and manages business logic", $sprite="kotlin")
  ContainerDb(database, "Database", "PostgreSQL", "Stores portfolio and financial data", $sprite="postgresql")
  ContainerDb(cache, "Cache", "Redis", "Caches session data and portfolio summaries", $sprite="redis")
  Container(market_price_tracker, "Market Price Tracker", "Python, Selenium", "Tracks and updates market prices using web scraping", $sprite="python")
  Container(email_whitelist, "emails.txt", "Text File", "List of authorized email addresses")
}

System_Ext(alphavantage, "Alpha Vantage API", "Provides financial market data", $sprite="cloud")
System_Ext(binance, "Binance API", "Provides cryptocurrency prices", $sprite="cloud")
System_Ext(ft_api, "Financial Times API", "Provides market data", $sprite="cloud")
System_Ext(google_oauth, "Google OAuth", "External identity provider", $sprite="cloud")

Container_Ext(browser, "Browser", "Chrome, LocalStorage, Cookies", "Stores OAuth2-Proxy session cookies", $sprite="chrome")

Rel(user, browser, "Uses", "HTTPS")
Rel(browser, caddy, "Accesses", "HTTP :80")
Rel(caddy, oauth2_proxy, "Forwards all requests", "HTTP :4180")
Rel(oauth2_proxy, keycloak, "Authenticates users", "OIDC/OAuth2")
Rel(keycloak, keycloak_db, "Stores data", "JDBC")
Rel(keycloak, google_oauth, "Delegates authentication", "OAuth2/OIDC")
Rel(oauth2_proxy, email_whitelist, "Reads whitelist", "File mount")
Rel(oauth2_proxy, app_internal, "Forwards authenticated requests", "HTTP :8090")
Rel(oauth2_proxy, cache, "Stores sessions", "Redis protocol")
Rel(app_internal, backend, "Routes /api/*", "HTTP :8081")
Rel(app_internal, frontend, "Routes UI requests", "HTTP :61234")
Rel(frontend, backend, "API calls", "via proxy")
Rel(backend, database, "Reads/writes data", "JDBC")
Rel(backend, cache, "Caches data", "Redis protocol")
Rel(backend, alphavantage, "Fetches stock prices", "HTTPS")
Rel(backend, binance, "Fetches crypto prices", "HTTPS")
Rel(backend, ft_api, "Fetches market data", "HTTPS")
Rel(market_price_tracker, backend, "Pushes scraped prices", "HTTP")

@enduml
```
