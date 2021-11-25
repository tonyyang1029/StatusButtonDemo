/*---------------------------------------------------------
 * 2021.11.23, Tony.Yang, Status-Button demo application.
 *---------------------------------------------------------*/
package com.oem.statusbuttondemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, AdapterView.OnItemClickListener {
    private static final String TAG = "StatusButtonDemo";

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case StatusButton.MSG_UPDATE_UI:
                    updateUi();
                    break;
            }
        }
    };

    private StatusButton mButton;

    private TextView mSn;
    private TextView mSnField;
    private TextView mVidField;
    private TextView mPidField;
    private TextView mManufacturerField;
    private TextView mProductField;
    private TextView mBtnStatus;

    private Spinner mSpinnerPressed;
    private Spinner mSpinnerReleased;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mButton = new StatusButton(this, getLifecycle(), mHandler);

        mSn = findViewById(R.id.tv_sn);
        mSn.setVisibility(View.GONE);
        mSnField = findViewById(R.id.tv_sn_field);
        mSnField.setVisibility(View.GONE);
        mVidField = findViewById(R.id.tv_vid_field);
        mPidField = findViewById(R.id.tv_pid_field);
        mManufacturerField = findViewById(R.id.tv_manufacturer_field);
        mProductField = findViewById(R.id.tv_product_field);
        mBtnStatus = findViewById(R.id.tv_btn_field);

        List<String> colorArray = new ArrayList<>();
        colorArray.add("Red");
        colorArray.add("Green");
        colorArray.add("Blue");
        //
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, colorArray);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        //
        mSpinnerPressed = findViewById(R.id.spinner_pressed);
        mSpinnerPressed.setAdapter(arrayAdapter);
        mSpinnerPressed.setOnItemSelectedListener(this);
        mSpinnerPressed.setSelection(0);
        mSpinnerPressed.setEnabled(false);
        //
        mSpinnerReleased = findViewById(R.id.spinner_released);
        mSpinnerReleased.setAdapter(arrayAdapter);
        mSpinnerReleased.setOnItemSelectedListener(this);
        mSpinnerReleased.setSelection(2);
        mSpinnerReleased.setEnabled(false);
    }


    @Override
    protected void onStart() {
        super.onStart();
        updateUi();
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.spinner_pressed) {
            if ((mButton.getReleasedColor() != (position + 1)) ||
                    (mButton.getPressedColor() == StatusButton.LIGHT_COLOR_NONE)) {
                mButton.setPressedColor(position + 1);
            } else {
                mSpinnerPressed.setSelection(mButton.getPressedColor() - 1);
            }
        } else if (parent.getId() == R.id.spinner_released) {
            if ((mButton.getPressedColor() != (position + 1)) ||
                    (mButton.getReleasedColor() == StatusButton.LIGHT_COLOR_NONE)) {
                mButton.setReleasedColor(position + 1);
            } else {
                mSpinnerReleased.setSelection(mButton.getReleasedColor() - 1);
            }
        }
    }


    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (view.getId() == R.id.spinner_pressed) {
            mButton.setPressedColor(position + 1);
        } else if (view.getId() == R.id.spinner_released) {
            mButton.setReleasedColor(position + 1);
        }
    }


    private void updateUi() {
        HashMap<String, String> description = mButton.getDeviceDescription();
        if (description.isEmpty()) {
            mSnField.setText("Unknown");
            mVidField.setText("Unknown");
            mPidField.setText("Unknown");
            mManufacturerField.setText("Unknown");
            mProductField.setText("Unknown");
            //
            mBtnStatus.setText("Unknown");
            //
            mSpinnerPressed.setEnabled(false);
            mSpinnerReleased.setEnabled(false);
        } else {
            mSnField.setText(description.get("sn"));
            mVidField.setText(description.get("vid"));
            mPidField.setText(description.get("pid"));
            mManufacturerField.setText(description.get("manufacturer"));
            mProductField.setText(description.get("product"));
            mBtnStatus.setText(description.get("status"));
            //
            mSpinnerPressed.setEnabled(true);
            mButton.setPressedColor(mSpinnerPressed.getSelectedItemPosition() + 1);
            mSpinnerReleased.setEnabled(true);
            mButton.setReleasedColor(mSpinnerReleased.getSelectedItemPosition() + 1);
        }
    }
}