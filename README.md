# FE2_SmartHome
The purpose of this service is to provide a simple REST API (GET-Requests) to switch the power state of [AVM Fritz!DECT](https://avm.de/produkte/fritzdect/) devices on and off.  
Reason for developing is to be able to enable TV-Screen for Alamos AlarmMonitor in case of fire alarm to display relevant information.  
Thereby, the alarm pipeline will call the endpoint to turn on the power of the device.

## Reason for AVM solution
Reason to use the AVM SmartHome solution is the fact that already a Fritz!Box Router is running in our fire department. This router natively supports those SmartHome gadgets which fulfill our needs completly without adding complexity.

## How it works
Implementation is developed with SpringBoot and using the library [kaklakariada/fritzbox-java-api](https://github.com/kaklakariada/fritzbox-java-api) to communicate with the FritzBox AHA Interface.

## Provided endpoints
All endpoints are simple GET requests which always return 200 OK to easen integration into Alamos FE2. It prints some information like switch state or error messages in text/plain style.  
Following apis are implemented: 
* /switchDeviceApi/toggle  
* /switchDeviceApi/switchOn
* /switchDeviceApi/switchOff
* /actuator/info
* /actuator/health  
  Checks if configured device is present! 

## Usage
The following environment variables need to be provided:
* fritzbox.url
* fritzbox.username
* fritzbox.password
* fritzbox.switchid
