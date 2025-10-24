#!/bin/bash

# Portfolio Development Runner
# Starts backend and frontend in separate terminal windows

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Starting Portfolio application..."
echo "Project directory: $PROJECT_DIR"

# Detect OS
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS - use osascript to open new Terminal windows

    # Start Backend in new window (run in background)
    osascript -e 'tell application "Terminal"
        do script "cd '"'$PROJECT_DIR'"' && echo '\''=== Starting Backend (Spring Boot) ==='\'' && export GRADLE_OPTS='\''--enable-native-access=ALL-UNNAMED'\'' && ./gradlew bootRun"
    end tell' &

    # Small delay to ensure first window opens
    sleep 0.5

    # Start Frontend in new window (run in background)
    osascript -e 'tell application "Terminal"
        do script "cd '"'$PROJECT_DIR'"' && echo '\''=== Starting Frontend (Vite) ==='\'' && npm run dev"
    end tell' &

    # Wait for both osascript commands to complete
    wait

    echo "✓ Opened 2 terminal windows:"
    echo "  - Backend: Spring Boot on http://localhost:8081"
    echo "  - Frontend: Vite on http://localhost:61234"

elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux - try gnome-terminal, then xterm as fallback
    if command -v gnome-terminal &> /dev/null; then
        gnome-terminal --title="Backend (Spring Boot)" -- bash -c "cd '$PROJECT_DIR' && echo '=== Starting Backend (Spring Boot) ===' && export GRADLE_OPTS='--enable-native-access=ALL-UNNAMED' && ./gradlew bootRun; exec bash"
        gnome-terminal --title="Frontend (Vite)" -- bash -c "cd '$PROJECT_DIR' && echo '=== Starting Frontend (Vite) ===' && npm run dev; exec bash"
        echo "✓ Opened 2 gnome-terminal windows"
    elif command -v xterm &> /dev/null; then
        xterm -T "Backend (Spring Boot)" -e "cd '$PROJECT_DIR' && echo '=== Starting Backend (Spring Boot) ===' && export GRADLE_OPTS='--enable-native-access=ALL-UNNAMED' && ./gradlew bootRun; bash" &
        xterm -T "Frontend (Vite)" -e "cd '$PROJECT_DIR' && echo '=== Starting Frontend (Vite) ===' && npm run dev; bash" &
        echo "✓ Opened 2 xterm windows"
    else
        echo "Error: No supported terminal emulator found (tried gnome-terminal, xterm)"
        exit 1
    fi

elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    # Windows Git Bash
    echo "Windows detected. Opening new windows..."
    start cmd //c "cd /d $PROJECT_DIR && echo === Starting Backend (Spring Boot) === && set GRADLE_OPTS=--enable-native-access=ALL-UNNAMED && gradlew.bat bootRun"
    start cmd //c "cd /d $PROJECT_DIR && echo === Starting Frontend (Vite) === && npm run dev"
    echo "✓ Opened 2 command prompt windows"

else
    echo "Error: Unsupported operating system: $OSTYPE"
    exit 1
fi

echo ""
echo "Services starting..."
echo "  Backend:  http://localhost:8081"
echo "  Frontend: http://localhost:61234"
echo ""
echo "To stop services, close the terminal windows or press Ctrl+C in each"
