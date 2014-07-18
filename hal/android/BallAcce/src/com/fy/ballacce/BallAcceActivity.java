package com.fy.ballacce;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.Window;

public class BallAcceActivity extends Activity {
	/** Called when the activity is first created. */

	MyView mAnimView = null;
	private Socket socket = null;
	private DataOutputStream dos = null;
	private String mIP;// = "192.168.52.54";
	private int mPort; //= 5885;
	private InetSocketAddress addr; //����socket
	//private SensorsData mData;
	private List<SensorsData> list = new ArrayList<SensorsData>();
	private final int LIST_LENGTH = 16;
	private SendData mThread = null;
	private final String KEY_IP = "IP";
	private final String KEY_PORT = "PORT";
	private final String TAG = "DiscoveryActivity";
	
//	private static HandlerThread sWorkThread = new HandlerThread("worker");
//	static {
//		sWorkThread.start();
//	}
//	private static Handler sWorker = new Handler(sWorkThread.getLooper());
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle bundle = this.getIntent().getExtras();
		mIP = bundle.getString(KEY_IP);
		mPort = bundle.getInt(KEY_PORT);
		
		Log.v(TAG, "rec ip : " + mIP);
		Log.v(TAG, "rec port : " + mPort);
		// ȫ����ʾ����,��Щ����������setContentView()֮ǰ��ʾ
		requestWindowFeature(Window.FEATURE_NO_TITLE);
//		requestWindowFeature(WindowManager.LayoutParams.FLAG_FULLSCREEN);
      
		// ǿ�ƺ���
//		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		addr = new InetSocketAddress(mIP, mPort);
		mThread = new SendData(addr);
		mThread.start();
		// ��ʾ�Զ����View
		mAnimView = new MyView(this);
		//mData = new SensorsData();
	
		setContentView(mAnimView);
	}
	
	class MyRunnable implements Runnable {
		
		private SensorsData mData;
		private Socket mSoc = null;
		
		public MyRunnable(Socket soc, SensorsData data) {
			mSoc = soc;
			mData = data;
		}

		@Override
		public void run() {
			//put data
			
			//socket
			Log.e("//////", "mData = " + mData.timestamp);
			try {
				if(mSoc == null){
					Log.v("//////", "soc = null");
					mSoc = new Socket();
					//socket = new Socket(IP, port);
					mSoc.connect(addr);
					if(dos == null){
						dos = new DataOutputStream(mSoc.getOutputStream());
					}
					
				}
				Log.v("//////", "dos.write data");
				
				dos.write(mData.byData);	
				if(mSoc == null){
					Log.v("SendData", "///soc is null");
				}
				//Send();
				dos.close();
				mSoc.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.v("SendData", "soc isnot connect !!");
				Log.v("BallAcce1", e.getMessage());
				e.printStackTrace();
			}
			
		}
	}
	
	class SensorsData {
		public int version;
		public int sensor;
		public int type;
		public int reserved0;
		public long timestamp;
		public float [] data;
		public int [] reserved1;
		
		public final int DATA_LENGTH = 16;
		public final int RESERVED1_LENGHT = 4;
		
		private int index;
		public byte [] byData;
		
		SensorsData(){
			data = new float[16];
			reserved1 = new int[4];
			sensor = 1;
			type = 0;
			reserved0 = 0;
			timestamp = 0;
			index = -1;
			version = 4 * Integer.SIZE + Long.SIZE + 16 * Float.SIZE + 4 * Integer.SIZE;
			version /= 8;
			
			byData = new byte[version];
			//index = 0;
		}
		
		public void genSendData() {
			intToByte(version);
			intToByte(sensor);
			intToByte(type);
			intToByte(reserved0);
			
			longToByte(timestamp);
			
			int i = 0;
			for(i = 0; i < DATA_LENGTH; ++i){
				floatToByte(data[i]);
			}
			
			for(i = 0; i < RESERVED1_LENGHT; ++i){
				intToByte(reserved1[i]);
			}
		}
		
		public void intToByte(int n){
			int i = 0;
			if(index >= version - 1){
				//Log.v("test", "intToByte" + version + index);
				return ;
			}
			for(; i < 4; i++){
				byData[++index] = (byte)(n >> (i*8));
			}
		}
		
		public void floatToByte(float x){
			int n = Float.floatToIntBits(x);
			int i = 0;
			if(index >= version - 1){
				return ;
			}
			for(; i < 4; i++){
				byData[++index] = (byte)(n >> (i*8));
			}
		}
		
		public void longToByte(long n){
			int i = 0;
			if(index >= version - 1){
				return ;
			}
			for(; i < 8; i++){
				byData[++index] = (byte)(n >> (i*8));
			}
		}
		
		public void setData(SensorEvent event){
			version = event.sensor.getVersion();
			type = event.sensor.getType();
			timestamp = event.timestamp;
			data[0] = event.values[0];
			data[1] = event.values[1];
			data[2] = event.values[2];
			data[3] = Float.intBitsToFloat(event.accuracy);
		}
	}
	
	class SendData extends Thread {
		
		private Lock lock = new ReentrantLock();
		private Condition cond = lock.newCondition();
		private Vector<SensorsData> queue = new Vector<SensorsData>();
		private InetSocketAddress addr = null;
		private Socket sock = null;
		private OutputStream stream = null;
		
		public SendData(InetSocketAddress addr)
		{
			this.addr = addr;
		}
		
		public void post(SensorsData d)
		{
			lock.lock();
			queue.add(d);
			cond.signal();
			lock.unlock();
		}
		
		public void exit()
		{
			lock.lock();
			this.interrupt();
			cond.signal();
			lock.unlock();
			try {
				this.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void run() {
			Log.v(TAG, "RUNNING");
			sock = new Socket();
			try {
				sock.connect(addr);
				Log.v(TAG, "RUNNING SOCKET");
			} catch (IOException e) {
				Log.v(TAG, "connect fail : " + e.getMessage());
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				stream = sock.getOutputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				Log.v(TAG, "connect fail : " + e.getMessage());
			}
			
			if((null == sock) || (stream == null)){
				Log.v(TAG, "mdns connect failed!");
			}
			
			lock.lock();
			
			while (!isInterrupted()) {
				
				if (queue.isEmpty()) {
					try {
						cond.await();
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					continue;
				}
					
				Log.v(TAG, "list size : " + queue.size());
				SensorsData first = queue.firstElement();
				queue.remove(0);
				
				try {
					stream.write(first.byData);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					Log.v(TAG, "mdns connect success!");
					e1.printStackTrace();
				}
			}
			Log.v(TAG, "mdns connect success++++!");
			lock.unlock();
		}
	}

	class MyView extends SurfaceView implements Callback, Runnable,
			SensorEventListener {

		/** ÿ50֡ˢ��һ����Ļ **/
		public static final int TIME_IN_FRAME = 50;
		/** ��Ϸ���� **/
		Paint mPaint = null;
		Paint mTextPaint = null;
		SurfaceHolder mSurfaceHolder = null;
		
		/** ������Ϸ����ѭ�� **/
		boolean mRunning = false;
		/** ��Ϸ���� **/
		Canvas mCanvas = null;
		/** ������Ϸѭ�� **/
		boolean mIsRunning = false;
		/** SensorManager������ **/
		private SensorManager mSensorMgr = null;
		Sensor mSensor = null;
		
		/** �ֻ���Ļ��� **/
		int mScreenWidth = 0;
		int mScreenHeight = 0;
		
		/** С����Դ�ļ�Խ������ **/
		private int mScreenBallWidth = 0;
		private int mScreenBallHeight = 0;
		
		/** ��Ϸ�����ļ� **/
		private Bitmap mbitmapBg;
		
		/** С����Դ�ļ� **/
		private Bitmap mbitmapBall;
		
		/** С�������λ�� **/
		private float mPosX = 200;
		private float mPosY = 0;
		
		/** ������ӦX�� Y�� Z�������ֵ **/
		private float mGX = 0;
		private float mGY = 0;
		private float mGZ = 0;
		
		public MyView(Context context) {
			super(context);
			// TODO Auto-generated constructor stub
			/** ���õ�ǰViewӵ�п��ƽ��� **/
			this.setFocusable(true);
			/** ���õ�ǰViewӵ�д����¼� **/
			this.setFocusableInTouchMode(true);
			/** �õ�SurfaceHolder���� **/
			mSurfaceHolder = this.getHolder();
			/** ��mSurfaceHolder��ӵ�Callback�ص������� **/
			mSurfaceHolder.addCallback(this);
			/** �������� **/
			mCanvas = new Canvas();
			/** �������߻��� **/
			mPaint = new Paint();
			mPaint.setColor(Color.WHITE);
			/** ����С����Դ **/
			mbitmapBall = BitmapFactory.decodeResource(this.getResources(),
					R.drawable.ball);
			/** ������Ϸ���� **/
			mbitmapBg = BitmapFactory.decodeResource(this.getResources(),
					R.drawable.bg);
			/** �õ�SensorManager���� **/
			mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
			mSensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			// ע��listener�������������Ǽ��ľ�ȷ��
			// SENSOR_DELAY_FASTEST ������ ��Ϊ̫����û��Ҫʹ��
			// SENSOR_DELAY_GAME ��Ϸ������ʹ��
			// SENSOR_DELAY_NORMAL �����ٶ�
			// SENSOR_DELAY_UI �������ٶ�	
			
			mSensorMgr.registerListener(this, mSensor,
					SensorManager.SENSOR_DELAY_GAME);
		}

		public void Draw() {
			/** ������Ϸ���� **/
			mCanvas.drawBitmap(mbitmapBg, 0, 0, mPaint);
			/** ����С�� **/
			mCanvas.drawBitmap(mbitmapBall, mPosX, mPosY, mPaint);
			/** X�� Y�� Z�������ֵ **/
			mCanvas.drawText("X������ֵ ��" + mGX, 0, 20, mPaint);
			mCanvas.drawText("Y������ֵ ��" + mGY, 0, 40, mPaint);
			mCanvas.drawText("Z������ֵ ��" + mGZ, 0, 60, mPaint);
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
		}
		@Override
		public void onSensorChanged(SensorEvent event) {
			SensorsData data = new SensorsData();
			//data.version = event.sensor.getVersion();
			data.type = event.sensor.getType();
			data.timestamp = event.timestamp;
			data.data[0] = event.values[0];
			data.data[1] = event.values[1];
			data.data[2] = event.values[2];
			data.data[3] = Float.intBitsToFloat(event.accuracy);
			//Log.v("/////", "onSensorChanged");
			data.genSendData();
			mThread.post(data);
			//sWorker.post(new MyRunnable(data));
			//mThread.new AddThread(data).start(); 
////			if(socket == null){
//				Log.v("SendData", "socket is null");
//			}
			// TODO Auto-generated method stub
			mGX = event.values[0];
			mGY = event.values[1];
			mGZ = event.values[2];
			// �������2��Ϊ����С���ƶ��ĸ���
			mPosX -= mGX * 2;
			mPosY += mGY * 2;
			// ���С���Ƿ񳬳��߽�
			if (mPosX < 0) {
				mPosX = 0;
			} else if (mPosX > mScreenBallWidth) {
				mPosX = mScreenBallWidth;
			}
			if (mPosY < 0) {
				mPosY = 0;
			} else if (mPosY > mScreenBallHeight) {
				mPosY = mScreenBallHeight;
			}
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			while (mIsRunning) {
				/** ȡ�ø�����Ϸ֮ǰ��ʱ�� **/
				long startTime = System.currentTimeMillis();
				/** ����������̰߳�ȫ�� **/
				synchronized (mSurfaceHolder) {
					/** �õ���ǰ���� Ȼ������ **/
					mCanvas = mSurfaceHolder.lockCanvas();
					Draw();
					/** ���ƽ����������ʾ����Ļ�� **/
					mSurfaceHolder.unlockCanvasAndPost(mCanvas);
				}
				/** ȡ�ø�����Ϸ������ʱ�� **/
				long endTime = System.currentTimeMillis();
				/** �������Ϸһ�θ��µĺ����� **/
				int diffTime = (int) (endTime - startTime);
				/** ȷ��ÿ�θ���ʱ��Ϊ50֡ **/
				while (diffTime <= TIME_IN_FRAME) {
					diffTime = (int) (System.currentTimeMillis() - startTime);
					/** �̵߳ȴ� **/
					Thread.yield();
				}
			}
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			// TODO Auto-generated method stub
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			// TODO Auto-generated method stub
			/** ��ʼ��Ϸ��ѭ���߳� **/
			mIsRunning = true;
			new Thread(this).start();
			/** �õ���ǰ��Ļ��� **/
			mScreenWidth = this.getWidth();
			mScreenHeight = this.getHeight();
			/** �õ�С��Խ������ **/
			mScreenBallWidth = mScreenWidth - mbitmapBall.getWidth();
			mScreenBallHeight = mScreenHeight - mbitmapBall.getHeight();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			// TODO Auto-generated method stub
			mIsRunning = false;
		}

	}
}