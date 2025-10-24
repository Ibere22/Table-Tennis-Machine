#include <ESP32Servo.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>

Servo esc1;
Servo esc2;
Servo horizontalServo;  // Left/Right movement
Servo verticalServo;    // Up/Down movement

const int escPin1 = 5;
const int escPin2 = 18;
const int horizontalPin = 19;  // Horizontal servo (Left/Right)
const int verticalPin = 21;    // Vertical servo (Up/Down)

// Stepper motor pins
const int IN1 = 33;
const int IN2 = 25;
const int IN3 = 26;
const int IN4 = 27;
const int sensorPin = 36; // Digital output pin of Hall sensor


const int START_THROTTLE = 1250;  // default until changed by /throttle
int currentThrottleUs = START_THROTTLE;
int currentHorizontalAngle = 90;  // Center position
int currentVerticalAngle = 90;    // Center position

// Session variables
String currentMode = "";
int totalBalls = 0;
int ballsRemaining = 0;
int currentShot = 0;
unsigned long sessionStartTime = 0;
bool sessionActive = false;
int sessionSpeed = 50;
int sessionDirection = 180;
int sessionVerticalAngle = 90;
bool alternatingMode = false;  // For forehand/backhand alternating

const char* ssid = "TableTennisLauncher";
const char* password = "12345678";

WebServer server(80);

void handleRoot() {
  server.send(200, "text/plain", "OK");
}

void handleStart() {
  Serial.printf("HTTP /start -> throttle %d us\n", currentThrottleUs);
  esc1.writeMicroseconds(currentThrottleUs);
  esc2.writeMicroseconds(currentThrottleUs);
  server.send(200, "application/json", "{\"status\":\"started\"}");
}

void handleStartSession() {
  Serial.println("HTTP /start-session POST");
  
  if (server.hasArg("plain")) {
    String body = server.arg("plain");
    Serial.print("Session body: ");
    Serial.println(body);

    DynamicJsonDocument doc(256);
    DeserializationError err = deserializeJson(doc, body);
    if (!err) {
      String mode = doc["mode"].as<String>();
      int speed = doc["speed"].as<int>();
      int direction = doc["direction"].as<int>();
      int verticalAngle = doc["verticalAngle"].as<int>();
      int ballCount = doc["ballCount"].as<int>();
      
      Serial.printf("Starting session - Mode: %s, Speed: %d%%, Direction: %d°, Vertical: %d°, Balls: %d\n", 
                    mode.c_str(), speed, direction, verticalAngle, ballCount);
      
      // Set session parameters
      currentMode = mode;
      sessionSpeed = speed;
      sessionDirection = direction;
      sessionVerticalAngle = verticalAngle;
      totalBalls = ballCount;
      ballsRemaining = ballCount;
      currentShot = 0;
      sessionStartTime = millis();
      sessionActive = true;
      alternatingMode = false;
      
      // Apply mode-specific settings
      if (mode == "FOREHAND") {
        Serial.println("FOREHAND mode selected - Setting horizontal servo to 72°");
        currentHorizontalAngle = 72;
        horizontalServo.write(72);
        applyThrottleFromPercent(speed);
        Serial.printf("FOREHAND mode: Horizontal servo set to 72°, Vertical servo at %d°, Speed set to %d%% (%d us), Ball count: %d\n", 
                      currentVerticalAngle, speed, currentThrottleUs, ballCount);
      }
      else if (mode == "BACKHAND") {
        Serial.println("BACKHAND mode selected - Setting horizontal servo to 110°");
        currentHorizontalAngle = 110;
        horizontalServo.write(110);
        applyThrottleFromPercent(speed);
        Serial.printf("BACKHAND mode: Horizontal servo set to 110°, Vertical servo at %d°, Speed set to %d%% (%d us), Ball count: %d\n", 
                      currentVerticalAngle, speed, currentThrottleUs, ballCount);
      }
      else if (mode == "FOREHAND_BACKHAND") {
        Serial.println("FOREHAND_BACKHAND alternating mode selected - Starting with forehand (72°)");
        currentHorizontalAngle = 72;
        horizontalServo.write(72);
        alternatingMode = true;
        applyThrottleFromPercent(speed);
        Serial.printf("FOREHAND_BACKHAND mode: Starting with forehand (72°), Vertical servo at %d°, Speed set to %d%% (%d us), Ball count: %d\n", 
                      currentVerticalAngle, speed, currentThrottleUs, ballCount);
      }
      else if (mode == "MANUAL") {
        Serial.println("MANUAL mode selected - Using user parameters");
        currentHorizontalAngle = direction;
        currentVerticalAngle = verticalAngle;
        horizontalServo.write(direction);
        verticalServo.write(verticalAngle);
        applyThrottleFromPercent(speed);
        Serial.printf("MANUAL mode: Horizontal servo %d°, Vertical servo %d°, Speed %d%% (%d us), Ball count: %d\n", 
                      direction, verticalAngle, speed, currentThrottleUs, ballCount);
      }
      
      // Start motors immediately for continuous spinning until session ends
      esc1.writeMicroseconds(currentThrottleUs);
      esc2.writeMicroseconds(currentThrottleUs);
      Serial.printf("Motors started at %d us for continuous spinning until session end\n", currentThrottleUs);
      
      
      server.send(200, "application/json", "{\"status\":\"session_started\",\"mode\":\"" + mode + "\"}");
    } else {
      Serial.print("JSON parse error: ");
      Serial.println(err.c_str());
      server.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
    }
  } else {
    Serial.println("No body present");
    server.send(400, "application/json", "{\"error\":\"No body\"}");
  }
}

void handleStop() {
  Serial.println("HTTP /stop");
  esc1.writeMicroseconds(1000);
  esc2.writeMicroseconds(1000);
  
  // Reset servos to center position
  currentHorizontalAngle = 90;
  currentVerticalAngle = 90;
  horizontalServo.write(90);
  verticalServo.write(90);
  
  // Stop stepper motor feeder
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  
  Serial.println("All systems reset to default positions");
  server.send(200, "application/json", "{\"status\":\"stopped\",\"message\":\"All systems reset to default\"}");
}

void handleStopSession() {
  Serial.println("HTTP /stop-session POST");
  
  // Always stop motors regardless of session state to prevent runaway motors
  esc1.writeMicroseconds(1000);
  esc2.writeMicroseconds(1000);
  Serial.println("Motors stopped (safety measure)");
  
  // Reset servos to default center position (90°)
  currentHorizontalAngle = 90;
  currentVerticalAngle = 90;
  horizontalServo.write(90);
  verticalServo.write(90);
  Serial.println("Servos reset to center position (90°)");
  
  // Stop stepper motor feeder if it's running
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  Serial.println("Stepper motor feeder stopped");
  
  if (sessionActive) {
    Serial.printf("Stopping session - Mode: %s, Total balls: %d, Balls remaining: %d, Current shot: %d\n", 
                  currentMode.c_str(), totalBalls, ballsRemaining, currentShot);
    
    sessionActive = false;
    
    
    unsigned long sessionDuration = (millis() - sessionStartTime) / 1000;
    Serial.printf("Session ended - Duration: %lu seconds\n", sessionDuration);
    
    String response = String("{\"status\":\"session_stopped\",\"mode\":\"") + currentMode + 
                     "\",\"totalBalls\":" + totalBalls + 
                     ",\"ballsRemaining\":" + ballsRemaining + 
                     ",\"currentShot\":" + currentShot + 
                     ",\"sessionDuration\":" + sessionDuration + "}";
    server.send(200, "application/json", response);
  } else {
    Serial.println("No active session to stop, but all systems reset to default");
    server.send(200, "application/json", "{\"status\":\"systems_reset\",\"message\":\"No active session, but all systems reset to default positions\"}");
  }
}

void handleThrowBall() {
  Serial.println("HTTP /throw-ball POST");
  
  if (!sessionActive) {
    Serial.println("No active session - cannot throw ball");
    server.send(400, "application/json", "{\"error\":\"No active session\"}");
    return;
  }
  
  if (ballsRemaining <= 0) {
    Serial.println("No balls remaining - session complete");
    server.send(400, "application/json", "{\"error\":\"No balls remaining\"}");
    return;
  }
  
  currentShot++;
  ballsRemaining--;
  
  // STEP 1: Set servo positions BEFORE feeding the ball
  if (alternatingMode && currentMode == "FOREHAND_BACKHAND") {
    if (currentShot % 2 == 1) {
      // Odd shots: forehand (72°)
      currentHorizontalAngle = 72;
      horizontalServo.write(72);
      Serial.printf("Shot %d: Setting servos for FOREHAND (72°) before feeding ball\n", currentShot);
    } else {
      // Even shots: backhand (110°)
      currentHorizontalAngle = 110;
      horizontalServo.write(110);
      Serial.printf("Shot %d: Setting servos for BACKHAND (110°) before feeding ball\n", currentShot);
    }
  }
  
  // STEP 2: Now feed the ball (servos are already in correct position)
  Serial.printf("Feeder: feeding ball %d with servos at correct position (H:%d°, V:%d°)\n", 
                currentShot, currentHorizontalAngle, currentVerticalAngle);
  feedOneBall();
  
  // STEP 3: Ball shoots from correct position (motors already spinning)
  Serial.printf("Ball thrown! Shot %d/%d, Balls remaining: %d, Horizontal servo: %d°, Vertical servo: %d°, Speed: %d%% (%d us)\n", 
                currentShot, totalBalls, ballsRemaining, currentHorizontalAngle, currentVerticalAngle, sessionSpeed, currentThrottleUs);
  
  // If this was the last ball, stop motors and end session
  if (ballsRemaining == 0) {
    Serial.println("All balls shot - stopping motors and ending session");
    esc1.writeMicroseconds(1000);
    esc2.writeMicroseconds(1000);
    sessionActive = false;
  }
  
  String response = String("{\"status\":\"ball_thrown\",\"currentShot\":") + currentShot + 
                   ",\"ballsRemaining\":" + ballsRemaining + 
                   ",\"horizontalAngle\":" + currentHorizontalAngle + 
                   ",\"verticalAngle\":" + currentVerticalAngle + 
                   ",\"speed\":" + sessionSpeed + "}";
  server.send(200, "application/json", response);
}

void applyThrottleUs(int us) {
  us = constrain(us, 1000, 2000);
  currentThrottleUs = us;
  esc1.writeMicroseconds(us);
  esc2.writeMicroseconds(us);
  Serial.printf("Applied throttle: %d us\n", us);
}

void applyThrottleFromPercent(int percent) {
  percent = constrain(percent, 0, 100);
  int us = 1000 + percent * 10;
  applyThrottleUs(us);
}

void handleThrottlePost() {
  Serial.println("HTTP /throttle POST");
  int us = -1;

  if (server.hasArg("plain")) {
    String body = server.arg("plain");
    Serial.print("Body: ");
    Serial.println(body);

    DynamicJsonDocument doc(128);
    DeserializationError err = deserializeJson(doc, body);
    if (!err) {
      if (doc.containsKey("throttleUs")) {
        us = doc["throttleUs"].as<int>();
        Serial.printf("Parsed JSON throttleUs: %d\n", us);
      } else if (doc.containsKey("percent")) {
        int percent = doc["percent"].as<int>();
        percent = constrain(percent, 0, 100);
        us = 1000 + percent * 10;
        Serial.printf("Parsed percent -> %d us\n", us);
      } else {
        Serial.println("JSON missing throttleUs/percent");
      }
    } else {
      Serial.print("JSON parse error: ");
      Serial.println(err.c_str());
    }
  } else {
    Serial.println("No body present");
  }

  if (us < 0) {
    server.send(400, "application/json", "{\"error\":\"No throttle value\"}");
    return;
  }

  applyThrottleUs(us);
  String res = String("{\"throttleUs\":") + currentThrottleUs + "}";
  server.send(200, "application/json", res);
}

void handleThrottleGet() {
  Serial.println("HTTP /throttle GET");
  if (!server.hasArg("us")) {
    server.send(400, "application/json", "{\"error\":\"Missing us query param\"}");
    return;
  }
  int us = server.arg("us").toInt();
  applyThrottleUs(us);
  String res = String("{\"throttleUs\":") + currentThrottleUs + "}";
  server.send(200, "application/json", res);
}

void handleHorizontalServo() {
  Serial.println("HTTP /servo/horizontal POST");
  int angle = -1;

  if (server.hasArg("plain")) {
    String body = server.arg("plain");
    Serial.print("Body: ");
    Serial.println(body);

    DynamicJsonDocument doc(128);
    DeserializationError err = deserializeJson(doc, body);
    if (!err) {
      if (doc.containsKey("horizontalAngle")) {
        angle = doc["horizontalAngle"].as<int>();
        Serial.printf("Parsed JSON horizontalAngle: %d\n", angle);
      } else {
        Serial.println("JSON missing horizontalAngle");
      }
    } else {
      Serial.print("JSON parse error: ");
      Serial.println(err.c_str());
    }
  } else {
    Serial.println("No body present");
  }

  if (angle < 0) {
    server.send(400, "application/json", "{\"error\":\"No angle value\"}");
    return;
  }

  angle = constrain(angle, 0, 180);
  currentHorizontalAngle = angle;
  horizontalServo.write(angle);
  Serial.printf("Applied horizontal servo: %d degrees\n", angle);
  
  String res = String("{\"horizontalAngle\":") + currentHorizontalAngle + "}";
  server.send(200, "application/json", res);
}

void handleVerticalServo() {
  Serial.println("HTTP /servo/vertical POST");
  int angle = -1;

  if (server.hasArg("plain")) {
    String body = server.arg("plain");
    Serial.print("Body: ");
    Serial.println(body);

    DynamicJsonDocument doc(128);
    DeserializationError err = deserializeJson(doc, body);
    if (!err) {
      if (doc.containsKey("verticalAngle")) {
        angle = doc["verticalAngle"].as<int>();
        Serial.printf("Parsed JSON verticalAngle: %d\n", angle);
      } else {
        Serial.println("JSON missing verticalAngle");
      }
    } else {
      Serial.print("JSON parse error: ");
      Serial.println(err.c_str());
    }
  } else {
    Serial.println("No body present");
  }

  if (angle < 0) {
    server.send(400, "application/json", "{\"error\":\"No angle value\"}");
    return;
  }

  angle = constrain(angle, 0, 180);
  currentVerticalAngle = angle;
  verticalServo.write(angle);
  Serial.printf("Applied vertical servo: %d degrees\n", angle);
  
  String res = String("{\"verticalAngle\":") + currentVerticalAngle + "}";
  server.send(200, "application/json", res);
}

void handleStatus() {
  Serial.println("HTTP /status GET");
  
  String response = String("{\"sessionActive\":") + (sessionActive ? "true" : "false") + 
                   ",\"mode\":\"" + currentMode + "\"" +
                   ",\"totalBalls\":" + totalBalls + 
                   ",\"ballsRemaining\":" + ballsRemaining + 
                   ",\"currentShot\":" + currentShot + 
                   ",\"sessionTime\":" + (sessionActive ? (millis() - sessionStartTime) / 1000 : 0) + 
                   ",\"horizontalAngle\":" + currentHorizontalAngle + 
                   ",\"verticalAngle\":" + currentVerticalAngle + 
                   ",\"speed\":" + sessionSpeed + "}";
  server.send(200, "application/json", response);
}

void handleStatistics() {
  Serial.println("HTTP /statistics GET");
  
  unsigned long sessionDuration = sessionActive ? (millis() - sessionStartTime) / 1000 : 0;
  int ballsPerMinute = (sessionDuration > 0) ? (currentShot * 60 / sessionDuration) : 0;
  
  String response = String("{\"mode\":\"") + currentMode + "\"" +
                   ",\"totalBalls\":" + totalBalls + 
                   ",\"sessionDuration\":" + sessionDuration + 
                   ",\"averageSpeed\":" + sessionSpeed + 
                   ",\"ballsPerMinute\":" + ballsPerMinute + 
                   ",\"shots\":[]}";  // Empty shots array for now
  server.send(200, "application/json", response);
}


void handleReset() {
  Serial.println("HTTP /reset POST");
  
  // Stop all motors
  esc1.writeMicroseconds(1000);
  esc2.writeMicroseconds(1000);
  
  // Reset servos to center position
  currentHorizontalAngle = 90;
  currentVerticalAngle = 90;
  horizontalServo.write(90);
  verticalServo.write(90);
  
  // Stop stepper motor feeder
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  
  // Reset session variables
  sessionActive = false;
  currentMode = "";
  totalBalls = 0;
  ballsRemaining = 0;
  currentShot = 0;
  
  Serial.println("System reset to default state - all motors stopped, servos centered");
  server.send(200, "application/json", "{\"status\":\"reset\",\"message\":\"All systems reset to default state\"}");
}


// ------------------------
// Stepper Feeder (updated with new working code)
// ------------------------
bool paused = false;
unsigned long pauseStart = 0;
int T = 2000; // period (100 nabijis dro)
const unsigned long pauseTime = T / 4; // must be less than period

void step4() {
  digitalWrite(IN1, HIGH); digitalWrite(IN2, LOW);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, LOW);
  delay(T / 100);

  digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, LOW);
  delay(T / 100);

  digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
  digitalWrite(IN3, LOW);  digitalWrite(IN4, HIGH);
  delay(T / 100);

  digitalWrite(IN1, HIGH); digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);  digitalWrite(IN4, HIGH);
  delay(T / 100);
}

// Feed exactly one ball using the new working stepper code
void feedOneBall() {
  bool pauseBegan = false;
  bool pauseEnded = false;
  paused = false;
  
  while (!pauseEnded && sessionActive) {  // Added sessionActive check
    int hallValue = digitalRead(sensorPin); // read digital signal (0 or 1)

    // Magnet detected (assuming sensor outputs LOW when magnet is near)
    if (hallValue == HIGH) {
      
      if (!paused) {
        delay(1000);
        paused = true;
        pauseStart = millis();
        pauseBegan = true;
      }
    }

    if (paused) {
      // check if pause time has passed
      if (millis() - pauseStart >= pauseTime) {
        paused = false; // end pause
        if (pauseBegan) {
          pauseEnded = true; // one complete feed cycle finished
        }
      }
    }

    // perform one step regardless of pause state
    step4();
  }
  
  // If session was stopped mid-feed, stop the stepper motor
  if (!sessionActive) {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, LOW);
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, LOW);
    Serial.println("Feed interrupted - stepper stopped");
  }
}

void setup() {
  Serial.begin(115200);

  // Initialize ESCs
  esc1.setPeriodHertz(50);
  esc2.setPeriodHertz(50);
  esc1.attach(escPin1, 1000, 2000);
  esc2.attach(escPin2, 1000, 2000);

  // Initialize servos
  horizontalServo.attach(horizontalPin);
  verticalServo.attach(verticalPin);
  
  // Set servos to center position
  horizontalServo.write(currentHorizontalAngle);
  verticalServo.write(currentVerticalAngle);

  Serial.println("ESCs Arming process!");
  esc1.writeMicroseconds(1000);
  esc2.writeMicroseconds(1000);
  delay(3000);
  Serial.println("ESCs Armed!");
  Serial.println("Servos initialized to center position (90°)");

  // Initialize stepper feeder pins
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  pinMode(sensorPin, INPUT_PULLUP);
  
  // Ensure stepper motor is stopped initially
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  Serial.println("Stepper motor initialized and stopped");
  

  WiFi.softAP(ssid, password);
  Serial.print("AP IP: "); Serial.println(WiFi.softAPIP());

  server.on("/", handleRoot);
  server.on("/start", HTTP_POST, handleStart);
  server.on("/stop", HTTP_POST, handleStop);
  server.on("/reset", HTTP_POST, handleReset);
  server.on("/start-session", HTTP_POST, handleStartSession);
  server.on("/stop-session", HTTP_POST, handleStopSession);
  server.on("/throw-ball", HTTP_POST, handleThrowBall);
  server.on("/status", HTTP_GET, handleStatus);
  server.on("/statistics", HTTP_GET, handleStatistics);
  server.on("/throttle", HTTP_POST, handleThrottlePost);
  server.on("/throttle", HTTP_GET, handleThrottleGet);
  server.on("/servo/horizontal", HTTP_POST, handleHorizontalServo);
  server.on("/servo/vertical", HTTP_POST, handleVerticalServo);
  server.begin();
  Serial.println("HTTP server started");
}

void loop() {
  server.handleClient();
}
