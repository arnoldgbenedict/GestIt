
#include "mbed.h" //import mbed.h

Serial serial(USBTX, USBRX);    //for serial communication with the pc
Serial t(D1,D0);          // for serial communication with the bluetooth

int usedPin[] = {0, 0, 0, 0, 0, 0};         //store boolean for used pins
int pinButtonState[] = {0,0,0,0,0,0};           //store boolean for button current state

//pins related to communication between master module and the device controllers
DigitalOut t1(D8, 0);
DigitalOut t2(D9, 0);
DigitalOut t3(D2, 0);
DigitalOut t4(D3, 0);
DigitalOut t5(D4, 0);
DigitalOut t6(D5, 0);

DigitalIn r1(A0);
DigitalIn r2(A1);
DigitalIn r3(A2);
DigitalIn r4(A3);
DigitalIn r5(A4);
DigitalIn r6(A5);

DigitalOut transPin[] = {t1, t2, t3, t4, t5, t6}; // order of transmitter pins 
DigitalIn recPin[] = {r1, r2, r3, r4, r5, r6};  // order of reciver pins

int main(void)
{   
    serial.baud(115200);
    t.baud(9600);
    
     int BluetoothData;
    int appType[10];
    int pinIndex[10];
    int countApp=0;
    while(1){
        //udpating variables from the feedback signals, also gives signals to the App
        for(int i = 0;i<countApp;++i){
              if(pinButtonState[i] != recPin[i].read() && usedPin[pinIndex[i]] == 1){
                  t.printf("%d", i);
                  pinButtonState[i] = recPin[pinIndex[i]].read();
              if(pinButtonState[i] == 1)
                transPin[pinIndex[i]] = 1;
              else
                transPin[pinIndex[i]] = 0;
              wait(0.05);
            }
          }
        //Listener for the App
        //The following are the control signals used for different functions 
        //0:key, 1:-x 2:+x 3:-y 4:+y 5,6:rightClick 7,8:LeftClick
        //9:initializeNew(starting index 0)
        //10:typeLight 11:fanType 12:speakerType 13:ProjectorTypr 14:doorType
        //15:clearAllData
        //16:getAppIndexTango
   
        if (t.readable()){
          BluetoothData = t.getc();
          if(BluetoothData == 0)
          {
            serial.printf("Key ");
             while (!t.readable());
              int Data = t.getc();
             serial.printf("%c", (char)Data);
          }
          else if(BluetoothData == 1)
          {
            serial.printf("Move x -");
             while (!t.readable());
              int Data = t.getc();
              serial.printf("%d", Data);
          }
          else if(BluetoothData == 2)
          {
            serial.printf("Move x +");
             while (!t.readable());
              int Data = t.getc();
              serial.printf("%d", Data);
          }
          else if(BluetoothData == 3)
          {
            serial.printf(" y -");
             while (!t.readable());
              int Data = t.getc();
              serial.printf("%d\n", Data);
          }
          else if(BluetoothData == 4)
          {
            serial.printf(" y +");
             while (!t.readable());
              int Data = t.getc();
             serial.printf("%d\n", Data);
          }
          else if(BluetoothData == 5)
          {
            serial.printf("LeftPress\n");
          }
          else if(BluetoothData == 6)
          {
            serial.printf("LeftRelease\n");
          }
          else if(BluetoothData == 7)
          {
            serial.printf("RightPress\n");
          }
          else if(BluetoothData == 8)
          {
            serial.printf("RightRelease\n");
          }
          else if(BluetoothData == 9){
            while (!t.readable());
            int Data = t.getc();
            appType[countApp] = Data;
            int pin;
            while(1){
              pin = -1;
              for(int j = 0;j < 6;++j){
                if(recPin[j].read() == 1 && usedPin[j] == 0){
                  pin = j;
                  break;
                }
              }
              if(pin != -1)break;
            }
            transPin[pin] = 1;
            usedPin[pin] = 1;
            pinButtonState[pin] = 0;
            pinIndex[countApp++] = pin;
            t.putc(1);
            wait(1.1);
          }
          else if(BluetoothData == 16){
            while (!t.readable());
            int Data = t.getc();
            int op, mode;
            switch(appType[Data]){
              case 0: while (!t.readable());
                      mode = t.getc();
                      transPin[pinIndex[Data]] = mode;
                      pinButtonState[Data] = mode;
                      break;
              case 1: while (!t.readable());
                      op = t.getc();
                      while (!t.readable());
                      mode = t.getc();
                      if(op == 0){
                        transPin[pinIndex[Data]] = mode;
                        pinButtonState[Data] = mode;
                      }
                      else{  
                      }
                      break;
               case 4: while (!t.readable());
                      mode = t.getc();
                      transPin[pinIndex[Data]] = mode;
                      pinButtonState[Data] = mode;
                      break;
                      
            }
            wait(0.05);
          }
        }
            
        }
}
