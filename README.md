# Strolch as a software PLC
A soft real time PLC written in Strolch running on Strolch

Checkout the documentation at https://strolch.li/plc.html

## Features
Strolch PLC supports the following features:
* Notification model
* Raspberry Pi GPIO Input and Output addresses
* I2C Input and Output addresses over PCF8574 chips
* DataLogic Scanner connection
* Virtual addresses
* WebUI to observer and manipulate the addresses
* WebSocket connection to Strolch Agent for notifying of changes
* Simple two key addressing of hardware addresses to store semantics, e.g. `Convey01 - MotorOn`, instead of `i2cInput.dev01.0.0`

## PlcAddress
PlcAddresses store the value of a hardware address: 

      <Resource Id="A_Conveyor01-Occupied" Name="Conveyor01 - Occupied" Type="PlcAddress">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
          <Parameter Id="description" Name="Description" Type="String" Index="5" Value="Conveyor 1"/>
          <Parameter Id="address" Name="HW Address" Type="String" Interpretation="PlcConnection" Index="10" Value="i2cInput.dev01.0.0"/>
          <Parameter Id="resource" Name="Resource ID for PlcAddress" Type="String" Index="20" Value="Conveyor01"/>
          <Parameter Id="action" Name="Action ID for PlcAddress" Type="String" Index="30" Value="Occupied"/>
          <Parameter Id="index" Name="Index" Type="Integer" Index="40" Value="10"/>
          <Parameter Id="value" Name="Value" Type="Boolean" Index="100" Value="false"/>
        </ParameterBag>
      </Resource>

The two parameters `resource` and `action` are the local keys for the address, 
while `address` is used to find the actual connection and the address on that 
connection.

## PlcTelegram
PlcTelegrams are used to store keys with a default value. For example it is 
easier to understand `Conveyor01 - MotorOn` instead of `Conveyor01 - Motor` with the value true or false.

      <Resource Id="T_Conveyor01-MotorOn" Name="Conveyor01 - MotorOn" Type="PlcTelegram">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
          <Parameter Id="description" Name="Description" Type="String" Index="5" Value="Conveyor 1"/>
          <Parameter Id="address" Name="HW Address" Type="String" Interpretation="PlcConnection" Index="10" Value="i2cOutput.dev01.0.0"/>
          <Parameter Id="resource" Name="Resource ID for PlcAddress" Type="String" Index="20" Value="Conveyor01"/>
          <Parameter Id="action" Name="Action ID for PlcAddress" Type="String" Index="30" Value="MotorOn"/>
          <Parameter Id="index" Name="Index" Type="Integer" Index="40" Value="10"/>
          <Parameter Id="value" Name="Value" Type="Boolean" Index="100" Value="true"/>
        </ParameterBag>
      </Resource>

## PlcLogicalDevice
Multiple PlcAddresses can be grouped together into a PlcLogicalDevice for 
visualization on the UI. In logistics a single conveyor might have multiple 
sensors and actors, e.g. the conveyor's motor, a light barrier and a scanner, 
grouping them together makes it easier for debugging.

      <Resource Id="D_MaterialFlow" Name="MaterialFlow" Type="PlcLogicalDevice">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
          <Parameter Id="description" Name="Description" Type="String" Index="5" Value="Material Flow"/>
          <Parameter Id="group" Name="Group" Type="String" Index="20" Value="01 Material Flow"/>
          <Parameter Id="index" Name="Index" Type="Integer" Index="30" Value="10"/>
        </ParameterBag>
        <ParameterBag Id="relations" Name="Relations" Type="Relations">
          <Parameter Id="addresses" Name="Addresses" Type="StringList" Interpretation="Resource-Ref" Uom="PlcAddress" Index="10" Value="A_Conveyor01-Occupied, A_Conveyor02-Occupied, A_Conveyor03-Occupied, A_Conveyor04-Occupied, A_Conveyor01-MotorOn, A_Conveyor02-MotorOn, A_Conveyor03-MotorOn, A_Conveyor04-MotorOn, A_Conveyor03-Barcode, A_Conveyor03-On"/>
          <Parameter Id="telegrams" Name="Telegrams" Type="StringList" Interpretation="Resource-Ref" Uom="PlcTelegram" Index="20" Value="T_Conveyor01-MotorOn, T_Conveyor01-MotorOff, T_Conveyor02-MotorOn, T_Conveyor02-MotorOff, T_Conveyor03-MotorOn, T_Conveyor03-MotorOff, T_Conveyor04-MotorOn, T_Conveyor04-MotorOff, T_Conveyor03-On, T_Conveyor03-Off"/>
        </ParameterBag>
      </Resource>

## PlcConnections
PlcConnections are used to model the actual hardware connections and define with
which class the connection is to be established. The following shows some of the implementations:

    <!--
        Raspberry GPIO BCM Address connection
    -->
    <Resource Id="raspiBcmGpioOutput" Name="Raspi BCM GPIO Output" Type="PlcConnection">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
            <Parameter Id="className" Name="Connection Class" Type="String" Value="li.strolch.plc.core.hw.gpio.RaspiBcmGpioOutputConnection"/>
            <Parameter Id="state" Name="Connection State" Type="String" Interpretation="Enumeration" Uom="ConnectionState" Value="Disconnected"/>
            <Parameter Id="stateMsg" Name="Connection State Msg" Type="String" Interpretation="Enumeration" Uom="ConnectionState"
                       Value=""/>
            <Parameter Id="inverted" Name="Inverted" Type="Boolean" Value="false"/>
            <Parameter Id="bcmOutputPins" Name="BCM Output Pins" Type="IntegerList" Value="27"/>
        </ParameterBag>
    </Resource>
    <Resource Id="raspiBcmGpioInput" Name="Raspi BCM GPIO Input" Type="PlcConnection">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
            <Parameter Id="className" Name="Connection Class" Type="String" Value="li.strolch.plc.core.hw.gpio.RaspiBcmGpioInputConnection"/>
            <Parameter Id="state" Name="Connection State" Type="String" Interpretation="Enumeration" Uom="ConnectionState" Value="Disconnected"/>
            <Parameter Id="stateMsg" Name="Connection State Msg" Type="String" Interpretation="Enumeration" Uom="ConnectionState"
                       Value=""/>
            <Parameter Id="inverted" Name="Inverted" Type="Boolean" Value="true"/>
            <Parameter Id="bcmInputPins" Name="BCM Input Pins" Type="IntegerList" Value="4"/>
        </ParameterBag>
    </Resource>

    <!--
        I2C input connections
    -->
    <Resource Id="i2cInput.dev01" Name="PCF8574 Input 0x38" Type="PlcConnection">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
            <Parameter Id="className" Name="Connection Class" Type="String" Value="li.strolch.plc.core.hw.i2c.PCF8574InputConnection"/>
            <Parameter Id="state" Name="Connection State" Type="String" Interpretation="Enumeration" Uom="ConnectionState" Value="Disconnected"/>
            <Parameter Id="stateMsg" Name="Connection State Msg" Type="String" Interpretation="Enumeration" Uom="ConnectionState"
                       Value=""/>
            <Parameter Id="i2cBus" Name="I2C Bus" Type="Integer" Value="1"/>
            <Parameter Id="addresses" Name="Addresses" Type="IntegerList" Value="0x38"/>
            <Parameter Id="verbose" Name="Verbose" Type="Boolean" Value="true"/>
            <Parameter Id="inverted" Name="Inverted" Type="Boolean" Value="true"/>
            <Parameter Id="interruptPinPullResistance" Name="Raspi Interrupt PinPullResistance" Type="String" Value="PULL_DOWN"/>
            <Parameter Id="interruptChangeState" Name="Raspi Interrupt Change State" Type="String" Value="HIGH"/>
            <Parameter Id="interruptBcmPinAddress" Name="Raspi BCM Interrupt Pin" Type="Integer" Value="17"/>
        </ParameterBag>
    </Resource>

    <!--
        I2C output connections
    -->
    <Resource Id="i2cOutput.dev01" Name="PCF8574 Output 0x21" Type="PlcConnection">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
            <Parameter Id="className" Name="Connection Class" Type="String" Value="li.strolch.plc.core.hw.i2c.PCF8574OutputConnection"/>
            <Parameter Id="state" Name="Connection State" Type="String" Interpretation="Enumeration" Uom="ConnectionState" Value="Disconnected"/>
            <Parameter Id="stateMsg" Name="Connection State Msg" Type="String" Interpretation="Enumeration" Uom="ConnectionState"
                       Value=""/>
            <Parameter Id="i2cBus" Name="I2C Bus" Type="Integer" Value="1"/>
            <Parameter Id="addresses" Name="Addresses" Type="IntegerList" Value="0x21"/>
            <Parameter Id="inverted" Name="Inverted" Type="Boolean" Value="false"/>
        </ParameterBag>
    </Resource>

    <!--
        Barcode reader connection, currently place holder with RandomString
    -->
    <Resource Id="dataLogicScanner" Name="DataLogic Scanner Connection" Type="PlcConnection">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
            <Parameter Id="className" Name="Connection Class" Type="String" Value="li.strolch.plc.core.hw.connections.DataLogicScannerConnection"/>
            <Parameter Id="address" Name="Scanner IP Address" Type="String" Value="192.168.1.249:51236"/>
            <Parameter Id="readTimeout" Name="Read Timeout (s)" Type="Integer" Value="60"/>
            <Parameter Id="state" Name="Connection State" Type="String" Interpretation="Enumeration" Uom="ConnectionState" Value="Disconnected"/>
            <Parameter Id="stateMsg" Name="Connection State Msg" Type="String" Interpretation="Enumeration" Uom="ConnectionState"
                       Value=""/>
        </ParameterBag>
    </Resource>

## Virtual Addresses
In some cases, especially in conjunction with a Strolch Agent as the main 
server, it is necessary to also have virtual addresses, with which to perform
notifications. The following shows examples:

    <Resource Id="addrPlcConnected" Name="PLC - Connected" Type="PlcAddress">
        <ParameterBag Id="parameters" Name="Parameters" Type="Parameters">
            <Parameter Id="address" Name="HW Address" Type="String" Interpretation="PlcConnection" Value="virtualString.plcServerConnected"/>
            <Parameter Id="resource" Name="Resource ID for PlcAddress" Type="String" Value="PLC"/>
            <Parameter Id="action" Name="Action ID for PlcAddress" Type="String" Value="ServerConnected"/>
            <Parameter Id="valueType" Name="Value Type" Type="String" Interpretation="Interpretation" Uom="PlcValueType" Value="String"/>
            <Parameter Id="value" Name="Value" Type="String" Value="false"/>
            <Parameter Id="index" Name="Index" Type="Integer" Value="5"/>
        </ParameterBag>
    </Resource>
