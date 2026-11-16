/* KalorexSmart - ESP8266 NodeMCU / ESP-12E
   Wi-Fi client (STA), HTTP control, servo signal on D5 / GPIO14.
   Set your Telmex Wi-Fi name and password locally before uploading.
   Servo must have a separate regulated 5 V supply with common GND.
*/
#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Servo.h>

const char* WIFI_SSID = "PON_AQUI_EL_NOMBRE_DE_TU_WIFI";
const char* WIFI_PASSWORD = "PON_AQUI_TU_CONTRASENA";
const uint8_t SERVO_PIN = D5;

ESP8266WebServer server(80);
Servo motor;
int lastAngle = 0;

void handleStatus() {
  String json = String("{\"connected\":true,\"angle\":") + lastAngle +
                String(",\"ip\":\"") + WiFi.localIP().toString() + "\"}";
  server.send(200, "application/json", json);
}

void handleMove() {
  if (!server.hasArg("angulo")) {
    server.send(400, "text/plain", "Falta parametro angulo");
    return;
  }
  int target = constrain(server.arg("angulo").toInt(), 0, 180);
  lastAngle = target;
  motor.write(target); // Orden inmediata; la velocidad fisica depende del servo.
  server.send(200, "text/plain", String("OK ") + target);
}

void setup() {
  Serial.begin(115200);
  motor.attach(SERVO_PIN);
  motor.write(lastAngle);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a Wi-Fi");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 30000) {
    delay(300);
    Serial.print(".");
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.print("IP del ESP8266: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\nNo se pudo conectar. Revisa SSID y contrasena.");
  }

  server.on("/status", HTTP_GET, handleStatus);
  server.on("/mover", HTTP_GET, handleMove);
  server.begin();
  Serial.println("Servidor HTTP listo en puerto 80");
}

void loop() {
  server.handleClient();
}
