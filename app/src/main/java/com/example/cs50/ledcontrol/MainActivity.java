package com.example.cs50.ledcontrol;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    ImageView ivBTCheck;
    BluetoothAdapter bluetoothAdapter;
    static final int REQUEST_ENABLE_BT = 10;
    int pairedDeviceCount = 0;
    Set<BluetoothDevice> deviceSet; //장치 가져올 때
    BluetoothDevice remoteDevice;
    BluetoothSocket socket = null; //통신하기 위한 소켓이 필요
    OutputStream outputStream = null;
    InputStream inputStream = null;
    Thread workerThread = null;
    String strDelimiter = "\n"; // 문자열 끝
    char charDelimiter = '\n';
    byte[] readBuffer;
    int readBufferPosition;

    //led
    String str;
    Switch led1, led2, led3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        led1 = findViewById(R.id.switch1);
        led2 = findViewById(R.id.switch2);
        led3 = findViewById(R.id.switch3);
        ivBTCheck = findViewById(R.id.ivBTcheck);

        led1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    str = "11";
                } else {
                    str = "10";
                }
                //데이터 송신 메소드 호출
                sendData(str);
            }
        });
        led2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    str = "21";
                } else {
                    str = "20";
                }
                sendData(str);
            }
        });

        led3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    str = "31";
                } else {
                    str = "30";
                }
                sendData(str);
            }
        });

        ivBTCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkBluetooth();
            }
        });
        //앱이 실행되지마자 실행하게 하려면 onCreate에 바로 블루투스 체크
    }

    void checkBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //메소드로 어댑터 반환함
        if (bluetoothAdapter == null) {
            //블루투스 지원 안됨
            //finish()
            //toast msg
            //show dialog
        } else {
            //블루투스 지원되는 장치
            if (!bluetoothAdapter.isEnabled()) {
                //안켜진 경우
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            } else {
                //켜진 경우
                selectDevice();

            }
        }
    }

    //블루투스 장치 선택(페어링 된 장치 중에서)
    void selectDevice() {
        deviceSet = bluetoothAdapter.getBondedDevices();
        //페어링된 목록이 변수에 들어감
        pairedDeviceCount = deviceSet.size(); // 장치 개수 들어감
        if (pairedDeviceCount == 0) {
            //페어링된 장치 0
            showToast("연결된 장치가 없습니다");
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("블루투스 장치 선택");
            //동적 배열
            List<String> listItems = new ArrayList<>();
            for (BluetoothDevice device : deviceSet) {
                listItems.add(device.getName());
            }
            listItems.add("취소");
            final CharSequence items[] = listItems.toArray(new CharSequence[listItems.size()]);
            //동적 배열을 일반배열로 바꿔줌
            // 밑의 메소드는 일반 배열만 가져올 수 있기 때문에
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == pairedDeviceCount) {//취소 눌렀을 때
                        showToast("취소를 선택했습니다. ");
                    } else {
                        //연결할 장치 선택할 때 장치와 연결을 시도함
                        connectToSelectedDevice(items[which].toString());
                    }
                }
            });
            builder.setCancelable(false); //뒤로가기 버튼 금지
            AlertDialog dialog = builder.create();
            dialog.show();
            // 다이얼로그에서 찾는 작업은 실제로 찾는 게 아님
        }
    }

    void connectToSelectedDevice(String selectedDeviceName) {
        remoteDevice = getDeviceFromList(selectedDeviceName); // 객체를 돌려줌
        //블루투스를 연결하는 소켓..? hc 06 -  제품 식별자
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        try {
            socket = remoteDevice.createRfcommSocketToServiceRecord(uuid); //장치 아이디 보냄 ? 연결?
            socket.connect();// 연결
            ivBTCheck.setImageResource(R.drawable.bluetooth_icon);
            //데이터 실제 송수신은 입출력스트림을 이용해서 이루어짐
            // 소켓이 생성되면 커넥트를 호출하고 두 기기가 연결이 완료 된다 socket.connect()
            outputStream = socket.getOutputStream(); // 보낼거
            inputStream = socket.getInputStream(); //받을거

        } catch (Exception e) {
            showToast("기기와 연결할 수 없습니다.");
        }
    }

    // 데이터 수신 준비 및 처리 메소드
    void beginListenForData() {
        final Handler handler = new Handler();
        readBuffer = new byte[1024]; //수신 처리
        readBufferPosition = 0;// 버퍼 내 수신된 문자 저장 위치
        //문자열 수신 스레드
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    //인터럽트 되지 않는한 계속 실행
                    try {
                        int bytesAvailable = inputStream.available(); //수신 데이터 확인
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable]; //가져온 값을 패킷바이트에 넣음
                            inputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == charDelimiter) {
                                    //마지막이면 \n을 만남
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII"); //아스켓 코드값으로
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() { // 이 부분만 달라지게..?
                                            //수신된 문자열 데이터에 대한 처리 작업
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                    //값을 배열에 하나씩 넣음
                                }
                            }
                        }
                    } catch (IOException e) {
                        showToast("데이터 수신 중 오류가 발생했습니다.");
                    }
                }
            }
        });
        workerThread.start();
    }

    //데이터 송신
    void sendData(String msg) {
        msg += strDelimiter; //문자열 종료 표시가 나오면 데이터를 다 받은 것
        try {
            outputStream.write(msg.getBytes());//아두이노로 문자열을 전송함

        } catch (Exception e) {
            showToast("문자열 전송 도중에 에러가 발생했습니다.");
        }
    }

    //블루투스 소켓 닫기, 데이터 수신 스레드 종료
    @Override
    protected void onDestroy() {
        try {
            workerThread.interrupt(); // 스레드 종료
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (Exception e) {
            showToast("앱 종료중 에러가 발생했습니다");
        }
        super.onDestroy();
    }

    //페어링된 장치를 이름으로 찾기 -> 실제로 디바이스를 찾는 작업
    BluetoothDevice getDeviceFromList(String name) {
        BluetoothDevice selectedDevice = null; //초기화
        for (BluetoothDevice device : deviceSet) {
            if (name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }


    void showToast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                //블루투스 장치 활성화 여부
                if (resultCode == RESULT_OK) {
                    //장치 선택
                    selectDevice();
                } else if (resultCode == RESULT_CANCELED) {
                    //finish();
                    //toast

                }
                break;

        }
    }
}