package g20capstone.arlocationtracking;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Process;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Drew on 1/8/2017.
 */

public class TagManager {
    private D2xxManager d2xxManager;
    private UsbManager usbManager;
    private Context context;
    private UsbDevice usbDevice;
    private FT_Device ftDevice;
    private PendingIntent mPermissionIntent;
    private TagParser mTagParser;
    private TagReadRunnable curReadRunnable;

    private static final String ACTION_USB_PERMISSION = "g20capstone.cameratest.USB_PERMISSION";

    public TagManager(Context context, TagParser tagParser) {
        this.context = context;
        this.mTagParser = tagParser;
        mPermissionIntent = PendingIntent.getBroadcast(context, 12, new Intent(ACTION_USB_PERMISSION), 0);

        //Get main manager for FTDI devices
        try {
            d2xxManager = D2xxManager.getInstance(context);
            usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        } catch (D2xxManager.D2xxException e) {
            e.printStackTrace();
        }
    }

    protected class TagReadRunnable implements Runnable {
        public volatile boolean keepRunning = true;

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
            byte[] buffer = new byte[1024];
            int len;

            while (keepRunning) {
                long start = 0;
                try {
                    FT_Device ftd = getFtDevice();

                    if (ftd != null && ftd.isOpen()) {
                        int numBytesAvailable = ftd.getQueueStatus();

                        if (numBytesAvailable > 0) {
                            try {
                                len = ftd.read(buffer, Math.min(buffer.length, numBytesAvailable));
                            } catch (Exception e) {
                                len = 0; //device disconnected or something
                            }
                            if (len > 0) {
                                final String text = new String(buffer, 0, len);
                                //Log.d("Runnable", "Read bytes: " + text);

                                mTagParser.addString(text);
                            }
                        }
                    }

                    Thread.sleep(0, 1);
                    //Thread.yield(); //Sleep just a little so as to not burn infinite battery
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onResume() {
        //Set up USB device callbacks
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.setPriority(500); //Not sure why priority is needed; this line was in example  j2xx code
        context.registerReceiver(usbReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.setPriority(500);
        context.registerReceiver(usbReceiverDetach, filter);

        filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.setPriority(500);
        context.registerReceiver(usbReceiverPermission, filter);

        if (curReadRunnable != null) {
            curReadRunnable.keepRunning = false;
            curReadRunnable = null;
        }
        curReadRunnable = new TagReadRunnable();
        new Thread(curReadRunnable).start();
    }

    public void onPause() {
        context.unregisterReceiver(usbReceiver);
        context.unregisterReceiver(usbReceiverDetach);
        context.unregisterReceiver(usbReceiverPermission);

        if (curReadRunnable != null) {
            curReadRunnable.keepRunning = false;
            curReadRunnable = null;
        }
    }

    public void pollConnectedDevices() {
        if (usbDevice == null) {
            //Look for already connected devices
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            while (deviceIterator.hasNext()) {
                UsbDevice d = deviceIterator.next();
                tryConnectDevice(d);
            }
        }
    }

    private void tryConnectDevice(UsbDevice d) {
/*        if (!usbManager.hasPermission(d)) {
            Log.d("TagManager", "Requesting permission for USB device.");
            usbManager.requestPermission(d, mPermissionIntent);
            return;
        } else {
            usbDevice = d;
            Log.d("TagManager", "Permission granted for USB.");
            connectToDevice();
            return;
        }*/
        usbManager.requestPermission(d, mPermissionIntent);
    }

    public void connectToDevice() {
        d2xxManager.createDeviceInfoList(context);
        if (d2xxManager.isFtDevice(usbDevice)) {
            Log.d("TagManager", "Granted permission for FTDI USB device.");
            ftDevice = d2xxManager.openByUsbDevice(context, usbDevice);
            if (ftDevice == null || !ftDevice.isOpen()) {
                Log.d("TagManager", "Error, clearing.");
                if (ftDevice == null) {
                    Log.d("TagManager", "Error, clearing. Was null.");
                } else {
                    Log.d("TagManager", "Error, clearing. Open failed.");
                }
                //Error, clear everything
                usbDevice = null;
                setFtDevice(null);
                return;
            }

            Log.v("TagManager", "Successfully opened.");
            ftDevice.setBaudRate(1000000);
        } else {
            Log.d("TagManager", "Device not FTDI.");
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        //Set usb device so that the polling will be skipped
        usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        usbManager.requestPermission(usbDevice, mPermissionIntent);
        //tryConnectDevice((UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
        }
    };

    private final BroadcastReceiver usbReceiverDetach = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            usbDevice = null;
            ftDevice = null;
        }
    };

    private final BroadcastReceiver usbReceiverPermission = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action) && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                connectToDevice();
            }
        }
    };

    public FT_Device getFtDevice() {
        return ftDevice;
    }

    public void setFtDevice(FT_Device ftDevice) {
        this.ftDevice = ftDevice;
    }
}
