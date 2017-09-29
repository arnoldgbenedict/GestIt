/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.pointtopoint;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.util.Log;
import android.view.Display;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.view.SurfaceView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import static java.lang.Math.abs;

public class PointToPointActivity extends Activity implements SeekBar.OnSeekBarChangeListener, Serializable{


    private class MeasuredPoint {
        public double mTimestamp;
        public float[] mDepthTPoint;

        public MeasuredPoint(double timestamp, float[] depthTPoint) {
            mTimestamp = timestamp;
            mDepthTPoint = depthTPoint;
        }
    }


    private static final String TAG = PointToPointActivity.class.getSimpleName();

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    // The interval at which we'll update our UI debug text in milliseconds.
    // This is the rate at which we query for distance data.
    private static final int UPDATE_UI_INTERVAL_MS = 100;

    private static final int INVALID_TEXTURE_ID = 0;

    private SurfaceView mSurfaceView;
    private PointToPointRenderer mRenderer;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private double mCameraPoseTimestamp = 0;
    private TextView mDistanceTextview;
    private CheckBox mBilateralBox;
    private volatile TangoImageBuffer mCurrentImageBuffer;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private boolean mPointSwitch = true;

    // Two measured points in Depth Camera space.
    private MeasuredPoint mMeasuredPoints;

    // Two measured points in OpenGL space, we used a stack to hold the data is because rajawalli
    // LineRenderer expects a stack of points to be passed in. This is render ready data format from
    // Rajawalli's perspective.
    private Stack<Vector3> mMeasurePoitnsInOpenGLSpace = new Stack<Vector3>();
    private float mMeasuredDistance = 0.0f;

    // Handles the debug text UI update loop.
    private Handler mHandler = new Handler();

    private int mDisplayRotation = 0;


    private ArrayList<Vector3> allAppl;
    private ArrayList<String> appName;
    private ArrayList<Integer> appType;
    private float cpx = 0, cpy = 0, cpz = 0;
    TextView appIDtextView;
    Button b, addItemButton, editAppName, settings, captureBackground, captureBackgroundShow;
    SeekBar slider;

    int currentAppID = -1;
    int setVis;
    boolean calibratedPoint = false;
    float[] p;
    File path;


    Button rc,bk,lc,bm;
    ImageView tp;

    SeekBar seekBar;

    String address = null;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private ProgressDialog progress;
    private boolean isBtConnected = false;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent newint = getIntent();
        address = newint.getStringExtra(blueMain.EXTRA_ADDRESS); //receive the address of the bluetooth device

        //view of the ledControl
        setContentView(R.layout.activity_main);

        mSurfaceView = (SurfaceView) findViewById(R.id.ar_view);
        mRenderer = new PointToPointRenderer(this);
        mSurfaceView.setSurfaceRenderer(mRenderer);
        //mSurfaceView.setOnTouchListener(this);
        //mSurfaceView.setOnLongClickListener(this);

        mPointCloudManager = new TangoPointCloudManager();
        mDistanceTextview = (TextView) findViewById(R.id.distance_textview);
        mBilateralBox = (CheckBox) findViewById(R.id.check_box);
        mMeasuredPoints = null;

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);

            allAppl = new ArrayList<Vector3>();
            appName = new ArrayList<String>();
            appType = new ArrayList<Integer>();
            appState = new ArrayList<stateList>();
            b = (Button) findViewById(R.id.buttonON_OFF);
            addItemButton = (Button) findViewById(R.id.addItemButton);
            editAppName = (Button) findViewById(R.id.editAppName);
            appIDtextView = (TextView) findViewById(R.id.appName);
            settings = (Button)findViewById(R.id.settings);
            captureBackground = (Button) findViewById(R.id.captureBackground);
            captureBackgroundShow = (Button) findViewById(R.id.captureBackgroundShow);
            slider = (SeekBar) findViewById(R.id.seekBar);
            slider.setOnSeekBarChangeListener(this);
            slider.setProgress(0);
            slider.setVisibility(View.GONE);
           // slider.OnSeekBarChangeListener(null);
            editAppName.setVisibility(View.GONE);
            appIDtextView.setVisibility(View.GONE);
            b.setVisibility(View.GONE);

            final Dialog dialogBox = new Dialog(PointToPointActivity.this);
            dialogBox.setTitle("Calibrate Point before proceeding");


            Button ok = new Button(PointToPointActivity.this);
            ok.setText("OK");
            ok.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    dialogBox.dismiss();
                }
            });
            TextView ct = new TextView(PointToPointActivity.this);
            ct.setText("->Click OK\n->Point the center to the Room ID\n->Press CALIBRATE");
            ct.setTextSize(18);
            LinearLayout layout = new LinearLayout(PointToPointActivity.this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layout.setLayoutParams(params);
            layout.setOrientation(LinearLayout.VERTICAL);

            layout.addView(ct);
            layout.addView(ok);
            dialogBox.setContentView(layout);
            dialogBox.show();


            settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    runUI = false;
                    final Dialog dialogBox = new Dialog(PointToPointActivity.this);
                    dialogBox.setTitle("Settings");

                    final ListView l = new ListView(PointToPointActivity.this);
                    ArrayList list = new ArrayList(){{
                        add("Remove PointCloud Data");
                        add("Disconnect");
                    }};

                    final ArrayAdapter adapter = new ArrayAdapter(PointToPointActivity.this, android.R.layout.simple_list_item_1, list);
                    l.setAdapter(adapter);

                    l.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            switch (position){
                                case 0: path.delete();
                                    allAppl = new ArrayList<Vector3>();
                                    appName = new ArrayList<String>();
                                    appType = new ArrayList<Integer>();
                                    appState = new ArrayList<stateList>();
                                    break;
                                case 1:Disconnect();
                                    break;
                            }
                            dialogBox.dismiss();
                            runUI = true;
                        }
                    });
                    dialogBox.setContentView(l);
                    dialogBox.show();
                }
            });


            path = new File(getFilesDir(),"cord.tmp");
            //path.delete();
            if(!path.exists()){
                try {
                    Log.d("file:", "Created");
                    path.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try{
                BufferedReader bi = new BufferedReader(new FileReader(path));
                String tempFirst;
                while((tempFirst =  bi.readLine()) != null){
                    Log.d("Some",""+tempFirst);
                    allAppl.add(new Vector3(Double.parseDouble(tempFirst), Double.parseDouble(bi.readLine()), Double.parseDouble(bi.readLine())));
                    appName.add(bi.readLine());
                    appType.add(Integer.parseInt(bi.readLine()));
                    stateList sl = new stateList(Integer.parseInt(bi.readLine()));
                    sl.buttonRequired = Boolean.parseBoolean(bi.readLine());
                    sl.sliderRequired = Boolean.parseBoolean(bi.readLine());
                    sl.laptopPanel = Boolean.parseBoolean(bi.readLine());
                    sl.buttonText[0] = bi.readLine();
                    sl.buttonText[1] = bi.readLine();
                    sl.buttonState = Boolean.parseBoolean(bi.readLine());
                    sl.progress = Integer.parseInt(bi.readLine());
                    appState.add(sl);
                }
                Log.d("file : ","Read");
                bi.close();
            } catch (FileNotFoundException e) {
                System.out.println("File not found");
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error initializing stream");
            }
        }




        new ConnectBT().execute();



        bk = (Button) findViewById(R.id.button);
        bm = (Button) findViewById(R.id.button3);
        rc = (Button) findViewById(R.id.rightClick);
        lc = (Button) findViewById(R.id.leftClick);
        tp = (ImageView) findViewById(R.id.TrackPad);


        bk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
            }
        });



        seekBar=(SeekBar)findViewById(R.id.seekBar2);
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setProgress(100);

        bm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(tp.getVisibility() == View.VISIBLE){
                    tp.setVisibility(View.GONE);
                    rc.setVisibility(View.GONE);
                    lc.setVisibility(View.GONE);
                    seekBar.setVisibility(View.GONE);
                }
                else{
                    tp.setVisibility(View.VISIBLE);
                    rc.setVisibility(View.VISIBLE);
                    lc.setVisibility(View.VISIBLE);
                    seekBar.setVisibility(View.VISIBLE);
                }
            }
        });

        lc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btSocket.getOutputStream().write(5);
                    btSocket.getOutputStream().write(6);
                } catch (IOException e) {
                    msg("Error");
                }
            }
        });
        rc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    btSocket.getOutputStream().write(7);
                    btSocket.getOutputStream().write(8);
                } catch (IOException e) {
                    msg("Error");
                }
            }
        });

        OptionsInvisible();
        b.setVisibility(View.VISIBLE);
        addItemButton.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);
        b.setText("CALIBRATE");
        b.setBackgroundColor(Color.BLUE);
        captureBackground.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(runUI){
                    if(setVis == 1){
                        captureBackgroundShow.setVisibility(View.VISIBLE);
                        captured = true;
                        runUI = false;
                    }
                }
                else {
                    runUI = true;
                    captured = false;
                }
                return true;
            }
        });

    }
    Drawable c;
    int[] intialPointer = new int[2];
    int[] currentPointer = new int[2];
    float sensitivity = 1f;
    int i;

    public boolean dispatchTouchEvent(MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            i=0;
            if (inViewInBounds(tp, (int) event.getX(), (int) event.getY())) {
                // User moved outside bounds
                intialPointer[0] = (int) event.getX();
                intialPointer[1] = (int) event.getY();
                //  Log.e("dispatchTouchEvent", "you touched inside button");
            }

        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (inViewInBounds(tp, (int) event.getX(), (int) event.getY())) {
                // User moved outside bounds
                currentPointer[0] = (int) event.getX();
                currentPointer[1] = (int) event.getY();

                int moveX = (int)(((float)(currentPointer[0] - intialPointer[0])) * sensitivity);
                int moveY = (int)(((float)(currentPointer[1] - intialPointer[1])) * sensitivity);

                Log.e("dispatchTouchEvent", "x : " + event.getX() + "y : " + event.getY() + "Loo : " + abs(moveX) + "  " + abs(moveY) );
                if(i==4){
                    i=0;
                    try {
                        btSocket.getOutputStream().write(moveX<0?1:2);
                        btSocket.getOutputStream().write(abs(moveX)>100?100:abs(moveX));
                        btSocket.getOutputStream().write(moveY<0?3:4);
                        btSocket.getOutputStream().write(abs(moveY)>100?100:abs(moveY));
                        intialPointer[0] = currentPointer[0];
                        intialPointer[1] = currentPointer[1];
                    } catch (IOException e) {
                        msg("Error");
                    }
                }
                ++i;


            }

        }
        return super.dispatchTouchEvent(event);
    }

    Rect outRect = new Rect();
    int[] location = new int[2];

    private boolean inViewInBounds(View view, int x, int y) {
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);
        outRect.offset(location[0], location[1]);
        return outRect.contains(x, y);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyaction = event.getAction();

        if(keyaction == KeyEvent.ACTION_DOWN)
        {
            int keycode = event.getKeyCode();
            char character = (char) event.getUnicodeChar(event.getMetaState() );
            try {
                btSocket.getOutputStream().write(0);
                btSocket.getOutputStream().write(character);
            } catch (IOException e) {
                msg("Error");
            }
            Log.d("key pressed","code = " + keycode + " char = " + character);
        }


        return super.dispatchKeyEvent(event);
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout

    }
    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        sensitivity = (float) progress/100f;
        Log.d("sensitivity : ",""+sensitivity);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }


    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(PointToPointActivity.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;

                startThreadConnected(btSocket);
            }
            progress.dismiss();
        }
    }

    ThreadConnected myThreadConnected;

    int rec;

    private void startThreadConnected(BluetoothSocket socket){

        myThreadConnected = new ThreadConnected(socket);
        myThreadConnected.start();
    }

    private class ThreadConnected extends Thread {
        private final BluetoothSocket connectedBluetoothSocket;
        private final InputStream connectedInputStream;

        public ThreadConnected(BluetoothSocket socket) {
            connectedBluetoothSocket = socket;
            InputStream in = null;

            try {
                in = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            connectedInputStream = in;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1];
            int bytes;

            while (true) {
                try {
                    bytes = connectedInputStream.read(buffer);

                    String strReceived = new String(buffer, 0, bytes);
                    //final String msgReceived = String.valueOf(bytes) +" bytes received:\n"+ strReceived;
                    rec = strReceived.charAt(0);
                    Log.d("Rec",""+rec);
                    if(askForComponentIntialisation){
                        if(bytes == 1){
                            initComponentDialogBox.dismiss();
                            askForComponentIntialisation = false;
                        }
                    }
                    else{
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                appState.get(rec).toggleButton();
                            }
                        });
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }


    class stateList{
        int type;
        boolean buttonRequired = false, sliderRequired = false, laptopPanel = false;
        String[] buttonText = {"",""};
        boolean buttonState = true;
        int progress = 0;
        stateList(int id){
            type = id;
            switch(id){
                case 0: buttonRequired = true;
                    setButtonTextInputs("ON","OFF");
                    break;
                case 1: sliderRequired = true;
                    buttonRequired = true;
                    setButtonTextInputs("ON","OFF");
                    break;
                case 4: buttonRequired = true;
                    setButtonTextInputs("OPEN","CLOSE");
                    break;
                case 2: sliderRequired = true;
                    break;
                case 3: laptopPanel = true;
            }
            b.setBackgroundColor(Color.GREEN);
            b.setText(buttonText[0]);
            applyProgress();
        }
        private void setButtonTextInputs(String posText, String negText){
            buttonText[0] = posText;
            buttonText[1] = negText;

        }
        void toggleButton(){
            if(buttonState){
                b.setText(buttonText[0]);
                b.setBackgroundColor(Color.GREEN);
            }
            else{
                b.setText(buttonText[1]);
                b.setBackgroundColor(Color.RED);
            }
            buttonState = !buttonState;
        }
        void updateProgress(int p){
            progress = p;
        }
        void applyProgress(){
            slider.setProgress(progress);
        }
    }

    ArrayList<stateList> appState;
    boolean captured = false;


    @Override
    protected void onStart() {
        super.onStart();

        // Check and request camera permission at run time.
        if (checkAndRequestPermissions()) {
            bindTangoService();
        }


        Timer t = new Timer();
//Set the schedule function and rate
        t.scheduleAtFixedRate(new TimerTask() {

              @Override
              public void run() {
                  float u = 0.5f;
                  float v = 0.5f;

                  try {
                      // Place point near the clicked point using the latest point cloud data.
                      // Synchronize against concurrent access to the RGB timestamp in the OpenGL thread
                      // and a possible service disconnection due to an onPause event.
                      MeasuredPoint newMeasuredPoint;
                      synchronized (this) {
                          TangoPointCloudData pointCloud= mPointCloudManager.getLatestPointCloud();
                          newMeasuredPoint = getDepthAtTouchPosition(pointCloud, u, v);
                      }
                      if (newMeasuredPoint != null) {
                          TangoSupport.TangoMatrixTransformData openglTDepthArr0 =
                                  TangoSupport.getMatrixTransformAtTime(
                                          newMeasuredPoint.mTimestamp,
                                          TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                          TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                          TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                          TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                          TangoSupport.ROTATION_IGNORED);
                          setVis = 0;
                          if (openglTDepthArr0.statusCode == TangoPoseData.POSE_VALID) {

                              p = TangoSupport.transformPoint(
                                      openglTDepthArr0.matrix,
                                      newMeasuredPoint.mDepthTPoint);

                              if(!calibratedPoint){
                                  b.setOnClickListener(new View.OnClickListener() {
                                      @Override
                                      public void onClick(View v) {
                                          cpx = p[0];
                                          cpy = p[1];
                                          cpz = p[2];
                                          calibratedPoint = true;
                                          b.setOnClickListener(null);
                                      }
                                  });
                              }
                              float threshold = 0.18f;
                              //Log.d("Cord", "" + p[0] + " : " + p[1] + " : " + p[2]);
                              for(int i = 0;i<allAppl.size() && runUI && calibratedPoint;++i){
                                  Vector3 cord = allAppl.get(i);
                                  Log.d("Cord", "" + cord.x + cpx + " : " + cord.y + cpy + " : " + cord.z + cpz);
                                  if((cord.x + cpx<(p[0]+threshold) && cord.x + cpx>(p[0]-threshold)) && (cord.y + cpy<(p[1]+threshold) && cord.y + cpy>(p[1]-threshold)) && (cord.z + cpz<(p[2]+threshold) && cord.z + cpz>(p[2]-threshold))){
                                      setVis = 1;
                                      currentAppID = i;
                                      break;
                                  }
                              }
                              if(captured)setVis = 1;
                              PointToPointActivity.this.runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      if(calibratedPoint) {
                                          if (setVis == 1 && appType.size() != 0) {
                                              switch (appType.get(currentAppID)) {
                                                  case 0:
                                                      lightOptions();
                                                      break;
                                                  case 1:
                                                      fanOptions();
                                                      break;
                                                  case 2:
                                                      speakerOptions();
                                                      break;
                                                  case 3:
                                                      laptopOptions();
                                                      break;
                                                  case 4:
                                                      doorOptions();
                                                      break;
                                              }
                                          }
                                          else{
                                              OptionsInvisible();
                                          }
                                      }
                                  }
                              });

                          }
                      } else {
                          Log.w(TAG, "Point was null.");
                      }

                  } catch (TangoException t) {
                      Toast.makeText(getApplicationContext(),
                              R.string.failed_measurement,
                              Toast.LENGTH_SHORT).show();
                      Log.e(TAG, getString(R.string.failed_measurement), t);
                  } catch (SecurityException t) {
                      Toast.makeText(getApplicationContext(),
                              R.string.failed_permissions,
                              Toast.LENGTH_SHORT).show();
                      Log.e(TAG, getString(R.string.failed_permissions), t);
                  }
              }

          },3500,80);


        addItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runUI = false;
                float u = 0.5f;
                float v = 0.5f;

                try {
                    MeasuredPoint newMeasuredPoint;
                    synchronized (this) {
                        newMeasuredPoint = getDepthAtTouchPosition(mPointCloudManager.getLatestPointCloud(), u, v);
                    }
                    if (newMeasuredPoint != null) {
                        TangoSupport.TangoMatrixTransformData openglTDepthArr0 =
                                TangoSupport.getMatrixTransformAtTime(
                                        newMeasuredPoint.mTimestamp,
                                        TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                        TangoSupport.ROTATION_IGNORED);

                        if (openglTDepthArr0.statusCode == TangoPoseData.POSE_VALID) {
                            float[] p0 = TangoSupport.transformPoint(
                                    openglTDepthArr0.matrix,
                                    newMeasuredPoint.mDepthTPoint);

                            allAppl.add(new Vector3(p0[0] - cpx, p0[1] - cpy, p0[2] - cpz));
                            Log.d("XYZ=",p0[0]+" "+p0[1]+" "+p0[2]);

                            appName.add("Appliance" + (appName.size() + 1));

                            final Dialog dialogBox = new Dialog(PointToPointActivity.this);
                            dialogBox.setTitle("Appliance Type");

                            final ListView l = new ListView(PointToPointActivity.this);
                            ArrayList list = new ArrayList(){{
                                add("Light");
                                add("Fan");
                                add("Speaker");
                                add("Projector");
                                add("Door");
                            }};

                            final ArrayAdapter adapter = new ArrayAdapter(PointToPointActivity.this, android.R.layout.simple_list_item_1, list);
                            l.setAdapter(adapter);

                            initComponentDialogBox = new Dialog(PointToPointActivity.this);
                            initComponentDialogBox.setTitle("Press The button");

                            TextView ct = new TextView(PointToPointActivity.this);
                            ct.setText("Press the switch of \n corresponing appliance");
                            ct.setTextSize(18);
                            initComponentDialogBox.setContentView(ct);

                            l.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                    dialogBox.dismiss();
                                    appType.add(position);
                                    appState.add(new stateList(position));
                                    runUI = true;
                                    if(position!=2&&position!=3) {
                                        initComponentDialogBox.show();
                                        try {
                                            btSocket.getOutputStream().write(9);
                                            btSocket.getOutputStream().write(position);
                                            Log.d("Send", "9" + position);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        askForComponentIntialisation = true;
                                    }
                                    updateFile();
                                }
                            });
                            dialogBox.setContentView(l);
                            dialogBox.show();


                        }
                    } else {
                        Log.w(TAG, "Point was null.");
                    }

                } catch (TangoException t) {
                    Toast.makeText(getApplicationContext(),
                            R.string.failed_measurement,
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, getString(R.string.failed_measurement), t);
                } catch (SecurityException t) {
                    Toast.makeText(getApplicationContext(),
                            R.string.failed_permissions,
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, getString(R.string.failed_permissions), t);
                }
            }
        });

        editAppName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(PointToPointActivity.this);
                builder.setTitle("Appliance Name");

                final EditText input = new EditText(PointToPointActivity.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        appName.add(currentAppID, input.getText().toString());
                        updateFile();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();


            }
        });
    }
    boolean runUI = true, askForComponentIntialisation = false;
    Dialog initComponentDialogBox;

    void updateFile(){
        try {
            BufferedWriter bo = new BufferedWriter(new FileWriter(path));

            for(int i = 0;i<allAppl.size();++i) {
                bo.write("" + allAppl.get(i).x + "\n");
                bo.write("" + allAppl.get(i).y + "\n");
                bo.write("" + allAppl.get(i).z + "\n");
                bo.write("" + appName.get(i) + "\n");
                bo.write("" + appType.get(i) + "\n");
                bo.write("" + appState.get(i).type + "\n");
                bo.write("" + appState.get(i).buttonRequired + "\n");
                bo.write("" + appState.get(i).sliderRequired + "\n");
                bo.write("" + appState.get(i).laptopPanel + "\n");
                bo.write("" + appState.get(i).buttonText[0] + "\n");
                bo.write("" + appState.get(i).buttonText[1] + "\n");
                bo.write("" + appState.get(i).buttonState + "\n");
                bo.write("" + appState.get(i).progress + "\n");
            }

            bo.close();

        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error initializing stream");
        }
    }

    private  void OptionsInvisible(){
        editAppName.setVisibility(View.GONE);
        appIDtextView.setVisibility(View.GONE);
        b.setVisibility(View.GONE);
        slider.setVisibility(View.GONE);
        addItemButton.setVisibility(View.VISIBLE);
        settings.setVisibility(View.VISIBLE);
        captureBackgroundShow.setVisibility(View.INVISIBLE);
        b.setOnClickListener(null);
        slider.setOnSeekBarChangeListener(null);
        lc.setVisibility(View.GONE);
        rc.setVisibility(View.GONE);
        bk.setVisibility(View.GONE);
        bm.setVisibility(View.GONE);
        tp.setVisibility(View.GONE);
        seekBar.setVisibility(View.GONE);
    }



    private void lightOptions(){
        appIDtextView.setText(appName.get(currentAppID));
        editAppName.setVisibility(View.VISIBLE);
        appIDtextView.setVisibility(View.VISIBLE);
        b.setVisibility(View.VISIBLE);
        addItemButton.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);
        appState.get(currentAppID).toggleButton();
        appState.get(currentAppID).toggleButton();
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appState.get(currentAppID).toggleButton();
                try {
                    btSocket.getOutputStream().write(16);
                    btSocket.getOutputStream().write(currentAppID);
                    btSocket.getOutputStream().write(appState.get(currentAppID).buttonState?1:0);
                    Log.d("Send", "16"+currentAppID + (appState.get(currentAppID).buttonState?1:0));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                updateFile();
            }
        });
    }
    private void fanOptions(){
        appIDtextView.setText(appName.get(currentAppID));
        editAppName.setVisibility(View.VISIBLE);
        appIDtextView.setVisibility(View.VISIBLE);
        b.setVisibility(View.VISIBLE);
        slider.setVisibility(View.VISIBLE);
        addItemButton.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);
        appState.get(currentAppID).applyProgress();
        appState.get(currentAppID).toggleButton();
        appState.get(currentAppID).toggleButton();
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appState.get(currentAppID).toggleButton();
                try {
                    btSocket.getOutputStream().write(16);
                    btSocket.getOutputStream().write(currentAppID);
                    btSocket.getOutputStream().write(0);
                    btSocket.getOutputStream().write(appState.get(currentAppID).buttonState?1:0);
                    Log.d("Send", "16"+currentAppID + (appState.get(currentAppID).buttonState?1:0));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                updateFile();
            }
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int p = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                p = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                appState.get(currentAppID).updateProgress(p);
                try {
                    btSocket.getOutputStream().write(16);
                    btSocket.getOutputStream().write(currentAppID);
                    btSocket.getOutputStream().write(1);
                    btSocket.getOutputStream().write(p);
                    Log.d("Send", "16"+currentAppID + p);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                updateFile();
            }
        });
    }
    private void speakerOptions(){
        appIDtextView.setText(appName.get(currentAppID));
        editAppName.setVisibility(View.VISIBLE);
        appIDtextView.setVisibility(View.VISIBLE);
        slider.setVisibility(View.VISIBLE);
        addItemButton.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);
        appState.get(currentAppID).applyProgress();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int previous, newv;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                newv = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                previous = appState.get(currentAppID).progress;

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                appState.get(currentAppID).updateProgress(newv);
                updateFile();
                try {
                    btSocket.getOutputStream().write(0);
                    btSocket.getOutputStream().write(newv>previous?18:19);
                } catch (IOException e) {
                    msg("Error");
                }
            }
        });
    }
    private void laptopOptions(){
        appIDtextView.setText(appName.get(currentAppID));
        editAppName.setVisibility(View.VISIBLE);
        appIDtextView.setVisibility(View.VISIBLE);
        addItemButton.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);
        lc.setVisibility(View.VISIBLE);
        rc.setVisibility(View.VISIBLE);
        bk.setVisibility(View.VISIBLE);
        bm.setVisibility(View.VISIBLE);
        tp.setVisibility(View.VISIBLE);
        seekBar.setVisibility(View.VISIBLE);
    }
    private void doorOptions(){
        appIDtextView.setText(appName.get(currentAppID));
        editAppName.setVisibility(View.VISIBLE);
        appIDtextView.setVisibility(View.VISIBLE);
        b.setVisibility(View.VISIBLE);
        addItemButton.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);
        appState.get(currentAppID).toggleButton();
        appState.get(currentAppID).toggleButton();
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appState.get(currentAppID).toggleButton();
                try {
                    btSocket.getOutputStream().write(16);
                    btSocket.getOutputStream().write(currentAppID);
                    btSocket.getOutputStream().write(appState.get(currentAppID).buttonState?1:0);
                    Log.d("Send", "16"+currentAppID + (appState.get(currentAppID).buttonState?1:0));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                updateFile();
            }
        });

    }

    @Override
    protected void onStop() {
        super.onStop();
        clearLine();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
        // will block here until all Tango callback calls are finished. If you lock against this
        // object in a Tango callback thread it will cause a deadlock.
        synchronized (this) {
            try {
                mRenderer.getCurrentScene().clearFrameCallbacks();
                if (mTango != null) {
                    mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                    mTango.disconnect();
                }
                // We need to invalidate the connected texture ID so that we cause a
                // re-connection in the OpenGL thread after resume.
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mIsConnected = false;
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Initialize Tango Service as a normal Android Service. Since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time onResume gets called we
        // should create a new Tango object.
        mTango = new Tango(PointToPointActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                // Synchronize against disconnecting while the service is being used in the OpenGL
                // thread or in the UI thread.
                synchronized (PointToPointActivity.this) {
                    try {
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        TangoSupport.initialize(mTango);
                        connectRenderer();
                        mIsConnected = true;
                        setDisplayRotation();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }
        });
        mHandler.post(mUpdateUiLoopRunnable);
    }

    /**
     * Sets up the Tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service (motion tracking), plus low latency
        // IMU integration, color camera, depth and drift correction.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a
        // precise alignment of virtual objects with the RBG image and
        // produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        // Drift correction allows motion tracking to recover after it loses tracking.
        // The drift-corrected pose is available through the frame pair with
        // base frame AREA_DESCRIPTION and target frame DEVICE.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);

        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the RGB camera and point cloud.
     */
    private void startupTango() {
        // No need to add any coordinate frame pairs since we are not
        // using pose data. So just initialize.
        ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // We are not using OnPoseAvailable for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Mark a camera frame as available for rendering in the OpenGL thread.
                    mIsFrameAvailableTangoThread.set(true);
                    mSurfaceView.requestRender();
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                // Save the cloud and point data for later use.
                mPointCloudManager.updatePointCloud(pointCloud);
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using OnPoseAvailable for this app.
            }
        });
        mTango.experimentalConnectOnFrameListener(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                new Tango.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(TangoImageBuffer tangoImageBuffer, int i) {
                        mCurrentImageBuffer = copyImageBuffer(tangoImageBuffer);
                    }

                    TangoImageBuffer copyImageBuffer(TangoImageBuffer imageBuffer) {
                        ByteBuffer clone = ByteBuffer.allocateDirect(imageBuffer.data.capacity());
                        imageBuffer.data.rewind();
                        clone.put(imageBuffer.data);
                        imageBuffer.data.rewind();
                        clone.flip();
                        return new TangoImageBuffer(imageBuffer.width, imageBuffer.height,
                                imageBuffer.stride, imageBuffer.frameNumber,
                                imageBuffer.timestamp, imageBuffer.format, clone);
                    }
                });
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void connectRenderer() {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks have a chance to run and before scene objects are rendered
                // into the scene.

                try {
                    // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the
                    // Tango callback thread and service disconnection from an onPause event.
                    synchronized (PointToPointActivity.this) {
                        // Don't execute any tango API actions if we're not connected to the
                        // service.
                        if (!mIsConnected) {
                            return;
                        }

                        // Set up scene camera projection to match RGB camera intrinsics.
                        if (!mRenderer.isSceneCameraConfigured()) {
                            TangoCameraIntrinsics intrinsics =
                            TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    mDisplayRotation);
                            mRenderer.setProjectionMatrix(
                                    projectionMatrixFromCameraIntrinsics(intrinsics));
                        }

                        // Connect the camera texture to the OpenGL Texture if necessary
                        // NOTE: When the OpenGL context is recycled, Rajawali may regenerate the
                        // texture with a different ID.
                        if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                            mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    mRenderer.getTextureId());
                            mConnectedTextureIdGlThread = mRenderer.getTextureId();
                            Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                        }

                        // If there is a new RGB camera frame available, update the texture with
                        // it.
                        if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                            mRgbTimestampGlThread =
                                    mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        }

                        // If a new RGB frame has been rendered, update the camera pose to match.
                        if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
                            // Calculate the camera color pose at the camera frame update time in
                            // OpenGL engine.
                            //
                            // When drift correction mode is enabled in config file, we need
                            // to query the device with respect to Area Description pose in
                            // order to use the drift-corrected pose.
                            //
                            // Note that if you don't want to use the drift corrected pose, the
                            // normal device with respect to start of service pose is still
                            // available.
                            //
                            // Also, we used mColorCameraToDipslayRotation to rotate the
                            // transformation to align with the display frame. The reason we use
                            // color camera instead depth camera frame is because the
                            // getDepthAtPointNearestNeighbor transformed depth point to camera
                            // frame.
                            TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                    mRgbTimestampGlThread,
                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    mDisplayRotation);
                            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                                // Update the camera pose from the renderer.
                                mRenderer.updateRenderCameraPose(lastFramePose);
                                mCameraPoseTimestamp = lastFramePose.timestamp;
                            } else {
                                // When the pose status is not valid, it indicates the tracking has
                                // been lost. In this case, we simply stop rendering.
                                //
                                // This is also the place to display UI to suggest the user walk
                                // to recover tracking.
                                Log.w(TAG, "Can't get device pose at time: " +
                                        mRgbTimestampGlThread);
                            }

                            // If both points have been measured, we transform the points to OpenGL
                            // space, and send it to mRenderer to render.
                            if (mMeasuredPoints != null) {
                                // To make sure drift correct pose is also applied to virtual
                                // object (measured points).
                                // We need to re-query the Area Description to Depth camera
                                // pose every frame. Note that you will need to use the timestamp
                                // at the time when the points were measured to query the pose.
                                TangoSupport.TangoMatrixTransformData openglTDepthArr =
                                        TangoSupport.getMatrixTransformAtTime(
                                                mMeasuredPoints.mTimestamp,
                                                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                                TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                                TangoSupport.ROTATION_IGNORED);

                                if (openglTDepthArr.statusCode == TangoPoseData.POSE_VALID) {
                                    mRenderer.updateObjectPose(openglTDepthArr.matrix,
                                            mDepthTPlane);
                                }
                            }

                        }
                    }
                    // Avoid crashing the application due to unhandled exceptions.
                } catch (TangoErrorException e) {
                    Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception on the OpenGL thread", t);
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });
    }

    /**
     * Use the Tango Support Library with point cloud data to calculate the plane
     * of the world feature pointed at the location the camera is looking.
     * It returns the transform of the fitted plane in a double array.
     */
    private float[] mDepthTPlane;
    private double mPlanePlacedTimestamp;
    private float[] doFitPlane(float u, float v, double rgbTimestamp) {
        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();

        if (pointCloud == null) {
            return null;
        }

        TangoPoseData depthToColorPose = TangoSupport.getPoseAtTime(
                rgbTimestamp,
                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.ROTATION_IGNORED);
        if (depthToColorPose.statusCode != TangoPoseData.POSE_VALID) {
            Log.d(TAG, "Could not get a valid pose from depth camera"
                    + "to color camera at time " + rgbTimestamp);
            return null;
        }

        // Plane model is in depth camera space due to input poses.
        TangoSupport.IntersectionPointPlaneModelPair intersectionPointPlaneModelPair =
                TangoSupport.fitPlaneModelNearPoint(pointCloud,
                        new double[] {0.0, 0.0, 0.0},
                        new double[] {0.0, 0.0, 0.0, 1.0},
                        u, v,
                        mDisplayRotation,
                        depthToColorPose.translation,
                        depthToColorPose.rotation);

        mPlanePlacedTimestamp = mRgbTimestampGlThread;
        return convertPlaneModelToMatrix(intersectionPointPlaneModelPair);
    }

    private float[] convertPlaneModelToMatrix(TangoSupport.IntersectionPointPlaneModelPair planeModel) {
        // Note that depth camera's space is:
        // X - right
        // Y - down
        // Z - forward
        float[] up = new float[]{0, 1, 0, 0};
        float[] depthTPlane = matrixFromPointNormalUp(
                planeModel.intersectionPoint,
                planeModel.planeModel,
                up);
        return depthTPlane;
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        double cx = intrinsics.cx;
        double cy = intrinsics.cy;
        double width = intrinsics.width;
        double height = intrinsics.height;
        double fx = intrinsics.fx;
        double fy = intrinsics.fy;

        double xscale = near / fx;
        double yscale = near / fy;

        double xoffset = (cx - (width / 2.0)) * xscale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        double yoffset = -(cy - (height / 2.0)) * yscale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                (float) (xscale * -width / 2.0 - xoffset),
                (float) (xscale * width / 2.0 - xoffset),
                (float) (yscale * -height / 2.0 - yoffset),
                (float) (yscale * height / 2.0 - yoffset), near, far);
        return m;
    }
    /**
     * Calculates a transformation matrix based on a point, a normal and the up gravity vector.
     * The coordinate frame of the target transformation will be a right handed system with Z+ in
     * the direction of the normal and Y+ up.
     */
    private float[] matrixFromPointNormalUp(double[] point, double[] normal, float[] up) {
        float[] zAxis = new float[]{(float) normal[0], (float) normal[1], (float) normal[2]};
        normalize(zAxis);
        float[] xAxis = crossProduct(up, zAxis);
        normalize(xAxis);
        float[] yAxis = crossProduct(zAxis, xAxis);
        normalize(yAxis);
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        m[0] = xAxis[0];
        m[1] = xAxis[1];
        m[2] = xAxis[2];
        m[4] = yAxis[0];
        m[5] = yAxis[1];
        m[6] = yAxis[2];
        m[8] = zAxis[0];
        m[9] = zAxis[1];
        m[10] = zAxis[2];
        m[12] = (float) point[0];
        m[13] = (float) point[1];
        m[14] = (float) point[2];
        return m;
    }
    /**
     * Normalize a vector.
     */
    private void normalize(float[] v) {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
    }

    /**
     * Cross product between two vectors following the right-hand rule.
     */
    private float[] crossProduct(float[] v1, float[] v2) {
        float[] result = new float[3];
        result[0] = v1[1] * v2[2] - v2[1] * v1[2];
        result[1] = v1[2] * v2[0] - v2[2] * v1[0];
        result[2] = v1[0] * v2[1] - v2[0] * v1[1];
        return result;
    }



    /**
     * Use the Tango Support Library with point cloud data to calculate the depth
     * of the point closest to where the user touches the screen. It returns a
     * Vector3 in OpenGL world space.
     */
    private MeasuredPoint getDepthAtTouchPosition(TangoPointCloudData pointCloud, float u, float v) {
        //TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
        if (pointCloud == null) {
            return null;
        }

        double rgbTimestamp;
        TangoImageBuffer imageBuffer = mCurrentImageBuffer;
        //if (mBilateralBox.isChecked()) {
        if (true) {
            rgbTimestamp = imageBuffer.timestamp; // CPU.
        } else {
            rgbTimestamp = mRgbTimestampGlThread; // GPU.
        }

        TangoPoseData depthlTcolorPose = TangoSupport.getPoseAtTime(
                rgbTimestamp,
                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.ROTATION_IGNORED);
        if (depthlTcolorPose.statusCode != TangoPoseData.POSE_VALID) {
            Log.w(TAG, "Could not get color camera transform at time "
                       + rgbTimestamp);
            return null;
        }

        float[] depthPoint;
        //if (mBilateralBox.isChecked()) {
        if (true) {
            depthPoint = TangoSupport.getDepthAtPointBilateral(
                    pointCloud,
                    new double[] {0.0, 0.0, 0.0},
                    new double[] {0.0, 0.0, 0.0, 1.0},
                    imageBuffer,
                    u, v,
                    mDisplayRotation,
                    depthlTcolorPose.translation,
                    depthlTcolorPose.rotation);
        } else {
            depthPoint = TangoSupport.getDepthAtPointNearestNeighbor(
                    pointCloud,
                    new double[] {0.0, 0.0, 0.0},
                    new double[] {0.0, 0.0, 0.0, 1.0},
                    u, v,
                    mDisplayRotation,
                    depthlTcolorPose.translation,
                    depthlTcolorPose.rotation);
        }

        if (depthPoint == null) {
            return null;
        }

        return new MeasuredPoint(rgbTimestamp, depthPoint);
    }

    /**
     * Update the oldest line endpoint to the value passed into this function.
     * This will also flag the line for update on the next render pass.
     */
    private synchronized void updateLine(MeasuredPoint newPoint) {
        /*if (mPointSwitch) {
            mPointSwitch = !mPointSwitch;
            mMeasuredPoints[0] = newPoint;
            return;
        }
        mPointSwitch = !mPointSwitch;
        mMeasuredPoints[1] = newPoint;*/
    }

    /*
     * Remove all the points from the Scene.
     */
    private synchronized void clearLine() {
        mMeasuredPoints = null;
        mPointSwitch = true;
        mRenderer.setLine(null);
    }

    // Debug text UI update loop, updating at 10Hz.
    private Runnable mUpdateUiLoopRunnable = new Runnable() {
        public void run() {
            try {
                //mDistanceTextview.setText(String.format("%.2f", mMeasuredDistance) + " meters");
            } catch (Exception e) {
                e.printStackTrace();
            }
            mHandler.postDelayed(this, UPDATE_UI_INTERVAL_MS);
        }
    };

    /**
     * Set the color camera background texture rotation and save the display rotation.
     */
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();

        // We also need to update the camera texture UV coordinates. This must be run in the OpenGL
        // thread.
        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (mIsConnected) {
                    mRenderer.updateColorCameraTextureUvGlThread(mDisplayRotation);
                }
            }
        });
    }

    /**
     * Check to see if we have the necessary permissions for this app; ask for them if we don't.
     *
     * @return True if we have the necessary permissions, false if we don't.
     */
    private boolean checkAndRequestPermissions() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return false;
        }
        return true;
    }

    /**
     * Check to see if we have the necessary permissions for this app.
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request the necessary permissions for this app.
     */
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION)) {
            showRequestPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION},
                    CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain that the app needs this
     * permission.
     */
    private void showRequestPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Java Point to point Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(PointToPointActivity.this,
                                new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PointToPointActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * Result for requesting camera permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasCameraPermission()) {
            bindTangoService();
        } else {
            Toast.makeText(this, "Java Point to point Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }
}
