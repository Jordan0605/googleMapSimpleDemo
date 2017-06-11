package com.android.pts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

public class SensorReader extends Activity {
	
	private SensorManager smgr;
	private TextView tv;
	private Sensor sensor;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reader);
        
        tv = (TextView)findViewById(R.id.tv_sresult);
        
        smgr = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        
        Bundle bunde = this.getIntent().getExtras();
        int type = bunde.getInt("KEY_TYPE");
        
        sensor = smgr.getDefaultSensor(type);
    }
	
	@Override
	protected void onResume() {
		smgr.registerListener(sListener, sensor, SensorManager.SENSOR_DELAY_UI);
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		smgr.unregisterListener(sListener, sensor);
		super.onPause();
	}
	
	private final SensorEventListener sListener = new SensorEventListener() {
		public void onSensorChanged (SensorEvent event) {
			if (event.sensor == sensor) {
				String str = "";
				
				switch (sensor.getType()) {
				case Sensor.TYPE_ACCELEROMETER:
					str = "Accelerometer Sensor\n";
					break;
				case Sensor.TYPE_GYROSCOPE:
					str = "Gyroscope Sensor\n";
					break;
				case Sensor.TYPE_LIGHT:
					str = "Light Sensor\n";
					break;
				case Sensor.TYPE_MAGNETIC_FIELD:
					str = "Magnetic Field Sensor\n";
					break;
				case Sensor.TYPE_ORIENTATION:
					str = "Orientation Sensor\n";
					break;
				case Sensor.TYPE_PRESSURE:
					str = "Pressure Sensor\n";
					break;
				case Sensor.TYPE_PROXIMITY:
					str = "Proximity Sensor\n";
					break;
				case Sensor.TYPE_TEMPERATURE:
					str = "Temperature Sensor\n";
					break;	
				}
				
				for (int i = 0; i < event.values.length; i++)
					str = str + "values[" + i + "]: " + event.values[i] + "\n";
				
				str = str + "Accuracy: " + event.accuracy;
				
				tv.setText(str);
			}
		}
		public void onAccuracyChanged (Sensor sensor, int accuracy) {
		}		
	};
}
