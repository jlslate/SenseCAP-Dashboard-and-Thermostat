/*
 * This is free and unencumbered software released into the public domain.
 * For more information, please refer to <https://unlicense.org>
 */

/**
 * SenseCAP Dashboard and Thermostat Driver v1.0.0
 *
 * Hubitat driver for the SenseCAP Indicator D1 (480x480) running openHASP firmware.
 * Communicates via MQTT. Up to 6 pages, each independently configurable.
 *
 * Key features:
 * - Up to 6 pages: smoke / motion / water / contact / lock / garage / light / thermostat / mixed
 * - Grid auto-sized 1x1 to 5x5 based on device count (1, 4, 9, 16, 25 slots)
 * - Tile rearrangement via slot position numbers in app
 * - Page display order adjustable in app
 * - Lock, garage door, and light tiles: tap-to-toggle via openHASP button events
 * - Motion/smoke/water/contact: red-to-inactive color fade on clear
 * - Icons centered top, labels bottom-aligned per tile
 * - Pages rendered sequentially off-screen; navigated to only after fully built (no flash)
 * - clearpage all at push start prevents stale objects from previous push
 * - Backlight management: idle timeout, motion-triggered on/off, touch timeout
 * - safePub() wraps all MQTT publishes -- auto-reconnects on drop
 * - Thermostat tap IPC via device event (no hub variable required)
 *
 * Author: jlslate
 * Version: 1.2.0
 */

import groovy.transform.Field

metadata {
    definition(
        name: "SenseCAP Dashboard and Thermostat",
        namespace: "jlslate",
        author: "jlslate (slate)",
        description: "Auto-paged sensor monitor -- Smoke/Motion/Water/Contact each get their own page"
    ) {
        capability "Initialize"
        capability "Actuator"

        command "reconnectMqtt"
        command "rebootDisplay"
        command "pushAllLayouts", [[name:"numberOfPages", type:"NUMBER"]]
        command "setNumberOfPages", [[name:"n", type:"NUMBER"]]

        command "setPage1GridLayout", [[name:"g", type:"STRING"]]
        command "setPage2GridLayout", [[name:"g", type:"STRING"]]
        command "setPage3GridLayout", [[name:"g", type:"STRING"]]
        command "setPage4GridLayout", [[name:"g", type:"STRING"]]
        command "setPage5GridLayout", [[name:"g", type:"STRING"]]
        command "setPage6GridLayout", [[name:"g", type:"STRING"]]

        command "setPage1MotionActive",   [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage1MotionInactive", [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage1SlotEmpty",      [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage2MotionActive",   [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage2MotionInactive", [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage2SlotEmpty",      [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage3MotionActive",   [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage3MotionInactive", [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage3SlotEmpty",      [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage4MotionActive",   [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage4MotionInactive", [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage4SlotEmpty",      [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage5MotionActive",   [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage5MotionInactive", [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage5SlotEmpty",      [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage6MotionActive",   [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage6MotionInactive", [[name:"sensorIndex", type:"NUMBER"]]
        command "setPage6SlotEmpty",      [[name:"sensorIndex", type:"NUMBER"]]

        command "updatePage1Labels",    [[name:"labels",    type:"JSON_OBJECT"]]
        command "updatePage2Labels",    [[name:"labels",    type:"JSON_OBJECT"]]
        command "updatePage3Labels",    [[name:"labels",    type:"JSON_OBJECT"]]
        command "updatePage4Labels",    [[name:"labels",    type:"JSON_OBJECT"]]
        command "updatePage5Labels",    [[name:"labels",    type:"JSON_OBJECT"]]
        command "updatePage6Labels",    [[name:"labels",    type:"JSON_OBJECT"]]
        command "updatePage1SlotTypes", [[name:"slotTypes", type:"JSON_OBJECT"]]
        command "updatePage2SlotTypes", [[name:"slotTypes", type:"JSON_OBJECT"]]
        command "updatePage3SlotTypes", [[name:"slotTypes", type:"JSON_OBJECT"]]
        command "updatePage4SlotTypes", [[name:"slotTypes", type:"JSON_OBJECT"]]
        command "updatePage5SlotTypes", [[name:"slotTypes", type:"JSON_OBJECT"]]
        command "updatePage6SlotTypes", [[name:"slotTypes", type:"JSON_OBJECT"]]

        // Thermostat commands (one set per page -- app calls these directly)
        (1..6).each { pg ->
            command "pushThermostatPage",         [[name:"page", type:"NUMBER"], [name:"data", type:"JSON_OBJECT"]]
            command "updateThermostatDisplay",    [[name:"page", type:"NUMBER"], [name:"data", type:"JSON_OBJECT"]]
        }
        attribute "thermostatTapped", "string"

        // Register thermostat device so driver can handle taps directly
        command "setPage1ThermostatDevice", [[name:"deviceId", type:"STRING"]]
        command "setPage2ThermostatDevice", [[name:"deviceId", type:"STRING"]]
        command "setPage3ThermostatDevice", [[name:"deviceId", type:"STRING"]]
        command "setPage4ThermostatDevice", [[name:"deviceId", type:"STRING"]]
        command "setPage5ThermostatDevice", [[name:"deviceId", type:"STRING"]]
        command "setPage6ThermostatDevice", [[name:"deviceId", type:"STRING"]]

        attribute "mqttStatus",        "string"
        attribute "displayRebooted",   "string"
        attribute "layoutPushComplete","string"
        attribute "pushInProgress",    "string"
        attribute "lightTapped",       "string"

        (1..6).each { pg ->
            attribute "page${pg}GridLayout", "string"
        }
    }

    preferences {
        input name: "mqttBroker",   type: "text",     title: "<b>MQTT Broker</b> (host:port)", required: true, defaultValue: "tcp://127.0.0.1:1883"
        input name: "mqttPassword", type: "password", title: "MQTT Password", required: true,
              description: "Found in Hubitat -> Integrations -> MQTT Broker"
        input name: "mqttClientId", type: "text",     title: "MQTT Client ID", required: true, defaultValue: "hubitat-sensecap-dashboard"
        input name: "haspNode",     type: "text",     title: "<b>openHASP Node Name</b>", required: true, defaultValue: "plate"

        input name: "colorActive",        type: "enum", title: "<b>Active color</b>",     options: activeColorOptions(), defaultValue: "#FF0000", required: true
        input name: "colorInactive",      type: "enum", title: "Inactive -- Motion",       options: colorOptions(),       defaultValue: "#008000", required: true
        input name: "colorContactInactive",type:"enum", title: "Inactive -- Contact",      options: colorOptions(),       defaultValue: "#00FFFF", required: true
        input name: "colorWaterInactive", type: "enum", title: "Inactive -- Water",        options: colorOptions(),       defaultValue: "#0000FF", required: true
        input name: "colorSmokeInactive", type: "enum", title: "Inactive -- Smoke",        options: colorOptions(),       defaultValue: "#808080", required: true
        input name: "colorLightInactive", type: "enum", title: "Inactive -- Light off",    options: colorOptions(),       defaultValue: "#808080", required: true
        input name: "colorLightActive",   type: "enum", title: "Active -- Light on",       options: colorOptions(),       defaultValue: "#FFFF00", required: true
        input name: "colorLockInactive",  type: "enum", title: "Inactive -- Lock closed",  options: colorOptions(),       defaultValue: "#1B5E20", required: true
        input name: "colorLockOpen",      type: "enum", title: "Active -- Lock open",      options: colorOptions(),       defaultValue: "#E65100", required: true
        input name: "colorGarageInactive",type: "enum", title: "Inactive -- Garage closed",options: colorOptions(),       defaultValue: "#1B5E20", required: true
        input name: "colorGarageOpen",    type: "enum", title: "Active -- Garage open",    options: colorOptions(),       defaultValue: "#E65100", required: true

        input name: "colorThermostatHeating", type: "enum", title: "Thermostat -- Heating",     options: colorOptions(), defaultValue: "#FF6600", required: true
        input name: "colorThermostatCooling", type: "enum", title: "Thermostat -- Cooling",     options: colorOptions(), defaultValue: "#0088FF", required: true
        input name: "colorThermostatIdle",    type: "enum", title: "Thermostat -- Idle",        options: colorOptions(), defaultValue: "#606060", required: true
        input name: "colorThermostatOff",     type: "enum", title: "Thermostat -- Off",         options: colorOptions(), defaultValue: "#303030", required: true
        input name: "colorThermostatFan",     type: "enum", title: "Thermostat -- Fan only",    options: colorOptions(), defaultValue: "#008080", required: true

        input name: "fadeDuration",           type: "number", title: "Fade duration (seconds)",                                              defaultValue: 30,  required: true
        input name: "showPageIndicator",      type: "bool",   title: "Show page indicator (e.g. 1/4)",                                      defaultValue: true
        input name: "rotationInterval",       type: "number", title: "Auto-scroll pages every (seconds, 0 = off)",                          defaultValue: 10
        input name: "backlightOnMotion",      type: "bool",   title: "<b>Backlight ON</b> when sensor active",                              defaultValue: true
        input name: "backlightOffDelay",      type: "number", title: "Backlight OFF after all clear (seconds, 0=never)",                    defaultValue: 0
        input name: "motionBacklightTimeout", type: "number", title: "Backlight OFF after active for (seconds, 0=never)",                   defaultValue: 60
        input name: "touchBacklightTimeout",  type: "number", title: "Backlight OFF after screen tap (seconds, 0=never)",                   defaultValue: 30
        input name: "idleTimeout",            type: "number", title: "Blank display after idle (seconds, 0=never)",                                                                    defaultValue: 300
        input name: "logLevel",               type: "enum",   title: "Logging Level",
              options: ["0":"None","1":"Info only","2":"Info + Debug"], defaultValue: "1", required: true

        // Thermostat devices -- one per page, set automatically by the app
        input name: "thermostatPage1", type: "capability.thermostat", title: "Thermostat -- Page 1", required: false, multiple: false
        input name: "thermostatPage2", type: "capability.thermostat", title: "Thermostat -- Page 2", required: false, multiple: false
        input name: "thermostatPage3", type: "capability.thermostat", title: "Thermostat -- Page 3", required: false, multiple: false
        input name: "thermostatPage4", type: "capability.thermostat", title: "Thermostat -- Page 4", required: false, multiple: false
        input name: "thermostatPage5", type: "capability.thermostat", title: "Thermostat -- Page 5", required: false, multiple: false
        input name: "thermostatPage6", type: "capability.thermostat", title: "Thermostat -- Page 6", required: false, multiple: false
    }
}

private Map activeColorOptions() {
    ["#FF0000":"Red","#FF4500":"Orange-red","#FF8C00":"Dark orange","#FF1493":"Deep pink",
     "#8B0000":"Dark red","#FF6347":"Tomato","#DC143C":"Crimson","#FF0080":"Hot magenta"]
}

private Map colorOptions() {
    ["#F8F8FF":"Ghost White","#D3D3D3":"Light Gray","#808080":"Gray","#800000":"Maroon",
     "#FF00FF":"Magenta","#800080":"Purple","#0000FF":"Blue","#000080":"Navy","#00FFFF":"Cyan",
     "#008080":"Teal","#00FF00":"Lime","#008000":"Green","#FFFF00":"Yellow","#808000":"Olive"]
}

// ── Object ID helpers ──────────────────────────────────────────────────────────

private int bgId(int slot)   { slot }

// ── State key helpers ──────────────────────────────────────────────────────────

private String stateKey(int page, int idx) { "p${page}sensor${idx}" }
private String typeKey(int page, int idx)  { "p${page}slotType${idx}" }
private String labelKey(int page, int idx) { "p${page}label${idx}" }

// ── Lifecycle ──────────────────────────────────────────────────────────────────

def installed() {
    infoLog "[Dashboard] Driver installed"
    initialize()
}

def updated() {
    infoLog "[Dashboard] Preferences updated"
    initialize()
}

def initialize() {
    String mqttSt = device.currentValue("mqttStatus") ?: ""
    if (!mqttSt.startsWith("Connected")) {
        connectMqtt()
    } else {
        infoLog "[Dashboard] MQTT already connected -- skipping reconnect"
    }
    unschedule("sendHeartbeat")
    runEvery1Minute("sendHeartbeat")
    scheduleIdleTimeout()
}

def uninstalled() {
    disconnectMqtt()
}

// ── Grid config ────────────────────────────────────────────────────────────────

def setNumberOfPages(n) {
    int num = Math.min(6, Math.max(1, (n as int)))
    state.numberOfPages = num
    infoLog "[Dashboard] Number of pages set to ${num}"
}

def setPage1GridLayout(String g) {
    applyGridLayout(1, g)
}

def setPage2GridLayout(String g) {
    applyGridLayout(2, g)
}

def setPage3GridLayout(String g) {
    applyGridLayout(3, g)
}

def setPage4GridLayout(String g) {
    applyGridLayout(4, g)
}

def setPage5GridLayout(String g) {
    applyGridLayout(5, g)
}

def setPage6GridLayout(String g) {
    applyGridLayout(6, g)
}

private void applyGridLayout(int page, String g) {
    state["page${page}GridLayout"] = g
    state["page${page}MaxSlots"] = maxSlotsForGrid(g)
    sendEvent(name: "page${page}GridLayout", value: g)
    infoLog "[Dashboard] Page ${page} grid -> ${g}"
    (1..25).each { s -> state.remove("p${page}label${s}") }
}

private int maxSlotsForGrid(String g) {
    switch (g) {
        case "thermostat": return 4
        case "1x1": return 1
        case "3x3": return 9
        case "4x4": return 16
        case "5x5": return 25
        default:    return 4   // 2x2
    }
}

private String activeGrid(int page) {
    return (state["page${page}GridLayout"] ?: "2x2") as String
}

private int maxSensors(int page) {
    int stored = (state["page${page}MaxSlots"] ?: 0) as int
    if (stored > 0) return stored
    return maxSlotsForGrid(activeGrid(page))
}

// Returns the text_font integer the layout uses for btn tiles on a given grid.
// All icon overlays and light tile rebuilds must use the same value.
private int tileFontFor(String grid) {
    switch (grid) {
        case "thermostat": return 24
        case "1x1": return 32
        case "2x2": return 40
        case "3x3": return 32
        case "4x4": return 16
        case "5x5": return 12
        default:    return 16
    }
}

// Icon font size for the value_str overlay on each grid size
private int iconFontFor(String grid) {
    switch (grid) {
        case "1x1": return 48
        case "2x2": return 40
        case "3x3": return 32
        case "4x4": return 24
        case "5x5": return 24
        default:    return 32
    }
}

// Icon in value_str, centered horizontally, near top of tile.
// Offset from object center: negative = up.
private int[] iconOffsetsFor(String grid) {
    int tileH, iconFont
    switch (grid) {
        case "1x1": tileH=476; iconFont=48; break
        case "2x2": tileH=236; iconFont=40; break
        case "3x3": tileH=157; iconFont=32; break
        case "4x4": tileH=117; iconFont=24; break
        case "5x5": tileH= 94; iconFont=24; break
        default:    tileH=157; iconFont=32
    }
    int ofsX = 0
    int iconCenterY = (int)(tileH * 0.16)
    int ofsY = iconCenterY - (int)(tileH / 2)
    return [ofsX, ofsY] as int[]
}

// Smaller font for the label
private int labelFontFor(String grid) {
    switch (grid) {
        case "1x1": return 28
        case "2x2": return 22
        case "3x3": return 18
        case "4x4": return 16
        case "5x5": return 14
        default:    return 16
    }
}

// pad_top reserves space for the icon and pushes centered label into the lower portion of the tile
private int labelPadTopFor(String grid) {
    switch (grid) {
        case "1x1": return 280
        case "2x2": return 106
        case "3x3": return  70
        case "4x4": return  52
        case "5x5": return  42
        default:    return  70
    }
}

// All MQTT publishes go through safePub.
// On failure, swallow silently. mqttClientStatus fires exactly once when the
// broker drops and handles reconnect. Any sendEvent or runIn here floods the
// log and prevents the reconnect timer from ever firing.
private void safePub(String topic, String payload, int qos = 1, boolean retained = false) {
    try {
        interfaces.mqtt.publish(topic, payload, qos, retained)
    } catch (Exception e) {
        // Silent -- mqttClientStatus handles reconnect
    }
}

def connectMqtt() {
    if (!settings.mqttPassword) { infoLog "[Dashboard] MQTT password not set"; return }
    try {
        String broker   = settings.mqttBroker   ?: "tcp://127.0.0.1:1883"
        String baseId   = settings.mqttClientId ?: "hubitat-sensecap-dashboard"
        String clientId = baseId + "-" + device.id
        interfaces.mqtt.connect(broker, clientId, "hubitat", settings.mqttPassword)
        infoLog "[Dashboard] MQTT connected -> ${broker}"
        sendEvent(name: "mqttStatus", value: "Connected")
        String node = settings.haspNode ?: "plate"
        interfaces.mqtt.subscribe("hasp/+/LWT")
        interfaces.mqtt.subscribe("hasp/+/state/+")
        interfaces.mqtt.subscribe("hasp/${node}/event/#")
        infoLog "[Dashboard] Subscribed -- node: ${node}"
        pushIdleConfig()
        // Resume any push that was deferred due to disconnect
        if (state.deferredPages) {
            infoLog "[Dashboard] Resuming deferred push (${state.deferredPages} pages)"
            runIn(3, "resumeDeferredPush")
        }
    } catch (Exception e) {
        infoLog "[Dashboard] ERROR -- MQTT connect failed: ${e.message}"
        sendEvent(name: "mqttStatus", value: "Error: ${e.message}")
        runIn(30, "connectMqtt")
    }
}

def disconnectMqtt() {
    try { interfaces.mqtt.disconnect() } catch (Exception e) { }
    sendEvent(name: "mqttStatus", value: "Disconnected")
}

def resumeDeferredPush() {
    int np = (state.deferredPages ?: 0) as int
    if (np < 1) return
    infoLog "[Dashboard] Resuming deferred pushAllLayouts (${np} pages)"
    pushAllLayouts(np)
}

def reconnectMqtt() {
    disconnectMqtt()
    pauseExecution(2000)
    connectMqtt()
}

def rebootDisplay() {
    String node = settings.haspNode ?: "plate"
    infoLog "[Dashboard] Sending reboot command to display"
    safePub("hasp/${node}/command", "reboot")
}

def mqttClientStatus(String status) {
    infoLog "[Dashboard] MQTT status: ${status}"
    sendEvent(name: "mqttStatus", value: status)
    if (status.startsWith("Error") || status.contains("lost")) {
        infoLog "[Dashboard] MQTT lost -- reconnecting in 5s"
        runIn(5, "connectMqtt")
    }
}

def sendHeartbeat() {
    state.lastHeartbeatMs = now()
    boolean connected = false
    try { connected = interfaces.mqtt.isConnected() } catch (Exception e) { connected = false }
    if (!connected) {
        infoLog "[Dashboard] Heartbeat: MQTT not connected -- reconnecting"
        connectMqtt()
        return
    }
    // NOTE: Do NOT send statusupdate here -- it resets the display's idle timer,
    // preventing the backlight from ever blanking. MQTT keepalive handles connection health.
}

// ── MQTT parse ─────────────────────────────────────────────────────────────────

def parse(String description) {
    def msg = interfaces.mqtt.parseMessage(description)
    debugLog "MQTT: topic=${msg.topic} payload=${msg.payload}"

    if (msg.topic.endsWith("/LWT")) {
        String actualNode = msg.topic.split("/")[1]
        String configNode = settings.haspNode ?: "plate"
        if (actualNode != configNode) {
            log.warn "[Dashboard] Node name mismatch! Device is '${actualNode}' but preference is '${configNode}'"
            sendEvent(name: "mqttStatus", value: "Wrong node name -- should be '${actualNode}'")
        }
        if (msg.payload?.trim() == "online") {
            infoLog "[Dashboard] LWT online (${actualNode}) -- display rebooted, pushing all layouts"
            state.pushInProgress     = false
            state.suppressNavigation = false
            sendEvent(name: "pushInProgress", value: "false")
            unschedule("rotatePage")
            unschedule("returnToPage1AndStartRotation")
            if (!state.lwtPending) {
                state.lwtPending = true
                runIn(5, "fireDisplayRebooted")
            }
        }
        return
    }

    if (msg.topic.contains("statusupdate")) {
        // Only process statusupdate from our configured node
        String updNode = msg.topic.split("/")[1]
        if (updNode != (settings.haspNode ?: "plate")) return
        if (!msg.payload?.trim()) return
        try {
            def json = new groovy.json.JsonSlurper().parseText(msg.payload)
            if (json.uptime == null) return
            int uptime = (json.uptime) as int
            if (uptime < 30) {
                // Guard: if we just finished a push recently, the low uptime is from the
                // display that was already up during our push -- not a fresh reboot.
                // Triggering another full push here causes the visible second render.
                long msSincePush = now() - (state.lastPushMs ?: 0L)
                if (msSincePush < 120000) {
                    infoLog "[Dashboard] Ignoring low-uptime statusupdate (${uptime}s) -- push completed ${msSincePush}ms ago, not a reboot"
                    return
                }
                debugLog "[Dashboard] Display rebooted (uptime ${uptime}s) -- scheduling push"
                state.pushInProgress     = false
                state.suppressNavigation = false
                unschedule("rotatePage")
                unschedule("returnToPage1AndStartRotation")
                runIn(5, "fireDisplayRebooted")
            } else {
                debugLog "[Dashboard] Display woke from idle"
                startBacklightTimer()
            }
        } catch (Exception e) { infoLog "[Dashboard] WARN -- Could not parse statusupdate: ${e.message}" }
        return
    }

    if (msg.topic.contains("state/idle") || msg.topic.endsWith("/idle")) {
        if (msg.topic.split("/")[1] != (settings.haspNode ?: "plate")) return
        String v = msg.payload?.trim()
        if (v == "long") {
            state.screenIdle = true
            infoLog "[Dashboard] Display idle (long) -- blanking"
            publishBacklight(false)
        } else if (v == "off") {
            long ms = now() - (state.lastHeartbeatMs ?: 0L)
            if (ms >= 3000) {
                state.screenIdle = false
                debugLog "[Dashboard] Screen woke from touch"
                startBacklightTimer()
            }
        }
        return
    }

    if (msg.topic.contains("state/backlight") || msg.topic.endsWith("/backlight")) {
        if (msg.topic.split("/")[1] != (settings.haspNode ?: "plate")) return
        try {
            def json = new groovy.json.JsonSlurper().parseText(msg.payload)
            if (json.state == "off") { state.screenIdle = true }
            else if (json.state == "on" && state.screenIdle) { state.screenIdle = false; startBacklightTimer() }
        } catch (Exception e) { if (msg.payload?.trim() == "off") state.screenIdle = true }
        return
    }

    String cfgNode = settings.haspNode ?: "plate"
    if (msg.topic.contains("/state/p") && msg.topic.contains("b") && msg.topic.contains(cfgNode)) {
        debugLog "[Dashboard] Button topic: ${msg.topic} payload: ${msg.payload}"
        handleButtonTap(msg.topic, msg.payload)
        return
    }
}

// ── Button tap handler ─────────────────────────────────────────────────────────

private void handleButtonTap(String topic, String payload) {
    if (!payload?.contains('"up"')) return
    def matcher = topic =~ /state\/p(\d+)b(\d+)$/
    if (!matcher) return
    int page  = matcher[0][1] as int
    int btnId = matcher[0][2] as int
    if (btnId < 1 || btnId > 25) return
    int slot = btnId
    String sType = state[typeKey(page, slot)] ?: "none"
    if (sType == "light" || sType == "lock" || sType == "garage") {
        debugLog "[Dashboard] Tappable tile tapped (${sType}): page ${page} slot ${slot}"
        sendEvent(name: "lightTapped", value: "${page},${slot},${now()}")
        return
    }
    if (sType == "thermostat") {
        debugLog "[Dashboard] Thermostat tile tapped: page ${page} slot ${slot}"
        sendEvent(name: "thermostatTapped", value: "${page},${slot},${now()}")
        handleThermostatTap(page, slot)
        return
    }
}

// ── Thermostat device registration ────────────────────────────────────────────

def setPage1ThermostatDevice(String deviceId) {
    state.p1ThermostatDeviceId = deviceId
}

def setPage2ThermostatDevice(String deviceId) {
    state.p2ThermostatDeviceId = deviceId
}

def setPage3ThermostatDevice(String deviceId) {
    state.p3ThermostatDeviceId = deviceId
}

def setPage4ThermostatDevice(String deviceId) {
    state.p4ThermostatDeviceId = deviceId
}

def setPage5ThermostatDevice(String deviceId) {
    state.p5ThermostatDeviceId = deviceId
}

def setPage6ThermostatDevice(String deviceId) {
    state.p6ThermostatDeviceId = deviceId
}

// ── Thermostat display ────────────────────────────────────────────────────────

// Called by the app whenever any thermostat attribute changes.
// data map keys: temp, heatSetpoint, coolSetpoint, mode, operatingState
def updateThermostatDisplay(page, data) {
    int pg = page as int
    if (!data) return
    String node    = settings.haspNode ?: "plate"

    String temp    = data.temp          ?: "--"
    String heat    = data.heatSetpoint  ?: "--"
    String cool    = data.coolSetpoint  ?: "--"
    String mode    = data.mode          ?: "off"
    String opState = data.operatingState ?: "idle"
    boolean away   = (data.away == "true")

    // Wake display if mode or operating state changed
    String prevMode   = state["p${pg}thermostatMode"]    ?: ""
    String prevOpState = state["p${pg}thermostatOpState"] ?: ""
    boolean prevAway  = (state["p${pg}thermostatAway"] == true)
    if (mode != prevMode || opState != prevOpState || away != prevAway) {
        if (state.screenIdle) {
            infoLog "[Dashboard] Thermostat state changed -- waking display"
            state.screenIdle = false
            publishBacklight(true)
        }
    }

    state["p${pg}thermostatTemp"]    = temp
    state["p${pg}thermostatMode"]    = mode
    state["p${pg}thermostatHeat"]    = heat
    state["p${pg}thermostatCool"]    = cool
    state["p${pg}thermostatOpState"] = opState
    state["p${pg}thermostatAway"]    = away

    pushThermostatTile(pg, node, temp, heat, cool, mode, opState, away)
}

private void handleThermostatTap(int page, int slot) {
    if (slot < 2 || slot > 4) return
    String mode = (state["p${page}thermostatMode"] ?: "off") as String
    String heat = (state["p${page}thermostatHeat"] ?: "68") as String
    String cool = (state["p${page}thermostatCool"] ?: "76") as String
    String val  = "${page},${slot},${mode},${heat},${cool}"
    infoLog "[Dashboard] Thermostat tap page ${page} slot ${slot}: mode=${mode}"
    sendEvent(name: "thermostatTapped", value: val)
}

private void pushThermostatTile(int pg, String node, String temp, String heat, String cool, String mode, String opState, boolean away = false) {
    // Single color applied to all 4 tiles
    String bgColor = away ? "#000000" : thermostatColorForState(opState, mode)
    String fgColor = contrastColor(bgColor)

    // Tile 1: temp + descriptive line2
    boolean activeHeat = (opState == "heating") || (mode == "heat" && (!opState || opState == "idle"))
    boolean activeCool = (opState == "cooling") || (mode == "cool" && (!opState || opState == "idle"))
    try {
        BigDecimal t = temp as BigDecimal; BigDecimal h = heat as BigDecimal; BigDecimal c = cool as BigDecimal
        if (activeHeat && t >= h) activeHeat = false
        if (activeCool && t <= c) activeCool = false
    } catch (Exception e) { }

    String line2
    if (away)                       line2 = "Away"
    else if (activeHeat)            line2 = "Heating to ${heat}\u00B0"
    else if (activeCool)            line2 = "Cooling to ${cool}\u00B0"
    else if (opState == "fan only") line2 = "Fan only"
    else if (mode == "heat")        line2 = "Heat: ${heat}\u00B0"
    else if (mode == "cool")        line2 = "Cool: ${cool}\u00B0"
    else                            line2 = "Off"

    debugLog "[Dashboard] Thermostat: page=${pg} temp=${temp} mode=${mode} line2=${line2} state=${opState} away=${away} bg=${bgColor}"

    String plusGlyph  = iconToJsonEscape("\uE415")
    String minusGlyph = iconToJsonEscape("\uE374")
    boolean btnActive = !away && (mode == "heat" || mode == "cool")
    String btnFg = btnActive ? fgColor : contrastColor(bgColor)
    String modeLabel = away ? "Away" : mode.capitalize()

    safePub("hasp/" + node + "/command/jsonl",
        '{"page":' + pg + ',"id":1,"bg_color":"' + bgColor + '","text_color":"' + fgColor + '","text_font":32,"text":"' + "${temp}\u00B0\n${line2}" + '"}')
    pauseExecution(15)
    safePub("hasp/" + node + "/command/jsonl",
        '{"page":' + pg + ',"id":2,"bg_color":"' + bgColor + '","text_color":"' + btnFg + '","text_font":56,"text":"' + plusGlyph + '","click":' + btnActive + '}')
    pauseExecution(15)
    safePub("hasp/" + node + "/command/jsonl",
        '{"page":' + pg + ',"id":3,"bg_color":"' + bgColor + '","text_color":"' + fgColor + '","text_font":56,"text":"' + modeLabel + '","click":true}')
    pauseExecution(15)
    safePub("hasp/" + node + "/command/jsonl",
        '{"page":' + pg + ',"id":4,"bg_color":"' + bgColor + '","text_color":"' + btnFg + '","text_font":56,"text":"' + minusGlyph + '","click":' + btnActive + '}')
}

private String thermostatColorForState(String opState, String mode) {
    // heating=red, cooling=blue, off/idle=slate, away handled in caller (black)
    if (opState == "heating" || mode == "heat") return "#CC0000"   // red
    if (opState == "cooling" || mode == "cool") return "#0055CC"   // blue
    return "#708090"   // slate -- off or idle
}

// ── Backlight ──────────────────────────────────────────────────────────────────

private void startBacklightTimer() {
    if (!settings.backlightOnMotion) return
    unschedule("backlightOff")
    if (!allInactive()) {
        int secs = (settings.motionBacklightTimeout != null ? settings.motionBacklightTimeout : 60) as int
        if (secs > 0) runIn(secs, "motionTimeoutBacklightOff")
    } else {
        int delay = (settings.touchBacklightTimeout != null ? settings.touchBacklightTimeout : 30) as int
        if (delay > 0) runIn(delay, "backlightOff")
    }
}

private void pushIdleConfig() {
    // The display idle timer durations are set in the display's web UI (Display Settings).
    // We can't set them via MQTT. Instead we use our own Hubitat-side timer.
    scheduleIdleTimeout()
    String node = settings.haspNode ?: "plate"
    infoLog "[Dashboard] Idle config: Hubitat-side timer active (${settings.idleTimeout ?: 0} sec)"
}

private void scheduleIdleTimeout() {
    unschedule("idleTimeoutBacklightOff")
    int secs = (settings.idleTimeout != null ? settings.idleTimeout : 0) as int
    if (secs > 0) {
        runIn(secs, "idleTimeoutBacklightOff")
        debugLog "[Dashboard] Idle timer set: ${secs}s"
    }
}

def idleTimeoutBacklightOff() {
    infoLog "[Dashboard] Idle timeout -- blanking display"
    publishBacklight(false)
    state.screenIdle = true
}

def backlightOff() {
    publishBacklight(false)
    state.screenIdle = true
}

def backlightOnAfterFade() {
    if (!settings.backlightOnMotion) return
    if (!allInactive()) return
    if (state.screenIdle) return
    // Fade just completed -- reset idle timer so blank waits the full interval from now
    scheduleIdleTimeout()
    int delay = (settings.backlightOffDelay != null ? settings.backlightOffDelay : 0) as int
    if (delay > 0) runIn(delay, "backlightOff")
}

def motionTimeoutBacklightOff() {
    if (!settings.backlightOnMotion) return
    backlightOff()
}

private boolean allInactive() {
    int numPg = (state.numberOfPages ?: 6) as int
    (1..numPg).every { pg ->
        (1..maxSensors(pg)).every { idx ->
            String sType = state[typeKey(pg, idx)] ?: "none"
            if (sType == "light" || sType == "lock" || sType == "garage") return true
            return state[stateKey(pg, idx)] != "active"
        }
    }
}

private void publishBacklight(boolean on) {
    String node = settings.haspNode ?: "plate"
    safePub("hasp/${node}/command/backlight", on ? '{"state":"on","brightness":255}' : '{"state":"off"}')
    if (on && !state.syncInProgress) scheduleIdleTimeout()
    else if (!on) unschedule("idleTimeoutBacklightOff")
}

// ── Resync ─────────────────────────────────────────────────────────────────────

def resyncStates() {
    if (state.pushInProgress) { infoLog "[Dashboard] Skipping resync -- layout push in progress"; return }
    infoLog "[Dashboard] Resyncing all page states from cache"
    int numPg = (state.numberOfPages ?: 6) as int
    (1..numPg).each { pg ->
        if (activeGrid(pg) == "thermostat") {
            // Re-push thermostat tile from stored state including away flag
            String nd   = settings.haspNode ?: "plate"
            String temp = (state["p${pg}thermostatTemp"]    ?: "--") as String
            String heat = (state["p${pg}thermostatHeat"]    ?: "--") as String
            String cool = (state["p${pg}thermostatCool"]    ?: "--") as String
            String mode = (state["p${pg}thermostatMode"]    ?: "off") as String
            String ops  = (state["p${pg}thermostatOpState"] ?: "idle") as String
            boolean away = (state["p${pg}thermostatAway"] == true)
            if (temp == "--") {
                sendEvent(name: "layoutPushComplete", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
            } else {
                pushThermostatTile(pg, nd, temp, heat, cool, mode, ops, away)
            }
            return
        }
        (1..maxSensors(pg)).each { idx ->
            String sType = state[typeKey(pg, idx)] ?: "none"
            String st    = state[stateKey(pg, idx)] ?: "inactive"
            if (sType == "none" || st == "empty") { setSlotEmptyForPage(pg, idx) }
            else if (st == "active")              { setMotionActiveForPage(pg, idx) }
            else                                  { setMotionInactiveForPage(pg, idx) }
            pauseExecution(30)
        }
    }
}

def fireDisplayRebooted() {
    state.lwtPending = false
    sendEvent(name: "displayRebooted", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
}

// ── Layout push ────────────────────────────────────────────────────────────────

def pushAllLayouts(numberOfPages) {
    int np = Math.min(6, Math.max(1, (numberOfPages as int)))
    // Don't push into a dead connection -- wait for reconnect then LWT will retrigger.
    // Use interfaces.mqtt.isConnected() not the device attribute -- the attribute
    // persists across Hubitat restarts and causes false "Connected" on startup.
    boolean mqttUp = false
    try { mqttUp = interfaces.mqtt.isConnected() } catch (Exception e) { mqttUp = false }
    if (!mqttUp) {
        infoLog "[Dashboard] pushAllLayouts deferred -- MQTT not connected"
        state.deferredPages = np
        return
    }
    state.numberOfPages      = np
    state.deferredPages      = null
    state.lastPushMs         = now()
    state.pushInProgress     = true
    state.suppressNavigation = true
    state.rotationPage       = 1
    state.deferClearPage     = 0   // reset any leftover defer from previous push
    sendEvent(name: "pushInProgress", value: "true")
    unschedule("rotatePage")
    unschedule("returnToPage1AndStartRotation")
    infoLog "[Dashboard] pushAllLayouts -- ${np} page(s)"
    sendEvent(name: "mqttStatus", value: "Building layouts...")
    String node = settings.haspNode ?: "plate"
    // Clear all pages at once before building. This prevents stale objects from
    // a previous push (e.g. a motion icon on the old page 2) bleeding through
    // into the new layout. The display briefly shows blank while page 1 builds.
    safePub("hasp/${node}/command", "clearpage all")
    pauseExecution(400)
    runIn(2, "pushPage1Layout")
}

def pushPage1Layout() {
    publishBacklight(true)
    pushPageLayout(1)
}

def pushPage2Layout() {
    int np2 = (state.numberOfPages ?: 6) as int
    if (np2 >= 2) pushPageLayout(2)
}

def pushPage3Layout() {
    int np3 = (state.numberOfPages ?: 6) as int
    if (np3 >= 3) pushPageLayout(3)
}

def pushPage4Layout() {
    int np4 = (state.numberOfPages ?: 6) as int
    if (np4 >= 4) pushPageLayout(4)
}

def pushPage5Layout() {
    int np5 = (state.numberOfPages ?: 6) as int
    if (np5 >= 5) pushPageLayout(5)
}

def pushPage6Layout() {
    int np6 = (state.numberOfPages ?: 6) as int
    if (np6 >= 6) pushPageLayout(6)
}

private void pushPageLayout(int page) {
    String grid     = activeGrid(page)
    int total       = (state.numberOfPages ?: 6) as int
    String node     = settings.haspNode ?: "plate"
    int tileFont    = tileFontFor(grid)
    int slots       = maxSensors(page)

    infoLog "[Dashboard] Pushing page ${page}/${total}: grid='${grid}' slots=${slots} storedGrid='${state["page${page}GridLayout"]}' storedSlots='${state["page${page}MaxSlots"]}'"
    sendEvent(name: "mqttStatus", value: "Pushing page ${page}/${total}...")

    // Clear the page before building it. Since we navigated to page 0 at push
    // start, no content page is currently visible, so no flash occurs.
    safePub("hasp/${node}/command", "clearpage ${page}")
    pauseExecution(150)

    (1..slots).each { s ->
        unschedule("p${page}fadeStep${s}")
        state.remove("p${page}fadeStep${s}")
    }

    // 1. Layout JSONL -- tile structure
    layoutJsonl(grid, page, total).each { jsonl ->
        safePub("hasp/${node}/command/jsonl", jsonl)
        pauseExecution(40)
    }

    pauseExecution(150)

    // 2. Slot colors and icons
    // Thermostat pages have a fixed slot layout rendered by pushThermostatSlots.
    if (grid == "thermostat") {
        // Button labels are set by updateThermostatDisplay after app syncs
        // Set placeholder text on control buttons
        String nd = settings.haspNode ?: "plate"
        // Let skeleton settle then push colors and content immediately
        pauseExecution(500)
        String temp    = (state["p${page}thermostatTemp"]    ?: "--") as String
        String heat    = (state["p${page}thermostatHeat"]    ?: "--") as String
        String cool    = (state["p${page}thermostatCool"]    ?: "--") as String
        String tMode   = (state["p${page}thermostatMode"]    ?: "off") as String
        String tOps    = (state["p${page}thermostatOpState"] ?: "idle") as String
        boolean tAway  = (state["p${page}thermostatAway"] == true)
        if (temp != "--") {
            pushThermostatTile(page, nd, temp, heat, cool, tMode, tOps, tAway)
        } else {
            // No stored data yet -- send placeholder labels
            [[2,"\uE415"], [3,"Mode"], [4,"\uE374"]].each { entry ->
                int sid = entry[0] as int
                String lbl = iconToJsonEscape(entry[1] as String)
                safePub("hasp/" + nd + "/command/jsonl", '{"page":' + page + ',"id":' + sid + ',"text":"' + lbl + '"}')
                pauseExecution(20)
            }
        }
        int drainSecs2 = 3
        switch (page) {
            case 1: runIn(drainSecs2, "navigatePage1"); break
            case 2: runIn(drainSecs2, "navigatePage2"); break
            case 3: runIn(drainSecs2, "navigatePage3"); break
            case 4: runIn(drainSecs2, "navigatePage4"); break
            case 5: runIn(drainSecs2, "navigatePage5"); break
            case 6: runIn(drainSecs2, "navigatePage6"); break
        }
        return
    }

    // Icons sent via JSONL to preserve non-ASCII (raw topic strips Unicode private-use chars).
    // Labels come from syncAllSensors after push -- no need to duplicate here.
    (1..slots).each { idx ->
        try {
            String slotType = state[typeKey(page, idx)] ?: "none"

            if (!slotType || slotType == "none") {
                // Empty slot -- grey, no icon, no label
                String emptyJsonl = '{"page":' + page + ',"id":' + bgId(idx) + ',"bg_color":"#708090","text_color":"#FFFFFF","text":"","click":false}'
                safePub("hasp/${node}/command/jsonl", emptyJsonl)
            } else if (slotType == "light" || slotType == "lock" || slotType == "garage") {
                // Tappable tile -- buildLightTileJsonl handles icon+label+color+click atomically
                String activeState = state[stateKey(page, idx)] ?: "inactive"
                String activeColor = (slotType == "light")  ? (settings.colorLightActive ?: "#FFFF00") :
                                     (slotType == "lock")   ? (settings.colorLockOpen    ?: "#E65100") :
                                                              (settings.colorGarageOpen  ?: "#E65100")
                String tileColor   = (activeState == "active") ? activeColor : inactiveColorFor(page, idx)
                String lbl         = state[labelKey(page, idx)] ?: ""
                String clickJsonl  = buildLightTileJsonl(page, idx, grid, tileFont, tileColor, lbl)
                safePub("hasp/${node}/command/jsonl", clickJsonl)
            } else {
                // Non-tappable sensor tile -- publishIconJsonl handles icon+label+color
                String ic  = inactiveColorFor(page, idx)
                String lbl = state[labelKey(page, idx)] ?: ""
                // Store label in state first so publishIconJsonl can embed it
                if (lbl) state[labelKey(page, idx)] = lbl
                publishIconJsonl(node, page, idx, inactiveIconFor(page, idx), ic)
            }
        } catch (Exception e) {
            infoLog "[Dashboard] WARN -- slot ${idx} render failed: ${e.message}"
        }
        pauseExecution(20)
    }

    int drainSecs = 3 + (int)(slots * 0.1)
    infoLog "[Dashboard] Scheduling navigate to page ${page}/${total} in ${drainSecs}s"
    switch (page) {
        case 1: runIn(drainSecs, "navigatePage1"); break
        case 2: runIn(drainSecs, "navigatePage2"); break
        case 3: runIn(drainSecs, "navigatePage3"); break
        case 4: runIn(drainSecs, "navigatePage4"); break
        case 5: runIn(drainSecs, "navigatePage5"); break
        case 6: runIn(drainSecs, "navigatePage6"); break
    }
}

def navigatePage1() {
    doNavigate(1)
}

def navigatePage2() {
    doNavigate(2)
}

def navigatePage3() {
    doNavigate(3)
}

def navigatePage4() {
    doNavigate(4)
}

def navigatePage5() {
    doNavigate(5)
}

def navigatePage6() {
    doNavigate(6)
}

private void doNavigate(int page) {
    int total   = (state.numberOfPages ?: 6) as int
    String node = settings.haspNode ?: "plate"
    infoLog "[Dashboard] Navigating to page ${page}/${total}"

    // Navigate to this page so the user sees it
    safePub("hasp/${node}/command/page", "${page}")
    state.currentDisplayPage = page

    if (page < total) {
        // Build the next page while the user is viewing this one
        infoLog "[Dashboard] Scheduling next page build after page ${page}"
        switch (page) {
            case 1: runIn(3, "pushPage2Layout"); break
            case 2: runIn(3, "pushPage3Layout"); break
            case 3: runIn(3, "pushPage4Layout"); break
            case 4: runIn(3, "pushPage5Layout"); break
            case 5: runIn(3, "pushPage6Layout"); break
        }
    } else {
        // Last page is now visible -- stay here a moment then return to page 1.
        // Keep pushInProgress=true until returnToPage1AndStartRotation completes
        // so statusupdate-triggered resyncStates cannot race the push.
        infoLog "[Dashboard] All ${total} page(s) pushed"
        runIn(12, "returnToPage1AndStartRotation")
    }
}

def returnToPage1AndStartRotation() {
    String node  = settings.haspNode ?: "plate"
    int numPg    = (state.numberOfPages ?: 1) as int
    // Re-push any thermostat tiles from stored state -- clearpage wiped them during layout build
    (1..numPg).each { pg ->
        if (activeGrid(pg) == "thermostat") {
            String temp = (state["p${pg}thermostatTemp"]    ?: "--") as String
            String heat = (state["p${pg}thermostatHeat"]    ?: "--") as String
            String cool = (state["p${pg}thermostatCool"]    ?: "--") as String
            String mode = (state["p${pg}thermostatMode"]    ?: "off") as String
            String ops  = (state["p${pg}thermostatOpState"] ?: "idle") as String
            pushThermostatTile(pg, node, temp, heat, cool, mode, ops)
            pauseExecution(50)
        }
    }
    safePub("hasp/${node}/command/page", "1")
    state.currentDisplayPage = 1
    state.lastPushMs     = now()   // extend grace window past syncAllSensors
    state.pushInProgress = false
    state.syncInProgress = true    // block idle timer resets during syncAllSensors
    scheduleIdleTimeout()
    sendEvent(name: "pushInProgress",     value: "false")
    sendEvent(name: "mqttStatus",         value: "Connected")
    sendEvent(name: "layoutPushComplete", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    infoLog "[Dashboard] Layout push complete -- syncAllSensors will follow"
    // Start rotation after sync has had time to complete (~15s)
    // suppressNavigation cleared in startRotationAfterSync so sensor events don't race
    int total = (state.numberOfPages ?: 1) as int
    if (total > 1) {
        runIn(15, "startRotationAfterSync")
    } else {
        state.suppressNavigation = false
    }
}

def startRotationAfterSync() {
    state.suppressNavigation = false
    state.syncInProgress     = false   // sync window over -- idle timer resets now permitted
    int total  = (state.numberOfPages ?: 1) as int
    int rotInt = (settings.rotationInterval ?: 0) as int
    if (total > 1 && rotInt > 0) {
        state.rotationPage = 1
        unschedule("rotatePage")
        runIn(rotInt, "rotatePage")
        infoLog "[Dashboard] Rotation started -- every ${rotInt}s"
    }
}

// Restart page rotation if it was stopped by a sensor event and all sensors are now inactive.
private void maybeRestartRotation() {
    int total  = (state.numberOfPages ?: 1) as int
    int rotInt = (settings.rotationInterval ?: 0) as int
    if (total < 2 || rotInt < 1) return
    unschedule("rotatePage")
    runIn(rotInt, "rotatePage")
    debugLog "[Dashboard] Rotation restarted after all sensors inactive"
}

// ── Layout JSONL generators ────────────────────────────────────────────────────

private List<String> layoutJsonl(String grid, int page, int totalPages) {
    List<String> out
    switch (grid) {
        case "thermostat": out = layoutThermostat(page); break
        case "1x1": out = layout1x1(page); break
        case "3x3": out = layout3x3(page); break
        case "4x4": out = layout4x4(page); break
        case "5x5": out = layoutNxN(page, 5, 94, 2, 12, 24); break
        default:    out = layout2x2(page)
    }

    if (totalPages > 1) {
        int prevPage = (page == 1) ? totalPages : page - 1
        int nextPage = (page == totalPages) ? 1 : page + 1
        int navOpa = 20
        out << """{"page":${page},"id":201,"obj":"btn","x":0,"y":0,"w":30,"h":480,"bg_color":"#000000","bg_opa":${navOpa},"border_width":0,"radius":0,"text":"","text_font":8,"toggle":false,"action":"p${prevPage}"}"""
        out << """{"page":${page},"id":202,"obj":"btn","x":450,"y":0,"w":30,"h":480,"bg_color":"#000000","bg_opa":${navOpa},"border_width":0,"radius":0,"text":"","text_font":8,"toggle":false,"action":"p${nextPage}"}"""
        if (settings.showPageIndicator == true) {
            out << """{"page":${page},"id":200,"obj":"label","x":432,"y":4,"w":46,"h":22,"bg_color":"#000000","bg_opa":180,"border_width":0,"radius":4,"text":"${page}/${totalPages}","text_font":16,"text_color":"white","align":"center","click":false}"""
        } else {
            out << """{"page":${page},"id":200,"obj":"label","x":0,"y":0,"w":1,"h":1,"bg_opa":0,"border_width":0,"text":"","text_font":8,"click":false}"""
        }
    } else {
        if (settings.showPageIndicator == true) {
            out << """{"page":${page},"id":200,"obj":"label","x":432,"y":4,"w":46,"h":22,"bg_color":"#000000","bg_opa":180,"border_width":0,"radius":4,"text":"1/1","text_font":16,"text_color":"white","align":"center","click":false}"""
        } else {
            out << """{"page":${page},"id":200,"obj":"label","x":0,"y":0,"w":1,"h":1,"bg_opa":0,"border_width":0,"text":"","text_font":8,"click":false}"""
        }
        out << """{"page":${page},"id":201,"obj":"label","x":0,"y":0,"w":1,"h":1,"bg_opa":0,"border_width":0,"text":"","text_font":8,"click":false}"""
        out << """{"page":${page},"id":202,"obj":"label","x":0,"y":0,"w":1,"h":1,"bg_opa":0,"border_width":0,"text":"","text_font":8,"click":false}"""
    }
    return out
}

// Fixed 2x2 thermostat layout: slot1=display, 2=+, 3=-, 4=mode
private List<String> layoutThermostat(int page) {
    List<String> out = []
    [[1,2,2,236,236],[2,242,2,236,236],[3,2,242,236,236],[4,242,242,236,236]].each { r ->
        int sid = r[0]; int x = r[1]; int y = r[2]; int w = r[3]; int h = r[4]
        int tf = (sid == 1) ? 32 : 56
        boolean clickable = (sid > 1)
        out << """{"page":${page},"id":${sid},"obj":"btn","x":${x},"y":${y},"w":${w},"h":${h},"bg_color":"#000000","border_color":"black","border_width":4,"radius":10,"text":"","text_font":${tf},"align":"center","text_color":"white","toggle":false,"click":${clickable}}"""
    }
    return out
}

private List<String> layout1x1(int page) {[
    """{"page":${page},"id":1,"obj":"btn","x":2,"y":2,"w":476,"h":476,"bg_color":"#000000","border_color":"black","border_width":4,"radius":10,"text":"","text_font":28,"align":"center","pad_top":280,"text_color":"white","value_str":"","value_font":48,"toggle":false,"click":false}"""
]}

private List<String> layout2x2(int page) {
    List<String> out = []
    [[1,2,2,236,236],[2,242,2,236,236],[3,2,242,236,236],[4,242,242,236,236]].each { r ->
        out << """{"page":${page},"id":${r[0]},"obj":"btn","x":${r[1]},"y":${r[2]},"w":${r[3]},"h":${r[4]},"bg_color":"#000000","border_color":"black","border_width":4,"radius":10,"text":"","text_font":22,"align":"center","pad_top":106,"text_color":"white","value_str":"","value_font":40,"toggle":false,"click":false}"""
    }
    return out
}

private List<String> layout3x3(int page) {
    List<String> out = []
    int[][] cells = [[2,2,157,157],[161,2,157,157],[320,2,158,157],[2,161,157,157],[161,161,157,157],[320,161,158,157],[2,320,157,158],[161,320,157,158],[320,320,158,158]]
    cells.eachWithIndex { c, i ->
        out << """{"page":${page},"id":${i+1},"obj":"btn","x":${c[0]},"y":${c[1]},"w":${c[2]},"h":${c[3]},"bg_color":"#000000","border_color":"black","border_width":4,"radius":10,"text":"","text_font":18,"align":"center","pad_top":70,"text_color":"white","value_str":"","value_font":32,"toggle":false,"click":false}"""
    }
    return out
}

private List<String> layout4x4(int page) {
    List<String> out = []; int cols = 4; int w = 117; int gap = 2
    (0..<cols).each { row -> (0..<cols).each { col ->
        int id = row*cols+col+1; int x = col*(w+gap)+gap; int y = row*(w+gap)+gap
        int tw = (col==cols-1) ? (480-x-gap) : w; int th = (row==cols-1) ? (480-y-gap) : w
        out << """{"page":${page},"id":${id},"obj":"btn","x":${x},"y":${y},"w":${tw},"h":${th},"bg_color":"#000000","border_color":"black","border_width":2,"radius":8,"text":"","text_font":16,"align":"center","pad_top":52,"text_color":"white","value_str":"","value_font":24,"toggle":false,"click":false}"""
    }}
    return out
}

private List<String> layoutNxN(int page, int cols, int w, int gap, int tf, int iconFont) {
    List<String> out = []
    (0..<cols).each { row -> (0..<cols).each { col ->
        int id = row*cols+col+1; int x = col*(w+gap)+gap; int y = row*(w+gap)+gap
        int tw = (col==cols-1) ? (480-x-gap) : w; int th = (row==cols-1) ? (480-y-gap) : w
        out << """{"page":${page},"id":${id},"obj":"btn","x":${x},"y":${y},"w":${tw},"h":${th},"bg_color":"#000000","border_color":"black","border_width":1,"radius":4,"text":"","text_font":${tf},"align":"center","pad_top":42,"text_color":"white","value_str":"","value_font":${iconFont},"toggle":false,"click":false}"""
    }}
    return out
}

// ── Page slot state commands ───────────────────────────────────────────────────

def setPage1MotionActive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(1)) setMotionActiveForPage(1, i)
}

def setPage1MotionInactive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(1)) setMotionInactiveForPage(1, i)
}

def setPage1SlotEmpty(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(1)) setSlotEmptyForPage(1, i)
}

def setPage2MotionActive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(2)) setMotionActiveForPage(2, i)
}

def setPage2MotionInactive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(2)) setMotionInactiveForPage(2, i)
}

def setPage2SlotEmpty(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(2)) setSlotEmptyForPage(2, i)
}

def setPage3MotionActive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(3)) setMotionActiveForPage(3, i)
}

def setPage3MotionInactive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(3)) setMotionInactiveForPage(3, i)
}

def setPage3SlotEmpty(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(3)) setSlotEmptyForPage(3, i)
}

def setPage4MotionActive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(4)) setMotionActiveForPage(4, i)
}

def setPage4MotionInactive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(4)) setMotionInactiveForPage(4, i)
}

def setPage4SlotEmpty(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(4)) setSlotEmptyForPage(4, i)
}

def setPage5MotionActive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(5)) setMotionActiveForPage(5, i)
}

def setPage5MotionInactive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(5)) setMotionInactiveForPage(5, i)
}

def setPage5SlotEmpty(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(5)) setSlotEmptyForPage(5, i)
}

def setPage6MotionActive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(6)) setMotionActiveForPage(6, i)
}

def setPage6MotionInactive(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(6)) setMotionInactiveForPage(6, i)
}

def setPage6SlotEmpty(n) {
    int i = n as int
    if (i >= 1 && i <= maxSensors(6)) setSlotEmptyForPage(6, i)
}

private void setMotionActiveForPage(int page, int idx) {
    state[stateKey(page, idx)] = "active"
    unschedule("p${page}fadeStep${idx}")
    state.remove("p${page}fadeStep${idx}")
    unschedule("backlightOnAfterFade")

    String sType = state[typeKey(page, idx)] ?: state["pageType${page}"] ?: "motion"
    debugLog "[Dashboard] Active p${page}s${idx} type=${sType}"

    // Slot has no assigned device -- should never go active, treat as empty
    if (!sType || sType == "none") {
        setSlotEmptyForPage(page, idx)
        return
    }
    if (sType == "light" || sType == "lock" || sType == "garage") {
        String activeColor = (sType == "light")  ? (settings.colorLightActive  ?: "#FFFF00") :
                             (sType == "lock")   ? (settings.colorLockOpen     ?: "#E65100") :
                                                   (settings.colorGarageOpen   ?: "#E65100")
        if (sType == "garage") activeColor = (settings.colorGarageOpen ?: "#E65100")
        String grid       = activeGrid(page)
        int    tileFont   = tileFontFor(grid)
        String lbl        = state[labelKey(page, idx)] ?: ""
        String clickJsonl = buildLightTileJsonl(page, idx, grid, tileFont, activeColor, lbl)
        if (clickJsonl) {
            String node = settings.haspNode ?: "plate"
            safePub("hasp/${node}/command/jsonl", clickJsonl)
        }
        return
    }

    String ac = settings.colorActive ?: "#FF0000"
    publishIconJsonl(settings.haspNode ?: "plate", page, idx, activeIconFor(page, idx), ac)

    if (!state.suppressNavigation) {
        // Only stop rotation and jump to page when sensor goes active mid-session
        unschedule("rotatePage")
        String node = settings.haspNode ?: "plate"
        safePub("hasp/${node}/command/page", "${page}")
    }
    // If suppressNavigation is true we are in post-push sync -- don't kill the rotation timer

    if (settings.backlightOnMotion) {
        unschedule("backlightOff")
        unschedule("motionTimeoutBacklightOff")
        state.screenIdle = false
        publishBacklight(true)
        int secs = (settings.motionBacklightTimeout ?: 60) as int
        if (secs > 0) runIn(secs, "motionTimeoutBacklightOff")
    }
}

private void setMotionInactiveForPage(int page, int idx) {
    String fadeKey  = "p${page}fadeStep${idx}"
    boolean wasActive = (state[stateKey(page, idx)] == "active")
    state[stateKey(page, idx)] = "inactive"

    String sType = state[typeKey(page, idx)] ?: state["pageType${page}"] ?: "motion"
    debugLog "[Dashboard] Inactive p${page}s${idx} type=${sType} wasActive=${wasActive}"

    // Slot has no assigned device -- treat as empty regardless of how we got here
    if (!sType || sType == "none") {
        setSlotEmptyForPage(page, idx)
        return
    }
    if (sType == "light" || sType == "lock" || sType == "garage") {
        unschedule(fadeKey); state.remove(fadeKey)
        String ic         = inactiveColorFor(page, idx)
        String grid       = activeGrid(page)
        int    tileFont   = tileFontFor(grid)
        String lbl        = state[labelKey(page, idx)] ?: ""
        String clickJsonl = buildLightTileJsonl(page, idx, grid, tileFont, ic, lbl)
        if (clickJsonl) {
            String node = settings.haspNode ?: "plate"
            safePub("hasp/${node}/command/jsonl", clickJsonl)
        }
        return
    }

    String sTypeForFade = state[typeKey(page, idx)] ?: state["pageType${page}"] ?: "motion"
    if (wasActive) {
        unschedule(fadeKey); state[fadeKey] = 0
        // Update icon to inactive (person standing still) but keep red color -- fade handles color
        String node = settings.haspNode ?: "plate"
        String escaped = iconToJsonEscape(inactiveIconFor(page, idx))
        String lbl = state[labelKey(page, idx)] ?: ""
        String escapedLbl = lbl.replace('"', '\\"')
        String grid = activeGrid(page)
        int iFont = iconFontFor(grid); int lFont = labelFontFor(grid)
        int[] ofs = iconOffsetsFor(grid); int padTop = labelPadTopFor(grid)
        safePub("hasp/${node}/command/jsonl",
            '{"page":' + page + ',"id":' + bgId(idx) +
            ',"value_str":"' + escaped + '","value_font":' + iFont +
            ',"value_ofs_x":' + ofs[0] + ',"value_ofs_y":' + ofs[1] +
            ',"text":"' + escapedLbl + '","text_font":' + lFont +
            ',"pad_top":' + padTop + '}')
        scheduleFadeStep(page, idx)
        if (settings.backlightOnMotion) {
            unschedule("motionTimeoutBacklightOff")
            if (!allInactive()) {
                int secs = (settings.motionBacklightTimeout ?: 60) as int
                if (secs > 0) runIn(secs, "motionTimeoutBacklightOff")
            } else {
                runIn((FADE_STEPS + 1) * fadeInterval() + 2, "backlightOnAfterFade")
            }
        }
    } else {
        String ic = inactiveColorFor(page, idx)
        publishIconJsonl(settings.haspNode ?: "plate", page, idx, inactiveIconFor(page, idx), ic)
        if (settings.backlightOnMotion && allInactive()) {
            int delay = (settings.backlightOffDelay ?: 0) as int
            if (delay > 0) { unschedule("backlightOff"); runIn(delay, "backlightOff") }
        }
    }
    // If all sensors are now inactive and rotation was stopped, restart it
    if (allInactive() && !state.suppressNavigation && !state.pushInProgress) {
        maybeRestartRotation()
    }
}

private void setSlotEmptyForPage(int page, int idx) {
    String fadeKey = "p${page}fadeStep${idx}"
    state[stateKey(page, idx)] = "empty"
    unschedule(fadeKey); state.remove(fadeKey)
    String node = settings.haspNode ?: "plate"
    String emptyJsonl = '{"page":' + page + ',"id":' + bgId(idx) + ',"bg_color":"#708090","text_color":"#FFFFFF","text":"","value_str":"","click":false}'
    safePub("hasp/${node}/command/jsonl", emptyJsonl)
}

// ── Label / type updates ───────────────────────────────────────────────────────

def updatePage1Labels(labels) {
    applyLabels(labels, 1)
}

def updatePage2Labels(labels) {
    applyLabels(labels, 2)
}

def updatePage3Labels(labels) {
    applyLabels(labels, 3)
}

def updatePage4Labels(labels) {
    applyLabels(labels, 4)
}

def updatePage5Labels(labels) {
    applyLabels(labels, 5)
}

def updatePage6Labels(labels) {
    applyLabels(labels, 6)
}

def updatePage1SlotTypes(types) {
    applySlotTypes(types, 1)
}

def updatePage2SlotTypes(types) {
    applySlotTypes(types, 2)
}

def updatePage3SlotTypes(types) {
    applySlotTypes(types, 3)
}

def updatePage4SlotTypes(types) {
    applySlotTypes(types, 4)
}

def updatePage5SlotTypes(types) {
    applySlotTypes(types, 5)
}

def updatePage6SlotTypes(types) {
    applySlotTypes(types, 6)
}

private void applyLabels(labels, int page) {
    if (!(labels instanceof Map)) {
        try { labels = new groovy.json.JsonSlurper().parseText(labels.toString()) }
        catch (Exception e) { infoLog "[Dashboard] WARN -- bad labels JSON: ${e.message}"; return }
    }

    // Store labels in state only -- pushPageLayout publishes them at the right time,
    // after the layout objects exist on the display. Publishing here would race ahead
    // of clearpage/layout JSONL and hit "Unknown object" errors.
    labels.each { k, v ->
        int    idx = (k as String).toInteger()
        if (idx < 1 || idx > 49) return
        state[labelKey(page, idx)] = v?.toString() ?: ""
    }
    infoLog "[Dashboard] Labels stored for page ${page}: ${labels.size()} entries"
}

private void applySlotTypes(slotTypes, int page) {
    if (!(slotTypes instanceof Map)) {
        try { slotTypes = new groovy.json.JsonSlurper().parseText(slotTypes.toString()) }
        catch (Exception e) { infoLog "[Dashboard] WARN -- bad slotTypes JSON: ${e.message}"; return }
    }
    Map typeCounts = [:]
    slotTypes.each { k, v ->
        int idx = (k as String).toInteger()
        if (idx < 1 || idx > 49) return
        String t = (v?.toString() ?: "none")
        state[typeKey(page, idx)] = t
        if (t != "none") typeCounts[t] = ((typeCounts[t] ?: 0) as int) + 1
    }
    if (typeCounts) {
        String dominant = typeCounts.max { it.value }.key
        state["pageType${page}"] = dominant
        infoLog "[Dashboard] Page ${page} type: ${dominant}"
    }
}

// ── Light tile helper ──────────────────────────────────────────────────────────

// Sends only the properties that change for tappable tiles (color, text, click).
// Updates tappable tile: color, click:true, label in text, icon in value_str (top-left).
private String buildLightTileJsonl(int page, int slot, String grid, int tileFont, String bgColor = "#000000", String label = "") {
    String sType       = state[typeKey(page, slot)] ?: "none"
    String activeState = state[stateKey(page, slot)] ?: "inactive"
    String icon        = (sType == "light")  ? (activeState == "active" ? ICON_LIGHTBULB_ON() : ICON_LIGHTBULB_OFF()) :
                         (sType == "lock")   ? (activeState == "active" ? ICON_LOCK_OPEN()    : ICON_LOCK())          :
                         (sType == "garage") ? (activeState == "active" ? ICON_GARAGE_OPEN()  : ICON_GARAGE())        : ""
    String escapedIcon  = iconToJsonEscape(icon)
    String escapedLabel = label.replace('"', '\\"').replace('\n', '\\n')
    String contrast     = contrastColor(bgColor)
    int    iFont        = iconFontFor(grid)
    int[]  ofs          = iconOffsetsFor(grid)
    int    lFont        = labelFontFor(grid)
    int    padTop       = labelPadTopFor(grid)
    return """{"page":${page},"id":${bgId(slot)},"bg_color":"${bgColor}","text_color":"${contrast}","text":"${escapedLabel}","text_font":${lFont},"align":"center","pad_top":${padTop},"value_str":"${escapedIcon}","value_font":${iFont},"value_ofs_x":${ofs[0]},"value_ofs_y":${ofs[1]},"value_color":"${contrast}","click":true}"""
}

// ── Rotation ───────────────────────────────────────────────────────────────────

def rotatePage() {
    if (state.pushInProgress) return
    int total = (state.numberOfPages ?: 1) as int
    if (total < 2) return
    int cur  = (state.rotationPage ?: 1) as int
    int next = (cur % total) + 1
    state.rotationPage = next
    String node = settings.haspNode ?: "plate"
    safePub("hasp/${node}/command/page", "${next}")
    int rotInt = (settings.rotationInterval ?: 0) as int
    if (rotInt > 0) runIn(rotInt, "rotatePage")
}

// ── Fade engine ────────────────────────────────────────────────────────────────

@Field static final int FADE_STEPS = 10

private int fadeInterval() {
    int dur = (settings.fadeDuration ?: 30) as int
    return Math.max(1, (int)(dur / FADE_STEPS))
}

private void scheduleFadeStep(int page, int idx) {
    int interval = fadeInterval()
    switch ("${page}_${idx}") {
        // Hubitat requires literal method names for runIn, so we encode page+slot in method name.
        // We support up to page 6, slot 49. Schedules fire fadeContinue which reads state.
        default:
            // Generic path: store page+idx for the single shared handler approach
            state["fadeTarget_${page}_${idx}"] = true
            runIn(interval, "fadeContinueAll")
            break
    }
}

def fadeContinueAll() {
    // Find all in-progress fades and advance them
    int numPg = (state.numberOfPages ?: 6) as int
    boolean anyRemaining = false
    (1..numPg).each { pg ->
        (1..maxSensors(pg)).each { idx ->
            String fadeKey = "p${pg}fadeStep${idx}"
            def stepObj = state[fadeKey]
            if (stepObj == null) return
            int step = (stepObj as int) + 1
            if (step >= FADE_STEPS) {
                state.remove(fadeKey)
                state.remove("fadeTarget_${pg}_${idx}")
                String ic = inactiveColorFor(pg, idx)
                publishColor(pg, idx, ic)
                publishTextColor(pg, idx, ic)
            } else {
                state[fadeKey] = step
                anyRemaining = true
                // Interpolate from active red to inactive color
                String ic    = inactiveColorFor(pg, idx)
                String color = interpolateColor("#FF0000", ic, step, FADE_STEPS)
                publishColor(pg, idx, color)
                publishTextColor(pg, idx, color)
            }
        }
    }
    if (anyRemaining) runIn(fadeInterval(), "fadeContinueAll")
}

private String interpolateColor(String from, String to, int step, int total) {
    try {
        int r1 = Integer.parseInt(from.substring(1,3), 16)
        int g1 = Integer.parseInt(from.substring(3,5), 16)
        int b1 = Integer.parseInt(from.substring(5,7), 16)
        int r2 = Integer.parseInt(to.substring(1,3), 16)
        int g2 = Integer.parseInt(to.substring(3,5), 16)
        int b2 = Integer.parseInt(to.substring(5,7), 16)
        float t  = (float)step / total
        int r    = (int)(r1 + (r2-r1)*t)
        int g    = (int)(g1 + (g2-g1)*t)
        int b    = (int)(b1 + (b2-b1)*t)
        return String.format("#%02X%02X%02X", r, g, b)
    } catch (Exception e) {
        return to
    }
}

// ── Publish helpers ────────────────────────────────────────────────────────────

private void publishColor(int page, int idx, String color) {
    String node     = settings.haspNode ?: "plate"
    String contrast = contrastColor(color)
    // Single JSONL updates bg_color and text_color atomically -- no render between them
    String jsonl = '{"page":' + page + ',"id":' + bgId(idx) + ',"bg_color":"' + color + '","text_color":"' + contrast + '"}' 
    safePub("hasp/" + node + "/command/jsonl", jsonl)
}

private void publishTextColor(int page, int idx, String color) {
    // No-op: publishColor now handles both btn and icon text colors together.
    // Kept for call-site compatibility.
}

// Send icon as value_str (top-left corner) and label as text (centered).
// value_str survives Hubitat MQTT intact since it goes via JSONL, not raw topic.
private void publishIconJsonl(String node, int page, int idx, String icon, String bgColor = null) {
    try {
        String escaped    = iconToJsonEscape(icon)
        String lbl        = state[labelKey(page, idx)] ?: ""
        String escapedLbl = lbl.replace('"', '\\"').replace('\n', '\\n')
        String grid       = activeGrid(page)
        int    iFont      = iconFontFor(grid)
        int[]  ofs        = iconOffsetsFor(grid)
        int    lFont      = labelFontFor(grid)
        int    padTop     = labelPadTopFor(grid)
        String tColor     = bgColor ? contrastColor(bgColor) : "white"

        String jsonl = '{"page":' + page + ',"id":' + bgId(idx) +
            ',"text":"' + escapedLbl + '"' +
            ',"text_font":' + lFont +
            ',"align":"center"' +
            ',"pad_top":' + padTop +
            ',"value_str":"' + escaped + '"' +
            ',"value_font":' + iFont +
            ',"value_ofs_x":' + ofs[0] +
            ',"value_ofs_y":' + ofs[1] +
            ',"value_color":"' + tColor + '"'
        if (bgColor) jsonl += ',"bg_color":"' + bgColor + '","text_color":"' + tColor + '"'
        jsonl += '}'
        debugLog "[Dashboard] Tile JSONL p${page}s${idx}: ${jsonl}"
        safePub("hasp/" + node + "/command/jsonl", jsonl)
    } catch (Exception e) {
        infoLog "[Dashboard] WARN -- publishIconJsonl p${page}s${idx}: ${e.message}"
    }
}

// Return the pre-computed JSON \\uXXXX escape for known icon glyphs.
// Avoids String.format and Integer.toHexString which are not available in Hubitat sandbox.
private String iconToJsonEscape(String s) {
    if (!s) return ""
    // Fast path: single-character icons from our known set
    if (s.length() == 1) {
        switch (s) {
            case "\uE70E": return "\\uE70E"   // run
            case "\uE004": return "\\uE004"   // account
            case "\uE026": return "\\uE026"   // alert
            case "\uE238": return "\\uE238"   // fire
            case "\uE58C": return "\\uE58C"   // water
            case "\uE58E": return "\\uE58E"   // water-percent
            case "\uE2DC": return "\\uE2DC"   // home
            case "\uE6A1": return "\\uE6A1"   // home-outline
            case "\uE6E8": return "\\uE6E8"   // lightbulb-on
            case "\uE335": return "\\uE335"   // lightbulb
            case "\uEFC6": return "\\uEFC6"   // lock-open-variant
            case "\uE33E": return "\\uE33E"   // lock
            case "\uF2D4": return "\\uF2D4"   // garage-open-variant
            case "\uF2D3": return "\\uF2D3"   // garage-variant
            case "\uE415": return "\\uE415"   // plus
            case "\uE374": return "\\uE374"   // minus
        }
    }
    // Fallback: pass ASCII strings through unchanged, drop unknowns
    boolean allAscii = true
    for (int i = 0; i < s.length(); i++) {
        if ((int) s.charAt(i) > 127) { allAscii = false; break }
    }
    return allAscii ? s : ""
}

// Returns black or white depending on which contrasts better with the given hex color.
private String contrastColor(String hex) {
    try {
        String h = hex.startsWith("#") ? hex.substring(1) : hex
        int r = Integer.parseInt(h.substring(0,2), 16)
        int g = Integer.parseInt(h.substring(2,4), 16)
        int b = Integer.parseInt(h.substring(4,6), 16)
        // Perceived luminance (WCAG formula)
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return (lum > 0.55) ? "#000000" : "#FFFFFF"
    } catch (Exception e) {
        return "#FFFFFF"
    }
}

private void publishJsonl(String node, int page, int objId, Map props) {
    def obj = [page: page, id: objId] + props
    String json = groovy.json.JsonOutput.toJson(obj)
    safePub("hasp/${node}/command/jsonl", json)
}

// ── Icon constants ─────────────────────────────────────────────────────────────

// Icon glyphs -- codes from official openHASP 0.7 font table:
// https://www.openhasp.com/0.7.0/design/fonts/
private String ICON_MOTION_ACTIVE()   { return "\uE70E" }   // run
private String ICON_MOTION_INACTIVE() { return "\uE004" }   // account
private String ICON_SMOKE_ACTIVE()    { return "\uE026" }   // alert
private String ICON_SMOKE_INACTIVE()  { return "\uE238" }   // fire
private String ICON_WATER_ACTIVE()    { return "\uE58C" }   // water
private String ICON_WATER_INACTIVE()  { return "\uE58E" }   // water-percent
private String ICON_CONTACT_OPEN()    { return "\uE2DC" }   // home (contact open/active)
private String ICON_CONTACT_CLOSED()  { return "\uE6A1" }   // home-outline (contact closed)
private String ICON_LIGHTBULB_ON()    { return "\uE6E8" }   // lightbulb-on
private String ICON_LIGHTBULB_OFF()   { return "\uE335" }   // lightbulb
private String ICON_LOCK_OPEN()       { return "\uEFC6" }   // lock-open-variant (included in openHASP font)
private String ICON_LOCK()            { return "\uE33E" }   // lock
private String ICON_GARAGE_OPEN()     { return "\uF2D4" }   // garage-open-variant (included in openHASP font)
private String ICON_GARAGE()          { return "\uF2D3" }   // garage-variant

private String activeIconFor(int page, int idx) {
    String t = state[typeKey(page, idx)] ?: state["pageType${page}"] ?: "motion"
    switch (t) {
        case "smoke":   return ICON_SMOKE_ACTIVE()
        case "water":   return ICON_WATER_ACTIVE()
        case "contact": return ICON_CONTACT_OPEN()
        case "lock":    return ICON_LOCK_OPEN()
        case "garage":  return ICON_GARAGE_OPEN()
        default:        return ICON_MOTION_ACTIVE()
    }
}

private String inactiveIconFor(int page, int idx) {
    String t = state[typeKey(page, idx)] ?: state["pageType${page}"] ?: "motion"
    switch (t) {
        case "smoke":   return ICON_SMOKE_INACTIVE()
        case "water":   return ICON_WATER_INACTIVE()
        case "contact": return ICON_CONTACT_CLOSED()
        case "light":   return ICON_LIGHTBULB_OFF()
        case "lock":    return ICON_LOCK()
        case "garage":  return ICON_GARAGE()
        default:        return ICON_MOTION_INACTIVE()
    }
}

private String inactiveColorFor(int page, int idx) {
    String t = state[typeKey(page, idx)] ?: state["pageType${page}"] ?: "motion"
    switch (t) {
        case "contact": return (settings.colorContactInactive ?: "#00FFFF")
        case "water":   return (settings.colorWaterInactive   ?: "#0000FF")
        case "smoke":   return (settings.colorSmokeInactive   ?: "#808080")
        case "light":   return (settings.colorLightInactive   ?: "#808080")
        case "lock":    return (settings.colorLockInactive    ?: "#1B5E20")   // dark green = locked/safe
        case "garage":  return (settings.colorGarageInactive  ?: "#1B5E20")   // dark green = closed/safe
        default:        return (settings.colorInactive        ?: "#008000")
    }
}

// ── Grid helpers ───────────────────────────────────────────────────────────────

private int gridNFor(String grid) {
    switch (grid) {
        case "1x1": return 1
        case "2x2": return 2
        case "3x3": return 3
        case "4x4": return 4
        case "5x5": return 5
        default:    return 2
    }
}

private int maxCharsForGrid(int n) {
    switch (n) {
        case 1: return 30
        case 2: return 16
        case 3: return 11
        case 4: return 7
        case 5: return 6
        case 6: return 5
        default: return 4
    }
}

// ── Logging ────────────────────────────────────────────────────────────────────

private void infoLog(String msg) {
    if ((settings.logLevel ?: "1") != "0") log.info msg
}

private void debugLog(String msg) {
    if ((settings.logLevel ?: "1") == "2") log.debug msg
}
