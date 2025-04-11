# Application Bluetooth pour ESP32

Cette application Android permet de:
- Se connecter à un ESP32 via Bluetooth
- Recevoir des données de température d'un capteur BMP280
- Recevoir des données de tension depuis le GPIO15 (pin 18)
- Afficher ces données sous forme de courbes (de 0 à 200°C pour la température)

## Configuration requise
- Android Studio
- Un appareil Android avec Bluetooth
- Un ESP32 avec capteur BMP280 configuré

## Étapes d'installation
1. Clonez ce dépôt
2. Ouvrez le projet dans Android Studio
3. Compilez et installez l'application sur votre appareil Android

## Structure du projet
- `app/src/main/java/com/example/bluetoothapp/` - Code source Java/Kotlin
- `app/src/main/res/layout/` - Fichiers de mise en page XML
- `app/src/main/AndroidManifest.xml` - Configuration de l'application 