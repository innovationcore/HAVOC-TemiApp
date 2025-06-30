package edu.uky.ai.roguetemi.streaming;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class SmellSensorUtils {

    //https://github.com/mik3y/usb-serial-for-android
    //Need to implement connection classes

    private UsbSerialPort port;
    private UsbDeviceConnection connection;
    private Context context;
    private final String TAG = "SmellSensorUtils()";

    public SmellSensorUtils() {
        this.port = null;
        this.connection = null;
        this.context = null;
    }

    public boolean init(Context c) {
        this.context = c.getApplicationContext();

        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) c.getSystemService(Context.USB_SERVICE);

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "availableDrivers.isEmpty()");
            return false;
        }

        for(UsbSerialDriver usbSerialDriver : availableDrivers) {
            System.out.println(usbSerialDriver.getDevice().getDeviceName());
        }

        String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
        PendingIntent permissionIntent = PendingIntent.getBroadcast(c, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        manager.requestPermission(driver.getDevice(), permissionIntent);

        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            Log.e(TAG, "connection == null");
            return false;
        }
        try {
            this.connection = connection;
            this.port = driver.getPorts().get(0); // Most devices have just one port (port 0)
            this.port.open(this.connection);
            this.port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            Log.d(TAG, "USB port opened");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    public String readData() {
        byte[] buffer = new byte[512];
        String data = null;
        String smell = "I have allergies, I can't smell right now.";
        try {
            if (this.connection != null) {
                if (this.port != null) {
                    if (!this.port.isOpen()) {
                        System.out.println("USB port is closed, reopening.");
                        this.port.open(this.connection);
                        this.port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                    }
                    int len = this.port.read(buffer, 3000);
                    if (len > 0) {
                        String tempData = new String(buffer, StandardCharsets.UTF_8);
                        tempData = tempData.split("\n")[0];
                        tempData = tempData.replaceAll("\r", "");
                        if (tempData.startsWith("start") && !tempData.contains("?")) {
                            data = tempData;
                        }
                    }

                    if (data != null) {
                        // send data to some server for processing
                        updateSmellData(data);
                        smell = data;
                    }
                } else {
                    Log.e(TAG, "Port is null");
                }
            } else {
                Log.e(TAG, "Connection is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred.");
            e.printStackTrace();
        }
        return smell;
    }

    public void updateSmellData(String data){
        WebRTCStreamingManager.updateSmellData(data);
    }

    public void close() {
        try {
            this.port.close();
            Log.i(TAG, "USB port closed.");
        } catch (Exception e) {
            Log.e(TAG, "Could not close USB port.");
            e.printStackTrace();
        }
    }
}