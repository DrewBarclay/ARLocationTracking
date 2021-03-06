//Created by Drew Barclay
//Code uses thotro's dwm1000-arduino project
//Protocol: one byte for ID, then five bytes for the timestamp of the sending of the message, then a list of device specific stuff. 
//For each device, append one byte for the ID of the device, one byte for the shared counter (used to detect lost transmissions), five bytes for timestamp of last received message from them, and four bytes for last calculated range

#include <SPI.h>
#include <DW1000.h>

class Device  {
public:
  byte id;
  byte transmissionCount;
  DW1000Time timeDevicePrevSent;
  DW1000Time timePrevReceived;
  DW1000Time timeSent;
  DW1000Time timeDeviceReceived;
  DW1000Time timeDeviceSent;
  DW1000Time timeReceived;
  float lastComputedRange;
  bool hasReplied;
  
  Device() : lastComputedRange(0.0f), id(0), hasReplied(false) {}
  
  void computeRange();
  
  float getLastComputedRange() {
    return this->lastComputedRange;
  }
};

// CONSTANTS AND DATA START
//number of devices that can form a network at once
#define NUM_DEVICES 6
Device devices[NUM_DEVICES];
int curNumDevices;

// connection pins
const uint8_t PIN_RST = 9; // reset pin
const uint8_t PIN_IRQ = 2; // irq pin
const uint8_t PIN_SS = SS; // spi select pin

// data buffer
#define LEN_DATA 256
byte data[LEN_DATA];

//id for this device
const byte OUR_ID = 5;

long lastTransmission; //from millis()

// delay time before sending a message, should be at least 3ms (3000us)
const unsigned int DELAY_TIME_US = 2048 + 1000 + NUM_DEVICES*83 + 200; //should be equal to preamble symbols (each take ~1us to transmit) + 1000 (base time to communicate and start transmitting and calculating a delay timestamp) + 4.5*bytes of data to send. add a little fudge room too. (experimentally found.) in microseconds.

volatile bool received; //Set when we are interrupted because we have received a transmission
// CONSTANTS AND DATA END

void Device::computeRange() {    
  // only call this when timestamps are correct, otherwise strangeness may result
  // asymmetric two-way ranging (more computationly intense, less error prone)
  DW1000Time round1 = (timeDeviceReceived - timeDevicePrevSent).wrap();
  DW1000Time reply1 = (timeSent - timePrevReceived).wrap();
  DW1000Time round2 = (timeReceived - timeSent).wrap();
  DW1000Time reply2 = (timeDeviceSent - timeDeviceReceived).wrap();
    
  DW1000Time tof = (round1 * round2 - reply1 * reply2) / (round1 + round2 + reply1 + reply2);
  this->lastComputedRange = tof.getAsMeters();
}

void setup() {
    received = false;  
    curNumDevices = 0;
    lastTransmission = millis();

    Serial.begin(115200);
    delay(1000);
    // initialize the driver
    DW1000.begin(PIN_IRQ, PIN_RST);
    DW1000.select(PIN_SS);
    Serial.println(F("DW1000 initialized ..."));
    // general configuration
    DW1000.newConfiguration();
    DW1000.setDefaults();
    DW1000.setDeviceAddress(OUR_ID);
    DW1000.setNetworkId(10);
    DW1000.enableMode(DW1000.MODE_LONGDATA_RANGE_ACCURACY);
    DW1000.commitConfiguration();
    Serial.println(F("Committed configuration ..."));

    // attach callback for (successfully) sent and received messages
    DW1000.attachSentHandler(handleSent);
    DW1000.attachReceivedHandler(handleReceived);
    DW1000.attachErrorHandler(handleError);
    DW1000.attachReceiveFailedHandler(handleReceiveFailed);
    
    receiver(); //start receiving
}

void handleError() {
  Serial.println("Error!"); 
}

void handleReceiveFailed() {
  Serial.println("Receive failed!"); 
}

void handleSent() {
  
}

void handleReceived() {
  received = true;
}

void receiver() {
    DW1000.newReceive();
    DW1000.setDefaults();
    // so we don't need to restart the receiver manually
    DW1000.receivePermanently(true);
    DW1000.startReceive();
}

void parseReceived() {
  unsigned int len = DW1000.getDataLength();
  DW1000Time timeReceived;
  DW1000.getData(data, len);
  DW1000.getReceiveTimestamp(timeReceived);
  
  if (len < 6) {
     Serial.println("Received message with length <6, error."); 
     return;
  }
  
  //Parse data
  //First byte, ID
  byte fromID = data[0];
  //Second byte, 5 byte timestamp when the transmission was sent (their clock)
  DW1000Time timeDeviceSent(data + 1);
  
  //If this device is not in our list, add it now.
  int idx = -1;
  for (int i = 0; i < curNumDevices; i++) {
   if (devices[i].id == fromID) {
     idx = i;
   }
  }
  if (idx == -1) { //If we haven't seen this device before...
    Serial.print("New device found. ID: "); Serial.println(fromID); 
    if (curNumDevices == NUM_DEVICES) {
      Serial.println("Max # of devices exceeded. Returning early from receive.");
      return; 
    }
    devices[curNumDevices].id = fromID;
    devices[curNumDevices].timeDeviceSent = timeDeviceSent;
    devices[curNumDevices].transmissionCount = 1;
    idx = curNumDevices;
    curNumDevices++;
  } 
  
  devices[idx].hasReplied = true;
  
  //Now, a list of device-specific stuff
  for (int i = 6; i < len;) {
    //First byte, device ID
    byte deviceID = data[i];
    i++; 
    
    //Second byte, transmission counter
    byte transmissionCount = data[i];
    i++;
    
    //Next five bytes are the timestamp of when the device received our last transmit
    DW1000Time timeDeviceReceived(data + i);
    i += 5; 
    
    //Next four bytes are a float representing the last calculated range
    float range;
    memcpy(&range, data + i, 4);
    i+= 4;
    
    //Is this our device? If so, we have an update to do and a range to report!
    if (deviceID == OUR_ID) {
      //Mark down the two timestamps it included, as well as the time we received it
      devices[idx].timeDeviceReceived = timeDeviceReceived;
      devices[idx].timeDeviceSent = timeDeviceSent;
      devices[idx].timeReceived = timeReceived;
      
      Serial.print("Transmission received from tag "); Serial.print(devices[idx].id); Serial.print(" with transmission count "); Serial.println(devices[idx].transmissionCount);
      
      //If everything looks good, we can compute the range! 
      if (transmissionCount == 0) {
        //Error sending, reset everything.
        devices[idx].transmissionCount = 1;
      } else if (devices[idx].transmissionCount == transmissionCount) {
        if (devices[idx].transmissionCount > 1) {
          devices[idx].computeRange();
          Serial.print("!range "); Serial.print(OUR_ID); Serial.print(" "); Serial.print(devices[idx].id); Serial.print(" "); Serial.println(devices[idx].getLastComputedRange());
        }
        devices[idx].transmissionCount++;
      } else {
        //Error in transmission!
        devices[idx].transmissionCount = 0; 
        Serial.println("Transmission count does not match.");
      }
      
      devices[idx].timeDevicePrevSent = timeDeviceSent;
      devices[idx].timePrevReceived = timeReceived;
    } 
    
    Serial.print("!range "); Serial.print(fromID); Serial.print(" "); Serial.print(deviceID); Serial.print(" "); Serial.println(range);
  }
  //TODO if our device was not in the list and we think it should have been, raise an error/reset the ranging stuff. Being robust is important!
}

void doTransmit() {  
  data[0] = OUR_ID;
  
  //Normally we would set the timestamp for when we send here (starting at the second byte), but we'll do that later because this can take a while to calculate and we want the delay before sending to be short
  
  int curByte = 6;
  for (int i = 0; i < curNumDevices; i++) {
    data[curByte] = devices[i].id;
    curByte++;
    data[curByte] = devices[i].transmissionCount;
    curByte++;
    devices[i].timeReceived.getTimestamp(data + curByte); //last timestamp will contain the time we last received a transmission
    curByte += 5;
    float range = devices[i].getLastComputedRange();
    memcpy(data + curByte, &range, 4); //floats are 4 bytes
    curByte += 4;
    
    if (devices[i].hasReplied) {
      devices[i].transmissionCount++; //Increment for every transmission, the other device will also increment and we can check to see if they're the same to ensure the transmission was received
      devices[i].hasReplied = false; //Set to not for this round
    }
  }
  
  //Do the actual transmission
  DW1000.newTransmit();
  DW1000.setDefaults();
  
  //Now we figure out the time to send this message!
  DW1000Time deltaTime = DW1000Time(DELAY_TIME_US, DW1000Time::MICROSECONDS);
  DW1000Time timeSent = DW1000.setDelay(deltaTime);
  timeSent.getTimestamp(data + 1); //set second byte (5 bytes will be written) to the timestamp
  
  DW1000.setData(data, curByte);
  DW1000.startTransmit();
  
  for (int i = 0; i < NUM_DEVICES; i++) {
    devices[i].timeSent = timeSent;
  }
}

void loop() {
  unsigned long curMillis = millis();
  
  if (received) {
    received = false;
    parseReceived();
  }
  
  if (curMillis - lastTransmission > 300) {
    doTransmit();
    lastTransmission = curMillis;
  }
}

