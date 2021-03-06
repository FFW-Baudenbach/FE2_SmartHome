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

## Provided schedule

### Switch Off
A configurable optional task takes care to automatically shut off the switch device after a given period in time.  
If a motion was detected by a Motion Detector, then this time will be prolonged by a configurable amount of minutes if a motion was detected in the last same amount of minutes.  
If there is an active calendar event, switch will stay on until the event is finished.

### Switch On
A configurable optional task grabs an ICS calendar file from Internet and checks it for active events.  
If an event is active and the title/location matches a given pattern, the switch will be turned on.

## Usage
The following environment variables need to be provided:
* fritzbox.url
* fritzbox.username
* fritzbox.password
* fritzbox.switchid


The following environment variables are optional:
* schedule.fixedDelayMinutes (default: empty => off)
* schedule.switchoff.defaultSwitchOnMinutes (default: 60)
* schedule.switchoff.defaultMotionMinutes (default: 10, 0 = off)
* schedule.switchon.calendar.url (default: empty => off)
* schedule.switchon.calendar.titleRegex (default: .*)
* schedule.switchon.calendar.locationRegex (default: .*)