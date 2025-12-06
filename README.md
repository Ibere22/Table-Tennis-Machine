# 🏓 Automated Table Tennis Training System

A fully functional, wireless-controlled table tennis ball launcher system designed for solo practice and skill development. This project combines embedded systems programming (ESP32), Android app development, and mechanical engineering to create an affordable, customizable training solution.

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [System Architecture](#-system-architecture)
- [Hardware Requirements](#-hardware-requirements)
- [Software Setup](#-software-setup)
- [Project Structure](#-project-structure)
- [Usage](#-usage)
- [Training Modes](#-training-modes)
- [API Endpoints](#-api-endpoints)
- [Technical Details](#-technical-details)
- [Contributors](#-contributors)
- [License](#-license)

## 🎯 Overview

This project implements an automated table tennis training system that enables players to practice independently without a human partner. The system consists of:

- **ESP32 Microcontroller**: Controls motors, servos, and ball feeding mechanism
- **Android Application**: Provides intuitive wireless control via Wi-Fi
- **Mechanical Launcher**: 3D-printed chassis with brushless motors and servo-controlled aiming

The launcher can fire table tennis balls with adjustable speed, vertical angle, and horizontal direction, supporting multiple training modes for comprehensive practice sessions.

## ✨ Features

### 🎮 Training Modes
- **MANUAL**: Full control over speed, direction, and vertical angle
- **FOREHAND**: Optimized for forehand practice (75° vertical angle)
- **BACKHAND**: Optimized for backhand practice (105° vertical angle)
- **FOREHAND_BACKHAND**: Alternates between forehand and backhand shots
- **MATCH**: Simulates realistic match play with logical shot sequences
- **HARDCORE**: High-speed shots (80-100%) targeting table edges
- **RANDOM**: Completely random speed and direction for unpredictable training

### ⚙️ Adjustable Parameters
- **Ball Speed**: 0-100% (via slider)
- **Ball Count**: User-defined number of balls per session
- **Throw Interval**: Configurable delay between shots
- **Direction Control**: Horizontal aiming (0°-180°)
- **Vertical Angle**: Up/down trajectory control (25°-140°)

### 📊 Real-time Monitoring
- Live session status updates
- Balls remaining counter
- Current shot number tracking
- Session duration timer
- Post-session statistics with shot history

### 📡 Wireless Control
- Wi-Fi based communication (no cables needed)
- Real-time bidirectional communication
- WebSocket support for live updates
- HTTP REST API for commands

## 🏗️ System Architecture

```
┌─────────────────────────────────────┐
│      Android Application            │
│  (Kotlin + Jetpack Compose)         │
│  - UI Controls                      │
│  - WiFiManager                      │
│  - Session Management               │
└──────────────┬──────────────────────┘
               │ Wi-Fi (192.168.4.1)
               │
┌──────────────▼──────────────────────┐
│      ESP32 Microcontroller          │
│  - Web Server (Port 80)             │
│  - WebSocket Server (Port 81)       │
│  - Motor Control Logic              │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Physical Hardware              │
│  - 2x Brushless DC Motors           │
│  - 2x Servo Motors                  │
│  - 1x Stepper Motor                 │
│  - 3D-Printed Chassis                │
└─────────────────────────────────────┘
```

## 🔧 Hardware Requirements

### Electronics
- **ESP32 Development Board**
- **2x Electronic Speed Controllers (ESCs)** for brushless motors
- **2x Servo Motors** (e.g., MG996R, SG90)
- **1x Stepper Motor** with driver (e.g., 28BYJ-48 with ULN2003)
- **Power Supply**: 12V 5A DC adapter for motors + 5V for ESP32

### Mechanical
- **2x Brushless DC Motors** (drone motors)
- **3D-Printed Chassis** (STL files available in CAD folder)
- **Circular Ball Feeder Bowl** with hole
- **Wires, connectors, bearings, screws**

### Pin Connections

| ESP32 Pin | Component | Function |
|-----------|-----------|----------|
| 5 | ESC 1 | Motor 1 speed control |
| 18 | ESC 2 | Motor 2 speed control |
| 19 | Horizontal Servo | Left/Right aiming |
| 21 | Vertical Servo | Up/Down angle |
| 22-26 | Stepper Motor | Ball feeding mechanism |

## 💻 Software Setup

### ESP32 Setup

1. **Install Arduino IDE** with ESP32 board support
2. **Install Required Libraries**:
   - `ESP32Servo` - For ESC and servo control
   - `WebSocketsServer_Generic` - For real-time communication
   - `ArduinoJson` - For JSON parsing
3. **Open** `esp32WithFeeder.ino`
4. **Select Board**: Tools → Board → ESP32 Arduino
5. **Upload** the code to your ESP32

### Android App Setup

1. **Open** `TableTennisLauncherApp` in Android Studio
2. **Sync** Gradle dependencies
3. **Build** and run on Android device or emulator
4. **Connect** to ESP32 Wi-Fi network: `TableTennisLauncher` (password: `12345678`)

### Wi-Fi Configuration

The ESP32 creates a Wi-Fi Access Point:
- **SSID**: `TableTennisLauncher`
- **Password**: `12345678`
- **IP Address**: `192.168.4.1`

## 📁 Project Structure

```
Table-Tennis-Machine/
├── README.md                          # This file
├── .gitignore                         # Git ignore rules
├── esp32WithFeeder.ino               # ESP32 firmware
└── TableTennisLauncherApp/            # Android application
    ├── app/
    │   ├── src/main/
    │   │   ├── java/com/example/tabletennislauncher/
    │   │   │   ├── MainActivity.kt    # Main UI
    │   │   │   └── WiFiManager.kt     # Network communication
    │   │   └── res/                   # Resources
    │   └── build.gradle.kts
    ├── build.gradle.kts
    └── settings.gradle.kts
```

## 🚀 Usage

1. **Power on** the ESP32 and wait for Wi-Fi AP to start
2. **Open** the Android app on your device
3. **Connect** to `TableTennisLauncher` Wi-Fi network
4. **Select** a training mode
5. **Adjust** parameters (speed, ball count, etc.)
6. **Start** the session
7. **Monitor** progress in real-time
8. **Review** session statistics when complete

## 🎯 Training Modes

### MANUAL Mode
Full user control over all parameters:
- Speed: 0-100%
- Direction: 72°-180° (horizontal)
- Vertical Angle: 25°-140°
- Throw Interval: User-defined

### FOREHAND Mode
Optimized for forehand practice:
- Vertical angle: 75° (slightly downward)
- Uses user-selected speed and direction
- 2-second interval between shots

### BACKHAND Mode
Optimized for backhand practice:
- Vertical angle: 105° (slightly upward)
- Uses user-selected speed and direction
- 2-second interval between shots

### FOREHAND_BACKHAND Mode
Alternates between forehand and backhand:
- Alternates between 75° and 105° vertical angles
- Adds ±5° random variation for realism
- 2-second interval between shots

### MATCH Mode
Simulates realistic match play:
- Logical shot sequences (center → left → right → cross-court)
- Speed variation: User speed ±15%
- Dynamic vertical angles based on shot sequence
- Random interval: 1.5-3 seconds

### HARDCORE Mode
High-intensity training:
- Speed: 80-100% (random)
- Targets table edges (30°, 60°, 120°, 150°)
- Random vertical angle: 60°-120°
- Fast interval: 1 second

### RANDOM Mode
Unpredictable training:
- Speed: 10-100% (completely random)
- Horizontal: 0-180° (random)
- Vertical: 45°-135° (random)
- Interval: 1-4 seconds (random)

## 🔌 API Endpoints

### HTTP REST API (Port 80)

- `GET /` - Basic status check
- `GET /status` - Current session status (JSON)
- `POST /start` - Start session with JSON parameters
- `POST /stop` - Stop current session
- `GET /statistics` - Session statistics with shot history

### WebSocket (Port 81)

- Real-time bidirectional communication
- Broadcasts session updates every second
- Sends status updates to connected clients

### Example JSON Request

```json
{
  "mode": "MANUAL",
  "speed": 75,
  "direction": 180,
  "verticalAngle": 90,
  "ballCount": 20
}
```

## 📊 Technical Details

### Motor Control
- **ESC PWM Range**: 1000-2000 microseconds
  - 1000 = Stopped
  - 1500 = 50% speed
  - 2000 = 100% speed
- **Servo Range**: 0°-180°
  - Horizontal: 0° = Far Left, 90° = Center, 180° = Far Right
  - Vertical: 0° = Max Down, 90° = Horizontal, 180° = Max Up
- **Stepper**: 8-step sequence, 512 steps = 1 full rotation

### Communication Protocol
- **Protocol**: HTTP REST API + WebSocket
- **Data Format**: JSON
- **Network**: ESP32 Access Point (no internet required)
- **IP Address**: 192.168.4.1
- **Ports**: 80 (HTTP), 81 (WebSocket)

## 👥 Contributors

- **Irakli Berelidze** - Software Development (ESP32 firmware, Android app)
- **Giorgi Abakumovi** - Electromechanical Design (3D printing, circuit assembly)

**Supervisors**: Guga Vardiashvili, Zviad Sulaberidze

**Institution**: Free University of Tbilisi, School of Computer Science, Mathematics, and Engineering (MACS[E])

## 📝 License

This project is open-source and available for educational and personal use.


**Note**: This project was developed as a Junior Project at the Free University of Tbilisi. For detailed technical documentation, see the project report.

