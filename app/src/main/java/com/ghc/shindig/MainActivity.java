/*
Copyright (c) 2015, Nordic Semiconductor

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

* Neither the name of nRF UART nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.ghc.shindig;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.oguzdev.circularfloatingactionmenu.library.FloatingActionButton;
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu;
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
  private static final int REQUEST_SELECT_DEVICE = 1;
  private static final int REQUEST_ENABLE_BT = 2;
  private static final int UART_PROFILE_READY = 10;
  public static final String TAG = "nRFUART";
  private static final int UART_PROFILE_CONNECTED = 20;
  private static final int UART_PROFILE_DISCONNECTED = 21;
  private static final int STATE_OFF = 10;
  public static final String NAME_SEARCH_TERM = "NAME_SEARCH_TERM";

  TextView mRemoteRssiVal;
  RadioGroup mRg;
  private int mState = UART_PROFILE_DISCONNECTED;
  private UartService mService = null;
  private BluetoothDevice mDevice = null;
  private BluetoothAdapter mBtAdapter = null;
  private ListView messageListView;
  private ArrayList<String> deviceNames = new ArrayList<>();
  private ArrayAdapter<String> listAdapter;
  private ArrayAdapter<String> deviceNamesAdapter;
  private Button btnConnectDisconnect, btnSend;
  private EditText edtMessage;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    //Remove title bar
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);

    //Remove notification bar
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

    final Context context = this;

    //set content view AFTER ABOVE sequence (to avoid crash)
    setContentView(R.layout.main);
    mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    if (mBtAdapter == null) {
      Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
      finish();
      return;
    }
    messageListView = (ListView) findViewById(R.id.listMessage);
    listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
    messageListView.setAdapter(listAdapter);
    messageListView.setDivider(null);
    btnConnectDisconnect = (Button) findViewById(R.id.btn_select);
    btnSend = (Button) findViewById(R.id.sendButton);
    edtMessage = (EditText) findViewById(R.id.sendText);
    service_init();

    final ListView deviceNamesListView = (ListView) findViewById(R.id.deviceNamesList);
    deviceNamesAdapter = new ArrayAdapter(this, R.layout.device_name_list, deviceNames);
    deviceNamesListView.setAdapter(deviceNamesAdapter);
    deviceNamesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, "OnItemClickListener: " + parent.getAdapter().getItem(position));
      }
    });

    // Handle Disconnect & Connect button
    btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {

        if (!mBtAdapter.isEnabled()) {
          Log.i(TAG, "onClick - BT not enabled yet");
          Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
          startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
          Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
          startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
        }

      }
    });

    // Handle Send button
    btnSend.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        EditText editText = (EditText) findViewById(R.id.sendText);
        final String message = editText.getText().toString().toUpperCase();
        byte[] value;
        try {
          //send data to service
          value = message.getBytes("UTF-8");
          mService.writeRXCharacteristic(value);
          //Update the log with time stamp
          final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              listAdapter.add("[" + currentDateTimeString + "] TX: " + message);
              messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
              edtMessage.setText("");
            }
          });

        } catch (UnsupportedEncodingException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    });

    // Trying to capture "enter" event
    // todo not sure if this works.
    EditText editText = (EditText) findViewById(R.id.sendText);
    TextView.OnEditorActionListener exampleListener = new TextView.OnEditorActionListener() {

      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        Toast.makeText(context, "event.getAction:" + event.getAction(), Toast.LENGTH_SHORT).show();
        if (actionId == EditorInfo.IME_NULL
            && (event.getAction() == KeyEvent.KEYCODE_ENTER ||
            event.getAction() == 0)) {
          btnSend.callOnClick();
        }
        return true;
      }
    };
    editText.setOnEditorActionListener(exampleListener);


    // in Activity Context
    ImageView icon = new ImageView(this); // Create an icon
    icon.setImageDrawable(getDrawable(R.drawable.paired));

    FloatingActionButton actionButton = new FloatingActionButton.Builder(this)
        .setContentView(icon)
        .build();

    SubActionButton.Builder itemBuilder = new SubActionButton.Builder(this);
    // repeat many times:
    ImageView itemIcon = new ImageView(this);
    itemIcon.setImageDrawable(getDrawable(R.drawable.close));
    SubActionButton button1 = itemBuilder.setContentView(itemIcon).build();

    FloatingActionMenu actionMenu = new FloatingActionMenu.Builder(this)
        .addSubActionView(button1)
        .attachTo(actionButton)
        .build();


  }

  //UART service connected/disconnected
  private ServiceConnection mServiceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder rawBinder) {
      mService = ((UartService.LocalBinder) rawBinder).getService();
      Log.d(TAG, "onServiceConnected mService= " + mService);
      if (!mService.initialize()) {
        Log.e(TAG, "Unable to initialize Bluetooth");
        finish();
      }

    }

    public void onServiceDisconnected(ComponentName classname) {
      ////     mService.disconnect(mDevice);
      mService = null;
    }
  };

  private Handler mHandler = new Handler() {
    @Override

    //Handler events that received from UART service
    public void handleMessage(Message msg) {

    }
  };

  private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();

      final Intent mIntent = intent;
      //*********************//
      if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
        runOnUiThread(new Runnable() {
          public void run() {
            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            Log.d(TAG, "UART_CONNECT_MSG");
            edtMessage.setEnabled(true);
            btnSend.setEnabled(true);
            deviceNames.add(mDevice.getName());
            deviceNamesAdapter.notifyDataSetChanged();
            listAdapter.add("[" + currentDateTimeString + "] Connected to: " + mDevice.getName());
            messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
            mState = UART_PROFILE_CONNECTED;
          }
        });
      }

      //*********************//
      if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
        runOnUiThread(new Runnable() {
          public void run() {
            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
            Log.d(TAG, "UART_DISCONNECT_MSG");
            edtMessage.setEnabled(false);
            btnSend.setEnabled(false);
            listAdapter.add("[" + currentDateTimeString + "] Disconnected to: " + mDevice.getName());
            deviceNames.remove(mDevice.getName());
            deviceNamesAdapter.notifyDataSetChanged();
            mState = UART_PROFILE_DISCONNECTED;
            mService.close();
            //setUiState();

          }
        });
      }


      //*********************//
      if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
        mService.enableTXNotification();
      }
      //*********************//
      if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

        final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
        runOnUiThread(new Runnable() {
          public void run() {
            try {
              String text = new String(txValue, "UTF-8");
              String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
              listAdapter.add("[" + currentDateTimeString + "] RX: " + text);
              messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);

            } catch (Exception e) {
              Log.e(TAG, e.toString());
            }
          }
        });
      }
      //*********************//
      if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)) {
        showMessage("Device doesn't support UART. Disconnecting");
        mService.disconnect();
      }


    }
  };

  private void service_init() {
    Intent bindIntent = new Intent(this, UartService.class);
    bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

    LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
  }

  private static IntentFilter makeGattUpdateIntentFilter() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
    intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
    intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
    intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
    intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
    return intentFilter;
  }

  @Override
  public void onStart() {
    super.onStart();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "onDestroy()");

    try {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
    } catch (Exception ignore) {
      Log.e(TAG, ignore.toString());
    }
    unbindService(mServiceConnection);
    mService.stopSelf();
    mService = null;

  }

  @Override
  protected void onStop() {
    Log.d(TAG, "onStop");
    super.onStop();
  }

  @Override
  protected void onPause() {
    Log.d(TAG, "onPause");
    super.onPause();
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    Log.d(TAG, "onRestart");
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume");
    if (!mBtAdapter.isEnabled()) {
      Log.i(TAG, "onResume - BT not enabled yet");
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {

      case REQUEST_SELECT_DEVICE:
        //When the DeviceListActivity return, with the selected device address
        if (resultCode == Activity.RESULT_OK && data != null) {
          String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
          mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

          Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
          mService.connect(deviceAddress);
        }
        break;
      case REQUEST_ENABLE_BT:
        // When the request to enable Bluetooth returns
        if (resultCode == Activity.RESULT_OK) {
          Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

        } else {
          // User did not enable Bluetooth or an error occurred
          Log.d(TAG, "BT not enabled");
          Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
          finish();
        }
        break;
      default:
        Log.e(TAG, "wrong request code");
        break;
    }
  }

  @Override
  public void onCheckedChanged(RadioGroup group, int checkedId) {

  }


  private void showMessage(String msg) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

  }

  @Override
  public void onBackPressed() {
    if (mState == UART_PROFILE_CONNECTED) {
      Intent startMain = new Intent(Intent.ACTION_MAIN);
      startMain.addCategory(Intent.CATEGORY_HOME);
      startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startMain);
      showMessage("nRFUART's running in background.\n             Disconnect to exit");
    } else {
      new AlertDialog.Builder(this)
          .setIcon(android.R.drawable.ic_dialog_alert)
          .setTitle(R.string.popup_title)
          .setMessage(R.string.popup_message)
          .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              finish();
            }
          })
          .setNegativeButton(R.string.popup_no, null)
          .show();
    }
  }
}
