# KalorexSmart 1.0

Proyecto nuevo de control local para ESP8266 NodeMCU y servomotor posicional de 0 a 180 grados.

## Contenido
- `app/`: aplicación Android nativa Kotlin.
- `esp8266/KalorexSmart_ESP8266.ino`: firmware para ESP8266 en modo Wi-Fi cliente (STA).

## Preparación del ESP8266
1. Abre el archivo `.ino` en Arduino IDE.
2. Instala el soporte de placas ESP8266 y la biblioteca `Servo` compatible con ESP8266.
3. Cambia `WIFI_SSID` y `WIFI_PASSWORD` en el propio IDE. No publiques esos datos ni los compartas en chats.
4. Selecciona NodeMCU 1.0 (ESP-12E Module), el puerto COM correspondiente y carga el programa.
5. Abre el Monitor Serie a 115200 baudios. Copia la IP que imprime el ESP8266.
6. En la app, escribe esa IP y pulsa **PROBAR CONEXIÓN**.

## Preparación Android
Abre la carpeta completa en Android Studio (Open). Deja que Gradle sincronice y ejecuta la app en un teléfono Android. Para generar APK: Build > Build Bundle(s) / APK(s) > Build APK(s).

## Conexiones del servo
- Señal: D5 (GPIO14).
- GND del servo: GND común con NodeMCU.
- Positivo del servo: fuente regulada externa de 5 V con capacidad suficiente para el servo.
- No alimentar el servo desde 3V3 ni desde el pin VIN de la placa. No unir en paralelo dos fuentes de 5 V independientes.

## Red
El teléfono y el ESP8266 deben estar conectados a la misma red local. Se recomienda reservar la IP del ESP8266 en el router Telmex para que no cambie.

## Seguridad y límites
Este proyecto es un prototipo de banco. No conectarlo a una válvula de gas ni usarlo como control de seguridad de una caldera. Un servo posicional estándar solo ofrece recorrido limitado (aprox. 0–180°); `motor.write()` envía la orden inmediatamente, pero la velocidad física depende del modelo y de la carga. El endpoint `/status` informa el último ángulo ordenado, no una medición real de posición.

## Compilar APK automáticamente con GitHub Actions
1. Sube el contenido de esta carpeta al repositorio GitHub `KalorexSmart` (en la rama `main` o `master`).
2. Abre la pestaña **Actions** del repositorio.
3. Selecciona **Crear APK KalorexSmart** y pulsa **Run workflow** si no se inició automáticamente al subir los archivos.
4. Cuando termine correctamente, abre la ejecución y descarga **KalorexSmart-debug-apk** en la sección **Artifacts**.
5. Descomprime el artefacto y encontrarás `app-debug.apk` para instalar en el teléfono.

Esta compilación genera una APK de depuración para pruebas; no es una versión firmada de distribución.
