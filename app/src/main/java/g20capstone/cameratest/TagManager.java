package g20capstone.cameratest;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

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

    private static final String ACTION_USB_PERMISSION = "g20capstone.cameratest.USB_PERMISSION";

    public TagManager(Context context) {
        this.context = context;
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);

        //Get main manager for FTDI devices
        try {
            d2xxManager = D2xxManager.getInstance(context);
            usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        } catch (D2xxManager.D2xxException e) {
            e.printStackTrace();
        }
    }

    public void onStart() {
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
        context.registerReceiver(usbReceiver, filter);
    }

    public void onStop() {
        context.unregisterReceiver(usbReceiver);
        context.unregisterReceiver(usbReceiverDetach);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("Drew", "USB Received");
            String action = intent.getAction();
            usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (!usbManager.hasPermission(usbDevice)) {
                Log.d("Drew", "requesting permission");
                usbManager.requestPermission(usbDevice, mPermissionIntent);
                return;
            }

            if (ACTION_USB_PERMISSION.equals(action)) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.d("Drew", "Permission granted for USB.");
                    connectToDevice();
                } else {
                    Log.d("Drew", "USB permission not granted.");
                }
            } else {
                Log.d("Drew", "Action not correct: " + action);
            }
        }

        public void connectToDevice() {
            d2xxManager.createDeviceInfoList(context);
            if (d2xxManager.isFtDevice(usbDevice)) {
                Log.d("Drew", "Granted permission for FTDI USB device.");
                ftDevice = d2xxManager.openByUsbDevice(context, usbDevice);
                if (ftDevice == null || !ftDevice.isOpen()) {
                    Log.d("Drew", "Error, clearing.");
                    if (ftDevice == null) {
                        Log.d("Drew", "Error, clearing. Was null.");
                    } else {
                        Log.d("Drew", "Error, clearing. Open failed.");
                    }
                    //Error, clear everything
                    usbDevice = null;
                    setFtDevice(null);
                    return;
                }

                Log.v("Drew", "Successfully opened.");
                ftDevice.setBaudRate(19200);
            } else {
                Log.d("Drew", "Device not FTDI.");
            }
        }
    };

    private final BroadcastReceiver usbReceiverDetach = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //usbDevice = null;
            //setFtDevice(null);
        }
    };

    public FT_Device getFtDevice() {
        return ftDevice;
    }

    public void setFtDevice(FT_Device ftDevice) {
        this.ftDevice = ftDevice;
    }
}
