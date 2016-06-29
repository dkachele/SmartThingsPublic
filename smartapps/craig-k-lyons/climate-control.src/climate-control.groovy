//*************************************
//  Author:  Craig Lyons
//  
//  This program can be openly used for personal use.
//	This program can not be resold, offered in coordination of a sale, 
//          or be changed / modified by a person being paid to complete the task
//
//	Notes:  Used "keep me cozy 2" as a reference for developing this software
//
// *************************************


definition(
    name: "Climate Control",
    namespace: "Craig.K.Lyons",
    author: "Craig Lyons",
    description: "Uses external sensor(s) to run themostat on HVAC.  Auto changes between heating and cooling.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo@2x.png"
)

preferences() {
	section("Choose Thermostat... ") {
		input "thermostat", "capability.thermostat"
        
	}
	
    section("Choose Sensor... " ) {
		input "sensor", "capability.temperatureMeasurement", multiple: true
	}
    
    section("Set threshold " ) {
		input "tempThreshold", "decimal"
	}
    
}


def installed()
{
	log.debug "enter installed, state: $state"
	subscribeToEvents()
}

def updated()
{
	log.debug "enter updated, state: $state"
	unsubscribe()
	subscribeToEvents()
}

def subscribeToEvents()
{
	subscribe(sensor, "Temperature", sensorHandler)
	subscribe(thermostat, "temperature",     sensorHandler)
	subscribe(thermostat, "thermostatMode",  sensorHandler)
    subscribe(thermostat, "heatingSetpoint", SetpointHandler)
    subscribe(thermostat, "coolingSetpoint", SetpointHandler)
    
    state.realHeatSetPoint = thermostat.currentHeatingSetpoint
    state.tempHeatSetPoint = thermostat.currentHeatingSetpoint
    state.realCoolSetPoint = thermostat.currentCoolingSetpoint
    state.tempCoolSetPoint = thermostat.currentCoolingSetpoint
    state.slowTemp = "No"
    
    checkTemp()
}

def SetpointHandler(evt)
{
		/*
        log.trace "****** Start ********"
        log.info "thermoHeatingSetPoint: '${thermostat.currentHeatingSetpoint}'"
        log.info "temp-HeatingSetPoint: '${state.tempHeatSetPoint}'"
        log.info "real-HeatingSetPoint: '${state.realHeatSetPoint}'"
        log.info "thermoCoolingSetPoint: '${thermostat.currentCoolingSetpoint}'"
        log.info "currentTemperature: '${thermostat.currentTemperature}'"
        log.info "currentMode: '${thermostat.currentThermostatMode}'"
        log.trace "----------------"

        log.info "####################"
        */
        
        if (state.slowTemp in ["New"])
        {
         log.trace "Ignoring Shift as this is a slowing Temp"
         state.slowTemp = "Yes"
        }
        else{
        if (state.tempHeatSetPoint <= thermostat.currentHeatingSetpoint - 0.5 || state.tempHeatSetPoint >= thermostat.currentHeatingSetpoint + 0.5)
        {
            log.info "IMPORTANT: Setting realHeatSetPoint: '${thermostat.currentHeatingSetpoint}'"
            log.info "'${state.tempHeatSetPoint}' <= '${thermostat.currentHeatingSetpoint - 0.5}'"
            log.info "'${state.tempHeatSetPoint}' >= '${thermostat.currentHeatingSetpoint + 0.5}'"
            log.trace "Setting Heat"
            state.realHeatSetPoint = thermostat.currentHeatingSetpoint
            state.tempHeatSetPoint = state.realHeatSetPoint
        }
        if ((state.tempCoolSetPoint <= thermostat.currentCoolingSetpoint - 0.5 || state.tempCoolSetPoint >= thermostat.currentCoolingSetpoint + 0.5) &&
            (thermostat.currentCoolingSetpoint <= thermostat.currentTemperature - 0.2 ||  thermostat.currentCoolingSetpoint >= thermostat.currentTemperature + 0.2 ))
        //if (state.tempCoolSetPoint != thermostat.currentCoolingSetpoint)
        {
            log.info "IMPORTANT: Setting realCoolSetPoint: '${thermostat.currentCoolingSetpoint}'"
            log.info "'${state.tempCoolSetPoint}' <= '${thermostat.currentCoolingSetpoint - 0.5}'"
            log.info "'${state.tempCoolSetPoint}' >= '${thermostat.currentCoolingSetpoint + 0.5}'"
            log.info "'${thermostat.currentCoolingSetpoint}' <= '${thermostat.currentTemperature - 0.2}'"
            log.info "'${thermostat.currentCoolingSetpoint}' >= '${thermostat.currentTemperature + 0.2}'"
            log.trace "Setting Cool"
            state.realCoolSetPoint = thermostat.currentCoolingSetpoint
            state.tempCoolSetPoint = state.realCoolSetPoint
        }}
        
       runIn(10,checkTemp)
       
}

def sensorHandler(evt)
{
	checkTemp()
}

private sensorAverage ()
{
	def rightNow = new Date()
    
    if (!state.avgTime) {state.avgTime = rightNow.time-(60000)}
    
    //if(((((rightNow.time - state.avgTime) / 60000) < 1)) || state.isRunning){
    if(state.isRunning){
    	//def lastTime = (rightNow.time - state.avgTime) / 60000
        log.trace "Not Running isRunning: '${state.isRunning}', temp:'${state.avgSensorTemp}'"  
    }
    else {
        state.avgTime = rightNow.time
        state.isRunning = true
        state.totalSensor = 0
        state.numSensor = 0
        def goodList = ""
        def badList = ""

        sensor.each {
            //log.info "sensor.currentTemperature: ${sensor.currentTemperature}"
            //log.info "state.numSensor: ${state.numSensor}"
            //log.info "state.totalSensor: ${state.totalSensor}"


                def lastTime = it.events(max: 1).date
                try {
                    if (lastTime) {
                        def hours = (((rightNow.time - lastTime.time) / 60000) / 60)
                        def xhours = (hours.toFloat()/1).round(2)

                        if (xhours < 1){
                            state.totalSensor = state.totalSensor + it.currentTemperature
                            state.numSensor = state.numSensor + 1
                            if (state.numSensor > 1){goodList += ", "}
                            goodList += it.displayName
                        }
                        else{
                        	if(badList){
                            	badList += ", "
                            }
                            badList += "${it.displayName} (xhours)"
                    	}
                    }
                    else {
                    	if(badList){
                            	badList += ", "
                        }
                        badList += "${it.displayName} (?)"}
                        

                } catch (e) {
                        log.error "Device Avg: '${e}'"
                        if(badList){
                            	badList += ", "
                        }
                        badList += "${it.displayName} (er)"
                }


        }

        state.avgSensorTemp = state.totalSensor / state.numSensor

        log.info "Avg Temp: ${state.avgSensorTemp} Used: [${goodList}] Not Used: [${badList}]"
        
        state.isRunning = false
        
	}
    
    return state.avgSensorTemp
}

def changeTemp()
{
	if (thermostat.currentThermostatMode == 'heat'){
    	if(state.tempHeatSetPoint < 72.9){
        	thermostat.setHeatingSetpoint(state.tempHeatSetPoint)
    	}
        else{
        	send("ERROR: ${thermostat.displayName} attempting to set to ${state.tempHeatSetPoint}./nSensor: state.avgSensorTemp/n")
        }
    }
    else{
    	if(state.tempCoolSetPoint > 70){
        	thermostat.setCoolingSetpoint(state.tempCoolSetPoint)
    	}
        else{
        	send("ERROR: ${thermostat.displayName} trying to set to ${state.tempCoolSetPoint}./nSensor: state.avgSensorTemp/n")
        }
	}
}
def checkTemp()
{
	   	log.trace "********Start checkTemp**********"
        runIn(60,checkTemp)
        //log.trace "******* Done Scheduling ***********"
        
        def tm = thermostat.currentThermostatMode
		def ctThermo = thermostat.currentTemperature
        state.numSensor = 0
        state.totalSensor = 0
		//def ctSensor = sensor.currentTemperature
        def ctSensor = sensorAverage()
        def sp = ctThermo
        
        //log.info "tm:[${tm}]"
        //log.info "ctThermo:[${ctThermo}]"
        //log.info "ctSensor:[${ctSensor}]"
        //log.info "sp:[${sp}]"
        //log.trace "--------------------"
              
    //sp = state.realCoolSetPoint
    
    if (needHeat())
    {
    	sp = state.realHeatSetPoint

		if (tm in ["cool","auto"])
        {
        	thermostat.setThermostatMode('heat')
            log.trace('Setting Thermostat to heating mode')
        }
        
        if (incrHeatSetPoint())
        {
        	state.tempHeatSetPoint = ctThermo + 3
            log.info "Heating Thermostat from '${thermostat.currentHeatingSetpoint}' to '${state.tempHeatSetPoint}' because Senors are at '${ctSensor}'"
        	runIn(5,changeTemp)
        }
        else
        {
        	log.info "Thermostat set to '${thermostat.currentHeatingSetpoint}' and current themostat temp is '${ctThermo}'. Not changing anything for heating conditions."
        }
    }
    else if (needCool())
    {
     	sp = state.realCoolSetPoint

		if (tm in ["heat","emergency heat","auto"])
        {
        	thermostat.setThermostatMode('cool')
            log.trace('Setting Thermostat to Cooling mode')
        }
        
        if (decrCoolSetPoint())
        {
        	state.tempCoolSetPoint = ctThermo - 3
            log.info "Cooling Thermostat from '${thermostat.currentCoolingSetpoint}' to '${state.tempCoolSetPoint}' because sensor is '${ctSensor}'"
        	runIn(5,changeTemp)
        }
        else
        {
        	log.info "Thermostat set to '${thermostat.currentCoolingSetpoint}' and current themostat temp is '${ctThermo}'. Not changing anything for cooling conditions."
        }
    	
    }
    else 
    {
    	if (thermoNotRight()) 
    	{
    		if (tm in ["heat","emergency heat","auto"])
        	{
            	log.info "Changing Thermostat to '${state.realHeatSetPoint}' because Thermostat is '${ctThermo}' and Sensor is '${ctSensor}'"
        		thermostat.setHeatingSetpoint(state.realHeatSetPoint)
                state.tempHeatSetPoint = state.realHeatSetPoint
            }
            else
            {
            	log.info "Changing Thermostat to '${state.realCoolSetPoint}' because Thermostat is '${ctThermo}' and Sensor is '${ctSensor}'"
        		thermostat.setCoolingSetpoint(state.realCoolSetPoint)
                state.tempCoolSetPoint = state.realCoolSetPoint
        	
            }
                
            //state.tempHeatSetPoint = -99
            
        }
    	else if (slowAC())
        {
        	log.info "Changing Thermostat to '${ctThermo}' because Thermostat is '${ctThermo}' and Sensor is '${ctSensor}'"
        	thermostat.setCoolingSetpoint(ctThermo)
                
        }
        else
    	{
    		log.info "Everything is correct"
    	}
    }
    
    /*
    log.trace "*******End Notes*******"
    log.info "thermoHeatingSetPoint: '${thermostat.currentHeatingSetpoint}'"
    log.info "temp-HeatingSetPoint: '${state.tempHeatSetPoint}'"
    log.info "real-HeatingSetPoint: '${state.realHeatSetPoint}'"
    log.info "thermoCoolingSetPoint: '${thermostat.currentCoolingSetpoint}'"
    log.info "currentTemperature: '${thermostat.currentTemperature}'"
    log.info "currentMode: '${thermostat.currentThermostatMode}'"
    log.trace "*******End*******"
    log.trace "---       All Done       ---"
    */
}

private thermoNotRight()
{
	def tm = thermostat.currentThermostatMode
    def result = false
    //log.info "state.slowTemp: [${state.slowTemp}]"
    
    if (state.slowTemp in ["No"] && (((state.realHeatSetPoint != thermostat.currentHeatingSetpoint) && (tm in ["heat","emergency heat"])) || ((state.realCoolSetPoint != thermostat.currentCoolingSetpoint) && (tm in ["cool"]))))
    {
    	result = true
    }
    
    //log.trace "*****************"
    //log.info "Mode: ${thermostat.currentThermostatMode}"
    if (tm in ["heat","emergency heat"]){
    //	log.info "[ RETURN: '${result}' ] -- ${state.realHeatSetPoint} != ${thermostat.currentHeatingSetpoint}"
    }
    else {
    	//log.info "Right Temp:: [RETURN: '${result}'] -- ${state.realCoolSetPoint} != ${thermostat.currentTemperature}"
    }
        
    return result
}

private needHeat()
{
		def tm = thermostat.currentThermostatMode
		state.numSensor = 0
        state.totalSensor = 0
		//def ctSensor = sensor.currentTemperature
        def ctSensor = sensorAverage()
        def sp = state.realHeatSetPoint
        def result=false
        def ctSensorDisplay = (ctSensor.toFloat()/1.0).round(2)
        
        // sensor + threshold less than desired Temp
        if ((ctSensor + tempThreshold) < sp)
        {
        	result = true
            state.slowTemp = "No"
        }
        
        log.info "Need Heat? :: [ RETURN: '${result}' ] -- ${ctSensorDisplay} + ${tempThreshold} ('${ctSensorDisplay + tempThreshold}')< ${sp}"
                
        return result
}

private needCool()
{
		def tm = thermostat.currentThermostatMode
		state.numSensor = 0
        state.totalSensor = 0
		//def ctSensor = sensor.currentTemperature
        def ctSensor = sensorAverage()
        def sp = state.realCoolSetPoint
        def result=false
        def ctSensorDisplay = (ctSensor.toFloat()/1.0).round(2)
        
        // sensor - threshold greater than desired Temp
        if ((ctSensor - tempThreshold) > sp)
        {
        	result = true
            state.slowTemp = "No"
        }
        
        log.info "Need AC? :: [ RETURN: '${result}' ] -- ${ctSensorDisplay} - ${tempThreshold} ('${ctSensorDisplay - tempThreshold}')> ${sp}"
                
        return result
}

private incrHeatSetPoint()
{

	def sp = state.tempHeatSetPoint
	def ctThermo = thermostat.currentTemperature
    def result = false
    
    
		
    if (ctThermo + 2 >= sp)
    {
    	result = true
    }


	log.info "more Heat? :: [ RETURN: '${result}' ] -- ${ctThermo} + 2 (${ctThermo + 2}) >= ${sp}"
    
    return result

}

private decrCoolSetPoint()
{

	def sp = state.tempCoolSetPoint
	def ctThermo = thermostat.currentTemperature
    def result = false
    		
    if (ctThermo - 2 <= sp)
    {
    	result = true
    }

	log.info "more AC? :: [ RETURN: '${result}' ] -- ${ctThermo} - 2 (${ctThermo - 2}) <= ${sp}"
    //log.trace "checking if we need to increase AC"
    
	return result

}

private slowAC()
{
	def sp = thermostat.currentCoolingSetpoint
	def ctThermo = thermostat.currentTemperature
    def result = false
    		
    if (sp < ctThermo - 0.2)
    {
    	result = true
        state.slowTemp = "New"
    }

	log.info "slow AC? :: [ RETURN: '${result}' ] -- (${ctThermo - 0.2}) > ${sp}"
    //log.trace "checking if we need to increase AC"
    
	return result
}

private send(message){
	log.debug("Send Notification Function")
	// check that contact book is enabled and recipients selected
	if (sendPushMessage != "No") {
    	log.debug ( "Sending Push Notification..." ) 
    	sendPush( message )
	}
    if (phoneNumber != "0") {
    	log.debug("Sending text message...")
		sendSms( phoneNumber, message )
	}
}