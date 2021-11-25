package com.oem.statusbuttondemo;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import java.nio.ByteBuffer;
import java.util.HashMap;


public class StatusButton implements LifecycleObserver  {
    public static final String ACTION_PERMISSION_REQUEST    = "com.oem.statusbuttondemo.USB_PERMISSION_REQUEST";
    public static final String ACTION_PERMISSION_GRANTED    = UsbManager.EXTRA_PERMISSION_GRANTED;
    public static final String ACTION_ATTACHED              = UsbManager.ACTION_USB_DEVICE_ATTACHED;
    public static final String ACTION_DETACHED              = UsbManager.ACTION_USB_DEVICE_DETACHED;

    public static final int RET_SUCCESS                     = 1;
    public static final int RET_FAILED                      = 0;
    public static final int RET_DEVICE_NOT_FOUND            = -1;
    public static final int RET_NO_USB_PERMISSION           = -2;
    public static final int RET_USB_EP_NOT_FOUND            = -3;
    public static final int RET_USB_CONNECTION_FAILED       = -4;

    public static final int LIGHT_COLOR_NONE                = 0;
    public static final int LIGHT_COLOR_RED                 = 1;
    public static final int LIGHT_COLOR_GREEN               = 2;
    public static final int LIGHT_COLOR_BLUE                = 3;

    public static final int TX_BTN_UNKNOWN                  = 0;
    public static final int TX_BTN_PRESSED                  = 1;
    public static final int TX_BTN_RELEASED                 = 2;
    public static final int TX_BTN_STATUS                   = 3;
    public static final int TX_BTN_OFF                      = 4;

    public static final int MSG_UPDATE_UI                   = 1;
    public static final int MSG_SET_LIGHT_COLOR             = 2;
    public static final int MSG_SET_LIGHT_OFF               = 3;
    public static final int MSG_INIT_BUTTON                 = 4;

    private static final String TAG = "StatusButton";

    private final int USB_VID = 0x04E7;
    private final int USB_PID = 0xA106;

    private final Context mCtxt;
    private final Handler mUiHandler;
    private final StatusButtonReceiver mReceiver;
    private final UsbManager mUsbManager;
    private final HandlerThread mBtnHandlerThread;
    private final BtnHandler mBtnHandler;
    private final RetrieveBtnStatusTask mRetrieveBtnStatusTask;


    private UsbDevice           mBtn;
    private UsbEndpoint         mBtnEpOut;
    private UsbEndpoint         mBtnEpIn;
    private UsbDeviceConnection mBtnConnection;
    private UsbInterface        mBtnInterface;
    private UsbRequest          mBtnRequestIn;
    private UsbRequest          mBtnRequestOut;
    private HashMap<String, String> mBtnDescriptionMap;

    private int mPressedColor  = LIGHT_COLOR_NONE;
    private int mReleasedColor = LIGHT_COLOR_NONE;

    private int mBtnStatus;


    public StatusButton(Context ctxt, Lifecycle lifecycle, Handler handler) {
        mCtxt = ctxt;
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }
        mUiHandler = handler;
        //
        mReceiver = new StatusButtonReceiver();
        mUsbManager = (UsbManager) mCtxt.getSystemService(Context.USB_SERVICE);
        mBtnRequestIn = new UsbRequest();
        mBtnRequestOut = new UsbRequest();
        mBtnDescriptionMap = new HashMap<>();
        //
        mBtnHandlerThread = new HandlerThread("StatusButton");
        mBtnHandlerThread.start();
        mBtnHandler = new BtnHandler(mBtnHandlerThread.getLooper());
        //
        mRetrieveBtnStatusTask = new RetrieveBtnStatusTask();
        mRetrieveBtnStatusTask.start();
    }


    public boolean isStatusButton(Intent intent) {
        UsbDevice dev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (dev.getVendorId() == USB_VID && dev.getProductId() == USB_PID) {
            return true;
        } else {
            return false;
        }
    }


    public HashMap<String, String> getDeviceDescription() {
        return mBtnDescriptionMap;
    }


    public void setPressedColor(int color) {
        Log.i(TAG, "Pressed color: " + color);
        mPressedColor = color;

        Message msg = mBtnHandler.obtainMessage();
        msg.what = MSG_SET_LIGHT_COLOR;
        msg.arg1 = mBtnStatus;
        mBtnHandler.sendMessage(msg);
    }


    public int getPressedColor() {
        return mPressedColor;
    }


    public void setReleasedColor(int color) {
        Log.i(TAG, "Released color: " + color);
        mReleasedColor = color;

        Message msg = mBtnHandler.obtainMessage();
        msg.what = MSG_SET_LIGHT_COLOR;
        msg.arg1 = mBtnStatus;
        mBtnHandler.sendMessage(msg);
    }


    public int getReleasedColor() {
        return mReleasedColor;
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private void onStart() {
        Log.i(TAG, "Status Button is started.");

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PERMISSION_REQUEST);
        filter.addAction(ACTION_ATTACHED);
        filter.addAction(ACTION_DETACHED);
        mCtxt.registerReceiver(mReceiver, filter);

        connect();
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private void onStop() {
        Log.i(TAG, "Status Button is stopped.");

        mCtxt.unregisterReceiver(mReceiver);

        disconnect();
    }


    private int connect() {
        Log.i(TAG, "Start to connect Status Button.");

        int ret = RET_SUCCESS;
        reset();

        mBtn = findStatusButton();
        if (mBtn == null) {
            ret = RET_DEVICE_NOT_FOUND;
        }

        if (ret == RET_SUCCESS && !mUsbManager.hasPermission(mBtn)) {
            PendingIntent pi = PendingIntent.getBroadcast(mCtxt, 0, new Intent(ACTION_PERMISSION_REQUEST), 0);
            mUsbManager.requestPermission(mBtn, pi);
            ret = RET_NO_USB_PERMISSION;
        }

        if (ret == RET_SUCCESS) {
            for (int i = 0; i < mBtn.getInterfaceCount(); i++) {
                mBtnInterface = mBtn.getInterface(i);
                if (mBtnInterface.getInterfaceClass() == 3) {
                    for (int j = 0; j < mBtnInterface.getEndpointCount(); j++) {
                        if (mBtnInterface.getEndpoint(j).getDirection() == 0) {
                            mBtnEpOut = mBtnInterface.getEndpoint(j);
                        } else {
                            mBtnEpIn = mBtnInterface.getEndpoint(j);
                        }
                    }
                    break;
                }
            }

            if (mBtnEpOut == null || mBtnEpIn == null) {
                ret = RET_USB_EP_NOT_FOUND;
            }
        }

        if (ret == RET_SUCCESS) {
            mBtnConnection = mUsbManager.openDevice(mBtn);
            if (mBtnConnection == null ) {
                return RET_USB_CONNECTION_FAILED;
            }
        }

        if (ret != RET_SUCCESS) {
            Log.i(TAG, "Status Button is failed to connect, " + ret);
            reset();
            mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_UPDATE_UI));
        } else {
            Log.i(TAG, "Status Button is connected.");
            saveDescription();
            mBtnHandler.sendMessageDelayed(mBtnHandler.obtainMessage(MSG_INIT_BUTTON), 500);
        }

        return ret;
    }


    private void disconnect() {
        Message msg = mBtnHandler.obtainMessage();
        msg.what = MSG_SET_LIGHT_OFF;
        msg.arg1 = TX_BTN_OFF;
        mBtnHandler.sendMessage(msg);
    }


    private UsbDevice findStatusButton() {
        for (UsbDevice dev : mUsbManager.getDeviceList().values()) {
            if (dev.getVendorId() == USB_VID && dev.getProductId() == USB_PID) {
                Log.i(TAG, "Status Button is found.");
                return dev;
            }
        }

        return null;
    }


    private void saveDescription() {
        if (mBtn != null) {
            mBtnDescriptionMap.clear();
            mBtnDescriptionMap.put("sn", mBtn.getSerialNumber());
            mBtnDescriptionMap.put("vid", String.valueOf(mBtn.getVendorId()));
            mBtnDescriptionMap.put("pid", String.valueOf(mBtn.getProductId()));
            mBtnDescriptionMap.put("manufacturer", mBtn.getManufacturerName());
            mBtnDescriptionMap.put("product", mBtn.getProductName());
        }
    }


    private void reset() {
        mBtn = null;
        mBtnEpOut = null;
        mBtnEpIn = null;
        mBtnConnection = null;
        mBtnInterface = null;
        mBtnDescriptionMap.clear();
        mBtnStatus = TX_BTN_UNKNOWN;
        mPressedColor = LIGHT_COLOR_NONE;
        mReleasedColor = LIGHT_COLOR_NONE;
    }


    class StatusButtonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isStatusButton(intent)) {
                switch (intent.getAction()) {
                    case StatusButton.ACTION_PERMISSION_REQUEST:
                        if (intent.getBooleanExtra(StatusButton.ACTION_PERMISSION_GRANTED, false)) {
                            connect();
                        }
                        break;

                    case StatusButton.ACTION_ATTACHED:
                    case StatusButton.ACTION_DETACHED:
                        connect();
                        break;
                }
            }
        }
    }


    class TransactionTask extends Thread {
        private int mAction;

        public TransactionTask(int action) {
            mAction = action;
        }

        @Override
        public void run() {
            if (mBtnConnection == null) {
                return;
            }
            //
            HidCommand command = new HidCommand(mAction, mPressedColor, mReleasedColor);
            ByteBuffer txData = command.generate();
            if (txData == null) {
                return;
            }
            //
            mBtnConnection.claimInterface(mBtnInterface, true);
            //
            mBtnRequestOut.initialize(mBtnConnection, mBtnEpOut);
            mBtnRequestOut.queue(txData, txData.capacity());
            if (mBtnConnection.requestWait() == null) {
                return;
            }
            //
            ByteBuffer rxData = ByteBuffer.allocate(64);
            mBtnRequestIn.initialize(mBtnConnection, mBtnEpIn);
            mBtnRequestIn.queue(rxData, rxData.capacity());
            if (mBtnConnection.requestWait() == null) {
                return;
            }


            if (mAction == TX_BTN_STATUS) {
                int status = command.resolveBtnStatus(rxData);
                if (status != mBtnStatus) {
                    Log.i(TAG, "Status transition: " + mBtnStatus + " -> " + status);
                    Message msg = mBtnHandler.obtainMessage();
                    msg.what = MSG_SET_LIGHT_COLOR;
                    msg.arg1 = status;
                    mBtnHandler.sendMessage(msg);
                    //
                    mBtnStatus = status;
                    //
                    mBtnDescriptionMap.put("status", mBtnStatus == TX_BTN_PRESSED ? "Pressed" : "Released");
                    mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_UPDATE_UI));
                }
            }

            if (mAction == StatusButton.TX_BTN_OFF) {
                mBtnHandler.removeCallbacksAndMessages(null);
                reset();
            }
        }
    }


    class RetrieveBtnStatusTask extends Thread {
        @Override
        public void run() {
            while (true) {
                if (mBtnConnection != null) {
                    TransactionTask task = new TransactionTask(TX_BTN_STATUS);
                    mBtnHandler.post(task);
                }
                SystemClock.sleep(300);
            }
        }
    }


    class BtnHandler extends Handler {
        public BtnHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            TransactionTask task;

            switch (msg.what) {
                case MSG_SET_LIGHT_COLOR:
                case MSG_SET_LIGHT_OFF:
                    task = new TransactionTask(msg.arg1);
                    mBtnHandler.post(task);
                    break;

                case MSG_INIT_BUTTON:
                    Message message = mBtnHandler.obtainMessage();
                    message.what = MSG_SET_LIGHT_COLOR;
                    message.arg1 = mBtnStatus;
                    mBtnHandler.sendMessage(message);
                    break;
            }
        }
    }
}
