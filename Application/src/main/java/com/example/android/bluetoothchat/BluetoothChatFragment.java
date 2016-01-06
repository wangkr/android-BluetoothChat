/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothchat;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.common.ByteStringConverter;
import com.example.android.common.Command;
import com.example.android.common.XXTEA;
import com.example.android.common.logger.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "BluetoothChatFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // constant
    public static final int FRAME_SIZE = 16;
    public static final int PAGE_SIZE = 128;

    public static final int FR_SENDING = 1;
    public static final int FR_SEND_SUCCESS = 2;
    public static final int FR_RECV_SUCCESS = 3;
    public static final int FR_RECV_FAILED = 4;
    public static final int FR_NONE = -1;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private Button mGetLockIdBtn;
    private Button mDownGujianBtn;
    private Button mVerifyGujianBtn;

    private ProgressBar progressBar;

    public static int frame_status = FR_NONE;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer1024 for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;

    private Command.CommandType commandType = Command.CommandType.NONE;
    private int frameId = -1;
    private int frameNum = -1;

    private byte[] buffer1024 = new byte[1024];
    private byte[] buffer16 = new byte[16];
    private byte[] buffer144 = new byte[144];
    private byte[] gjbuffer;

    // send rom data
    InputStream inputStream;
    private int curPos;
    private int curPage;
    private int total;
    private int totalPage;
    private int temp;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.in);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);
        mSendButton = (Button) view.findViewById(R.id.button_send);
        mVerifyGujianBtn = (Button) view.findViewById(R.id.verifyGujianBtn);
        mGetLockIdBtn = (Button) view.findViewById(R.id.getLockIdBtn);
        mDownGujianBtn = (Button) view.findViewById(R.id.downGuJianBtn);
        progressBar = (ProgressBar) view.findViewById(R.id.progressbar);

        mDownGujianBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commandType = Command.CommandType.ERASE_GJ;
                sendMessage(XXTEA.encrypt(Command.getCmd(commandType)));
            }
        });

        mGetLockIdBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commandType = Command.CommandType.GET_ID;
                sendMessage(XXTEA.encrypt(Command.getCmd(commandType)));
            }
        });

        mVerifyGujianBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commandType = Command.CommandType.VERIFY_GJ;
                sendGujian();
            }
        });
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    commandType = Command.CommandType.NONE;
                    sendMessage(XXTEA.encrypt(message.getBytes()));
                }
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // Initialize the buffer1024 for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(byte[] message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            mChatService.write(message);

            // Reset out string buffer1024 to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(XXTEA.encrypt(message.getBytes()));
            }
            return true;
        }
    };

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final MyHandler mHandler = new MyHandler(this);

    static class MyHandler extends Handler {
        private WeakReference<BluetoothChatFragment> fragment;
        public MyHandler(BluetoothChatFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }
        @Override
        public void handleMessage(Message msg) {
            BluetoothChatFragment chatFragment = fragment.get();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            chatFragment.setStatus(chatFragment.getString(R.string.title_connected_to, chatFragment.mConnectedDeviceName));
                            chatFragment.mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            chatFragment.setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            chatFragment.setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(XXTEA.decrypt(writeBuf));
                    chatFragment.mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_END:
                    chatFragment.onDataEnd();
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    chatFragment.onDataRead(readBuf, msg.arg1, msg.arg2);

//                    mConversationArrayAdapter.add(mConnectedDeviceName+": "+XXTEA.decrypt(data));
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    chatFragment.mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != chatFragment.getActivity()) {
                        Toast.makeText(chatFragment.getActivity(), "Connected to "
                                + chatFragment.mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != chatFragment.getActivity()) {
                        Toast.makeText(chatFragment.getActivity(), msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 20:
                    int progress = (int)(chatFragment.curPos*1.0 / chatFragment.total) * 100;
                    chatFragment.progressBar.setProgress(progress);
                    break;
            }
        }
    }

    private void onDataEnd() {
        switch (commandType) {
            case DOWN_GJ:
                if ( frame_status == FR_SEND_SUCCESS ) {
                    // 失败重发
                    curPos -= temp;
                    curPage --;
                    mHandler.obtainMessage(20).sendToTarget();
                    commandType = Command.CommandType.DOWN_GJ;
                    sendGujian();
                    Log.d(TAG, "resend "+curPage);
                }
                break;
            case VERIFY_GJ:
                if ( frame_status == FR_SEND_SUCCESS ) {
                    // 失败重发
                    curPos -= temp;
                    curPage --;
                    mHandler.obtainMessage(20).sendToTarget();
                    commandType = Command.CommandType.VERIFY_GJ;
                    sendGujian();
                    Log.d(TAG, "resend "+curPage);
                }
                break;
        }
    }


    private void onDataRead(byte[] data, int length, int status) {
        if (status != FR_RECV_SUCCESS) {
            return;
        }
        // 解密
//        if (commandType != Command.CommandType.NONE) {
//
//            if (frameId == -1) {
//                frameId++;
//                frameNum = Command.getDataFrameNum(commandType, re);
//
//                System.arraycopy(re, 0, buffer1024, 0, length);
//                frameId++;
//            } else if (frameId < frameNum) {
//                System.arraycopy(re, 0, buffer1024, frameId*FRAME_SIZE, length);
//                frameId++;
//            }
//        }
        switch (commandType) {
            case GET_ID:
                frame_status = FR_NONE;
                System.arraycopy(data, 0, buffer16, 0, FRAME_SIZE);
                byte[] xxx = new byte[12];
                System.arraycopy(XXTEA.decrypt(buffer16), 4, xxx, 0, 12);
                mConversationArrayAdapter.add(mConnectedDeviceName+": "+ ByteStringConverter.bytesToHexString(xxx));
                commandType = Command.CommandType.NONE;
                // 数据帧数据置初始化状态
                frameId = -1;
                frameNum = -1;
                break;
            case ERASE_GJ:
                frame_status = FR_NONE;
                System.arraycopy(data, 0, buffer16, 0, FRAME_SIZE);
                System.arraycopy(XXTEA.decrypt(buffer16), 0, buffer16, 0, FRAME_SIZE);
                if (Command.isOK(commandType, buffer16)) {
                    commandType = Command.CommandType.DOWN_GJ;
                    sendGujian();
                    // 数据帧数据置初始化状态
                    frameId = -1;
                    frameNum = -1;
                }
                break;
            case DOWN_GJ:
                System.arraycopy(data, 0, buffer16, 0, FRAME_SIZE);
                System.arraycopy(XXTEA.decrypt(buffer16), 0, buffer16, 0, FRAME_SIZE);
                if (Command.isOK(commandType, buffer16)) {
                    frame_status = FR_NONE;
                    if (curPos != total) {
                        sendGujian();
                        // 数据帧数据置初始化状态
                        frameId = -1;
                        frameNum = -1;
                    } else {
                        Toast.makeText(getContext(), "发送完成", Toast.LENGTH_LONG).show();
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            inputStream = null;
                        }
                        commandType = Command.CommandType.NONE;
                        // 数据帧数据置初始化状态
                        frameId = -1;
                        frameNum = -1;
                    }
                }
                break;
            case VERIFY_GJ:
                System.arraycopy(data, 0, buffer16, 0, FRAME_SIZE);
                System.arraycopy(XXTEA.decrypt(buffer16), 0, buffer16, 0, FRAME_SIZE);
                if (Command.isOK(commandType, buffer16)) {
                    frame_status = FR_NONE;
                    if (curPos != total) {
                        sendGujian();
                        // 数据帧数据置初始化状态
                        frameId = -1;
                        frameNum = -1;
                    } else {
                        Toast.makeText(getContext(), "校验成功", Toast.LENGTH_LONG).show();
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            inputStream = null;
                        }
                        commandType = Command.CommandType.NONE;
                        // 数据帧数据置初始化状态
                        frameId = -1;
                        frameNum = -1;
                    }
                } else {
                    Toast.makeText(getContext(), "校验失败", Toast.LENGTH_LONG).show();
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        inputStream = null;
                    }
                    commandType = Command.CommandType.NONE;
                    // 数据帧数据置初始化状态
                    frameId = -1;
                    frameNum = -1;
                }
                break;
        }
    }

    private void sendGujian() {
        frame_status = FR_SENDING;
        try {
            if (inputStream == null) {
                inputStream = getResources().openRawResource(R.raw.lock2);
                total = inputStream.available();
                total -= 24;
                totalPage = (int)(total/PAGE_SIZE) + (total%PAGE_SIZE==0?0:1);
                // 全部读到缓冲区
                gjbuffer = new byte[total];
                if (inputStream.read(gjbuffer) != total){
                    return;
                }

                curPage = 0;
                curPos = 0;
            }

            System.arraycopy(Command.getCmd(commandType), 0, buffer16, 0, FRAME_SIZE);
            Command.attachLengthInfo(buffer16, totalPage, curPage);
            System.arraycopy(buffer16, 0, buffer144, 0, 8);

            if (curPos <= total - PAGE_SIZE) {
                System.arraycopy(gjbuffer, curPos, buffer144, 8, PAGE_SIZE);
                for (int i = 0; i < 8; i++) {
                    buffer144[136+i] = 0x00;
                }

                for (int i = 0; i < buffer144.length ; i += FRAME_SIZE) {
                    System.arraycopy(buffer144, i, buffer16, 0, FRAME_SIZE);
                    byte[] temp = XXTEA.encrypt(buffer16);
                    System.arraycopy(temp, 0, buffer144, i, FRAME_SIZE);
                }
                sendMessage(buffer144);
                curPos += PAGE_SIZE;
                temp = PAGE_SIZE;
            } else if (curPos < total) {
                temp = total - curPos;
                System.arraycopy(gjbuffer, curPos, buffer144, 8, total-curPos);
                for (int i = 0; i < 144-(total-curPos)-8; i++) {
                    buffer144[8+total-curPos+i] = 0x00;
                }

                for (int i = 0; i < buffer144.length ; i+=FRAME_SIZE) {
                    System.arraycopy(buffer144, i, buffer16, 0, FRAME_SIZE);
                    byte[] temp = XXTEA.encrypt(buffer16);
                    System.arraycopy(temp, 0, buffer144, i, FRAME_SIZE);
                }
                sendMessage(buffer144);
                curPos = total;
            }
            curPage++;
            Log.d(TAG, "sendGujian curPage="+curPage);
            mHandler.obtainMessage(20).sendToTarget();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

}
