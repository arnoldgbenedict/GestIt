package com.projecttango.examples.java.pointtopoint;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class intro extends Activity{


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);
        Log.d("Starting","started");

        Thread timerThread = new Thread(){
            public void run(){
                try{
                    Log.d("Starting","doing");
                    sleep(3000);

                }catch(InterruptedException e){
                    e.printStackTrace();
                }finally{
                    Intent intent = new Intent(intro.this,blueMain.class);
                    startActivity(intent);
                }
            }
        };
        timerThread.start();

    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}

