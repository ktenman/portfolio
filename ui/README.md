# Weather Forecast UI

This project is a weather forecast user interface built with Vue 3, TypeScript, and Vite. It provides a simple and
intuitive way for users to view weather forecasts for various locations in Estonia.

## Technologies Used

- Vue 3
- TypeScript
- Vite
- Bootstrap 5
- Axios

## Features

- Search for weather forecasts by location
- Display temperature ranges for different dates
- Show alerts for errors or no data scenarios

## Project Setup

### Prerequisites

- Node.js (v20.11.1 or later)
- npm (v10.2.4 or later)

### Installation

1. Clone the repository:

   ```
   git clone https://github.com/ktenman/portfolio.git
   ```

2. Install dependencies:
   ```
   npm install
   ```

### Development

To start the development server:

```
npm run dev
```

This will start the Vite dev server. Open your browser and navigate to `http://localhost:61234` to see the application.

### Building for Production

To build the application for production:

```
npm run build
```

This will generate a production-ready build in the `dist` directory.

### Linting

To lint your code:

```
npm run lint
```

### Formatting

To format your code using Prettier:

```
npm run format
```

## Recommended IDE Setup

We recommend using Visual Studio Code with the following extensions:

- [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur)
- [TypeScript Vue Plugin (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.vscode-typescript-vue-plugin)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default, so we use `vue-tsc` for type checking. You may
need
the [TypeScript Vue Plugin (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.vscode-typescript-vue-plugin)
in your editor for the best development experience.

## Project Structure

```
/ui
  /components
    weather-component.vue
  /models
    alert-type.ts
    api-error.ts
    weather-forecast.ts
    weather-forecast-response.ts
  /services
    weather-service.ts
  app.vue
  index.html
  main.ts
  style.css
```
