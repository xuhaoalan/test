package com.example.camerasendserver;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.widget.Button;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.graphics.PixelFormat.RGBA_8888;


public class MainActivity extends Activity {
    MyHandler handler;
    //ClientThread clientThread;
    ByteArrayOutputStream outstream;

    Button start;
    Button stop;
    SurfaceView surfaceView;
    SurfaceHolder  sfh;
    Camera camera;
    boolean isPreview = false;        //是否在浏览中
    int screenWidth=300, screenHeight=300;
    private ServerSocket serverSocket;
    private Socket mSocket=null;
    public InputStream ins;
    private BufferedReader in;
    private PrintWriter out;
    private long sendTime=0;
    static String TAG = "senderServer";

    static final int SCREEN_CAPTURE_PERMISSION = 101;
    private DisplayMetrics metrics;
    private int width, height;
    private MediaProjectionManager projectionManager;
    public static MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader = null;

    static Intent mResultIntent;
    static int mResultCode;
    public static final int REQUEST_MEDIA_PROJECTION = 1002;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置全屏
         requestWindowFeature(Window.FEATURE_NO_TITLE);
         getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        
        handler = new MyHandler();
        //clientThread = new ClientThread(handler);
        //new Thread(clientThread).start();
        
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenWidth = dm.widthPixels;// 获取屏幕分辨率宽度
        screenHeight = dm.heightPixels;

        //checkScreenShotPermission();//add
        checkScreenRecordPermission();

        
        start = (Button)findViewById(R.id.start);
        stop = (Button)findViewById(R.id.stop);
        surfaceView = (SurfaceView)findViewById(R.id.surfaceView);
        sfh = surfaceView.getHolder(); 
        sfh.setFixedSize(screenWidth, screenHeight/4*3);
         
        sfh.addCallback(new Callback(){

            @Override
            public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2,
                    int arg3) {
                // TODO Auto-generated method stub
                
            }

            @Override
            public void surfaceCreated(SurfaceHolder arg0) {
                // TODO Auto-generated method stub
                //initCamera();

                //delayHandler.postDelayed(delayRunnable, 100);//zhaokun
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder arg0) {
                if (camera != null) {
                    if (isPreview)
                        camera.stopPreview();
                    camera.release();
                    camera = null;
                }
                
            }
            
        });

        RecordService.mSocket = mSocket;
        Intent intent = new Intent(getApplicationContext(), RecordService.class);
        startService(intent);
        
        //开启连接服务
        start.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View arg0) {
                
                //start.setEnabled(false);
                //delayHandler.postDelayed(delayRunnable, 100);//zhaokun
                //screenShot();

                Intent cmd = new Intent("com.sim.screenrecorder.start");
                //cmd.putExtra("command","start");
                sendBroadcast(cmd);
            }
            
        });

        stop.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View arg0) {

                //start.setEnabled(false);
                //delayHandler.postDelayed(delayRunnable, 100);//zhaokun
                //screenShot();

                Intent cmd = new Intent("com.sim.screenrecorder.stop");
                //cmd.putExtra("command","stop");
                sendBroadcast(cmd);
                //RecordService.mSocket = mSocket;
                //Intent intent = new Intent(getApplicationContext(), RecordService.class);
                //stopService(intent);

            }

        });

        try {
			serverSocket = new ServerSocket(5000);
			Log.d(TAG,"serverSocket start start start start serverSocket");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        Log.d(TAG,"ServerThread().start()");
        new ServerThread().start();
	}

	private boolean mRemoteConnect = false;
	
    private class ServerThread extends Thread{

        @Override
        public void run() {
            // TODO Auto-generated method stub
            super.run();
            {//while (true) {
                try {
                    Log.d("zzkk check","1...serverSocket run run..start accept()");
                    mRemoteConnect = false;
                    mSocket = serverSocket.accept();//阻塞等待处理...
                    //ClientThread.setSocket(mSocket);

                    //RecordService.mSocket = mSocket;
                    //Intent intent = new Intent(getApplicationContext(), RecordService.class);
                    //startService(intent);

                    String remoteIP = mSocket.getInetAddress().getHostAddress();
                    int remotePort = mSocket.getLocalPort();

                    RecordService.mSocket = mSocket;
                    RecordService.mRemoteIP = remoteIP;
                    Intent cmd = new Intent("com.sim.screenrecorder.start");
                    sendBroadcast(cmd);
                    ////cmd.putExtra("command","start");

                    Log.d("zzkk check","2...start thread run run run remoteIP="+remoteIP+"; remotePort="+remotePort);
                    Log.d("zzkk","2...sevied mSocket="+mSocket);

                    //in = new BufferedReader(new InputStreamReader(
                            //mSocket.getInputStream()));
                    //DataInputStream dins = new DataInputStream(
                            //mSocket.getInputStream());
                    //DataOutputStream dous = new DataOutputStream(
                            //mSocket.getOutputStream());
                    //out = new PrintWriter(mSocket.getOutputStream(), false);

                    mRemoteConnect = true;

                    //screenShot();//set up outStream

                    //.;
                    //Intent cmd = new Intent("com.sim.screenrecorder.MyBroadcastReceiver");
                    //cmd.putExtra("command","start");
                    //sendBroadcast(cmd);

                    /*// 获得 client 端发送的数据
                    String tmp = dins.readLine();
                    //String content = new String(tmp.getBytes("utf-8"));
                    Log.d("zzkk check","3...Client message is: " + tmp);

                    // 向 client 端发送响应数据
                    //out.println("Your message has been received successfully！.\r\n\r\n");
                    dous.write("Ok,your message has been received successfully！.\r\n\r\n".getBytes());

                    dous.flush();
                    dous.close();
                    dins.close();
                    if(!mSocket.isClosed()){
                        mSocket.close();
                    }
                    */
                    //int i=0;
                    //int len;
                   // while(true) {
                      //  i++;
                        //byte[] readdata = new byte[1024];
                        //int len = dins.read(readdata);
                        //String tmp1 = in.readLine();
                        /*String str = i+" Hello world!!66";
                        byte[] data = str.getBytes();
                        len = (str).length();
                        Log.d("zzkk","4...dous.writeInt(len)="+len);
                        byte[] byteLen = intToBytes(len);
                        for(int j=0 ;j<byteLen.length;j++)
                            Log.d("zzkk","4...dous.write(),byte byteLen["+j+"]="+byteLen[j]);
                        dous.write(byteLen);
                        Log.d("zzkk","4...dous.write()="+str);
                        for(int j=0 ;j<len;j++)
                            Log.d("zzkk","4...dous.write(),byte data["+j+"]="+data[j]);
                        dous.write(data);*/

                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            //e.printStackTrace();
                        }
                   // }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    Log.d("zzkk","5...server mSocket,exception="+e);
                }

                //Intent cmd = new Intent("com.sim.screenrecorder.stop");
                //cmd.putExtra("command","stop");
                //sendBroadcast(cmd);

                /*RecordService.mSocket = null;
                Intent intent = new Intent(getApplicationContext(), RecordService.class);
                stopService(intent);*/

            }

        }

    }

    public static byte[] intToBytes( int value )
    {
        byte[] src = new byte[4];
        src[3] =  (byte) ((value>>24) & 0xFF);
        src[2] =  (byte) ((value>>16) & 0xFF);
        src[1] =  (byte) ((value>>8) & 0xFF);
        src[0] =  (byte) (value & 0xFF);
        return src;
    }
    
    class MyHandler extends Handler{
        @Override
        public void handleMessage(Message msg){
            if(msg.what==0x222){
                //返回信息显示代码
                Log.d(TAG,"myhandler 0x222 start.. screenShot()");
                //screenShot();

            }
        }
    }
    
    @Override
    protected void onDestroy() {
    	// TODO Auto-generated method stub
    	super.onDestroy();
        if(virtualDisplay!=null)
            virtualDisplay.release();

        RecordService.mSocket = mSocket;
        Intent intent = new Intent(getApplicationContext(), RecordService.class);
        stopService(intent);
    }

    Handler delayHandler=new Handler();
    Runnable delayRunnable=new Runnable() {
        public void run() {
            //delayHandler.removeCallbacks(delayRunnable);
            //delayHandler.postDelayed(delayRunnable, 3000);
            Log.d(TAG,"Delay handler zzkk start.. screenShot()");
            //mMediaRecorder.getbuf();//add by zhaokun
           // screenShot();
            //delayHandler.postDelayed(delayRunnable, 50);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case SCREEN_CAPTURE_PERMISSION:
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                //screenShotPrepare();
                break;
        }

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                Log.e(TAG,"get capture permission success!");
                mResultCode = resultCode;
                mResultIntent = data;

                //mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                //startScreenPushIntent();

            }
        }
    }

    /**
     * 申请截屏相关权限
     * */
    protected void checkScreenShotPermission() {
        //FileUtil.requestWritePermission(this);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_PERMISSION);
    }

    /**
     * 申请录屏相关权限
     * */
    protected void checkScreenRecordPermission() {
        //FileUtil.requestWritePermission(this);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    /**
     * 初始化截屏相关设置
     * MediaProjectionManager -> MediaProjection -> VirtualDisplay
     * */
    protected void screenShotPrepare() {
        if(mediaProjection==null)
            return;

        Display display = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        Point point = new Point();
        display.getRealSize(point);
        width = point.x;
        height = point.y;

        //将屏幕画面放入ImageReader关联的Surface中
        imageReader = ImageReader.newInstance(width, height, RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenShotDemo",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null/*Callbacks*/, null/*Handler*/);
    }

    /**
     * 进行截屏
     * */
    /*protected boolean screenShot()
    {
        Image image = imageReader.acquireLatestImage();  //获取缓冲区中的图像，关键代码
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //button.setVisibility(View.VISIBLE);
            }
        });
        Log.d("zzkk","image="+image+",width="+width+",height="+height);
        if(image==null) {
            //try{
                //Thread.sleep(100);
              //  Log.d("zzkk","sleep---,100");
            //}catch(Exception e){
                //e.printStackTrace();
            //}
            delayHandler.postDelayed(delayRunnable, 50);

            //Message msg = clientThread.revHandler.obtainMessage();
            //msg.what=0x111;
            //msg.obj=null;
            //clientThread.revHandler.sendMessage(msg);
            return false;
        }

        //Image -> Bitmap
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int rowStride = planes[0].getRowStride();  //Image中的行宽，大于Bitmap所设的真实行宽
        byte[] oldBuffer = new byte[rowStride*height];
        buffer.get(oldBuffer);
        byte[] newBuffer = new byte[width*4*height];

        Bitmap bitmap = Bitmap.createBitmap(metrics, width, height, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < height; ++i) {
            System.arraycopy(oldBuffer,i*rowStride,newBuffer,i*width*4,width*4);  //跳过多余的行宽部分，关键代码
        }
        Log.d("zzkk","++++copyPixelsFromBuffer,length="+newBuffer.length);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(newBuffer));  //用byte数组填充bitmap，关键代码
        Log.d("zzkk","----copyPixelsFromBuffer");
        image.close();

        //saveBitmapFile(bitmap);
        if(bitmap != null){
            //Message msg = clientThread.revHandler.obtainMessage();
            //msg.what=0x111;
            //msg.obj=bitmap;
            //clientThread.revHandler.sendMessage(msg);
            //sendTime = System.currentTimeMillis();
        }
        Log.d("zzkk","----save jpg");
        return true;//FileUtil.saveImage(""+width+"×"+height+"-ScreenShot.png",bitmap);
    }*/
}
