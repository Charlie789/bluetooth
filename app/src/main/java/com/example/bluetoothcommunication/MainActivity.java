package com.example.bluetoothcommunication;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    TextView main_textview, status_textview;
    Button listen_button, send_button, list_devices_button;
    EditText write_msg;
    ListView device_listview;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;

    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REQUEST_ENABLE_BLUETOOTH = 1;

    private static final String APP_NAME = "BTCommunicator";
    private static final UUID MY_UUID = UUID.fromString("8ac8c6f0-cce4-4cf8-9b44-ad722bb0dc5c");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        find_view_by_ids();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(!bluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }
        implement_listeners();
    }

    private void implement_listeners() {
        list_devices_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
                String[] strings = new String[bt.size()];
                btArray = new BluetoothDevice[bt.size()];
                int index = 0;

                if (bt.size() > 0){
                    for (BluetoothDevice device : bt){
                        btArray[index] = device;
                        strings[index] = device.getName();
                        index++;
                    }
                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);
                    device_listview.setAdapter(arrayAdapter);
                }
            }
        });

        listen_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Server server = new Server();
                server.start();
            }
        });

        device_listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Client client = new Client(btArray[position]);
                client.start();

                status_textview.setText("Connecting");
            }
        });

        send_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String string = String.valueOf(write_msg.getText());
                sendReceive.write(string.getBytes());
            }
        });

    }

    private void find_view_by_ids(){
        main_textview = (TextView) findViewById(R.id.main_textview);
        status_textview = (TextView) findViewById(R.id.status_textview);
        listen_button = (Button) findViewById(R.id.listen_button);
        send_button = (Button) findViewById(R.id.send_button);
        list_devices_button = (Button) findViewById(R.id.list_devices_button);
        write_msg = (EditText) findViewById(R.id.write_msg);
        device_listview = (ListView) findViewById(R.id.list_devices);
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case STATE_LISTENING:
                    status_textview.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status_textview.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    status_textview.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status_textview.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    main_textview.setText(tempMsg);
                    break;

            }
            return true;
        }
    });

    private class Server extends Thread {
        private BluetoothServerSocket server_socket;

        public Server() {
            try {
                server_socket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run() {
            BluetoothSocket socket = null;

            while(socket == null){
                try {
                    Message msg = Message.obtain();
                    msg.what = STATE_CONNECTING;
                    handler.sendMessage(msg);

                    socket = server_socket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message msg = Message.obtain();
                    msg.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(msg);
                }
                if (socket != null){
                    Message msg = Message.obtain();
                    msg.what = STATE_CONNECTED;
                    handler.sendMessage(msg);

                    sendReceive = new SendReceive(socket);
                    sendReceive.start();

                    break;
                }
            }
        }
    }

    private class Client extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public Client(BluetoothDevice device1){
            device = device1;

            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            try {
                socket.connect();
                Message msg = Message.obtain();
                msg.what = STATE_CONNECTED;
                handler.sendMessage(msg);

                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
                Message msg = Message.obtain();
                msg.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(msg);
            }
        }
    }

    private class SendReceive extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive(BluetoothSocket socket){
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tempIn;
            outputStream = tempOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            while (true){
                try {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
