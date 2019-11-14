/**
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *  2017 
 */

metadata {
    definition(name: "Xiaomi Aqara Roller Shutter ", namespace: "ShinJjang", author: "ShinJjang", ocfDeviceType: "oic.d.blind") {
        capability "Window Shade"
//      capability "Window Shade Preset"
        capability "Switch Level"
        capability "Switch"
        capability "Actuator"
        capability "Health Check"
        capability "Sensor"
        capability "Refresh"
      
        command "levelOpenClose"
        command "shadeAction"
        command "pause"       

        fingerprint endpointId: "0x01", profileId: "0104", deviceId: "0202", inClusters: "0000, 0004, 0003, 0005, 000A, 0102, 000D, 0013", outClusters: "000A", manufacturer: "LUMI", model: "lumi.curtain.aq2", deviceJoinName: "Xiaomi Blind"
    }

    preferences {
    }    

    tiles(scale: 2) {
        multiAttributeTile(name: "windowShade", type: "generic", width: 6, height: 4) {
            tileAttribute("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState("closed", label: 'closed', action: "windowShade.open", icon: "st.doors.garage.garage-closed", backgroundColor: "#A8A8C6", nextState: "opening")
                attributeState("open", label: 'open', action: "windowShade.close", icon: "st.doors.garage.garage-open", backgroundColor: "#F7D73E", nextState: "closing")
                attributeState("closing", label: '${name}', action: "windowShade.open", icon: "st.contact.contact.closed", backgroundColor: "#B9C6A8")
                attributeState("opening", label: '${name}', action: "windowShade.close", icon: "st.contact.contact.open", backgroundColor: "#D4CF14")
                attributeState("partially open", label: 'partially\nopen', action: "windowShade.close", icon: "st.doors.garage.garage-closing", backgroundColor: "#D4ACEE", nextState: "closing")
            }
            tileAttribute("device.level", key: "SLIDER_CONTROL") {
                attributeState("level", action: "setLevel")
            }
        }
        standardTile("open", "open", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("open", label: 'open', action: "windowShade.open", icon: "st.contact.contact.open")
        }
        standardTile("close", "close", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("close", label: 'close', action: "windowShade.close", icon: "st.contact.contact.closed")
        }
        standardTile("stop", "stop", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state("stop", label: 'stop', action: "pause", icon: "st.illuminance.illuminance.dark")
        }
        standardTile("refresh", "command.refresh", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label: " ", action: "refresh.refresh", icon: "https://www.shareicon.net/data/128x128/2016/06/27/623885_home_256x256.png"
        }
 //       standardTile("home", "device.level", width: 2, height: 2, decoration: "flat") {
 //           state "default", label: "home", action:"presetPosition", icon:"st.Home.home2"
 //       }

        main(["windowShade"])
        details(["windowShade", "open", "stop", "close", "refresh"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    def parseMap = zigbee.parseDescriptionAsMap(description)
   log.debug "parseMap: ${parseMap}"
    def event = zigbee.getEvent(description)

    try {
        def windowShadeStatus = ""
        def curtainLevel = null

        if (parseMap["cluster"] == "000D" && parseMap["attrId"] == "0055") {
            long theValue = Long.parseLong(parseMap["value"], 16)
            float floatValue = Float.intBitsToFloat(theValue.intValue());

            if(parseMap.raw.endsWith("00000000") || parseMap["size"] =="16") { //&& parseMap["size"] == "28") {    
                //log.debug "long => ${theValue}, float => ${floatValue}"
                curtainLevel = floatValue.intValue()
                log.debug "level => ${curtainLevel}"
            } else if (parseMap.raw.endsWith("00000000") || parseMap["size"] == "1C") {
                if (curtainLevel == 100) {
                    log.debug "Just Fully Open"
                    windowShadeStatus = "open"
                    curtainLevel = 100
                } else if (curtainLevel > 0) {
                    log.debug curtainLevel + '% Partially Open'
                    windowShadeStatus = "partially open"
                    curtainLevel = curtainLevel
                } else {
                    log.debug "Just Closed"
                    windowShadeStatus = "closed"
                    curtainLevel = 0
                }

                def eventStack = []

                if (parseMap.additionalAttrs[0].value.startsWith("03")) {  //A68E00
                    eventStack.push(createEvent(name: "windowShade", value: windowShadeStatus as String))
                }
                
                //eventStack.push(createEvent(name:"windowShade", value: windowShadeStatus as String))
                eventStack.push(createEvent(name:"level", value: curtainLevel))
                eventStack.push(createEvent(name:"switch", value: (windowShadeStatus == "closed" ? "off" : "on")))
                return eventStack                
            } else {
                log.debug "running…"
            }
        } else if (parseMap["clusterId"] == "0102"){
            if (parseMap["attrId"] == "0008") {
                long endValue = Long.parseLong(parseMap["value"], 16)
                curtainLevel = endValue
                //log.debug "endValuecurtainLevel=>${curtainLevel}"
            } else if(parseMap["command"] == "0B") {
                log.debug "stopped"
            }
        } else if (parseMap["clusterId"] == "0000" && parseMap["encoding"] == "42") {
            /*
            def valueData = parseMap["value"]
            def eventStack = []
            def position = valueData[36,37];
            //log.debug "check position = ${position}"
            String hexposition = position
            long endValue = Long.parseLong(hexposition, 16)
            curtainLevel = endValue
            log.debug "check position = ${position}, check Level = ${curtainLevel}"
            */
        } else if (parseMap.raw.startsWith("0104")) {
            log.debug "Xiaomi Curtain"
            runIn(20, checkP)
          //  log.debug "Unhandled Event - description:${description}, parseMap:${parseMap}, event:${event}"
        } else if (parseMap.raw.endsWith("0007")) {
            log.debug "Unhandled Event - description:${description}, parseMap:${parseMap}, event:${event}"
        }
        else {
            log.debug "Unhandled Event - description:${description}, parseMap:${parseMap}, event:${event}"
        }                
    } catch (Exception e) {
        log.warn e
    }
}

def updated() {
    sendEvent(name: "openlevel", value: openInt)
} 

def on() {
    setLevel(100)
}

def off() {
    setLevel(0)
}

def close() {
    setLevel(0)
}

def open() {
    setLevel(100)
}

def pause() {
    log.info "pause()"
    zigbee.command(0x0102, 0x02)
}

def setLevel(level) {
    if (level == null) {level = 0}
    level = level as int
    Integer  currentLevel = device.currentValue("level")

    if (level > currentLevel) {
        sendEvent(name: "windowShade", value: "opening")
    } else if (level < currentLevel) {
        sendEvent(name: "windowShade", value: "closing")
    }

    String hex = Integer.toHexString(Float.floatToIntBits(level)).toUpperCase()
    if (level == 100) {
        log.info "open()"
        zigbee.writeAttribute(0x000d, 0x0055, 0x39, hex)
    } else if(level > 0) {
        log.info "Set Level: ${level}%"
        zigbee.writeAttribute(0x000d, 0x0055, 0x39, hex)
    } else {
        log.info "close()"
        zigbee.writeAttribute(0x000d, 0x0055, 0x39, hex)
    } 
}

def ping() {
    return refresh()
}

def refresh() {
    log.debug "refresh()"
     zigbee.readAttribute(0x000d, 0x0055)
}