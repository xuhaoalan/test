package com.example.camerasendserver;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;


public class RecordService extends Service {//implements EasyIPCamera.IPCameraCallBack{

    private static final String TAG = "RecordService";
    private String mVideoPath;
    private MediaProjectionManager mMpmngr;
    private MediaProjection mMpj;
    private VirtualDisplay mVirtualDisplay;
    private int windowWidth;
    private int windowHeight;
    private int screenDensity;

    private Surface mSurface;
    private MediaCodec mMediaCodec;

    private WindowManager wm;

    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    //static EasyIPCamera mEasyIPCamera;
    private Thread mPushThread;
    private byte[] mPpsSps;
//    private static AudioStream mAudioStream;

    private int mChannelId = 1;
    private int mChannelState = 0;
    private int mFrameRate = 20;
    private int mBitRate;
    private Context mApplicationContext;
    private boolean codecAvailable = false;
    private byte[] mVps = new byte[255];
    private byte[] mSps = new byte[255];
    private byte[] mPps = new byte[128];
    private byte[] mMei = new byte[128];
    private byte[] mH264Buffer;
    private long timeStamp = System.currentTimeMillis();
    private boolean mIsRunning = false;
    private boolean mStartingService = false;

    public static Socket mSocket=null;
    public static String mRemoteIP;
    MyBroadcastReceiver mReceiver;
    //BufferedOutputStream mSocketBos;
    public static DataOutputStream mSocketBos = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mMpmngr = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
//        mAudioStream = new AudioStream(mEasyIPCamera);
        mApplicationContext = getApplicationContext();
        codecAvailable = false;
        createEnvironment();

        mReceiver = new MyBroadcastReceiver();
        IntentFilter filter = new IntentFilter("com.sim.screenrecorder.start");
        filter.addAction("com.sim.screenrecorder.stop");
        registerReceiver(mReceiver,filter);
    }

    private void createEnvironment() {
        mVideoPath = Environment.getExternalStorageDirectory().getPath() + "/";
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        windowWidth = 720;//720;//wm.getDefaultDisplay().getWidth();
        windowHeight = 1280;//1280;//wm.getDefaultDisplay().getHeight();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        screenDensity = displayMetrics.densityDpi;

//        while (windowWidth > 1080){
//            windowWidth /= 2;
//            windowHeight /=2;
//        }

        Log.d(TAG, String.format("kim createEnvironment Size=%dx%d", windowWidth, windowHeight));

        //EncoderDebugger debugger = EncoderDebugger.debug(mApplicationContext, windowWidth, windowHeight, mFrameRate);
        //mSps = Base64.decode(debugger.getB64SPS(), Base64.NO_WRAP);
        //mPps = Base64.decode(debugger.getB64PPS(), Base64.NO_WRAP);
        mH264Buffer = new byte[(int) (windowWidth*windowHeight*1.5)];
    }

    /**
     * 初始化编码器
     */
    //private void initMediaCodec() {
        //mFrameRate = 30;//20;
        //mBitRate = 3000000;//1200000;
        //EncoderDebugger debugger = EncoderDebugger.debug(mApplicationContext, windowWidth, windowHeight, mFrameRate);
        //mSps = Base64.decode(debugger.getB64SPS(), Base64.NO_WRAP);
        //mPps = Base64.decode(debugger.getB64PPS(), Base64.NO_WRAP);
    //}

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void  startMediaCodec() {
        Log.i("zzkk check", "137  startMediaCodec");
        mFrameRate = 30;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", windowWidth, windowHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 3000000);//mBitRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, mFrameRate);
        mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / mFrameRate);

        try {
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mMediaCodec.createInputSurface();
        codecAvailable = true;
        mMediaCodec.start();
        startPush();
    }

    /**
     * 停止编码并释放编码资源占用
     */
    private void stopMediaCodec() {
        if (mMediaCodec != null) {
            codecAvailable = false;
//            mMediaCodec.stop();
//            mMediaCodec.release();
//            mMediaCodec = null;
        }
        stopPush();
    }

    private MyList mMyList;

    public class MyList {
        private LinkedList<Object> linkedList;

        public MyList() {
            linkedList = new LinkedList<Object>();
        }

        public void put(Object object) {
            linkedList.addLast(object);
        }

        public Object get() {
            Object object = null;
            if (linkedList.size() != 0) {
                object = linkedList.getFirst();
                linkedList.removeFirst();
            }
            return object;
        }

        public boolean isEmpty() {
            if (linkedList.size() != 0) {
                return false;
            } else {
                return true;
            }
        }

    }
    private long mLastsendTime ;//= System.currentTimeMillis();
    private boolean mIsStartSendListData = false;
    int  mDelayTime = 50;
    private void sendMyListData(){
         //= 60;//ms
        boolean isNull = mMyList.isEmpty();
        if(isNull){
            mIsStartSendListData = false;
            Log.i("zzkk check", "sendMyListData Send Data >>>>>>>>>>>  0 null return");
            //delayHandler.postDelayed(delayRunnable, mDelayTime);
            return;
        }
        mIsStartSendListData = true;

        //if(System.currentTimeMillis()-mLastsendTime <50){
            //return;
        //}

        try {

            /*Log.i("zzkk check", "Send Data >>>>>>>>>>>  1,mRemoteIP="+mRemoteIP);
            Socket skt = new Socket(mRemoteIP, 5001);
            Log.i("zzkk check", "Send Data >>>>>>>>>>> 2");
            //mSocketInputStream = new DataInputStream(skt.getInputStream());
            DataOutputStream socketDos = new DataOutputStream(skt.getOutputStream());
            DataInputStream socketDis = new DataInputStream(skt.getInputStream());
            Log.i("zzkk check", "Send Data >>>>>>>>>>> 3");*/
            DataOutputStream socketDos = mSocketDos;
            DataInputStream socketDis = mSocketDis;


            byte[] data = (byte[])mMyList.get();
            /*ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
            int n=0;
            int leng=0;
            while(!(mMyList.isEmpty())) {
                byte[] myListData = (byte[])mMyList.get();
                if(myListData!=null) {
                    leng += myListData.length;
                    swapStream.write(myListData, 0, myListData.length);
                    n++;

                    if(leng>1024) break;
                }
            }
            byte[] data = swapStream.toByteArray(); //in_b为转换之后的结果
            */

            int len = (data==null?0:data.length);
            byte[] byteLen = intToBytes(len);
            //for(int j=0 ;j<byteLen.length;j++)
            //Log.d("zzkk","sendMyListData screen service...dous.write(), byteLen["+j+"]="+byteLen[j]);

            socketDos.write(byteLen);

            //Log.d("zzkk", "screen service...dous.write(),data len=" +len+",read MyListData num="+n);

            for(int j=3 ;j<len;j++) {
                Log.d("zzkk", "screen service...dous.write(),data[" + j + "]=" + data[j]);
                if(j==5) break;
            }
            for(int j=len-3 ;j<len;j++) {
                if(j<0) j=0;
                Log.d("zzkk", "screen service...dous.write(),data[" + j + "]=" + data[j]);
            }

            //socketDos.write(data, 0, len);
            int sendLen=0;
            int i=0;
            while(sendLen<len) {
                if((len-sendLen)>1024) {
                    socketDos.write(data, i * 1024, 1024);
                    sendLen += 1024;
                }else{
                    socketDos.write(data, i * 1024, (len-sendLen));
                    sendLen += (len-sendLen);
                }

                i++;
                /*try{
                    Thread.sleep(60);
                    Log.d("zzkk","sleep---,30");
                }catch(Exception e){
                    e.printStackTrace();
                }*/
            }

            String tmp = socketDis.readLine();
            //String content = new String(tmp.getBytes("utf-8"));
            Log.d("zzkk check","dous.write(),data, Rev message is: " + tmp);

            //socketDis.close();
            //socketDos.flush();
            //socketDos.close();
            //skt.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e1) {
            e1.printStackTrace();
            Log.d("zzkk check","dous.write(),data get socket error is: " + e1);
        }

        //Log.d(TAG,"Start 288 Delay handler zzkk runnable.. postDelayed()");
        //delayHandler.postDelayed(delayRunnable, 30);
        //mLastsendTime = System.currentTimeMillis();
    }

    Handler delayHandler=new Handler();
    Runnable delayRunnable=new Runnable() {
        public void run() {
            //delayHandler.removeCallbacks(delayRunnable);
            //delayHandler.postDelayed(delayRunnable, 3000);
            Log.d(TAG,"Delay handler zzkk runnable.. sendMyListData()");
            //mMediaRecorder.getbuf();//add by zhaokun
            //screenShot();
            //delayHandler.postDelayed(delayRunnable, 50);
            sendMyListData();
        }
    };

    /*private void sendShortData(byte[] data){
        int len = (data==null?0:data.length);
        try {
            byte[] byteLen = intToBytes(len);
            for(int j=0 ;j<byteLen.length;j++)
                Log.d("zzkk","screen service...dous.write(), byteLen["+j+"]="+byteLen[j]);

            Log.i("zzkk check", "Send Data >>>>>>>>>>>  1");
            Socket skt = new Socket(mRemoteIP, 5001);
            Log.i("zzkk check", "Send Data >>>>>>>>>>> 2");
            //mSocketInputStream = new DataInputStream(skt.getInputStream());
            DataOutputStream socketDos = new DataOutputStream(skt.getOutputStream());
            Log.i("zzkk check", "Send Data >>>>>>>>>>> 3");

            socketDos.write(byteLen);

            Log.d("zzkk", "screen service...dous.write(),data len=" +len);

            for(int j=2 ;j<len;j++) {
                Log.d("zzkk", "screen service...dous.write(),data[" + j + "]=" + data[j]);
                if(j==5) break;
            }
            for(int j=len-3 ;j<len;j++) {
                if(j<0) j=0;
                Log.d("zzkk", "screen service...dous.write(),data[" + j + "]=" + data[j]);
            }

            socketDos.write(data, 0, len);

            socketDos.flush();
            socketDos.close();
            skt.close();


            //ByteArrayOutputStream outputstream = new ByteArrayOutputStream();
            //ByteArrayInputStream inputstream = new ByteArrayInputStream(outputstream.toByteArray());

            *//*int sendLen=0;
            int i=0;
            while(sendLen<len) {
                if((len-sendLen)>1024) {
                    mSocketBos.write(data, i * 1024, 1024);
                    sendLen += 1024;
                }else{
                    mSocketBos.write(data, i * 1024, len-sendLen);
                    sendLen += (len-sendLen);
                }

                //mSocketBos.write(data,0,1024); //1
                //mSocketBos.write(data,1024,1024); //2
                //mSocketBos.write(data,1024*2,1024); //3
                //mSocketBos.write(data,1024*3,512); //4

                i++;
            }*/
        /*} catch (Exception e) {
            e.printStackTrace();
        }

    }*/

    DataOutputStream mSocketDos;//= new DataOutputStream(mSocket.getOutputStream());
    DataInputStream mSocketDis;// = new DataInputStream(mSocket.getInputStream());
    private void startPush() {
        /*try {
        Log.i("zzkk check", "Send Data >>>>>>>>>>>  1,mRemoteIP="+mRemoteIP);
        //Socket skt = null;
        mSocket = new Socket(mRemoteIP, 5001);

        Log.i("zzkk check", "Send Data >>>>>>>>>>> 2");
        //mSocketInputStream = new DataInputStream(skt.getInputStream());
        mSocketDos = new DataOutputStream(mSocket.getOutputStream());
        mSocketDis = new DataInputStream(mSocket.getInputStream());
        Log.i("zzkk check", "Send Data >>>>>>>>>>> 3");
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        File file=new File("/sdcard/"+"test.h264");//将要保存图片的路径
        if(file.exists()){
            file.delete();
        }
        mMyList = new MyList();
        try {
            mBos = new BufferedOutputStream(new FileOutputStream(file));
            //bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            //mBos.write(frameByte);
            //bos.flush();
            //bos.close();

            if(mSocket!=null){
                //mSocketBos = new DataOutputStream(mSocket.getOutputStream());
                mSocketDos = new DataOutputStream(mSocket.getOutputStream());
                mSocketDis = new DataInputStream(mSocket.getInputStream());
            }
            Log.d("zzkk","startPush mSocketDos="+mSocketDos+",mSocket="+mSocket);

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mPushThread != null) return;
        mPushThread = new Thread(){
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
//                mAudioStream.startRecord();
                while (mPushThread != null && codecAvailable) {
                    try {
                        int index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 100000);//10000);

                        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

                        /*if ((EasyIPCamera.ChannelState.EASY_IPCAMERA_STATE_REQUEST_MEDIA_INFO != mChannelState) &&
                                (EasyIPCamera.ChannelState.EASY_IPCAMERA_STATE_REQUEST_PLAY_STREAM != mChannelState)) {
                            Log.e(TAG, "RecordService startPush state error! mChannelState=" + mChannelState);
                            continue;
                        }*/

                        if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
                            try {
                                // wait 10ms
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                            }
                        } else if (index >= 0) {//有效输出

                            {
                                ByteBuffer outputBuffer = outputBuffers[index];
                                byte[] outData = new byte[mBufferInfo.size];
                                outputBuffer.get(outData);
                                if(mBufferInfo.flags == 2){
                                    mConfigbyte = new byte[mBufferInfo.size];
                                    mConfigbyte = outData;
                                }else if(mBufferInfo.flags == 1){
                                    byte[] keyframe = new byte[mBufferInfo.size + mConfigbyte.length];
                                    System.arraycopy(mConfigbyte, 0, keyframe, 0, mConfigbyte.length);
                                    System.arraycopy(outData, 0, keyframe, mConfigbyte.length, outData.length);
                                    Log.d("zzkk","222 dous.write(),data saveFileToH264, keyframe.length="+keyframe.length+",mSocketBos="+mSocketBos+",index="+index);
                                    //if(mSocketBos!=null) {
                                        //mSocketBos.writeInt(keyframe.length);
                                        ////mSocketBos.write(intToBytes(keyframe.length));
                                        //mSocketBos.write(keyframe, 0, keyframe.length);
                                        //sendShortData(keyframe);
                                    //}
                                    //sendData(keyframe, keyframe.length);
                                    //boolean isSend = mMyList.isEmpty();
                                    mMyList.put(keyframe);

                                    //mBos.write(keyframe, 0, keyframe.length);
                                    //sendShortData(keyframe);

                                    //if(!mIsStartSendListData) {//isSend)
                                        //mIsStartSendListData = true;
                                        //delayHandler.postDelayed(delayRunnable, 0);
                                        sendMyListData();
                                    //}

                                }else{
                                    Log.d("zzkk","225 dous.write(),data saveFileToH264, outData.lengt="+outData.length+",mSocketBos="+mSocketBos+",index="+index);
                                    if(mSocketBos!=null) {
                                        //mSocketBos.writeInt(outData.length);
                                        ////mSocketBos.write(intToBytes(outData.length));
                                        //mSocketBos.write(outData, 0, outData.length);
                                    }
                                    //sendData(outData, outData.length);
                                    //boolean isSend = mMyList.isEmpty();
                                    mMyList.put(outData);

                                    //mBos.write(outData, 0, outData.length);
                                    //sendShortData(outData);

                                    //if(!mIsStartSendListData) {//isSend)
                                        //mIsStartSendListData = true;
                                        //delayHandler.postDelayed(delayRunnable, 0);
                                        sendMyListData();
                                    //}
                                }
                            }

                            /*ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(index);
                            //记录pps和sps
                            int type = outputBuffer.get(4) & 0x07;
                            if (type == 7 || type == 8) {
                                byte[] outData = new byte[mBufferInfo.size];
                                outputBuffer.get(outData);
                                mPpsSps = outData;
                            } else if (type == 5) {
                                outputBuffer.get(mH264Buffer, 0, mBufferInfo.size);
                                //saveFileToH264(mH264Buffer);
                                //Log.d("zzkk","215 saveFileToH264,mBufferInfo.size="+mBufferInfo.size);
                                //mEasyIPCamera.pushFrame(mChannelId, EasyIPCamera.FrameFlag.EASY_SDK_VIDEO_FRAME_FLAG, System.currentTimeMillis(), mH264Buffer, 0, mBufferInfo.size);
                            } else {
                                outputBuffer.get(mH264Buffer, 0, mBufferInfo.size);
                                if (System.currentTimeMillis() - timeStamp >= 3000) {
                                    timeStamp = System.currentTimeMillis();
                                    if (Build.VERSION.SDK_INT >= 23) {
                                        Bundle params = new Bundle();
                                        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                                        mMediaCodec.setParameters(params);
                                    }
                                }
                                //saveFileToH264(mH264Buffer);
                                //Log.d("zzkk","227 saveFileToH264,mPpsSps.length+mBufferInfo.size="+(mPpsSps.length+mBufferInfo.size));
                                //mEasyIPCamera.pushFrame(mChannelId, EasyIPCamera.FrameFlag.EASY_SDK_VIDEO_FRAME_FLAG, System.currentTimeMillis(), mH264Buffer, 0,mPpsSps.length+mBufferInfo.size);
                            }*/

                            mMediaCodec.releaseOutputBuffer(index, false);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                }

                if(!codecAvailable){
                    mMediaCodec.stop();
                    mMediaCodec.release();
                    mMediaCodec = null;
                }
            }
        };
        mPushThread.start();
        startVirtualDisplay();
    }

    public byte[] mConfigbyte;
    BufferedOutputStream mBos; //= new BufferedOutputStream(new FileOutputStream(file));
    public void saveFileToH264(byte[] frameByte){
        //File file=new File("/sdcard/"+"ScreenShot.h264");//将要保存图片的路径
        try {
            //BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            //bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            mBos.write(frameByte);
            //bos.flush();
            //bos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopPush(){
        mIsStartSendListData = false;

        Thread t = mPushThread;
        if (t != null){
            mPushThread = null;
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
//        mAudioStream.stop();
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }

        try {
            //BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            //bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            //bos.write(frameByte);
            mBos.flush();
            mBos.close();
            if(mSocketBos!=null) {
                mSocketBos.flush();
                mSocketBos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startVirtualDisplay() {
        if (mMpj == null) {
            mMpj = mMpmngr.getMediaProjection(MainActivity.mResultCode, MainActivity.mResultIntent);
            MainActivity.mResultCode = 0;
            MainActivity.mResultIntent = null;

        }
        //if(mMpj==null)
            //mMpj = MainActivity.mediaProjection;
        mVirtualDisplay = mMpj.createVirtualDisplay("record_screen", windowWidth, windowHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR| DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC| DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, mSurface, null, null);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void release() {
        Log.i(TAG, " release() ");
        if (mSurface != null){
            mSurface.release();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    /*@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mIsRunning = true;
        mStartingService = true;
        String strPort = EasyApplication.getEasyApplication().getPort();
        final String strId = EasyApplication.getEasyApplication().getId();
        if(strPort == null|| strPort.isEmpty() || strId == null || strId.isEmpty()) {
            mStartingService = false;
            return START_STICKY;
        }

        final int iport = Integer.parseInt(strPort);

        if(mEasyIPCamera == null) {
            mStartingService = false;
            return START_STICKY;
        }

        mChannelId = mEasyIPCamera.registerCallback(this);
//        mAudioStream.setChannelId(mChannelId);
//        mAudioStream.startRecord();

        new Thread(new Runnable() {
            @Override
            public void run() {
                int result = -1;
                while(mIsRunning && result < 0) {
                    result = mEasyIPCamera.startup(iport, EasyIPCamera.AuthType.AUTHENTICATION_TYPE_BASIC, "", "", "", 0, mChannelId, strId.getBytes());
                    if(result < 0){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.d(TAG, "kim startup result="+result);
                }

                //initMediaCodec();
                mStartingService = false;
            }
        }).start();

        int ret = super.onStartCommand(intent, flags, startId);
        return ret;
    }*/

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onDestroy() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mIsRunning = false;
                while (mStartingService){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                stopPush();
                setChannelState(0);
                //mEasyIPCamera.resetChannel(mChannelId);
                //int result = mEasyIPCamera.shutdown();
                //Log.d(TAG, "kim shutdown result="+result);
                //mEasyIPCamera.unrigisterCallback(RecordService.this);
                //mEasyIPCamera = null;
                release();
                if (mMpj != null) {
                    mMpj.stop();
                }
                //super.onDestroy();
            }
        }).start();

        if (mReceiver != null){
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private void setChannelState(int state){
        if(state <= 2) {
            mChannelState = state;
//            mAudioStream.setChannelState(state);
        }
    }


    /*public static Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg){
            if(msg.what==0x555){
                //返回信息显示代码
                Log.d(TAG,"myhandler 0x555 start.. screenShot()");
                //screenShot();
                startMediaCodec();


            }else if(msg.what==0x666){
                //返回信息显示代码
                Log.d(TAG,"myhandler 0x666 start.. screenShot()");
                //screenShot();
                stopMediaCodec();

            }
        }
    };*/
    class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("zzkk check", "475 action="+action);
            if ("com.sim.screenrecorder.start".equals(action)){

                Log.i("zzkk check", "479 startMediaCodec();");
                startMediaCodec();


            }else if("com.sim.screenrecorder.stop".equals(action)){
                Log.i("zzkk check", "482 stopMediaCodec();");
                stopMediaCodec();
            }
        }
    }


    /*private void sendData(byte[] data, int len){
        Log.i("dj check", "server sendData message aaaaaaa socket=");
        if (mSocket == null){
            try{
                Thread.sleep(20);
                Log.d("zzkk","sleep---,100");
            }catch(Exception e){
                e.printStackTrace();
            }

            if(mSocket == null)
                return;
        }
        try {
            //socket = new Socket("10.0.56.232",3000);
//                    os = socket.getOutputStream();
//                    os.write("Your message has been received successfully！.\r\n\r\n".getBytes());
//                    os.flush();
//                    Log.i("dj check", "server send message !!!!!!!!!");
            Log.i("zzkk check", "server send message bbbbbbbb");
            if(mSocket.isOutputShutdown()){
                Log.i("zzkk check", "server send message cccccccc");
                mSocket.getKeepAlive();

            }else{
                Log.i("zzkk check", "server send message ddddddd");

                mSocketBos = new DataOutputStream(mSocket.getOutputStream());

                mSocketBos.write(data,0,len);
                //}

                        //try{
                           // Thread.sleep(5000);
                           // Log.d("zzkk","sleep---,100");
                        //}catch(Exception e){
                            //e.printStackTrace();
                        //}

                mSocketBos.write("\r\n\r\n".getBytes());

                //Log.i("zzkk check", "server send message !!!!!!!!!,amount="+amount+",byteBuffer size="+byteBuffer.length+",allNum="+allNum);
                Log.d("zzkk check", "server send message ------,send time internal="+(System.currentTimeMillis()));
                //mLastsendTime = System.currentTimeMillis();
                mSocketBos.flush();
                mSocketBos.close();
                mSocket.close();

            }

        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        //Message msg1 = mUiHandler.obtainMessage();
        //msg1.what=0x222;
        //mUiHandler.sendMessage(msg1);
    }*/

    public Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg){
            if(msg.what==0x555){
                //返回信息显示代码
                //screenShot();
                //stopMediaCodec();
                byte[] dataVedio = (byte[])msg.obj;
                Log.d("zzkk rev","myhandler 0x666 start.. getRtspStream(),dataVedio="+(dataVedio!=null?0:dataVedio.length));
                //sendData(dataVedio);
            }
        }
    };

    public static byte[] intToBytes( int value )
    {
        byte[] src = new byte[4];
        src[3] =  (byte) ((value>>24) & 0xFF);
        src[2] =  (byte) ((value>>16) & 0xFF);
        src[1] =  (byte) ((value>>8) & 0xFF);
        src[0] =  (byte) (value & 0xFF);
        return src;
    }

    public static int bytesToInt(byte[] src, int offset) {
        int value;
        value = (int) ((src[offset] & 0xFF)
                | ((src[offset+1] & 0xFF)<<8)
                | ((src[offset+2] & 0xFF)<<16)
                | ((src[offset+3] & 0xFF)<<24));
        return value;
    }

    /*
    public void onIPCameraCallBack(int channelId, int channelState, byte[] mediaInfo, int userPtr) {
        //        Log.d(TAG, "kim onIPCameraCallBack, channelId="+channelId+", mChannelId="+mChannelId+", channelState="+channelState);
        if(channelId != mChannelId)
            return;

        setChannelState(channelState);

        switch(channelState){
            case EasyIPCamera.ChannelState.EASY_IPCAMERA_STATE_ERROR:
                Log.d(TAG, "Screen Record EASY_IPCAMERA_STATE_ERROR");
                Util.showDbgMsg(StatusInfoView.DbgLevel.DBG_LEVEL_WARN, "Screen Record EASY_IPCAMERA_STATE_ERROR");
                break;
            case EasyIPCamera.ChannelState.EASY_IPCAMERA_STATE_REQUEST_MEDIA_INFO:
                Util.showDbgMsg(StatusInfoView.DbgLevel.DBG_LEVEL_INFO, "Screen Record EASY_IPCAMERA_STATE_REQUEST_MEDIA_INFO");
                ByteBuffer buffer = ByteBuffer.wrap(mediaInfo);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putInt(EasyIPCamera.VideoCodec.EASY_SDK_VIDEO_CODEC_H264);
                buffer.putInt(mFrameRate);
//                buffer.putInt(mAudioStream.getAudioEncCodec());
//                buffer.putInt(mAudioStream.getSamplingRate());
//                buffer.putInt(mAudioStream.getChannelNum());
//                buffer.putInt(mAudioStream.getBitsPerSample());
                buffer.putInt(0);
                buffer.putInt(0);
                buffer.putInt(0);
                buffer.putInt(0);

                buffer.putInt(0);//vps length
                buffer.putInt(mSps.length);
                buffer.putInt(mPps.length);
                buffer.putInt(0);
                buffer.put(mVps);
                buffer.put(mSps,0,mSps.length);
                if(mSps.length < 255) {
                    buffer.put(mVps, 0, 255 - mSps.length);
                }
                buffer.put(mPps,0,mPps.length);
                if(mPps.length < 128) {
                    buffer.put(mVps, 0, 128 - mPps.length);
                }
                buffer.put(mMei);
                break;
            case EasyIPCamera.ChannelState.EASY_IPCAMERA_STATE_REQUEST_PLAY_STREAM:
                Util.showDbgMsg(StatusInfoView.DbgLevel.DBG_LEVEL_INFO, "Screen Record EASY_IPCAMERA_STATE_REQUEST_PLAY_STREAM");
                startMediaCodec();
                //mAudioStream.startPush();
                break;
            case EasyIPCamera.ChannelState.EASY_IPCAMERA_STATE_REQUEST_STOP_STREAM:
                Util.showDbgMsg(StatusInfoView.DbgLevel.DBG_LEVEL_INFO, "Screen Record EASY_IPCAMERA_STATE_REQUEST_STOP_STREAM");
                stopMediaCodec();
                //mAudioStream.stopPush();
                break;
            default:
                break;
        }
    }*/
}
