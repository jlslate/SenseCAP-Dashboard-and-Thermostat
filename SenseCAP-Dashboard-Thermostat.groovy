/*
 * This is free and unencumbered software released into the public domain.
 * For more information, please refer to <https://unlicense.org>
 */

/**
 * SenseCAP Dashboard and Thermostat App v1.0.0
 *
 * Hubitat app companion to SenseCAP Dashboard and Thermostat Driver.
 * Manages up to 6 display pages on the SenseCAP Indicator D1 via openHASP/MQTT.
 *
 * Key features:
 * - Up to 6 pages, each with independent sensor type and device selection
 * - Page type can be "mixed" -- each slot picks its own sensor type + device
 * - Slot labels can be overridden per-slot (single-type and mixed pages)
 * - Supported types: smoke / motion / water / contact / light (switch)
 * - Grid auto-sized 1x1 to 5x5 based on device count
 * - Page display order adjustable via Move Up / Move Down buttons
 * - Remove button on each page (except first) to delete pages
 * - Add Page toggle appears at bottom of last page section
 * - Devices sorted alphabetically (emoji-stripped) within each page
 * - Light pages: tap-to-toggle via MQTT button events; periodic re-sync (configurable)
 * - Sensor events gated during layout push via appPushInProgress state flag
 * - syncAllSensors fires after returnToPage1 so all states correct after render
 * - On save/reboot: rebootDisplay sent if MQTT connected, LWT triggers re-push
 * - getPageOrder() for UI (all pages); activePageOrder() for driver operations
 *
 * Author: jlslate (slate)
 * Version: 1.2.0
 */

definition(
    name: "SenseCAP Dashboard and Thermostat",
    namespace: "jlslate",
    author: "jlslate (slate)",
    description: "Auto-assigns sensors to pages by type on a SenseCAP Indicator display via MQTT",
    category: "Integration",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

// ── UI ────────────────────────────────────────────────────────────────────────

def mainPage() {
    dynamicPage(name: "mainPage", title: "SenseCAP Dashboard and Thermostat", install: true, uninstall: true) {

        section("<b>App Name</b>") {
            label title: "Rename this app (optional)", required: false
        }

        section("<b>SenseCAP Indicator Device</b>") {
            input name: "indicatorDevice",
                  type: "capability.actuator",
                  title: "Select your SenseCAP Dashboard and Thermostat device",
                  required: true,
                  multiple: false
        }

        List<Integer> ord = getPageOrder()
        int totalPages = numberOfPages()

        // Pages -- shown in current display order with inline move buttons
        ord.eachWithIndex { srcPage, dispIdx ->
            int dispPos = dispIdx + 1
            boolean canUp = (dispPos > 1)
            boolean canDn = (dispPos < ord.size())

            if (srcPage == 1 ||
                (srcPage == 2 && settings.addPage2) ||
                (srcPage == 3 && settings.addPage2 && settings.addPage3) ||
                (srcPage == 4 && settings.addPage2 && settings.addPage3 && settings.addPage4) ||
                (srcPage == 5 && settings.addPage2 && settings.addPage3 && settings.addPage4 && settings.addPage5) ||
                (srcPage == 6 && settings.addPage2 && settings.addPage3 && settings.addPage4 && settings.addPage5 && settings.addPage6)) {

                section("<b>Page ${dispPos}</b>") {
                    input name: "page${srcPage}Type",
                          type: "enum",
                          title: "Page type",
                          options: pageTypeOptions(),
                          required: false,
                          defaultValue: null,
                          submitOnChange: true,
                          width: 6

                    if (canUp) {
                        input name: "moveUp_${dispPos}",
                              type: "button",
                              title: "&#9650; Move Up",
                              width: canDn ? 2 : 3
                    }
                    if (canDn) {
                        input name: "moveDn_${dispPos}",
                              type: "button",
                              title: "&#9660; Move Down",
                              width: canUp ? 2 : 3
                    }
                    if (dispPos > 1) {
                        input name: "deletePage_${srcPage}",
                              type: "button",
                              title: "&#10006; Remove",
                              width: 2
                    }

                    String pType = settings["page${srcPage}Type"] ?: ""

                    if (pType == "mixed") {
                        renderMixedPageInputs(srcPage)
                    } else if (pType == "thermostat") {
                        input name: "page${srcPage}ThermostatDevice",
                              type: "capability.thermostat",
                              title: "Select thermostat",
                              required: false,
                              multiple: false,
                              submitOnChange: true
                        input name: "page${srcPage}ThermostatTempSensor",
                              type: "capability.temperatureMeasurement",
                              title: "Separate temperature sensor (optional)",
                              required: false,
                              multiple: false
                        paragraph "<b>Away/Home limits</b>"
                        input name: "page${srcPage}VarAwayHigh",  type: "number", title: "Away high",  required: false, width: 6
                        input name: "page${srcPage}VarAwayLow",   type: "number", title: "Away low",   required: false, width: 6
                        input name: "page${srcPage}VarHereHigh",  type: "number", title: "Home high",  required: false, width: 6
                        input name: "page${srcPage}VarHereLow",   type: "number", title: "Home low",   required: false, width: 6
                        paragraph "Fixed 2x2 layout: temp/status | + | mode | −"
                    } else if (pType) {
                        String devInputName = "page${srcPage}Devices_${pType}"
                        input name: devInputName,
                              type: capabilityFor(pType),
                              title: "Select devices",
                              required: false,
                              multiple: true,
                              submitOnChange: true
                        List selDevs = settings[devInputName] ?: []
                        int cnt = selDevs.size()
                        int n = gridSizeFor(cnt)
                        paragraph "Devices: <b>${cnt}</b> &rarr; Grid: <b>${n}x${n}</b>"
                        if (cnt > 0) {
                            input name: "page${srcPage}RenameSlots",
                                  type: "bool",
                                  title: "Rename or rearrange slots",
                                  defaultValue: false,
                                  submitOnChange: true
                            if (settings["page${srcPage}RenameSlots"]) {
                                List sortedDevs = sortDevicesForPage(srcPage, selDevs)
                                int totalDevs = sortedDevs.size()
                                sortedDevs.eachWithIndex { dev, idx ->
                                    if (!dev) return
                                    int slot = idx + 1
                                    String hint = stripEmoji(dev.displayName ?: "")
                                    input name: "page${srcPage}SlotPos${dev.id}",
                                          type: "number",
                                          title: "Position",
                                          required: false,
                                          defaultValue: slot,
                                          range: "1..${totalDevs}",
                                          submitOnChange: true,
                                          width: 2
                                    input name: "page${srcPage}SlotLabel${slot}",
                                          type: "text",
                                          title: "Label (default: ${hint})",
                                          required: false,
                                          defaultValue: "",
                                          width: 10
                                }
                            }
                        }
                    }

                    // Add-page toggle at the last displayed page
                    int activePages = ord.size()
                    boolean isLastDisplayed = (dispPos == activePages)
                    if (isLastDisplayed && activePages < 6) {
                        switch (activePages) {
                            case 1: input name: "addPage2", type: "bool", title: "Add page 2", defaultValue: false, submitOnChange: true; break
                            case 2: input name: "addPage3", type: "bool", title: "Add page 3", defaultValue: false, submitOnChange: true; break
                            case 3: input name: "addPage4", type: "bool", title: "Add page 4", defaultValue: false, submitOnChange: true; break
                            case 4: input name: "addPage5", type: "bool", title: "Add page 5", defaultValue: false, submitOnChange: true; break
                            case 5: input name: "addPage6", type: "bool", title: "Add page 6", defaultValue: false, submitOnChange: true; break
                        }
                    }
                }
            }
        }

        section("<b>Options</b>") {
            input name: "syncOnStartup",
                  type: "bool",
                  title: "Sync all sensor states on startup/save",
                  defaultValue: true
            input name: "lightSyncInterval",
                  type: "enum",
                  title: "Re-sync light states every",
                  options: ["0":"Never","5":"5 minutes","10":"10 minutes","30":"30 minutes"],
                  defaultValue: "10"
            input name: "logLevel",
                  type: "enum",
                  title: "Logging Level",
                  options: ["0":"None","1":"Info only","2":"Info + Debug"],
                  defaultValue: "1",
                  required: true
        }

        section("<b>Status</b>") {
            int total = activePageOrder().size()
            int devices = activePageOrder().sum { pg -> pageDeviceCount(pg) } as int
            paragraph "Pages: <b>${total}</b> -- Total devices: <b>${devices}</b>"
            if (settings.indicatorDevice) {
                paragraph "MQTT status: <b>${settings.indicatorDevice.currentValue('mqttStatus') ?: 'unknown'}</b>"
            }
        }
    }
}

// Render per-slot inputs for a mixed page.
// The user picks a grid size first, then gets one type+device row per slot.
private void renderMixedPageInputs(int srcPage) {
    input name: "page${srcPage}MixedGrid",
          type: "enum",
          title: "Grid size (slots)",
          options: mixedGridOptions(),
          required: false,
          defaultValue: "1",
          submitOnChange: true

    String gridSel = settings["page${srcPage}MixedGrid"] ?: "1"
    int gridN = gridSel as int
    int totalSlots = gridN * gridN

    paragraph "Grid: <b>${gridN}x${gridN}</b> (${totalSlots} slots)"

    (1..totalSlots).each { slot ->
        String typeKey = "page${srcPage}MixedSlot${slot}Type"
        String slotType = settings[typeKey] ?: ""

        input name: typeKey,
              type: "enum",
              title: "Slot ${slot} type",
              options: sensorTypeOptions(),
              required: false,
              defaultValue: null,
              submitOnChange: true,
              width: 4

        if (slotType) {
            String devKey = "page${srcPage}MixedSlot${slot}Device_${slotType}"
            def slotDev = settings[devKey]
            input name: devKey,
                  type: capabilityFor(slotType),
                  title: "Slot ${slot} device",
                  required: false,
                  multiple: false,
                  submitOnChange: true,
                  width: slotDev ? 5 : 8
            if (slotDev) {
                String hint = stripEmoji(slotDev.displayName ?: "")
                input name: "page${srcPage}MixedSlot${slot}Label",
                      type: "text",
                      title: "Slot ${slot} label (default: ${hint})",
                      required: false,
                      defaultValue: "",
                      width: 3
            }
        }
    }
}

// ── Option maps ───────────────────────────────────────────────────────────────

private Map pageTypeOptions() {
    ["smoke":"Smoke detectors",
     "motion":"Motion sensors",
     "water":"Water sensors",
     "contact":"Contact sensors",
     "lock":"Locks",
     "garage":"Garage doors",
     "light":"Lights (switch)",
     "thermostat":"Thermostat",
     "mixed":"Mixed (any sensor per slot)"]
}

private Map sensorTypeOptions() {
    ["smoke":"Smoke",
     "motion":"Motion",
     "water":"Water",
     "contact":"Contact",
     "lock":"Lock",
     "garage":"Garage door",
     "light":"Light (switch)"]
}

private Map mixedGridOptions() {
    ["1":"1x1 (1 slot)",
     "2":"2x2 (4 slots)",
     "3":"3x3 (9 slots)",
     "4":"4x4 (16 slots)",
     "5":"5x5 (25 slots)"]
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private String capabilityFor(String sType) {
    switch (sType) {
        case "smoke":   return "capability.smokeDetector"
        case "motion":  return "capability.motionSensor"
        case "water":   return "capability.waterSensor"
        case "contact": return "capability.contactSensor"
        case "lock":    return "capability.lock"
        case "garage":  return "capability.garageDoorControl"
        case "light":       return "capability.switch"
        case "thermostat":  return "capability.thermostat"
        default:            return "capability.sensor"
    }
}

private String attributeFor(String sType) {
    switch (sType) {
        case "smoke":   return "smoke"
        case "motion":  return "motion"
        case "water":   return "water"
        case "contact": return "contact"
        case "lock":    return "lock"
        case "garage":  return "door"
        case "light":   return "switch"
        default:        return "motion"
    }
}

private String activeValueFor(String sType) {
    switch (sType) {
        case "smoke":   return "detected"
        case "motion":  return "active"
        case "water":   return "wet"
        case "contact": return "open"
        case "lock":    return "unlocked"
        case "garage":  return "open"
        case "light":   return "on"
        default:        return "active"
    }
}

private int numberOfPages() {
    if (!settings.addPage2) return 1
    if (!settings.addPage3) return 2
    if (!settings.addPage4) return 3
    if (!settings.addPage5) return 4
    if (!settings.addPage6) return 5
    return 6
}

// Returns the flat ordered device list for a page (for non-mixed pages).
private List pageDevices(int page) {
    String sType = settings["page${page}Type"] ?: ""
    if (!sType || sType == "mixed") return []
    return (settings["page${page}Devices_${sType}"] ?: []) as List
}

// Returns total device count for a page (works for both mixed and single-type).
private int pageDeviceCount(int page) {
    String pType = settings["page${page}Type"] ?: ""
    if (pType == "mixed") {
        int gridN = (settings["page${page}MixedGrid"] ?: "1") as int
        int count = 0
        (1..gridN * gridN).each { slot ->
            String slotType = settings["page${page}MixedSlot${slot}Type"] ?: ""
            if (slotType) {
                def dev = settings["page${page}MixedSlot${slot}Device_${slotType}"]
                if (dev) count++
            }
        }
        return count
    }
    if (pType == "thermostat") return settings["page${page}ThermostatDevice"] ? 1 : 0
    return pageDevices(page)?.size() ?: 0
}

private String pageType(int page) {
    return (settings["page${page}Type"] ?: "motion") as String
}

// Build a slot map: deviceId -> slotNumber (1-based).
// For mixed pages, slot numbers match the grid slot indices directly.
private Map buildSlotMap(int page, List devices, String sType) {
    Map m = [:]
    if (sType == "mixed") {
        int gridN = (settings["page${page}MixedGrid"] ?: "1") as int
        (1..gridN * gridN).each { slot ->
            String slotType = settings["page${page}MixedSlot${slot}Type"] ?: ""
            if (slotType) {
                def dev = settings["page${page}MixedSlot${slot}Device_${slotType}"]
                if (dev) m[dev.id.toString()] = slot
            }
        }
    } else {
        devices.eachWithIndex { dev, idx ->
            if (dev) m[dev.id.toString()] = idx + 1
        }
    }
    return m
}

// Returns a List of [slot, sType, dev] triples for a page (all types combined).
// Used for sync and subscription -- works for both mixed and single-type pages.
private List<Map> pageSlotEntries(int page) {
    String pType = settings["page${page}Type"] ?: ""
    List<Map> entries = []
    if (pType == "mixed") {
        int gridN = (settings["page${page}MixedGrid"] ?: "1") as int
        (1..gridN * gridN).each { slot ->
            String slotType = settings["page${page}MixedSlot${slot}Type"] ?: ""
            if (slotType) {
                def dev = settings["page${page}MixedSlot${slot}Device_${slotType}"]
                if (dev) entries << [slot: slot, sType: slotType, dev: dev]
            }
        }
    } else if (pType == "thermostat") {
        def dev = settings["page${page}ThermostatDevice"]
        if (dev) entries << [slot: 1, sType: "thermostat", dev: dev]
    } else if (pType) {
        List devs = sortDevicesForPage(page, pageDevices(page))
        devs.eachWithIndex { dev, idx ->
            if (dev) entries << [slot: idx + 1, sType: pType, dev: dev]
        }
    }
    return entries
}

private List<Integer> getPageOrder() {
    int total = numberOfPages()
    List stored = state.pageOrder ?: []
    List<Integer> order = stored.findAll { it >= 1 && it <= total }.collect { it as int }
    (1..total).each { pg -> if (!order.contains(pg)) order << pg }
    return order
}

private List<Integer> activePageOrder() {
    return getPageOrder()
}

// ── Button handler ────────────────────────────────────────────────────────────

def appButtonHandler(String buttonName) {
    List<Integer> order = getPageOrder()
    boolean changed = false

    if (buttonName.startsWith("deletePage_")) {
        int srcPage = buttonName.replace("deletePage_", "").toInteger()
        int total = numberOfPages()

        for (int i = srcPage; i < total; i++) {
            int next = i + 1
            String nextType = settings["page${next}Type"] ?: ""
            if (nextType) {
                app.updateSetting("page${i}Type", [value: nextType, type: "enum"])
            } else {
                app.clearSetting("page${i}Type")
            }
            // Copy mixed grid setting if applicable
            if (nextType == "mixed") {
                String ng = settings["page${next}MixedGrid"] ?: "1"
                app.updateSetting("page${i}MixedGrid", [value: ng, type: "enum"])
            } else {
                app.clearSetting("page${i}MixedGrid")
            }
            state["prevType${i}"] = nextType
        }

        app.clearSetting("page${total}Type")
        app.clearSetting("page${total}Devices")
        app.clearSetting("page${total}MixedGrid")
        state["prevType${total}"] = ""

        switch (total) {
            case 2: app.updateSetting("addPage2", [value: false, type: "bool"]); break
            case 3: app.updateSetting("addPage3", [value: false, type: "bool"]); break
            case 4: app.updateSetting("addPage4", [value: false, type: "bool"]); break
            case 5: app.updateSetting("addPage5", [value: false, type: "bool"]); break
            case 6: app.updateSetting("addPage6", [value: false, type: "bool"]); break
        }

        state.pageOrder = null
        infoLog "[Dashboard] Deleted page at position ${srcPage}, shifted pages down, new total ${total - 1}"
        return
    }

    if (buttonName.startsWith("moveUp_")) {
        int pos = buttonName.replace("moveUp_", "").toInteger()
        if (pos > 1 && pos <= order.size()) {
            int tmp = order[pos - 2]
            order[pos - 2] = order[pos - 1]
            order[pos - 1] = tmp
            changed = true
        }
    } else if (buttonName.startsWith("moveDn_")) {
        int pos = buttonName.replace("moveDn_", "").toInteger()
        if (pos >= 1 && pos < order.size()) {
            int tmp = order[pos - 1]
            order[pos - 1] = order[pos]
            order[pos] = tmp
            changed = true
        }
    }

    if (changed) {
        state.pageOrder = order
        infoLog "[Dashboard] Page order: ${order.collect { pageType(it) }.join(' -> ')}"
    }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

def installed() {
    infoLog "[Dashboard] App installed"
    initialize()
}

def updated() {
    infoLog "[Dashboard] App updated"
    unsubscribe()
    initialize()
}

def uninstalled() {
    unsubscribe()
}

def initialize() {
    if (!settings.indicatorDevice) {
        infoLog "[Dashboard] No indicator device selected"
        return
    }

    int total = numberOfPages()
    List<Integer> order = getPageOrder()
    state.pageOrder = order
    infoLog "[Dashboard] Display order: ${order.collect { pageType(it) }.join(' -> ')}"


    // Push grid layouts in display order
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        String pType = pageType(srcPage)
        String grid
        if (pType == "mixed") {
            int gridN = (settings["page${srcPage}MixedGrid"] ?: "1") as int
            grid = "${gridN}x${gridN}"
        } else if (pType == "thermostat") {
            grid = "thermostat"
        } else {
            List devs = pageDevices(srcPage)
            grid = nxnString(devs.size())
        }
        try {
            settings.indicatorDevice."setPage${dispPage}GridLayout"(grid)
        } catch (Exception e) {
            infoLog "[Dashboard] WARN -- setPage${dispPage}GridLayout failed: ${e.message}"
        }
    }

    int activePages = activePageOrder().size()
    try {
        settings.indicatorDevice.setNumberOfPages(activePages)
    } catch (Exception e) {
        infoLog "[Dashboard] WARN -- setNumberOfPages failed: ${e.message}"
    }

    // Build slot maps and subscribe devices
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        String sType = pageType(srcPage)
        List devs = (sType == "mixed") ? [] : sortDevicesForPage(srcPage, pageDevices(srcPage))
        Map slotMap = buildSlotMap(srcPage, devs, sType)
        state["slotMap${dispPage}"] = slotMap
        state["pageType${dispPage}"] = sType
        subscribePageDevices(dispPage, srcPage, sType)
    }

    subscribe(settings.indicatorDevice, "displayRebooted", displayRebootedHandler)
    subscribe(settings.indicatorDevice, "layoutPushComplete", layoutPushCompleteHandler)
    unsubscribe(thermostatTappedHandler)
    unsubscribe(lightTappedHandler)
    subscribe(settings.indicatorDevice, "lightTapped",      lightTappedHandler)
    subscribe(settings.indicatorDevice, "thermostatTapped", thermostatTappedHandler)
    subscribe(location, "mode", locationModeHandler)
    // Periodic sync fallback for thermostat pages
    unschedule("thermostatPeriodicSync")
    runEvery1Minute("thermostatPeriodicSync")

    String mqttSt = settings.indicatorDevice.currentValue("mqttStatus") ?: ""
    if (mqttSt.startsWith("Connected")) {
        infoLog "[Dashboard] Rebooting display to clear stale state"
        try { settings.indicatorDevice.rebootDisplay() } catch (Exception e) { infoLog "[Dashboard] WARN -- rebootDisplay: ${e.message}" }
        runIn(35, "pushSlotTypesAndLayouts")
    } else {
        runIn(2, "pushSlotTypesAndLayouts")
    }

    unschedule("syncLightStates")
    int syncMins = (settings.lightSyncInterval ?: "10") as int
    if (syncMins > 0) schedule("0 */${syncMins} * ? * *", syncLightStates)

    infoLog "[Dashboard] Initialized -- ${total} page(s)"
    // Push correct setpoints to all thermostat pages based on current mode
    runIn(5, "runAutoControlAllPages")
}

private void subscribePageDevices(int dispPage, int srcPage, String pType) {
    List<Map> entries = pageSlotEntries(srcPage)
    entries.each { e ->
        def dev = e.dev
        String sType = e.sType
        if (!dev) return
        switch (sType) {
            case "smoke":   subscribe(dev, "smoke",   smokeHandler);   break
            case "motion":  subscribe(dev, "motion",  motionHandler);  break
            case "water":   subscribe(dev, "water",   waterHandler);   break
            case "contact": subscribe(dev, "contact", contactHandler); break
            case "lock":    subscribe(dev, "lock",    lockHandler);    break
            case "garage":  subscribe(dev, "door",    garageHandler);  break
            case "light":   subscribe(dev, "switch",  lightHandler);       break
            case "thermostat":
                subscribe(dev, "temperature",          thermostatHandler)
                subscribe(dev, "heatingSetpoint",      thermostatHandler)
                subscribe(dev, "coolingSetpoint",      thermostatHandler)
                subscribe(dev, "thermostatMode",       thermostatHandler)
                subscribe(dev, "thermostatOperatingState", thermostatHandler)
                // Separate temp sensor if configured for this page
                def tSensor = settings["page${dispPage}ThermostatTempSensor"]
                if (tSensor) subscribe(tSensor, "temperature", thermostatHandler)
                break
        }
    }
}

// ── Push layout ───────────────────────────────────────────────────────────────

def pushSlotTypesAndLayouts() {
    List<Integer> order = activePageOrder()
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        String sType = pageType(srcPage)
        List devs = (sType == "mixed") ? [] : sortDevicesForPage(srcPage, pageDevices(srcPage))
        pushPageSlotTypes(dispPage, srcPage, devs, sType)
        pauseExecution(100)
        pushPageLabels(dispPage, srcPage, devs, sType)
        pauseExecution(100)
    }

    pauseExecution(500)
    int activePages = activePageOrder().size()
    try { settings.indicatorDevice.setNumberOfPages(activePages) } catch (Exception e) { }
    pauseExecution(500)

    // Register thermostat device IDs so driver can handle taps directly (no subscription needed)
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        if (pageType(srcPage) == "thermostat") {
            def dev = settings["page${srcPage}ThermostatDevice"]
            if (dev) {
                try {
                    settings.indicatorDevice."setPage${dispPage}ThermostatDevice"(dev.id.toString())
                    // Also set the driver preference so it can call device commands directly
                    settings.indicatorDevice.updateSetting("thermostatPage${dispPage}", [value: dev, type: "capability.thermostat"])
                    infoLog "[Dashboard] Registered thermostat device ${dev.displayName} for page ${dispPage}"
                } catch (Exception e) {
                    infoLog "[Dashboard] WARN -- thermostat registration failed: ${e.message}"
                }
            }
        }
    }

    state.appPushInProgress = true
    try {
        settings.indicatorDevice.pushAllLayouts(activePages)
    } catch (Exception e) {
        infoLog "[Dashboard] WARN -- pushAllLayouts failed: ${e.message}"
    }
}

private void pushPageSlotTypes(int dispPage, int srcPage, List devices, String sType) {
    Map types = [:]
    if (sType == "mixed") {
        int gridN = (settings["page${srcPage}MixedGrid"] ?: "1") as int
        int totalSlots = gridN * gridN
        (1..totalSlots).each { slot ->
            String slotType = settings["page${srcPage}MixedSlot${slot}Type"] ?: ""
            types[slot] = slotType ?: "none"
        }
    } else if (sType == "thermostat") {
        // Slot 1 = thermostat display, slots 2-6 = thermostat control buttons
        (1..4).each { slot -> types[slot] = "thermostat" }
    } else {
        int n = gridSizeFor(devices.size())
        (1..n * n).each { slot ->
            types[slot] = (slot <= devices.size()) ? sType : "none"
        }
    }
    try {
        switch (dispPage) {
            case 1: settings.indicatorDevice.updatePage1SlotTypes(types); break
            case 2: settings.indicatorDevice.updatePage2SlotTypes(types); break
            case 3: settings.indicatorDevice.updatePage3SlotTypes(types); break
            case 4: settings.indicatorDevice.updatePage4SlotTypes(types); break
            case 5: settings.indicatorDevice.updatePage5SlotTypes(types); break
            case 6: settings.indicatorDevice.updatePage6SlotTypes(types); break
        }
    } catch (Exception e) {
        infoLog "[Dashboard] WARN -- updatePage${dispPage}SlotTypes failed: ${e.message}"
    }
}

private void pushPageLabels(int dispPage, int srcPage, List devices, String sType) {
    if (sType == "thermostat") return   // thermostat manages its own display
    Map labels = [:]
    if (sType == "mixed") {
        int gridN = (settings["page${srcPage}MixedGrid"] ?: "1") as int
        int maxChars = maxCharsForGrid(gridN)
        (1..gridN * gridN).each { slot ->
            String slotType = settings["page${srcPage}MixedSlot${slot}Type"] ?: ""
            if (slotType) {
                def dev = settings["page${srcPage}MixedSlot${slot}Device_${slotType}"]
                if (dev) {
                    String custom = (settings["page${srcPage}MixedSlot${slot}Label"] ?: "").trim()
                    String name = custom ?: stripEmoji(dev.displayName ?: "")
                    if (name) labels[slot] = wrapLabel(name, maxChars)
                }
            }
        }
    } else {
        if (!devices) return
        int n = gridSizeFor(devices.size())
        int maxChars = maxCharsForGrid(n)
        devices.eachWithIndex { dev, idx ->
            if (!dev) return
            int slot = idx + 1
            String custom = (settings["page${srcPage}SlotLabel${slot}"] ?: "").trim()
            String name = custom ?: stripEmoji(dev.displayName ?: "")
            if (name) labels[slot] = wrapLabel(name, maxChars)
        }
    }
    if (!labels) return
    try {
        switch (dispPage) {
            case 1: settings.indicatorDevice.updatePage1Labels(labels); break
            case 2: settings.indicatorDevice.updatePage2Labels(labels); break
            case 3: settings.indicatorDevice.updatePage3Labels(labels); break
            case 4: settings.indicatorDevice.updatePage4Labels(labels); break
            case 5: settings.indicatorDevice.updatePage5Labels(labels); break
            case 6: settings.indicatorDevice.updatePage6Labels(labels); break
        }
    } catch (Exception e) {
        infoLog "[Dashboard] WARN -- updatePage${dispPage}Labels failed: ${e.message}"
    }
}

// ── Event handlers ────────────────────────────────────────────────────────────

def lightHandler(evt) {
    handleEvent(evt, "light", "on")
}

def smokeHandler(evt) {
    handleEvent(evt, "smoke", "detected")
}

def motionHandler(evt) {
    handleEvent(evt, "motion", "active")
}

def waterHandler(evt) {
    handleEvent(evt, "water", "wet")
}

def contactHandler(evt) {
    handleEvent(evt, "contact", "open")
}

def lockHandler(evt) {
    handleEvent(evt, "lock", "unlocked")
}

def garageHandler(evt) {
    handleEvent(evt, "garage", "open")
}

private void handleThermostatTapInApp(String tapValue) {
    List parts = tapValue?.split(",")
    if (!parts || parts.size() < 5) { infoLog "[Dashboard] thermostatTap bad value: ${tapValue}"; return }
    int dispPage    = parts[0] as int
    int slot        = parts[1] as int
    String mode     = parts[2]
    BigDecimal heat = parts[3] as BigDecimal
    BigDecimal cool = parts[4] as BigDecimal

    List<Integer> order = getPageOrder()
    if (dispPage < 1 || dispPage > order.size()) return
    int srcPage = order[dispPage - 1]
    if (pageType(srcPage) != "thermostat") return
    def dev = settings["page${srcPage}ThermostatDevice"]
    if (!dev) { infoLog "[Dashboard] thermostatTap: no device for page ${srcPage}"; return }

    boolean away = (location.mode == "Away")

    // +/- disabled when away
    if (away && (slot == 2 || slot == 4)) {
        infoLog "[Dashboard] Away mode -- +/- taps ignored"
        return
    }

    BigDecimal hereHigh = getThermostatLimit(settings["page${srcPage}VarHereHigh"] ?: "")
    BigDecimal hereLow  = getThermostatLimit(settings["page${srcPage}VarHereLow"]  ?: "")

    infoLog "[Dashboard] Thermostat tap page ${dispPage} slot ${slot}: mode=${mode} heat=${heat} cool=${cool} away=${away}"
    switch (slot) {
        case 2:
            if (mode == "heat") {
                BigDecimal newSp = heat + 1
                if (hereHigh != null) newSp = [newSp, hereHigh].min()
                dev.setHeatingSetpoint(newSp)
            } else if (mode == "cool") {
                BigDecimal newSp = cool + 1
                if (hereHigh != null) newSp = [newSp, hereHigh].min()
                dev.setCoolingSetpoint(newSp)
            }
            break
        case 3:
            List<String> cycle = ["cool", "off", "heat"]
            String liveMode = dev.currentValue("thermostatMode") ?: "off"
            int curIdx = cycle.indexOf(liveMode)
            if (curIdx < 0) curIdx = 0
            String next = cycle[(curIdx + 1) % cycle.size()]
            infoLog "[Dashboard] Mode: ${liveMode} -> ${next}"
            dev.setThermostatMode(next)
            break
        case 4:
            if (mode == "heat") {
                BigDecimal newSp = heat - 1
                if (hereLow != null) newSp = [newSp, hereLow].max()
                dev.setHeatingSetpoint(newSp)
            } else if (mode == "cool") {
                BigDecimal newSp = cool - 1
                if (hereLow != null) newSp = [newSp, hereLow].max()
                dev.setCoolingSetpoint(newSp)
            }
            break
    }
    state.pendingThermostatSyncPage = dispPage
    runIn(3, "runPendingThermostatSync")
}

def runPendingThermostatSync() {
    int dispPage = (state.pendingThermostatSyncPage ?: 0) as int
    if (dispPage < 1) return
    List<Integer> order = getPageOrder()
    if (dispPage > order.size()) return
    int srcPage = order[dispPage - 1]
    def dev = settings["page${srcPage}ThermostatDevice"]
    if (dev) syncThermostatDisplay(dispPage, dev)
}



def thermostatHandler(evt) {
    debugLog "[Dashboard] thermostatHandler: ${evt.device.displayName} ${evt.name}=${evt.value}"
    if (state.appPushInProgress) return
    String deviceId = evt.device.id.toString()
    List<Integer> order = activePageOrder()
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        if (pageType(srcPage) != "thermostat") return
        def dev = settings["page${srcPage}ThermostatDevice"]
        if (!dev || dev.id.toString() != deviceId) return
        syncThermostatDisplay(dispPage, dev)
    }
}

def thermostatTappedHandler(evt) {
    List parts = evt.value?.split(",")
    if (parts?.size() == 5) handleThermostatTapInApp(evt.value)
}

private void syncThermostatDisplay(int dispPage, dev) {
    if (!dev) return
    List<Integer> order = getPageOrder()
    int srcPage = (dispPage <= order.size()) ? order[dispPage - 1] : dispPage

    // Use separate temp sensor if configured for this page
    def tSensor = settings["page${srcPage}ThermostatTempSensor"]
    String temp
    if (tSensor) {
        def sv = tSensor.currentValue("temperature")
        temp = sv != null ? sv.toString() : "--"
    } else {
        temp = (dev.currentValue("temperature") ?: "--").toString()
    }

    String heat  = (dev.currentValue("heatingSetpoint") ?: "--").toString()
    String cool  = (dev.currentValue("coolingSetpoint") ?: "--").toString()
    String mode  = (dev.currentValue("thermostatMode") ?: "off").toString()
    String opSt  = (dev.currentValue("thermostatOperatingState") ?: "idle").toString()
    boolean away = (location.mode == "Away")

    infoLog "[Dashboard] Thermostat sync p${dispPage}: temp=${temp} heat=${heat} cool=${cool} mode=${mode} op=${opSt} away=${away}"
    Map data = [temp: temp, heatSetpoint: heat, coolSetpoint: cool, mode: mode, operatingState: opSt, away: away.toString()]
    try {
        settings.indicatorDevice.updateThermostatDisplay(dispPage, data)
    } catch (Exception e) {
        infoLog "[Dashboard] WARN -- updateThermostatDisplay failed: ${e.message}"
    }
}

def thermostatPeriodicSync() {
    if (state.appPushInProgress) return
    List<Integer> order = activePageOrder()
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        if (pageType(srcPage) != "thermostat") return
        def dev = settings["page${srcPage}ThermostatDevice"]
        if (dev) syncThermostatDisplay(dispPage, dev)
    }
}

def locationModeHandler(evt) {
    infoLog "[Dashboard] Location mode changed: ${evt.value}"
    thermostatPeriodicSync()
    runAutoControlAllPages()
}

def runAutoControlAllPages() {
    List<Integer> order = activePageOrder()
    order.eachWithIndex { srcPage, dispIdx ->
        if (pageType(srcPage) == "thermostat") runAutoControlForPage(srcPage)
    }
}

private void runAutoControlForPage(int srcPage) {
    def dev = settings["page${srcPage}ThermostatDevice"]
    if (!dev) return

    boolean away = (location.mode == "Away")

    if (away) {
        BigDecimal awayHigh = getThermostatLimit(settings["page${srcPage}VarAwayHigh"])
        BigDecimal awayLow  = getThermostatLimit(settings["page${srcPage}VarAwayLow"])
        if (awayHigh == null || awayLow == null) {
            infoLog "[Dashboard] Auto-control p${srcPage}: away limits not set"
            return
        }

        // Push away setpoints
        BigDecimal curHeat = dev.currentValue("heatingSetpoint") as BigDecimal
        BigDecimal curCool = dev.currentValue("coolingSetpoint") as BigDecimal
        if (curHeat != awayLow)  { infoLog "[Dashboard] Away: setting heat setpoint ${curHeat} -> ${awayLow}";  dev.setHeatingSetpoint(awayLow) }
        if (curCool != awayHigh) { infoLog "[Dashboard] Away: setting cool setpoint ${curCool} -> ${awayHigh}"; dev.setCoolingSetpoint(awayHigh) }

        def tSensor = settings["page${srcPage}ThermostatTempSensor"]
        def tempVal = tSensor ? tSensor.currentValue("temperature") : dev.currentValue("temperature")
        if (tempVal == null) { infoLog "[Dashboard] Auto-control p${srcPage}: no temperature"; return }
        BigDecimal temp = tempVal as BigDecimal

        String curMode   = dev.currentValue("thermostatMode") ?: "off"
        String targetMode
        if (temp > awayHigh)     { targetMode = "cool"; infoLog "[Dashboard] Away: ${temp} > ${awayHigh} -- cooling" }
        else if (temp < awayLow) { targetMode = "heat"; infoLog "[Dashboard] Away: ${temp} < ${awayLow} -- heating" }
        else                     { targetMode = "off";  infoLog "[Dashboard] Away: ${temp} within [${awayLow}-${awayHigh}] -- off" }

        if (curMode != targetMode) {
            infoLog "[Dashboard] Auto-control p${srcPage}: ${curMode} -> ${targetMode}"
            dev.setThermostatMode(targetMode)
        }
    } else {
        // Home -- push home setpoints
        BigDecimal hereHigh = getThermostatLimit(settings["page${srcPage}VarHereHigh"])
        BigDecimal hereLow  = getThermostatLimit(settings["page${srcPage}VarHereLow"])
        if (hereHigh == null || hereLow == null) {
            infoLog "[Dashboard] Auto-control p${srcPage}: home limits not set"
            return
        }
        BigDecimal curHeat = dev.currentValue("heatingSetpoint") as BigDecimal
        BigDecimal curCool = dev.currentValue("coolingSetpoint") as BigDecimal
        infoLog "[Dashboard] Auto-control Home p${srcPage}: low=${hereLow} high=${hereHigh} curHeat=${curHeat} curCool=${curCool}"
        if (curHeat != hereLow)  { infoLog "[Dashboard] Home: heat ${curHeat} -> ${hereLow}";  dev.setHeatingSetpoint(hereLow) }
        if (curCool != hereHigh) { infoLog "[Dashboard] Home: cool ${curCool} -> ${hereHigh}"; dev.setCoolingSetpoint(hereHigh) }
    }
}

private BigDecimal getThermostatLimit(val) {
    if (val == null || val == "") return null
    try { return val as BigDecimal } catch (Exception e) { return null }
}

private void handleEvent(evt, String sType, String activeValue) {
    if (state.appPushInProgress) {
        debugLog "[Dashboard] Skipping event during push: ${evt.displayName} ${evt.value}"
        return
    }

    String deviceId = evt.device.id.toString()
    List<Integer> order = activePageOrder()
    boolean found = false

    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        String pType = pageType(srcPage)

        if (pType == "mixed") {
            // Search mixed slots for this device + matching type
            int gridN = (settings["page${srcPage}MixedGrid"] ?: "1") as int
            (1..gridN * gridN).each { slot ->
                String slotType = settings["page${srcPage}MixedSlot${slot}Type"] ?: ""
                if (slotType != sType) return
                def dev = settings["page${srcPage}MixedSlot${slot}Device_${slotType}"]
                if (!dev || dev.id.toString() != deviceId) return
                found = true
                debugLog "Event p${dispPage}s${slot} ${sType} mixed (${evt.displayName}): ${evt.value}"
                if (evt.value == activeValue) {
                    settings.indicatorDevice."setPage${dispPage}MotionActive"(slot)
                } else {
                    settings.indicatorDevice."setPage${dispPage}MotionInactive"(slot)
                }
            }
        } else {
            if (pType != sType) return
            Map slotMap = state["slotMap${dispPage}"] ?: [:]
            int slot = (slotMap[deviceId] ?: 0) as int
            if (slot < 1) return
            found = true
            debugLog "Event p${dispPage}s${slot} ${sType} (${evt.displayName}): ${evt.value}"
            if (evt.value == activeValue) {
                settings.indicatorDevice."setPage${dispPage}MotionActive"(slot)
            } else {
                settings.indicatorDevice."setPage${dispPage}MotionInactive"(slot)
            }
        }
    }

    if (!found) infoLog "[Dashboard] WARN -- device ${deviceId} not found in any ${sType} slot map"
}

// ── Light tap handler ─────────────────────────────────────────────────────────

def lightTappedHandler(evt) {
    infoLog "[Dashboard] lightTappedHandler fired: ${evt.value}"
    List parts = evt.value?.split(",")
    if (!parts || parts.size() < 2) { infoLog "[Dashboard] WARN bad lightTapped value"; return }
    int dispPage = parts[0] as int
    int slot = parts[1] as int
    List<Integer> order = getPageOrder()
    if (dispPage < 1 || dispPage > order.size()) { infoLog "[Dashboard] WARN dispPage out of range"; return }

    int srcPage = order[dispPage - 1]
    String pType = pageType(srcPage)

    def dev = null
    String effectiveType = pType

    if (pType == "mixed") {
        String slotType = settings["page${srcPage}MixedSlot${slot}Type"] ?: ""
        effectiveType = slotType
        dev = settings["page${srcPage}MixedSlot${slot}Device_${slotType}"]
    } else {
        List devs = sortDevicesForPage(srcPage, pageDevices(srcPage))
        if (slot < 1 || slot > (devs?.size() ?: 0)) { infoLog "[Dashboard] WARN slot out of range"; return }
        dev = devs[slot - 1]
    }

    if (!dev) { infoLog "[Dashboard] WARN dev is null for tap p${dispPage}s${slot}"; return }

    switch (effectiveType) {
        case "light":
            String curState = dev.currentValue("switch") ?: "off"
            infoLog "[Dashboard] Toggling light: ${dev.displayName} currently ${curState}"
            if (curState == "on") { dev.off() } else { dev.on() }
            break
        case "lock":
            String curLock = dev.currentValue("lock") ?: "locked"
            infoLog "[Dashboard] Toggling lock: ${dev.displayName} currently ${curLock}"
            // Treat anything other than "locked" as unlocked (covers "unlocking", "unknown" states)
            if (curLock == "locked") { dev.unlock() } else { dev.lock() }
            break
        case "garage":
            String curDoor = dev.currentValue("door") ?: "closed"
            infoLog "[Dashboard] Toggling garage: ${dev.displayName} currently ${curDoor}"
            if (curDoor == "open") { dev.close() } else { dev.open() }
            break
        default:
            infoLog "[Dashboard] WARN tap on non-tappable type: ${effectiveType}"
    }
}

// ── Display reboot / push complete ───────────────────────────────────────────

def layoutPushCompleteHandler(evt) {
    state.appPushInProgress = false
    infoLog "[Dashboard] layoutPushCompleteHandler fired -- calling syncAllSensors"
    syncAllSensors()
    runIn(3, "runAutoControlAllPages")
}

def displayRebootedHandler(evt) {
    state.appPushInProgress = false
    infoLog "[Dashboard] Display rebooted -- repushing everything"
    // Re-subscribe in case subscriptions were lost
    subscribe(settings.indicatorDevice, "layoutPushComplete", layoutPushCompleteHandler)
    subscribe(settings.indicatorDevice, "thermostatTapped",   thermostatTappedHandler)
    infoLog "[Dashboard] Subscriptions re-established"
    subscribe(settings.indicatorDevice, "lightTapped",        lightTappedHandler)
    List<Integer> ord = activePageOrder()
    state.pageOrder = ord
    ord.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        String pType = pageType(srcPage)
        String grid
        if (pType == "mixed") {
            int gridN = (settings["page${srcPage}MixedGrid"] ?: "1") as int
            grid = "${gridN}x${gridN}"
        } else if (pType == "thermostat") {
            grid = "thermostat"
        } else {
            List devs = pageDevices(srcPage)
            grid = nxnString(devs.size())
        }
        try { settings.indicatorDevice."setPage${dispPage}GridLayout"(grid) } catch (Exception e) { }
    }
    try { settings.indicatorDevice.setNumberOfPages(getPageOrder().size()) } catch (Exception e) { }
    runIn(2, "pushSlotTypesAndLayouts")
}

// ── State sync ────────────────────────────────────────────────────────────────

def syncLightStates() {
    List<Integer> order = activePageOrder()
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        String pType = pageType(srcPage)
        if (pType == "mixed") {
            int gridN = (settings["page${srcPage}MixedGrid"] ?: "1") as int
            (1..gridN * gridN).each { slot ->
                String slotType = settings["page${srcPage}MixedSlot${slot}Type"] ?: ""
                if (slotType != "light") return
                def dev = settings["page${srcPage}MixedSlot${slot}Device_${slotType}"]
                if (!dev) return
                String cur = dev.currentValue("switch") ?: "off"
                if (cur == "on") {
                    settings.indicatorDevice."setPage${dispPage}MotionActive"(slot)
                } else {
                    settings.indicatorDevice."setPage${dispPage}MotionInactive"(slot)
                }
                pauseExecution(30)
            }
        } else if (pType == "light") {
            List devs = sortDevicesForPage(srcPage, pageDevices(srcPage))
            devs.eachWithIndex { dev, idx ->
                if (!dev) return
                int slot = idx + 1
                String cur = dev.currentValue("switch") ?: "off"
                if (cur == "on") {
                    settings.indicatorDevice."setPage${dispPage}MotionActive"(slot)
                } else {
                    settings.indicatorDevice."setPage${dispPage}MotionInactive"(slot)
                }
                pauseExecution(30)
            }
        }
    }
}

def syncAllSensors() {
    infoLog "[Dashboard] Syncing all sensor states (appPushInProgress=${state.appPushInProgress})"
    state.appPushInProgress = false
    List<Integer> order = activePageOrder()
    order.eachWithIndex { srcPage, dispIdx ->
        int dispPage = dispIdx + 1
        List<Map> entries = pageSlotEntries(srcPage)
        String pType = pageType(srcPage)

        // For single-type pages, also clear empty trailing slots
        int totalSlots = 0
        if (pType != "mixed") {
            List devs = sortDevicesForPage(srcPage, pageDevices(srcPage))
            int n = gridSizeFor(devs.size())
            totalSlots = n * n
        }

        entries.each { e ->
            def dev = e.dev
            String sType = e.sType
            int slot = e.slot
            if (!dev) return
            if (sType == "thermostat") {
                syncThermostatDisplay(dispPage, dev)
                return
            }
            String attr = attributeFor(sType)
            String actVal = activeValueFor(sType)
            String cur = dev.currentValue(attr) ?: ""
            debugLog "Sync p${dispPage}s${slot} ${sType} (${dev.displayName}) = ${cur}"
            if (cur == actVal) {
                settings.indicatorDevice."setPage${dispPage}MotionActive"(slot)
            } else {
                settings.indicatorDevice."setPage${dispPage}MotionInactive"(slot)
            }
            pauseExecution(40)
        }

        if (pType != "mixed" && pType != "thermostat" && entries.size() < totalSlots) {
            ((entries.size() + 1)..totalSlots).each { slot ->
                settings.indicatorDevice."setPage${dispPage}SlotEmpty"(slot)
                pauseExecution(30)
            }
        }
    }
}

// ── Grid / label helpers ──────────────────────────────────────────────────────

private int gridSizeFor(int count) {
    if (count <= 1) return 1
    if (count <= 4) return 2
    if (count <= 9) return 3
    if (count <= 16) return 4
    if (count <= 25) return 5
    return 5
}

private String nxnString(int count) {
    int n = gridSizeFor(count)
    return "${n}x${n}"
}

private int maxCharsForGrid(int n) {
    switch (n) {
        case 1: return 30
        case 2: return 16
        case 3: return 11
        case 4: return 7
        default: return 6
    }
}

private String stripEmoji(String text) {
    if (!text) return ""
    return text.replaceAll(/[^\x20-\x7E]/, "").replaceAll(/\s+/, " ").trim()
}

private List sortDevicesByName(List devices) {
    if (!devices) return []
    return devices.findAll { it != null }
        .sort { a, b -> stripEmoji(a.displayName ?: "").compareToIgnoreCase(stripEmoji(b.displayName ?: "")) }
}

// Returns devices in slot-position order for a single-type page.
// If the user has assigned slot positions via page${p}SlotPos${devId}, those are used.
// Ties and unset positions fall back to alphabetical order.
private List sortDevicesForPage(int page, List devices) {
    if (!devices) return []
    List valid = devices.findAll { it != null }
    // Check whether any position overrides exist for this page
    boolean hasOverrides = valid.any { dev ->
        settings["page${page}SlotPos${dev.id}"] != null
    }
    if (!hasOverrides) return sortDevicesByName(valid)
    // Sort by user-assigned position, then alphabetically as tiebreaker
    return valid.sort { a, b ->
        int posA = (settings["page${page}SlotPos${a.id}"] ?: 9999) as int
        int posB = (settings["page${page}SlotPos${b.id}"] ?: 9999) as int
        if (posA != posB) return posA <=> posB
        return stripEmoji(a.displayName ?: "").compareToIgnoreCase(stripEmoji(b.displayName ?: ""))
    }
}

private String wrapLabel(String text, int maxChars) {
    if (!text || text.length() <= maxChars) return text ?: ""
    List<String> words = text.split(" ") as List
    List<String> lines = []
    String current = ""
    words.each { word ->
        if (current.isEmpty()) {
            current = word
        } else if ((current + " " + word).length() <= maxChars) {
            current += " " + word
        } else {
            lines << current
            current = word
        }
    }
    if (current) lines << current
    return lines.join("\n")
}

// ── Logging ───────────────────────────────────────────────────────────────────

private void infoLog(String msg) {
    if ((settings.logLevel ?: "1") != "0") log.info msg
}

private void debugLog(String msg) {
    if ((settings.logLevel ?: "1") == "2") log.debug msg
}
