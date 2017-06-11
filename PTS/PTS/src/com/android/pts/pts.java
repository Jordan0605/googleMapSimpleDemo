package com.android.pts;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

final class glo{
	  protected static final Object[][] MG = null;
	  static int Mag_start = 100;
	  static double PI = 3.14159265358;
	  static int SAMPLE_RATE = 20; // (samples/sec)
	  static int MG_size = 500;
	  //int SLIDING_WIN = 32;  //without US&DS
	  static int SLIDING_WIN = 20   ;  //window size
	  static int SLIDING_DIST = 2;   //window slides unit
	  static int Acc_dec_start = 20;  //start acceleration decomposition
	  //**********for gravity calculation*************
	  static int G0_num = 20;   //sample number for calculate g0
	  static int G0_start =20;  //start to calculate g0 after this sample
	  static int G0_feq = 20 ;  //frequency of calling cal_g0
	  static double G0_U_THD = 1.02; //1.02-->1.07
	  static double G0_L_THD = 0.93; //0.9775 -->0.93
	  static double G0_average = 0.99640413;  //unit g in static
	  static double AMP1_U_THD = 0.29;//0.28-->0.29
	  static double AMP1_L_THD = 0.0095625;
	  static double AMP2_U_THD = 0.29;//0.28-->0.29
	  static double AMP2_L_THD = 0.0095625;
	  static double AMP_Ratio_U_THD = 3.1;//3.0-->3.1
	  static double AMP_Ratio_L_THD = 0.4;//0.5-->0.4
	  static int TIME_U_THD = 840;//0.84->840
	  static int TIME_L_THD = 300;//0.3->300
	  static double WALK_THD = 0.003;
	  static double ha_U_THD = 0.162375;//0.142375-->0.162375
	  static double ha_L_THD = 0.0035;//0.006-->0.0035
	}

public class pts extends Activity {
	public class data{
		public double ax;  //accelerometer vector
		public double ay;
		public double az;
		public double va;   //vertical acceleration 
		public double ha;   //horizontal acceleration
		public long time;
		public float orientation;
	}
	/*共同變數*/
	public static class dmg{
      static int step_count = 0;
      static double distance = 0;
      static double [][]MG = new double [glo.MG_size][20];//data window
      static int MG_index=-1;
      static int Sam_num=-1;
      static int Sliding_start = glo.G0_start;  
      static double [] G0={0,0,0};  //gravity
      static double G0_length;
      static double a_length;//go_length
      static double ha_sum=0;
      static int state=0;
      static float walking_direction;
    }
	public static data[] sensor;
	/* 建立Handler物件 */
	private Handler push = new Handler();
	private Handler walking_judge = new Handler();
	public int index=0;//MG index
	public long time=0;//系統時間
	public long time1=0;
	public int count=0;
	public float max1=0;
	public float amin1=0,amin2=0;
	public float acc_x,acc_y,acc_z;//加速度值
	public float mag_x,mag_y,mag_z;//磁力值
	public float ori_x,ori_y,ori_z;//方向值
	private SensorManager mSensorManager;
	private TextView tv_sc,tv_sl,tv_wd;
	public  boolean mIsRunning=false;
	private boolean setG0=false;
	
	private File sdcard;
	  PrintWriter sdcardWriter = null;
  /*push data 50ms一次*/
	  private Runnable mTasks = new Runnable() 
	  { 
	    public void run() 
	    {
	      synchronized (this)	{
	      if(mIsRunning){
	    	  dmg.MG_index=(dmg.MG_index+1)%glo.MG_size;
		      int index=dmg.MG_index;
		      push(index);//push data  
		      dmg.Sam_num++;//計算sample數
		      if(dmg.Sam_num%glo.G0_feq==0&&dmg.Sam_num>glo.G0_start){ //calculate g0 periodically
		          if(dmg.Sam_num-glo.G0_start<glo.G0_num)
		                  Cal_G0(dmg.Sam_num-glo.G0_start,index);
		           else
		                  Cal_G0(glo.G0_num,index); 
		       if(!setG0&&dmg.G0_length!=0)
		          Toast.makeText(pts.this,"G0 get,請走!!", Toast.LENGTH_SHORT).show();
		       			setG0=true; 
		       
		       }
	      }
	      }
	     
	      push.postDelayed(mTasks, 50); //重覆執行task
	    }
	  };
	  
	  //腳步判斷,25ms一次
	  private Runnable mTasks2 = new Runnable() 
	  { 
	    public void run() 
	    {	
	    	if(setG0){
		    	int index=dmg.MG_index;
		    	if(((dmg.MG_index-dmg.Sliding_start+glo.MG_size)%glo.MG_size)>2*glo.SLIDING_WIN){
		      		
		    		walking_judge(index);
		    		
		      	}
	    	}
	    	walking_judge.postDelayed(mTasks2,25);
	    }
	  }; 
	  
	 
	  
	  
	  public void onCreate(Bundle savedInstanceState)
	  {	
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,  WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);  
		super.onCreate(savedInstanceState);
	    setContentView(R.layout.pts_ui);
	    // TODO Auto-generated method stub
	    /*宣告一個sensor物件,並註冊它*/
	    mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
	    
	    mSensorManager.registerListener(mStepDetector, 
	            SensorManager.SENSOR_ACCELEROMETER | 
	            SensorManager.SENSOR_MAGNETIC_FIELD | 
	            SensorManager.SENSOR_ORIENTATION,
	            SensorManager.SENSOR_DELAY_FASTEST);
	    Date timeNow = new Date(System.currentTimeMillis());//
	    time=(long)timeNow.getTime();//求進到service的時間
	    sensor = new data[glo.MG_size]; 
	    push.postDelayed(mTasks, 50); //push data, cal_g0 
	    walking_judge.postDelayed(mTasks2, 25);//walking_judge
	    tv_sc = (TextView)findViewById(R.id.st_c);
	    tv_sl = (TextView)findViewById(R.id.st_l);
	    tv_wd = (TextView)findViewById(R.id.wa_d);
	    
	   
		
	    //開sdcard卡, 存資料
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_REMOVED))
			sdcard = null;
		else
		{	//開檔
			File sdcardDir = Environment.getExternalStorageDirectory();
			String sdcardPath = sdcardDir.getAbsolutePath() + File.separator
					+ getString(R.string.app_name) + File.separator
					+ getString(R.string.app_name) + "-"
					+  "_pts.txt";
			
			sdcard = new File(sdcardPath);
			sdcard.getParentFile().mkdirs();
		}
		
		try
		{
			sdcardWriter = new PrintWriter(new FileWriter(sdcard));
		}
		catch(IOException e)
		{
			//Log.d("a","cannot open file!!");
		}
	  }

	 
	  
	  public void onDestroy()
	  {
	    // TODO Auto-generated method stub
	    
	    /* 關閉服務時，關閉執行緒 */
	    mSensorManager.unregisterListener(mStepDetector);
	    push.removeCallbacks(mTasks);
	    walking_judge.removeCallbacks(mTasks2);
	    super.onDestroy();
	  }
	 
	  private final SensorListener mStepDetector = new SensorListener(){
	    public void onAccuracyChanged(int sensor, int accuracy)
	    {
	      // TODO Auto-generated method stub
	    }
	    /*sensor 值一變化,就處理並暫存*/
	    /*可以分成加速度計和磁力計*/
	    public void onSensorChanged(int sensor, float[] values)
	    {
	      // TODO Auto-generated method stub
	      if(sensor == SensorManager.SENSOR_ACCELEROMETER){
	    	  acc_x = values[SensorManager.DATA_X]/SensorManager.GRAVITY_EARTH; 
	    	  acc_y = values[SensorManager.DATA_Y]/SensorManager.GRAVITY_EARTH; 
	    	  acc_z = values[SensorManager.DATA_Z]/SensorManager.GRAVITY_EARTH;
	    	  
	      
	      }
	      if (sensor == SensorManager.SENSOR_MAGNETIC_FIELD){
	    	  mag_x = values[SensorManager.DATA_X]; 
	          mag_y = values[SensorManager.DATA_Y]; 
	          mag_z = values[SensorManager.DATA_Z];   
	      }
	      if(sensor == SensorManager.SENSOR_ORIENTATION){
	    	  ori_x = values[SensorManager.DATA_X]; 
	          ori_y = values[SensorManager.DATA_Y]; 
	          ori_z = values[SensorManager.DATA_Z];   
	      }
	    }
	  };
	  
	 	/*
		 public boolean onCreateOptionsMenu(Menu menu) {
		     
		     menu.add(0, 1, 0, "start");
		     if (mIsRunning) {
		   	  menu.add(1, 2, 0,"pause");
		     }
		     else {
		   	  menu.add(0, 3, 0, "resume");
		     }
		     menu.add(0, 4, 0, "reset");
		     
		     menu.add(0, 5, 0, "quit");
		     
		     return true;
		 }
		 */
	  	/*可以動態調整選單*/
		 public boolean onPrepareOptionsMenu(Menu menu) {        
			 menu.clear();
			 menu.add(0, 1, 0, "start");
		     if (mIsRunning) {
		   	  menu.add(1, 2, 0,"pause");
		     }
		     else {
		   	  menu.add(0, 3, 0, "resume");
		     }
		     menu.add(0, 4, 0, "reset");
		     
		     menu.add(0, 5, 0, "quit");
			
			 return true;    
		}
		 
		 /* menu選擇事件 */
		 public boolean onOptionsItemSelected(MenuItem item) {
		     switch (item.getItemId()) {
		     	  case 1:
		     		  start();
		     		  return true;
		     	  case 2:
		     		  stop();
		             return true;
		         case 3:
		             start();
		             return true;
		         case 4:
		             resetValues();
		             return true;
		         case 5:
		            
		             stop();
		             finish();
		             return true;
		     }
		     return false;
		 }
		/*start*/
		 private void start() {
		     mIsRunning = true;
		     Toast.makeText(pts.this, "程式開始,請等G0計算好", Toast.LENGTH_SHORT).show();
		 }
		
		 
		 /*stop*/
		 private void stop() {
		     mIsRunning = false;
		     Toast.makeText(pts.this,"程式暫停/結束", Toast.LENGTH_LONG).show(); 
		 }
		 
		 /*resetvalues*/
		 private void resetValues(){
			 dmg.step_count = 0;
			 dmg.distance = 0;
			 tv_sc.setText("0");
			 tv_sl.setText("0");
			 tv_wd.setText("0");
			 
		 }
		 
		 
		 /*將處理過的值放到sensor[]*/
		  private void push(int index){
			  int i,j;
			  
			  sensor[index] = new data();
			  sensor[index].ax=(double)acc_x;
			  sensor[index].ay=(double)acc_y;
			  sensor[index].az=-(double)acc_z;
			 
			  if(index>glo.Acc_dec_start){
				  //將這個index的加速度值與前七個做平均,low-power filter
				  for(i=1;i<8;i++){
					  j=((index - i)+glo.MG_size)%glo.MG_size;
					  sensor[index].ax += sensor[j].ax;
					  sensor[index].ay += sensor[j].ay;
					  sensor[index].az += sensor[j].az;
					  
			      }
				  
				  sensor[index].ax=sensor[index].ax/i;
				  sensor[index].ay=sensor[index].ay/i;
				  sensor[index].az=sensor[index].az/i;                            
			  }
			  //initial
			  if(index-glo.G0_start<=glo.Acc_dec_start){
				  sensor[index].va=0;
				  sensor[index].ha=0;
				  sensor[index].time=0;
				  sensor[index].orientation=0;
			  }
			  else{
				  //acceleration decomposition 
				  sensor[index].va=(sensor[index].ax*dmg.G0[0]+sensor[index].ay*dmg.G0[1]+sensor[index].az*dmg.G0[2])/dmg.G0_length;//va
			      dmg.a_length=Math.pow(sensor[index].ax,2)+Math.pow(sensor[index].ay,2)+Math.pow(sensor[index].az,2);
			      sensor[index].ha=Math.sqrt(dmg.a_length-Math.pow(sensor[index].va, 2));//ha
			      sensor[index].va-=dmg.G0_length;
			      Date timeNow1 = new Date(System.currentTimeMillis()); 
			      sensor[index].time=(int) ((long)timeNow1.getTime()- time);//time
			      sensor[index].orientation =ori_x;
			      if(sensor[index].orientation>180)
			    	  sensor[index].orientation-=360;
			      show_ori(index);
			  }
			
//			sdcardWriter.print(sensor[index].va);
//			sdcardWriter.print("\n");
//			sdcardWriter.flush(); //寫入sdcard卡  
			 
		}
		
		//show_ori
		private void show_ori(int index) {
			// TODO Auto-generated method stub
			//秀出手機與北方的夾角
			 
			 tv_wd.setText(""+sensor[index].orientation);
		}



		//cal_g0 
		  private void Cal_G0(int num, int index){
		 	 int i,j,temp_num=0;
		      double[]sum={0,0,0};
		      double temp_x=0,temp_y=0,temp_z,temp_length;
		      /*找出在thd內的G0值,並加總*/
		      for(i=0;i<num;i++){
		          j = (index-i+glo.MG_size)%glo.MG_size; 
		          temp_x=sensor[j].ax;
		          temp_y=sensor[j].ay;
		          temp_z=sensor[j].az;
		          temp_length=Math.sqrt(Math.pow(temp_x,2) + Math.pow(temp_y,2) + Math.pow(temp_z,2) );
		          
		          //看長度有無在thread內,有就採用這筆data
		          if(temp_length>glo.G0_L_THD&&temp_length<glo.G0_U_THD){
		         	 sum[0]+=temp_x;
		         	 sum[1]+=temp_y;
		         	 sum[2]+=temp_z;
		         	 temp_num++;
		          }
		        
		       }
		      
		       /*算平均的G0和G0長度*/
		       if(temp_num>0){
		          for(i=0;i<3;i++){
		           dmg.G0[i]=sum[i]/temp_num;
		          }
		           dmg.G0_length=Math.sqrt( Math.pow(dmg.G0[0],2) + Math.pow(dmg.G0[1],2) + Math.pow(dmg.G0[2],2) );
		       }
		       
		   }
		  
		  
		//walking_judge
		  private void walking_judge(int index) {
		 	 int s_start = dmg.Sliding_start;
		      int s_end = (s_start + glo.SLIDING_WIN - 1) % glo.MG_size;
		      int state=0;
		      int s = 0; 
		      
		      if (index > s_end ||index < s_start)//if MG_index is in next cycle
		      {   
		     	 double max=0,min1,min2;
		     	 long max_t, min1_t = 0,min2_t = 0;
		     	 double ha_sum=0;
		     	 double s_amp1,s_amp2,s_amp;
		     	 double s_time;
		     	 double s_amp_ratio;
		     	 int max_num = -1;
		     	 int min1_num = -1;
		     	 int min2_num = -1;
		     	 int i = 0,j=0;
		       
		     	 max = sensor[index].va;
		     	 min1= sensor[index].va;
		     	 min2= sensor[index].va;
		     	
		     	 //先找max
		     	 while(i < glo.SLIDING_WIN)  //search max in sliding window
		     	 { 
		     		 s = (dmg.Sliding_start + i) % glo.MG_size;
		     		 /*找max in window*/
		     		 if(sensor[s].va > max &&  sensor[s].va >= glo.WALK_THD)
		     		 {   
		     			 max = sensor[s].va;
		     			 max_num = s;
		     			 max_t = sensor[s].time;
		     			 max1=(float)max;
		     		 }
		     		 i++;
		     		 
		     	 }
		     	 

		     	 /*開始判斷是否為一步*/
		     	  
		     	 if(((max_num-s_start+glo.MG_size)%glo.MG_size<glo.SLIDING_WIN)&&((max_num-s_start+glo.MG_size)%glo.MG_size>glo.SLIDING_WIN/6)){
		     		 min1=max;
		     		 min2=max;
		     		 i=0;
		     		
		     		 /*找出兩個min*/     //search min in sliding window
		     		 for(i=0;i<glo.SLIDING_WIN/2;i++){      
		     			 s = (s_start + i) % glo.MG_size;
		     			 if(sensor[s].va < min1)
		     			 {
		     				 min1 = sensor[s].va;
		     				 min1_num = s;
		     				 min1_t = sensor[s].time;
		     				 amin1= (float)min1;
		     			 }
		     		 }
		     		 for(i=glo.SLIDING_WIN/2;i<glo.SLIDING_WIN;i++){
		     			 s = (s_start + i) % glo.MG_size;
		     			 if(sensor[s].va < min2)
		     			 {
		     				 min2 = sensor[s].va;
		     				 min2_num = s;
		     				 min2_t = sensor[s].time;
		     				 amin2 = (float) min2;
		     			 }                  
		     		 }
		     		//tv_sl.setText(max+"\n"+min1+"\n"+min2);
		     		 dmg.distance=0;
		     		 double speed=0;
		     		//tv_sc.setText(max_num+"\n"+min1_num+"\n"+min2_num);
		     		 //算ha_sum
		     		 int step_itvl = (min2_num-min1_num+glo.MG_size)%glo.MG_size;
		     		 //calculate intensity of horizontal acceleration
		              for(i=0;i<step_itvl;i++)
		              {
		             	 ha_sum+=sensor[(min1_num+i)%glo.MG_size].ha;//ha
		             	 speed+=sensor[(min1_num+i)%glo.MG_size].ha;//speed 變化
		             	 dmg.distance+=speed*0.05*glo.G0_average;
		              }
		              ha_sum/=glo.SAMPLE_RATE;
		     
		              
		              //開始就各條件式來判斷
		              if(max_num != -1 && min1_num != -1 && min2_num != -1){
		             	 state++;
		             	 if((min1!=max) && (min2!=max))
		             	 {
		                    //compute parameters
		             		 s_amp1=max-min1;
		             		 s_amp2=max-min2;
		             		 s_amp=s_amp1+s_amp2;
		             		 s_amp_ratio=s_amp1/s_amp2;
		             		 s_time=min2_t-min1_t;
		             		 state++;
		             		 //tv_sc.setText("th3");
		             		//tv_sl.setText(max_t+"\n"+min1+"\n"+min2);
		             		 //check parameters(時間)
		             		 if((s_time >=glo.TIME_L_THD)&& (s_time <=glo.TIME_U_THD)){
		             			//tv_sc.setText("th3");
		             			 //(max 和 min1間的sample數)
		             			 if((s_amp1 <= glo.AMP1_U_THD)&& (s_amp1 >= glo.AMP1_L_THD)){ 
		             				 //(max 和 min2間的sample數)(兩sample數的比例)
		             				 if((s_amp2 <= glo.AMP2_U_THD)&&(s_amp2 >= glo.AMP2_L_THD)&&(s_amp_ratio<glo.AMP_Ratio_U_THD)
		             						 &&(s_amp_ratio>(glo.AMP_Ratio_L_THD))){
		             					 //(ha_sum)
		             					 if((ha_sum<=glo.ha_U_THD) && (ha_sum>=glo.ha_L_THD)){ 
		             						 dmg.step_count++;//累加一步
		             						 dmg.Sliding_start=(max_num+glo.SLIDING_DIST)%glo.MG_size;//
		             						 tv_sc.setText(""+dmg.step_count);
		             						 tv_sl.setText(""+dmg.distance);
		             						
		                                  }  
		                              }
		                          }
		                     }          
		             	 }
		               }
		            }
		     	  dmg.Sliding_start = (glo.SLIDING_DIST + dmg.Sliding_start) % glo.MG_size;  //window slides 
		        }
		     
		  	}
    
}