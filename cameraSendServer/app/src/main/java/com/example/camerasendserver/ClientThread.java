package com.example.camerasendserver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class ClientThread implements Runnable {
    private static Socket socket=null ;
    private static ByteArrayOutputStream outputstream;
    private static byte byteBuffer[] = new byte[1024];
    public static Size size;

    //向UI线程发送消息
    private Handler uiHandler;
    
    //接受UI线程消息
    public MyHandler revHandler;
    
    BufferedReader br= null;
    static OutputStream os = null;
    
    public ClientThread(Handler handler){
        this.uiHandler=handler;
        
    }

    @Override
    public void run() {

            
            Looper.prepare();
            //接受UI发来的信息
            revHandler = new MyHandler(uiHandler);
            Looper.loop();

    }
    public static void setSocket(Socket s){
    	socket = s;
    	Log.i("dj check", "setSocket socket="+socket);
    }

    public static class MyHandler extends Handler{
        Handler mUiHandler;
        public MyHandler(Handler handler){
            mUiHandler=handler;

        }

        long mLastsendTime;

        @Override
        public void handleMessage(Message msg){
            if(msg.what==0x111){
            	Log.i("dj check", "server send message aaaaaaa socket="+socket);
            	if (socket == null){
            	    return;
            	}
                try {
                    //socket = new Socket("10.0.56.232",3000);
//                    os = socket.getOutputStream(); 
//                    os.write("Your message has been received successfully！.\r\n\r\n".getBytes());
//                    os.flush();
//                    Log.i("dj check", "server send message !!!!!!!!!");
                	Log.i("zzkk check", "server send message bbbbbbbb");
                    //YuvImage image = (YuvImage) msg.obj;
                    Bitmap bmp = (Bitmap)msg.obj;
                    if(socket.isOutputShutdown()){
                    	Log.i("zzkk check", "server send message cccccccc");
                        socket.getKeepAlive();
                    
                    }else{
                    	Log.i("zzkk check", "server send message ddddddd");
                        os = socket.getOutputStream();  
                        outputstream = new ByteArrayOutputStream();
                        //image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, outputstream);
                        Log.i("zzkk check", "server send message eeeeee");
                        bmp.compress(Bitmap.CompressFormat.JPEG, 100, outputstream);
                        ByteArrayInputStream inputstream = new ByteArrayInputStream(outputstream.toByteArray());
                        int amount,allNum=0;
                        Log.i("zzkk check","inputstream length="+outputstream.toByteArray().length);

                        while ((amount = inputstream.read(byteBuffer)) != -1) {
                            allNum += amount;
                            os.write(byteBuffer, 0, amount);
                        }

                        /*try{
                            Thread.sleep(5000);
                            Log.d("zzkk","sleep---,100");
                        }catch(Exception e){
                            e.printStackTrace();
                        }*/

                        os.write("\r\n\r\n".getBytes());

                        Log.i("zzkk check", "server send message !!!!!!!!!,amount="+amount+",byteBuffer size="+byteBuffer.length+",allNum="+allNum);
                        Log.d("zzkk check", "server send message ------,send time internal="+(System.currentTimeMillis()-mLastsendTime));
                        mLastsendTime = System.currentTimeMillis();
                        outputstream.flush();
                        outputstream.close();
                        os.flush();
                        os.close();
                        socket.close();

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
            }
        }
    }
}
