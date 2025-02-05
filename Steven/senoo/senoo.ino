#include "DFRobot_VEML7700.h"
#include <Wire.h>

/*
 * Instantiate an object to drive the sensor
 */
DFRobot_VEML7700 als;

void setup()
{
  Serial.begin(9600);
  als.begin();   // Init
}

void loop()
{
  float lux;
  
  als.getALSLux(lux);   // Get the measured ambient light value
  Serial.print("Lux:");
  Serial.print(lux);
  Serial.println(" lx");
  
  delay(200);
}