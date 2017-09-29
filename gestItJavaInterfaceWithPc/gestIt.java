import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import gnu.io.CommPortIdentifier; 
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent; 
import gnu.io.SerialPortEventListener; 
import java.util.Enumeration;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;


import java.awt.Robot;

public class gestIt implements SerialPortEventListener {
	static Robot robot;
	SerialPort serialPort;
        /** The port we're normally going to use. */
	private static final String PORT_NAMES[] = { 
			"/dev/tty.usbserial-A9007UX1", // Mac OS X
                        "/dev/ttyACM0", // Raspberry Pi
			"/dev/ttyUSB0", // Linux
			"COM3", // Windows
	};
	/**
	* A BufferedReader which will be fed by a InputStreamReader 
	* converting the bytes into characters 
	* making the displayed results codepage independent
	*/
	private BufferedReader input;
	/** The output stream to the port */
	private OutputStream output;
	/** Milliseconds to block while waiting for port open */
	private static final int TIME_OUT = 2000;
	/** Default bits per second for COM port. */
	private static final int DATA_RATE = 9600;

	private JFrame f = new JFrame("Serial Connection");
	private JLabel l = new JLabel();
	public void initialize() {
                // the next line is for Raspberry Pi and 
                // gets us into the while loop and was suggested here was suggested http://www.raspberrypi.org/phpBB3/viewtopic.php?f=81&t=32186
            //    System.setProperty("gnu.io.rxtx.SerialPorts", "/dev/ttyACM0");

		
		
		f.setLayout(new GridLayout(1,2));
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    f.setSize(500,600);
		f.setVisible(true);
		
		CommPortIdentifier portId = null;
		Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();

		//First, Find an instance of serial port as set in PORT_NAMES.
		while (portEnum.hasMoreElements()) {
			CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
			for (String portName : PORT_NAMES) {
				if (currPortId.getName().equals(portName)) {
					portId = currPortId;
					break;
				}
			}
		}
		if (portId == null) {
			l.setText("Could not find COM port.");
			System.out.println("Could not find COM port.");
			return;
		}

		try {
			// open serial port, and use class name for the appName.
			serialPort = (SerialPort) portId.open(this.getClass().getName(),
					TIME_OUT);

			// set port parameters
			serialPort.setSerialPortParams(DATA_RATE,
					SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);

			// open the streams
			input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
			output = serialPort.getOutputStream();

			// add event listeners
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
		} catch (Exception e) {
			System.err.println(e.toString());
		}
	}

	/**
	 * This should be called when you stop using the port.
	 * This will prevent port locking on platforms like Linux.
	 */
	public synchronized void close() {
		if (serialPort != null) {
			serialPort.removeEventListener();
			serialPort.close();
		}
	}

	/**
	 * Handle an event on the serial port. Read the data and print it.
	 */
	public synchronized void serialEvent(SerialPortEvent oEvent) {
		if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try {
				robot = new Robot();
				String inputLine=input.readLine();
				System.out.println(inputLine);
				String temp="";
				for(int i = 0;i<inputLine.length() && inputLine.charAt(i) != ' ';++i){
					temp+=inputLine.charAt(i);
				}
				if(temp.equals("Key")){
					char data = inputLine.charAt(4);
					System.out.println("" + data);
					robot.keyPress(KeyEvent.getExtendedKeyCodeForChar(data));
					robot.keyRelease(KeyEvent.getExtendedKeyCodeForChar(data));
				}
				else if(temp.equals("Move")){
					boolean sign;
					double mouseX = MouseInfo.getPointerInfo().getLocation().getX();
			        double mouseY = MouseInfo.getPointerInfo().getLocation().getY();
					for(int i = 0;i<inputLine.length();){
						temp="";
						if(inputLine.charAt(i) == 'x'){
							sign = (inputLine.charAt(i+2) == '+');
							i=i+3;
							while(inputLine.charAt(i) != ' '){
								temp+=inputLine.charAt(i++);
							}
							mouseX = (sign)?mouseX + Integer.parseInt(temp):mouseX - Integer.parseInt(temp);
						}
						else if(inputLine.charAt(i) == 'y'){
							sign = (inputLine.charAt(i+2) == '+');
							i=i+3;
							while(i<inputLine.length()){
								temp+=inputLine.charAt(i++);//arnold is a good biy1234hshwhwhhe
							}
							mouseY = (sign)?mouseY + Integer.parseInt(temp):mouseY - Integer.parseInt(temp);
						}
						++i;
					}
					robot.mouseMove((int)mouseX, (int)mouseY);
				}
				else if(temp.equals("LeftPress")){
					robot.mousePress(InputEvent.BUTTON1_MASK);
				}
				else if(temp.equals("LeftRelease")){
					robot.mouseRelease(InputEvent.BUTTON1_MASK);
				}
				else if(temp.equals("RightPress")){
					robot.mousePress(InputEvent.BUTTON3_MASK);
				}
				else if(temp.equals("RightRelease")){
					robot.mouseRelease(InputEvent.BUTTON3_MASK);
				}
			} catch (Exception e) {
				System.err.println(e.toString());//absjsjei9273bwnsbsvjbnsnsb
			}
		}
		// Ignore all the other eventTypes, but you should consider the other ones.
	}

	public static void main(String[] args) throws Exception {
		gestIt main = new gestIt();
		main.initialize();
		Thread t=new Thread() {
			public void run() {
				//the following line will keep this app alive for 1000 seconds,
				//waiting for events to occur and responding to them (printing incoming messages to console).
				try {Thread.sleep(1000000);} catch (InterruptedException ie) {}
			}
		};
		t.start();
		System.out.println("Started");
	}
}
