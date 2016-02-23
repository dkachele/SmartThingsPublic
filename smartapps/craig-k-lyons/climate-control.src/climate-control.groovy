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
	subscribe(thermostat, "temperature",     heatingSetpointHandler)
	subscribe(thermostat, "thermostatMode",  heatingSetpointHandler)
    subscribe(thermostat, "heatingSetpoint", heatingSetpointHandler)
    subscribe(thermostat, "coolingSetpoint", heatingSetpointHandler)
    
    state.realHeatSetPoint = thermostat.currentHeatingSetpoint
    state.tempHeatSetPoint = thermostat.currentHeatingSetpoint + 2
    state.realCoolSetPoint = thermostat.currentCoolingSetpoint
    state.tempCoolSetPoint = thermostat.currentCoolingSetpoint - 2
    
    runIn(30,checkTemp)
}

def heatingSetpointHandler(evt)
{
	log.trace "**************"
    log.info "thermoHeatingSetPoint: '${thermostat.currentHeatingSetpoint}'"
    log.info "temp-HeatingSetPoint: '${state.tempHeatSetPoint}'"
    log.info "real-HeatingSetPoint: '${state.realHeatSetPoint}'"
    log.info "thermoCoolingSetPoint: '${thermostat.currentCoolingSetpoint}'"
    log.info "currentTemperature: '${thermostat.currentTemperature}'"
    log.info "currentMode: '${thermostat.currentThermostatMode}'"
    log.trace "----------------"
    
    
    if (state.tempHeatSetPoint <= thermostat.currentHeatingSetpoint - 0.5 || state.tempHeatSetPoint >= thermostat.currentHeatingSetpoint + 0.5)
    {
    	log.info "IMPORTANT: Setting realHeatSetPoint: '${thermostat.currentHeatingSetpoint}'"
        state.realHeatSetPoint = thermostat.currentHeatingSetpoint
        state.tempHeatSetPoint = state.realHeatSetPoint
    }
    if (state.tempCoolSetPoint != thermostat.currentCoolingSetpoint)
    {
    	log.info "IMPORTANT: Setting realCoolSetPoint: '${thermostat.currentCoolingSetpoint}'"
        state.realCoolSetPoint = thermostat.currentCoolingSetpoint
        state.tempCoolSetPoint = state.realCoolSetPoint
    }
    
    log.trace "**** heatingSetPoint function *****"
    
    checkTemp()
    
}

def sensorHandler(evt)
{
	checkTemp()
}

private sensorAverage ()
{
	sensor.each {
		//log.info "sensor.currentTemperature: ${sensor.currentTemperature}"
        state.totalSensor = state.totalSensor + it.currentTemperature
        state.numSensor = state.numSensor + 1
        //log.info "state.numSensor: ${state.numSensor}"
        //log.info "state.totalSensor: ${state.totalSensor}"
	}
    
    def temp = state.totalSensor / state.numSensor
    
    //log.info "Avg Temp: ${temp}"
    //log.trace "Determining Sensor Average Temperature"
    
    return temp
}

def checkTemp()
{
	   	def tm = thermostat.currentThermostatMode
		def ctThermo = thermostat.currentTemperature
        state.numSensor = 0
        state.totalSensor = 0
		//def ctSensor = sensor.currentTemperature
        def ctSensor = sensorAverage()
        def sp = ctThermo
        
        //log.trace "********************"
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
        	state.tempHeatSetPoint = ctThermo + 2
            log.info "Heating Thermostat from '${sp}' to '${state.tempHeatSetPoint}' because Senors are at '${ctSensor}'"
            thermostat.setHeatingSetpoint(state.tempHeatSetPoint)
        }
        else
        {
        	log.info "Thermostat set to '${sp}' and current themostat temp is '${ctThermo}'. Not changing anything for heating conditions."
        }
    }
    else if (needCool())
    {
     	sp = state.realHeatSetPoint

		if (tm in ["heat","emergency heat","auto"])
        {
        	thermostat.setThermostatMode('cool')
            log.trace('Setting Thermostat to Cooling mode')
        }
        
        if (decrCoolSetPoint())
        {
        	state.tempCoolSetPoint = ctThermo - 2
            log.info "Cooling Thermostat from '${sp}' to '${state.tempCoolSetPoint}' because sensor is '${ctSensor}'"
            thermostat.cool()
            thermostat.setHeatingSetpoint(state.tempHeatSetPoint)
        }
        else
        {
        	log.info "Thermostat set to '${sp}' and current themostat temp is '${ctThermo}'. Not changing anything for cooling conditions."
        }
    	
    }
    else 
    {
    	if (thermoNotRight()) 
    	{
    		log.info "Changing Thermostat to '${sp}' because Thermostat is '${ctThermo}' and Sensor is '${ctSensor}'"
        	thermostat.setHeatingSetpoint(sp)
        	//state.tempHeatSetPoint = -99
    	}
    	else
    	{
    		log.info "Everything is correct"
    	}
    }
    
    //log.trace "*****check temp*******"
    //log.info " "
    log.trace "---       All Done       ---"
    
}

private thermoNotRight()
{
	def tm = thermostat.currentThermostatMode
    def result = false
    
    if (((state.realHeatSetPoint != thermostat.currentHeatingSetpoint) && (tm in ["heat","emergency heat"])) || ((state.realCoolSetPoint != thermostat.currentCoolingSetpoint) && (tm in ["cool"])))
    {
    	result = true
    }
    
    //log.trace "*****************"
    //log.info "Mode: ${thermostat.currentThermostatMode}"
    if (tm in ["heat","emergency heat"]){
    //	log.info "[ RETURN: '${result}' ] -- ${state.realHeatSetPoint} != ${thermostat.currentHeatingSetpoint}"
    }
    else {
    //	log.info "[ RETURN: '${result}' ] -- ${state.realCoolSetPoint} != ${thermostat.currentCoolingSetpoint}"
    }
    //log.trace "********  thermoNotRight *******"
        
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
        def ctSensorDisplay = (ctSensor.toFloat()/1).round(2)
        
        // sensor + threshold less than desired Temp
        if ((ctSensor + tempThreshold) < sp)
        {
        	result = true
        }
        
        log.info "[ RETURN: '${result}' ] -- ${ctSensorDisplay} + ${tempThreshold} ('${ctSensorDisplay + tempThreshold}')< ${sp}"
        log.trace "checking if we Need Heat"
                
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
        def ctSensorDisplay = (ctSensor.toFloat()/1).round(2)
        
        // sensor - threshold greater than desired Temp
        if ((ctSensor - tempThreshold) > sp)
        {
        	result = true
        }
        
        log.info "[ RETURN: '${result}' ] -- ${ctSensorDisplay} - ${tempThreshold} ('${ctSensorDisplay - tempThreshold}')> ${sp}"
        log.trace "checking if we need Cooling"
                
        return result
}

private incrHeatSetPoint()
{

	def sp = state.realHeatSetPoint
	def ctThermo = thermostat.currentTemperature
    def result = false
    
    
		
    if (ctThermo >= sp)
    {
    	result = true
    }


	log.info "[ RETURN: '${result}' ] -- ${ctThermo} >= ${sp}"
    log.trace "checking if we Need to increase heat"
    
    return result

}

private decrCoolSetPoint()
{

	def sp = state.realCoolSetPoint
	def ctThermo = thermostat.currentTemperature
    def result = false
    
    
		
    if (ctThermo <= sp)
    {
    	result = true
    }


	log.info "[ RETURN: '${result}' ] -- ${ctThermo} <= ${sp}"
    log.trace "checking if we need to increase AC"
    
    
    
	return result

}