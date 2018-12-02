def getVersionNum() { return "1.0.0b" }
private def getVersionLabel() { return "Smarter Motion Lights : ${getVersionNum()}" }

definition(
    name: "Smarter Motion Lights",
    namespace: "qwertypo",
    author: "JB",
    description: "Turn your lights on when motion is detected unless you turn them off!",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_motion-outlet@2x.png"
)



preferences {
	section (getVersionLabel())
	section("When there is movement here:") {
		input "motion1", "capability.motionSensor", title: "Where?", multiple: true
    }
    section("Turn ON These Dimmers"){
        input "Dimswitches", "capability.switchLevel", multiple: true, required: false
        input "BrightLevel", "number", title: "Dimmer Level 1-99% (OPTIONAL) : 0 for no dimming adjustment", required: false, defaultValue: ""
	}
    section("Turn ON Switches"){
		input "switches", "capability.switch", multiple: true, required: false
        }
    section("Unless there has been movement within the past"){
		input "minutes1", "number", title: "Minutes?"
    }
    section("Turn off when there's been no movement?"){
		input "TurnOff", "enum", title: "Yes or No?", options: ["Yes", "No"], required: true, defaultValue: "Yes"
	}
    section("And it is dark (OPTIONAL)") {
		input "LightMeter", "capability.illuminanceMeasurement", title: "Where?", required: false
        input "luminanceLevel", "number", title: "1-1000", defaultValue: "250", required: false
	}
    section("Turn on when there's movement immediatelly after meeting Lux Threshold?"){
		input "LuxOn", "enum", title: "Yes or No?", options: ["Yes", "No"], required: true, defaultValue: "Yes"
    }
    
    section("Do NOT turn on between these hours:"){
			input "starting", "time", title: "Starting", required: false
			input "ending", "time", title: "Ending", required: false
	}
            
    section("Auto Adjust Threshold using averages?"){
		input "AutoThres", "enum", title: "Yes or No?", options: ["Yes", "No"], required: true, defaultValue: "No"        
        input "AutoType", "enum", title: "Progressive or Adaptive", options: ["Progressive", "Adaptive"], required: true, defaultValue: "Progressive"
        input "Ratio", "enum", title: "Threshold Average Ratio", options: ["Wide", "Medium", "Tight"], required: true, defaultValue: "Medium"
        input "QuickOn", "enum", title: "Quick Motion open Threshold?", options: ["Yes", "No"], required: true, defaultValue: "Yes" 
        input "Reset", "enum", title: "Reset Thresholds when updating settings?", options: ["Yes", "No"], required: true, defaultValue: "No"
        
   	}
    section("Notications"){
    	input "NotifyLights", "enum", title: "Notifications for Turning lights On and Off?", options: ["Yes", "No"], required: true, defaultValue: "No"        
        input "NotifyThresholds", "enum", title: "Notifications for adjusting Thresholds?", options: ["Yes", "No"], required: true, defaultValue: "No"        
    }
}

def installed() {
	Reset() 
    initialize()
    
}

def updated() {
	unsubscribe()
    unschedule()
    initialize()
    if (AutoThres == "No") {
    	Reset()
    }
    if (Reset == "Yes") {
    	Reset()
    }   
}

def Reset() {
	log.trace "---------------------------------------------- Reset : Smarter Motion Lights : $app.label ----------------------------------------------"
   
 /* Averaging Init */
    state.Average = 0
    state.RunningTotal = 0
    state.Count = 0 
    state.PeakAverage = 0
    state.PeakCount = 0
    state.RunningTotalPeaks = 0
    state.Peak = minutes1
 /* Averaging Init*/
 
 	state.threshold = minutes1
    ResetClock()
    
/* Lux events */
    state.previousSensorState = 999
    state.lightSensorState = 999
    state.LuxThresh = "No"
/* Lux events */

/* Reset Clock to now */
    log.debug "Set lights for ON next motion event" 
    state.motionEvent = (now() / 60000) - state.threshold 
/* Reset Clock to now */
       
}


def initialize() {
	log.trace "---------------------------------------------- initialize : Smarter Motion Lights : $app.label ----------------------------------------------"
    log.debug "Turn Off = $TurnOff"
    log.debug "subscribe: $motion1"
   	subscribe(motion1, "motion", motionHandler)
    
/* On/Off status checks */
	if (switches) {
    subscribe(switches, "switch.off", SwitchHandler)
   	log.debug "Subscribe Switches ${switches} Off events"
    }
    if (Dimswitches) {
    subscribe(Dimswitches, "switch.off", SwitchHandler)
    log.debug "Subscribe Dimmers ${Dimswitches} Off events"
    }
    if (switches) {
    subscribe(switches, "switch.on", SwitchHandler)
    log.debug "Subscribe Switches ${switches} On events"
    }
    if (Dimswitches) {
    subscribe(Dimswitches, "switch.on", SwitchHandler)
    log.debug "Subscribe Dimmers ${Dimswitches} On events"
    }
    SwitchHandler()
/* On/Off status checks */ 
 
    ActivityClock()
 
 /* IF LIGHTMETER SET - SUBSCRIBE TO IT */
    if (LightMeter){
    	log.debug "subscribe: $LightMeter"
    	subscribe(LightMeter, "illuminance", handleLuxChange)
    }
 /* IF LIGHTMETER SET - SUBSCRIBE TO IT */
    
 /* IF BrightLevel IS SET - CONTAIN IT TO 0-99 */
    if (BrightLevel != null) {
    	state.BrightLevel = BrightLevel // as Integer
    	if (state.BrightLevel >= 100) {
    		log.debug "state.BrightLevel >= 100"
    		state.BrightLevel = 99
    	}
    	if (state.BrightLevel <= 0) {
    	   log.debug "state.BrightLevel <= 0"
     	   state.BrightLevel = 0
    	}
 /* IF BrightLevel IS SET - CONTAIN IT TO 0-99 */ 
    
 /* IF BrightLevel IS NOT SET - SET IT TO 0 */
    } else {
    	state.BrightLevel = 0
        log.debug "BrightLevel was left empty"
    }
 /* IF BrightLevel IS NOT SET - SET IT TO 0 */
    
 /* IF luminanceLevel IS SET - CONTAIN IT TO 0-1000 */
    if (luminanceLevel != null) {
    	state.luminanceLevel = luminanceLevel // as Integer
   		if (state.luminanceLevel <= 0) {
    		state.luminanceLevel = 0
   		}
    	if (state.luminanceLevel >= 1000) {
    		state.luminanceLevel = 1000
    	}
    }
 /* IF luminanceLevel IS SET - CONTAIN IT TO 0-1000 */
    
 /* IF luminanceLevel IS NOT SET - SET IT TO 250 */
    else {
        state.luminanceLevel = 250
        log.debug "luminanceLevel was left empty"
    }
    log.info "Brightness: $state.BrightLevel Luminance: $state.luminanceLevel"
/* IF luminanceLevel IS NOT SET - SET IT TO 250 */
    
/* timeOfDayIsBetween init */
    state.timeOk = "true"
/* timeOfDayIsBetween init */
    
/* Ratio */
    state.Ratio = 1.2 // This isn't needed, but just in case
    log.debug "Ratio: $Ratio"
    if ($Ratio != null) {
    	if ($Ratio == "Wide") {
        state.Ratio = 2
        }
        if ($Ratio == "Medium") {
        state.Ratio = 1.5
        }
        if ($Ratio == "Tight") {
        state.Ratio = 1
        }
    log.debug "$state.Ratio"
    }
/* Ratio */

/* ERROR FIX TEST */
	state.TurnOff = (now() / 60000)
	/* TurnOffLights()  */
	
/* ERROR FIX TEST */
    
}

def SwitchHandler(evt) {
	log.debug "SwitchHandler"
	if (switches) {
    	def switchescurrstate = switches.currentSwitch
    	log.debug "switches.currentSwitch $switchescurrstate"
	    if (switches.currentSwitch.contains("on")) {
	    	log.debug "Some switches are On"
            state.SwitchStatus = "on"
	    } else {
	    	state.SwitchStatus = "off"
			log.debug "Switches are Off"	
	    }
    }
    if (Dimswitches) {
    	def Dimswitchescurrstate = Dimswitches.currentSwitch
    	log.debug "Dimswitches.currentLevel $Dimswitchescurrstate"
    	if (Dimswitches.currentSwitch.contains("on")) {
    	log.debug "Some Dim Switches are On"
        state.DimswitchStatus = "on"
    	} else {
    		state.DimswitchStatus = "off"
			log.debug "Dim Switches are Off"	
    	}
    }
}

def SwitchOnHandler(evt) {
	
    if (switches) {
    	def switchescurrstate = switches.currentSwitch
    	log.debug "switches.currentstate $switchescurrstate"
		state.SwitchStatus = "on"
		log.debug "Switches turned On"
    }
    if (Dimswitches) {
    	def Dimswitchescurrstate = Dimswitches.currentSwitch
        state.DimswitchStatus = "on"
			log.debug "Dim Switches turned On"   
    }
    
}

   
def ActivityClock() {
	log.trace "ActivityClock()"
    state.elapsedMinutes = (now() / 60000) - state.motionEvent /* edit */
    /*state.roundElapsed = Math.round(state.elapsedMinutes) */
    log.info "ActivityClock: $state.elapsedMinutes Minutes Elapsed: Threshold is $state.threshold Minute(s)" /* edit - Removed state.roundElapsed */
    if (AutoThres == "Yes") {
    	log.info "ActivityClock: Auto Threshold On with Threshold: $minutes1 - $AutoType: $Ratio"
        //log.debug "ActivityClock: Ratio Adjust Threshold Potentional: ${minutes1*state.Ratio}"
        }
    
    /* TEST CODE 
    def currentStateSwitches = device.currentState("switches").getValue()
    log.debug "TEST $currentStateSwitches"
    def currentStateDimSwitches = device.currentState("Dimswitches").getValue()
    log.debug "TEST $currentStateDimSwitches"
     TEST CODE */
    
}

def CheckTime() {
	if (starting) {
    	if (ending) {
			def startTime = timeToday(starting, location.timeZone)
    		def endTime = timeToday(ending, location.timeZone)
    		if (startTime > endTime) {
    			endTime.date = endTime.date + 1
    		}
    		log.debug "Window for Non-Operation is $startTime to $endTime"
            
            /* OLD IFS
    		if (now() > startTime.time) {
    			log.debug "timeToday() NOW IS AFTER START TIME"
    		}
    		if (now() < startTime.time) {
    			log.debug "timeToday() NOW IS BEFORE START TIME"
    		}
    		if (now() > endTime.time) {
    			log.debug "timeToday() NOW IS AFTER END TIME"
    		}
    		if (now() < endTime.time) {
    			log.debug "timeToday() NOW IS BEFORE END TIME"
    		}
    		if (now() < endTime.time && now() > startTime.time) {
    			log.debug "timeToday() NOW BETWEEN TIMES"
    		}
    		else {
   			 	log.debug "timeToday() NOW IS OUTSIDE OF TIMES"
    		}
            END OLD IFS */
            
  			if(timeOfDayIsBetween(startTime, endTime, (new Date()), location.timeZone)) {
    			log.debug "timeOfDayIsBetween() NOW IS BETWEEN TIMES"
                state.timeOk = "false"
    		} else {
    			log.debug "timeOfDayIsBetween() NOW IS OUTSIDE OF TIMES"
                state.timeOk = "true"
    		}
    	}
    }
    log.trace "timeOk = $state.timeOk"
}

def CheckAverages() {
	log.debug "CheckAverages"
	log.debug "state.elapsedMinutes ${state.elapsedMinutes}"
    
    //state.threshold = Math.round(state.threshold)
    
	//if (state.elapsedMinutes < (minutes1 * 1.3)) {
    //if (state.elapsedMinutes < (minutes1 * state.Ratio)) {
    if (state.elapsedMinutes < minutes1) {
  		state.Count = state.Count + 1
        state.RunningTotal = state.RunningTotal + state.elapsedMinutes
        state.Average = state.RunningTotal / state.Count
        if (state.elapsedMinutes > (state.Average * 2)) {
        	log.debug "Peak"
            
            if (state.elapsedMinutes > (state.PeakAverage * 2)) {
            	state.Peak = state.elapsedMinutes
            } 
            
        	state.PeakCount = state.PeakCount + 1
            state.RunningTotalPeaks = state.RunningTotalPeaks + state.elapsedMinutes
            state.PeakAverage = state.RunningTotalPeaks / state.PeakCount
            if (state.PeakCount > 3 && AutoThres == "Yes") {
            	state.oldthreshold = state.threshold
                state.threshold = ( state.threshold / 2 ) + state.PeakAverage * state.Ratio
                state.threshold = Math.round(state.threshold)
                if (state.threshold > minutes1) {
                	log.debug "Contain to Threshold: Threshold of $state.threshold contained to $minutes1"
                    state.threshold = minutes1 
                }
                log.debug "Ratio: $Ratio"
                log.debug "Threshold adjusted to $state.threshold"
                if (state.threshold != state.oldthreshold) {
                	if (NotifyThresholds == "Yes") {
                		sendNotificationEvent("Average Threshold for $app.label set to $state.threshold because of activity near ${motion1}")
                    }
                } else {
                	log.debug "New Threshold of $state.threshold is the same as the old one at $state.oldthreshold"
                }
            }    
            
        }
     
    }
    log.trace "Peak = $state.Peak"
    log.info "Average = $state.Average, Count = $state.Count"   
    log.info "Peak Average = $state.PeakAverage, Count = $state.PeakCount"
    
/* Coalesce Averages */
	/* Adaptive Averages */
    state.AdaptiveCoalesce = 2 // Quickly adapt the averages
    state.ProgressiveCoalesce = 25 // Slowly adapt the averages
    if (AutoType == "Adaptive" && state.Count > 20) {
    	log.trace "Adaptive: Coalesce Average Times"
        state.RunningTotal = state.RunningTotal / state.Count * state.AdaptiveCoalesce
        state.Count = state.AdaptiveCoalesce        
    }
    if (AutoType == "Adaptive" && state.PeakCount > 10) {
    	log.trace "Adaptive: Coalesce Peak Times"
        state.RunningTotalPeaks = state.RunningTotalPeaks / state.PeakCount * state.AdaptiveCoalesce
        state.PeakCount = state.AdaptiveCoalesce
    }
	/* Adaptive Averages */

	/*Progressive Averages*/
    if (AutoType == "Progressive" && state.Count > 1000) {
    	log.trace "Progressive: Coalesce Average Times"
        state.RunningTotal = state.RunningTotal / state.Count * state.ProgressiveCoalesce
        state.Count = state.ProgressiveCoalesce * 4      
    }
    if (AutoType == "Progressive" && state.PeakCount > 250) {
    	log.trace "Progressive: Coalesce Peak Times"
        state.RunningTotalPeaks = state.RunningTotalPeaks / state.PeakCount * state.ProgressiveCoalesce
        state.PeakCount = state.ProgressiveCoalesce
    }
	/*Progressive Averages*/
/* Coalesce Averages */
    
}

def ResetClock() {
	log.trace "ResetClock"
	state.motionEvent = (now() / 60000) /* edit */    
}

def ShutOffCheck() {
	log.trace "ShutOffCheck: ${state.threshold * 60 + 5}"
    ActivityClock()
    
/*--------------ALTERNATE METHOD
//	  def motionState = motion1.currentMotion
//	  log.debug "$motionState"
//    if (motionState.contains("inactive")) {
//    	log.info "ShutOffCheck: Run TurnOffLights"
//       	TurnOffLights()
//		} else {
//    	log.info "ShutOffCheck: Motion Active, keep lights on"
//   }
//--------------ALTERNATE METHOD */

   	if (state.motion == false) {
  		log.info "ShutOffCheck: Run TurnOffLights"
       	TurnOffLights()
    } else {
    	log.info "ShutOffCheck: Motion Active, keep lights on"
    }
} 

def motionHandler(evt) {
	log.trace "$motion1: motionHandler: $evt.name: $evt.value"
	if (evt.value == "active") {
		//log.debug "motionHandler: $evt.value"
        state.motion = true
		motionActiveHandler()
	} else if (evt.value == "inactive") {
		//log.debug "motionHandler: $evt.value"
        state.motion = false
        motionInactiveHandler()
	}
}
def motionActiveHandler() {
	//log.trace "motionActiveHandler()"
    ActivityClock()
    CheckTime()
    if (state.elapsedMinutes >= state.threshold) {
    	log.info "$motion1: motionActiveHandler: $state.elapsedMinutes Minutes >= $state.threshold Minute(s) Check Light Sensor"
    	checkLuminance()
    } else {
    	log.info "$motion1: motionActiveHandler: Not enough time has elapsed, do nothing"
    }
} 

def motionInactiveHandler() {
	//log.trace "motionInactiveHandler()"
    ResetClock()
    CheckAverages()
    if (TurnOff == "Yes"){
    	log.debug "Turn Off == $TurnOff"
    	log.debug "runIn(${state.threshold * 60 + 5}, ShutOffCheck, [overwrite: true])"
    	runIn(state.threshold * 60 + 5, ShutOffCheck, [overwrite: true])
    } else {
    	log.debug "Turn Off == $TurnOff"
    }
}

def handleLuxChange(evt){
	//log.trace "handleLuxChange()"
	if (LightMeter){
    
    	def lightSensorState = LightMeter.currentIlluminance
        log.debug "$LightMeter: $LightMeter.currentIlluminance"
        state.previousSensorState = state.lightSensorState
        state.lightSensorState = lightSensorState
        
    	if (state.previousSensorState <= luminanceLevel) {
        	state.LuxThresh = "Yes"
            //log.debug "handleLuxChange: $state.LuxThresh, $state.previousSensorState <= $luminanceLevel"
        } else {
        	state.LuxThresh = "No"
            //log.debug "handleLuxChange: $state.LuxThresh, $state.previousSensorState >= $luminanceLevel"
       	}
        
        log.info "$LightMeter: handleLuxChange: Previously Met Threshold? $state.LuxThresh"    	
        log.info "$LightMeter: handleLuxChange: Previous Lux State $state.previousSensorState"  
		log.info "$LightMeter: handleLuxChange: SENSOR = ${lightSensorState}"
        
        if (state.previousSensorState >= lightSensorState && lightSensorState <= luminanceLevel && state.LuxThresh == "No" && LuxOn == "Yes"){
        	log.debug "handleLuxChange: LuxOn"
            state.motionEvent = (now() /60000) - state.threshold /* edit */
            ActivityClock()
            if (state.motion == true){
				motionActiveHandler()
            }
        }
    } else {
    	log.info "handleLuxChange: SENSOR = No Light Meter"
    }
}
	
def checkLuminance() {
	//log.trace "checkLuminance()"  
	if (LightMeter){
        def lightSensorState = LightMeter.currentIlluminance
		// log.info "checkLuminance: SENSOR = ${lightSensorState}"
		if (lightSensorState != null && lightSensorState <= state.luminanceLevel) {
			log.info "$LightMeter: checkLuminance: SENSOR = ${lightSensorState} is <= ${state.luminanceLevel}: Turn On Lights"
			TurnOnLights()
        } else {
        	log.info "$LightMeter: checkLuminance: SENSOR = ${lightSensorState} is >= ${state.luminanceLevel}: Do Not Turn On Lights"
        }
    } else {
    	log.info "checkLuminance: SENSOR = No Light Meter: TURN ON LIGHTS"
        TurnOnLights()
        }
}

def QuickOn() {
	log.trace "QuickOn()"
	if (state.timeoff < 1) {
    	state.oldthreshold = state.threshold
        state.threshold = state.threshold + 1
//QUICK ON TEST TO AJUST AVERAGE 
        state.elapsedMinutes = state.elapsedMinutes + 60000
        sendNotificationEvent("$state.threshold $state.elapsedMinutes ") //delete this 
//QUICK ON TEST TO AJUST AVERAGE 
        log.trace "Added 1 to Threshold: Adjusted threshold to $state.threshold"
        if (state.threshold > minutes1) {
        	state.threshold = minutes1
            log.debug "Contain to Threshold: Threshold of $state.threshold contained to $minutes1"
            if (NotifyThresholds == "Yes") {
            	sendNotificationEvent("Quick Motion added 1 minute to Threshold for $app.label because movement near ${motion1}: Adjusted threshold to $state.threshold minutes")
           	}
        }
	} else {
    	log.debug "Quick ON not triggered"
    }
}   

def TurnOnLights() {
	log.trace "TurnOnLights()"
    state.timeoff = (now() /60000) - state.TurnOff
    log.debug "Time off was $state.timeoff"
    if (AutoThres == "Yes") {
    	if (QuickOn == "Yes") {
        	QuickOn()
        }
    }
    if (state.timeOk == "true"){
    	state.combinednote = "" 
    	if (switches) {
    		log.info "TurnOnLights: ${switches} ON"
            state.combinednote = "Turning On ${switches} because ${motion1} has been inactive for over $state.threshold minutes"
            // sendNotificationEvent("Turning On ${switches} because ${motion1} has been inactive for over $state.threshold minutes")
            switches.on()
    	}
    	if (Dimswitches) {
    		if (state.BrightLevel > 0) {
     	    	log.info "TurnOnLights: User set Dim Level ${BrightLevel}%: ${Dimswitches} Light ON at ${state.BrightLevel}%"
     	    	Dimswitches?.setLevel(state.BrightLevel)
            	if (state.combinednote != "") {
               		state.combinednote = state.combinednote + ", also turning On ${Dimswitches} at ${state.BrightLevel}% because ${motion1} has been inactive for over $state.threshold minutes"
               	} else {
               		state.combinednote = "Turning On ${Dimswitches} at ${state.BrightLevel}% because ${motion1} has been inactive for over $state.threshold minutes"
                }
               // sendNotificationEvent("Turning On ${Dimswitches} at ${state.BrightLevel}% because ${motion1} has been inactive for over $state.threshold minutes")
     	  	} else {
      	    	log.info "TurnOnLights: Not set to Dim Level: ${Dimswitches} Light ON"
     	       	Dimswitches.on()
               	if (state.combinednote != "") {
               		state.combinednote = state.combinednote + ", also turning On ${Dimswitches} because ${motion1} has been inactive for over $state.threshold minutes"
                } else {
                	state.combinednote = "Turning On ${Dimswitches} because ${motion1} has been inactive for over $state.threshold minutes"
                }
               //sendNotificationEvent("Turning On ${Dimswitches} because ${motion1} has been inactive for over $state.threshold minutes")
      	 	}
		}  
    } else {
    	log.trace "TurnOnLights(): Time Window Disabled Turning Lights On"
    }
    if (state.combinednote != "") {
    	if (NotifyLights == "Yes") {
    		sendNotificationEvent("$state.combinednote")
        }
    	log.trace "$state.combinednote"
    }
}

def TurnOffLights() {
	log.trace "TurnOffLights()"
    SwitchHandler()
    state.TurnOff = (now() / 60000)
    state.combinednote = ""
	if (switches) {
    	if (state.SwitchStatus == "on") {
        	log.info "TurnOffLights: ${switches} OFF"
       		switches.off()
            state.combinednote = "Turning off ${switches} because ${motion1} has been inactive for $state.threshold"
        	//sendNotificationEvent("Turning off ${switches} because ${motion1} has been inactive for $state.threshold")
        } else {
        log.debug "TurnOffLights() No Switches to turn off"
        }
    }
    if (Dimswitches) {
    	if (state.DimswitchStatus == "on") {
    		log.info "TurnOffLights: ${Dimswitches} OFF"
        	Dimswitches.off()
            if (state.combinednote != "") {
               		state.combinednote = state.combinednote + " also turning off ${Dimswitches} because ${motion1} has been inactive for $state.threshold"
            } else {
            	state.combinednote = "Turning off ${Dimswitches} because ${motion1} has been inactive for $state.threshold"
            }
        	//sendNotificationEvent("Turning off ${Dimswitches} because ${motion1} has been inactive for $state.threshold")
        } else {
        log.debug "TurnOffLights() No Dimmers to turn off - $state.DimswitchStatus"
        }
        
	}
    if (state.combinednote != "") {
    	if (NotifyLights == "Yes") {
    		sendNotificationEvent("$state.combinednote")
        }
    	log.trace "$state.combinednote"
    }
}