package com.oem.statusbuttondemo;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import java.nio.ByteBuffer;

public class HidCommand {
    public static final String TAG = "StatusButton-HidComm";

    private static final int SET_TX_IDX_START             = 0;
    private static final int SET_TX_IDX_GPO_RED_CTRL      = 2;
    private static final int SET_TX_IDX_GPO_RED_DATA      = 3;
    private static final int SET_TX_IDX_GP2_GREEN_CTRL    = 10;
    private static final int SET_TX_IDX_GP2_GREEN_DATA    = 11;
    private static final int SET_TX_IDX_GP3_BLUE_CTRL     = 14;
    private static final int SET_TX_IDX_GP3_BLUE_DATA     = 15;

    private static final int GET_TX_IDX_START             = 0;
    private static final int GET_RX_IDX_GP1_BTN_CTRL      = 4;
    private static final int GET_RX_IDX_GP1_BTN_DATA      = 5;

    private int mAction;
    private int mPressedColor;
    private int mReleasedColor;

    private ByteBuffer mTxData;
    private ByteBuffer mRxData;


    public HidCommand(int action, int pressedcolor, int releasedcolor) {
        mAction = action;
        mPressedColor = pressedcolor;
        mReleasedColor = releasedcolor;
    }


    public ByteBuffer generate() {
        mTxData = ByteBuffer.allocate(64);

        switch (mAction) {
            case StatusButton.TX_BTN_OFF:
                mTxData.put(SET_TX_IDX_START, (byte) 0x50);
                setColorOff();
                break;

            case StatusButton.TX_BTN_PRESSED:
            case StatusButton.TX_BTN_RELEASED:
                mTxData.put(SET_TX_IDX_START, (byte) 0x50);
                if (mPressedColor == StatusButton.LIGHT_COLOR_NONE || mReleasedColor == StatusButton.LIGHT_COLOR_NONE) {
                    mTxData.clear();
                    mTxData = null;
                } else {
                    Log.i(TAG, "Set color, action(" + mAction + "), pressed color(" + mPressedColor + "), " + "released color(" + mReleasedColor + ").");
                    setColorOff();
                    setColorData(mPressedColor, mAction == StatusButton.TX_BTN_PRESSED ? true : false);
                    setColorData(mReleasedColor, mAction == StatusButton.TX_BTN_PRESSED ? false : true);
                }
                break;

            case StatusButton.TX_BTN_STATUS:
                mTxData.put(GET_TX_IDX_START, (byte) 0x51);
                break;
        }

        return mTxData;
    }


    public int resolveBtnStatus(ByteBuffer rxData) {
        byte result = rxData.get(GET_RX_IDX_GP1_BTN_CTRL);
        return result == 0 ? StatusButton.TX_BTN_PRESSED : StatusButton.TX_BTN_RELEASED;
    }


    private void setColorData(int color, boolean enabled) {
        switch (color) {
            case StatusButton.LIGHT_COLOR_RED:
                mTxData.put(SET_TX_IDX_GPO_RED_CTRL, (byte) 0x01);
                mTxData.put(SET_TX_IDX_GPO_RED_DATA, enabled ? (byte) 0x00 : (byte) 0x01);
                break;

            case StatusButton.LIGHT_COLOR_GREEN:
                mTxData.put(SET_TX_IDX_GP2_GREEN_CTRL, (byte) 0x01);
                mTxData.put(SET_TX_IDX_GP2_GREEN_DATA, enabled ? (byte) 0x00 : (byte) 0x01);
                break;

            case StatusButton.LIGHT_COLOR_BLUE:
                mTxData.put(SET_TX_IDX_GP3_BLUE_CTRL, (byte) 0x01);
                mTxData.put(SET_TX_IDX_GP3_BLUE_DATA, enabled ? (byte) 0x00 : (byte) 0x01);
                break;
        }
    }


    private void setColorOff() {
        mTxData.put(SET_TX_IDX_GPO_RED_CTRL,   (byte) 0x01);
        mTxData.put(SET_TX_IDX_GPO_RED_DATA,   (byte) 0x01);
        mTxData.put(SET_TX_IDX_GP2_GREEN_CTRL, (byte) 0x01);
        mTxData.put(SET_TX_IDX_GP2_GREEN_DATA, (byte) 0x01);
        mTxData.put(SET_TX_IDX_GP3_BLUE_CTRL,  (byte) 0x01);
        mTxData.put(SET_TX_IDX_GP3_BLUE_DATA,  (byte) 0x01);
    }
}
