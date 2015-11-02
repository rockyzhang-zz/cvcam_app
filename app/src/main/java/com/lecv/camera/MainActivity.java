/*
    Rocky Zhang <zhangyan.hit@gmail.com>
 */


package com.lecv.camera;


/* Reference: http://developer.android.com/guide/topics/connectivity/usb/accessory.html#manifest */


/*
 *
 *
 *  Android App                    C/C++ Daemon
 *      |                                |
 *  accessory_gadget                   libusb
 *      |                                |
 *    USB device <------USB AOA -----> USB Host
 *
 *
 *    Phone                             Zynq
 *
 */




import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.Buffer;
import java.util.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION =
            "com.lecv.camera.USB_PERMISSION";

    public UsbManager mUsbmanager;
    public UsbAccessory usbaccessory;
    public PendingIntent mPermissionIntent;
    public ParcelFileDescriptor mFileDescriptor;
    public FileInputStream mInputStream;
    public FileOutputStream mOutputStream;
    public boolean mPermissionRequestPending = true;


    //UI Widgets
    public Button button1;
    public Button button2;
    public TextView textView;
    public ImageView imageView;


    //public Handler usbhandler;
    public byte[] usbdata;
    public byte[] writeusbdata;
    public byte[] bitmap;




    public int readcount;
    /*thread to listen USB data*/
    public handler_thread handlerThread;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbdata = new byte[4];
        writeusbdata = new byte[4];
        bitmap = new byte[640*480*3];

        mUsbmanager = (UsbManager) getSystemService(Context.USB_SERVICE);


        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        /* TODO: put button & callbacks here */

        button1 = (Button) findViewById(R.id.button1);

        button1.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v){


                Log.d("LECV", "Button 1 pressed");
                byte ibutton = (byte) new Random().nextInt(256);
                WriteUsbData(ibutton);

            }
        });


        textView = (TextView) findViewById(R.id.textView);
        imageView = (ImageView) findViewById(R.id.imageView);

    }

    @Override
    public void onResume()
    {
        super.onResume();
        Intent intent = getIntent();
        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        UsbAccessory[] accessories = mUsbmanager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbmanager.hasPermission(accessory)) {
                OpenAccessory(accessory);
            }
            else
            {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbmanager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {}
    }

    @Override
    public void onDestroy()
    {
        unregisterReceiver(mUsbReceiver);
        //CloseAccessory();
        super.onDestroy();
    }


    /*open the accessory*/
    private void OpenAccessory(UsbAccessory accessory)
    {
        mFileDescriptor = mUsbmanager.openAccessory(accessory);
        if(mFileDescriptor != null){
            usbaccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
			/*check if any of them are null*/
            if(mInputStream == null || mOutputStream==null){
                return;
            }
        }

        handlerThread = new handler_thread(handler, mInputStream);
        handlerThread.start();

    } /*end OpenAccessory*/

    public void ReadUsbData()
    {
        String text = String.format("%x %x %x %x",
                usbdata[0], usbdata[1], usbdata[2], usbdata[3]);
        textView.setText(text);
        Bitmap bm = BitmapFactory.decodeByteArray(bitmap, 0, readcount);



        /*

        Bitmap bm = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);

        // Setting RGB888 bytes buffer into ARGB8888 bitmap
        for(int i=0; i<640; i++)
        {
            for(int j=0; j<480; j++)
            {
                bm.setPixel(i, j , 0xff << 24 | bitmap[j*640*3+i*3] << 16 |
                        bitmap[j*640*3+i*3 +1 ] | bitmap[j*640*3+i*3 +2]);
            }
        }
        */


        imageView.setImageBitmap(bm);
    }

    private void CloseAccessory()
    {
        try{
            mFileDescriptor.close();
        }catch (IOException e){}

        try {
            mInputStream.close();
        } catch(IOException e){}

        try {
            mOutputStream.close();

        }catch(IOException e){}
		/*FIXME, add the notfication also to close the application*/
        //unregisterReceiver(mUsbReceiver);
        //CloseAccessory();
        //super.onDestroy();
        mFileDescriptor = null;
        mInputStream = null;
        mOutputStream = null;

        System.exit(0);

    }


    final Handler handler =  new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            ReadUsbData();
        }

    };


    private class handler_thread  extends Thread {
        Handler mHandler;
        FileInputStream instream;

        handler_thread(Handler h,FileInputStream stream ){
            mHandler = h;
            instream = stream;
        }

        public void run()
        {

            while(true)
            {
                Message msg = mHandler.obtainMessage();
                try{
                    if(instream != null)
                    {
                        readcount = instream.read(usbdata,0,4);

                        int count = 0;
                        readcount = 0;

                        for(;;){

                            /* libusb will try to do bulk transfer using 16K buffer */

                            count = instream.read(bitmap, readcount, 493862 - readcount);

                            if (count == -1 || readcount == 493862)
                                break;

                            readcount += count;
                            Log.i("LECV", "got USB transfer size: "+count+" bytes, total size: " + readcount +" bytes");
                        }


                        if(readcount > 0)
                        {
                            msg.arg1 = usbdata[0];
                            msg.arg2 = usbdata[3];
                        }
                        mHandler.sendMessage(msg);
                    }
                }catch (IOException e){}
            }
        }
    }

    public void WriteUsbData(byte iButton){
        writeusbdata[0] = 0;
        writeusbdata[1] = 1;
        writeusbdata[2] = 2;
        writeusbdata[3] = iButton;

        Log.d("LECV", "pressed " + iButton);

        DataOutputStream out = new DataOutputStream(mOutputStream);
        try {
            out.writeInt(0xdeadbeef);
        }catch (IOException e){

        }


        try{
            if(mOutputStream != null){
                mOutputStream.write(writeusbdata,0,4);
            }
        }
        catch (IOException e) {}
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action))
            {
                synchronized (this)
                {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    {
                        OpenAccessory(accessory);
                    }
                    else
                    {
                        Log.d("LECV", "permission denied for accessory "+ accessory);

                    }
                    mPermissionRequestPending = false;
                }
            }
            else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action))
            {
                UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null )//&& accessory.equals(usbaccessory))
                {
                    CloseAccessory();
                }
            }else
            {
                Log.d("LECV", "....");
            }
        }
    };
}


