#include <BluetoothSerial.h>
#include <Wire.h>
#include <Adafruit_BMP280.h>

// Pin pour la mesure de tension
#define VOLTAGE_PIN 15  // GPIO15 (pin 18)

// Configuration du diviseur de tension
// Si R1 = 10k et R2 = 3k, le facteur de division est (R1 + R2) / R2 = 4.33
const float VOLTAGE_DIVIDER_FACTOR = 4.43; // Ajustez cette valeur selon votre diviseur réel
const float REFERENCE_VOLTAGE = 3.3; // Tension de référence de l'ADC
const float ADC_RESOLUTION = 4095.0; // Résolution de l'ADC de l'ESP32 (12 bits)

// Configuration de la batterie
const float BATTERY_MAX_VOLTAGE = 14.0; // Tension max de la batterie
const float BATTERY_MIN_VOLTAGE = 11.0; // Tension min de la batterie

// Initialisation du BlueTooth
BluetoothSerial SerialBT;

// Initialisation du capteur BMP280
Adafruit_BMP280 bmp;

void setup() {
  // Initialisation de la communication série
  Serial.begin(115200);
  
  // Initialisation du Bluetooth
  SerialBT.begin("ESP32_BatteryMonitor");
  Serial.println("L'appareil Bluetooth est prêt à être jumelé");
  
  // Initialisation du BMP280
  if (!bmp.begin(0x76)) {
    Serial.println("Impossible de trouver un capteur BMP280");
    // Continuer quand même, la tension fonctionnera toujours
  } else {
    Serial.println("Capteur BMP280 détecté");
  }
  
  // Configuration du capteur
  bmp.setSampling(Adafruit_BMP280::MODE_NORMAL,     /* Mode de fonctionnement */
                  Adafruit_BMP280::SAMPLING_X2,     /* Temp. oversampling */
                  Adafruit_BMP280::SAMPLING_X16,    /* Pressure oversampling */
                  Adafruit_BMP280::FILTER_X16,      /* Filtering. */
                  Adafruit_BMP280::STANDBY_MS_500); /* Standby time. */
  
  // Configuration du pin pour la mesure de tension
  pinMode(VOLTAGE_PIN, INPUT);
}

void loop() {
  // Lecture de la température
  float temperature = bmp.readTemperature();
  
  // Lecture de la tension
  float adcValue = analogRead(VOLTAGE_PIN);
  float measuredVoltage = (adcValue / ADC_RESOLUTION) * REFERENCE_VOLTAGE;
  
  // Calcul de la tension réelle de la batterie
  float batteryVoltage = measuredVoltage * VOLTAGE_DIVIDER_FACTOR;
  
  // Calcul du pourcentage de batterie
  int batteryPercentage = calculateBatteryPercentage(batteryVoltage);
  
  // Affichage des données en série pour le débogage
  Serial.print("Température: ");
  Serial.print(temperature);
  Serial.print(" °C, Tension mesurée: ");
  Serial.print(measuredVoltage);
  Serial.print(" V, Tension batterie: ");
  Serial.print(batteryVoltage);
  Serial.print(" V, Batterie: ");
  Serial.print(batteryPercentage);
  Serial.println(" %");
  
  // Envoi des données via Bluetooth
  // Le format est "T:25.5,V:3.3" car c'est ce que l'application Android attend
  // Mise à jour pour inclure le pourcentage de batterie: "T:25.5,V:3.3,B:75"
  String data = "T:" + String(temperature, 1) + ",V:" + String(measuredVoltage, 2) + ",B:" + String(batteryPercentage);
  SerialBT.println(data);
  
  // Attendre un peu
  delay(1000);
}

// Fonction pour calculer le pourcentage de batterie
int calculateBatteryPercentage(float voltage) {
  if (voltage >= BATTERY_MAX_VOLTAGE) {
    return 100;
  } else if (voltage <= BATTERY_MIN_VOLTAGE) {
    return 0;
  } else {
    return (int)((voltage - BATTERY_MIN_VOLTAGE) * 100.0 / (BATTERY_MAX_VOLTAGE - BATTERY_MIN_VOLTAGE));
  }
} 