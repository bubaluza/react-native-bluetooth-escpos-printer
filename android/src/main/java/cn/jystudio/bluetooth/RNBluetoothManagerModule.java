package cn.jystudio.bluetooth;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.jystudio.bluetooth.BluetoothService.DEVICE_ADDRESS;

/**
 * Created by januslo on 2018/9/22.
 */
public class RNBluetoothManagerModule extends ReactContextBaseJavaModule
        implements ActivityEventListener, BluetoothServiceStateObserver {

    private static final String TAG = "BluetoothManager";
    private final ReactApplicationContext reactContext;
    public static final String EVENT_DEVICE_ALREADY_PAIRED = "EVENT_DEVICE_ALREADY_PAIRED";
    public static final String EVENT_DEVICE_FOUND = "EVENT_DEVICE_FOUND";
    public static final String EVENT_DEVICE_DISCOVER_DONE = "EVENT_DEVICE_DISCOVER_DONE";
    public static final String EVENT_CONNECTION_LOST = "EVENT_CONNECTION_LOST";
    public static final String EVENT_UNABLE_CONNECT = "EVENT_UNABLE_CONNECT";
    public static final String EVENT_CONNECTED = "EVENT_CONNECTED";
    public static final String EVENT_BLUETOOTH_NOT_SUPPORT = "EVENT_BLUETOOTH_NOT_SUPPORT";


    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    public static final int MESSAGE_STATE_CHANGE = BluetoothService.MESSAGE_STATE_CHANGE;
    public static final int MESSAGE_READ = BluetoothService.MESSAGE_READ;
    public static final int MESSAGE_WRITE = BluetoothService.MESSAGE_WRITE;
    public static final int MESSAGE_DEVICE_NAME = BluetoothService.MESSAGE_DEVICE_NAME;

    public static final int MESSAGE_CONNECTION_LOST = BluetoothService.MESSAGE_CONNECTION_LOST;
    public static final int MESSAGE_UNABLE_CONNECT = BluetoothService.MESSAGE_UNABLE_CONNECT;
    public static final String DEVICE_NAME = BluetoothService.DEVICE_NAME;
    public static final String TOAST = BluetoothService.TOAST;

    // Return Intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    private static final Map<String, Promise> promiseMap = Collections.synchronizedMap(new HashMap<String, Promise>());
    private static final String PROMISE_ENABLE_BT = "ENABLE_BT";
    private static final String PROMISE_SCAN = "SCAN";
    private static final String PROMISE_CONNECT = "CONNECT";

    private List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private List<BluetoothDevice> foundDevice = new ArrayList<>();
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the services
    private BluetoothService mService = null;

    public RNBluetoothManagerModule(ReactApplicationContext reactContext, BluetoothService bluetoothService) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(this);
        this.mService = bluetoothService;
        this.mService.addStateObserver(this);
        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.reactContext.registerReceiver(discoverReceiver, filter);
    }

    @Override
    public
    @Nullable
    Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        constants.put(EVENT_DEVICE_ALREADY_PAIRED, EVENT_DEVICE_ALREADY_PAIRED);
        constants.put(EVENT_DEVICE_DISCOVER_DONE, EVENT_DEVICE_DISCOVER_DONE);
        constants.put(EVENT_DEVICE_FOUND, EVENT_DEVICE_FOUND);
        constants.put(EVENT_CONNECTION_LOST, EVENT_CONNECTION_LOST);
        constants.put(EVENT_UNABLE_CONNECT, EVENT_UNABLE_CONNECT);
        constants.put(EVENT_CONNECTED, EVENT_CONNECTED);
        constants.put(EVENT_BLUETOOTH_NOT_SUPPORT, EVENT_BLUETOOTH_NOT_SUPPORT);
        constants.put(DEVICE_NAME, DEVICE_NAME);
        return constants;
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (mBluetoothAdapter == null) {
            // Get local Bluetooth adapter
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            emitRNEvent(EVENT_BLUETOOTH_NOT_SUPPORT, Arguments.createMap());
        }

        return mBluetoothAdapter;
    }

    public void requestPermission() {
//            the ACCESS_COARSE_LOCATION may need in ANDROID API < 30
        int permissionChecked = ContextCompat.checkSelfPermission(reactContext, android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionChecked == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(getCurrentActivity(),
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }
    }

    public List<BluetoothDevice> getPairedDevices() {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        pairedDevices = new ArrayList<>();
        requestPermission();

        Set<BluetoothDevice> boundDevices = adapter.getBondedDevices();
        Log.d(TAG, "getPairedDevices: " + boundDevices.toString());
        pairedDevices.addAll(boundDevices);

        return pairedDevices;
    }

    public WritableArray listToArray(List<BluetoothDevice> list) {
        WritableArray array = Arguments.createArray();
        for (BluetoothDevice device : list) {
            try {
                WritableMap device_obj = Arguments.createMap();
                device_obj.putString("name", device.getName());
                device_obj.putString("address", device.getAddress());
                array.pushMap(device_obj);
            } catch (Exception e) {
                Log.d(TAG, "listToArray: " + e.toString());
            }
        }
        return array;
    }


    @ReactMethod
    public void enableBluetooth(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter == null) {
            promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT);
        } else if (!adapter.isEnabled()) {
            // If Bluetooth is not on, request that it be enabled.
            // setupChat() will then be called during onActivityResult
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            promiseMap.put(PROMISE_ENABLE_BT, promise);
            this.reactContext.startActivityForResult(enableIntent, REQUEST_ENABLE_BT, Bundle.EMPTY);
        } else {
            pairedDevices = getPairedDevices();
            Log.d(TAG, "ble Enabled");
            WritableMap param = Arguments.createMap();
            param.putArray("paired", listToArray(pairedDevices));
            promise.resolve(param);
        }
    }

    @ReactMethod
    public void disableBluetooth(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter == null) {
            promise.resolve(true);
        } else {
            if (mService != null && mService.getState() != BluetoothService.STATE_NONE) {
                mService.stop();
            }
            promise.resolve(!adapter.isEnabled() || adapter.disable());
        }
    }

    @ReactMethod
    public void isBluetoothEnabled(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        WritableMap map = Arguments.createMap();
        if (adapter == null) {
            map.putBoolean("status", false);
            promise.resolve(map);
        } else if (adapter.isEnabled()) {
            pairedDevices = getPairedDevices();

            map.putBoolean("status", true);
            map.putArray("paired", listToArray(pairedDevices));

            promise.resolve(map);
        } else {
            map.putBoolean("status", false);
            promise.resolve(map);
        }
    }

    @ReactMethod
    public void scanDevices(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter == null) {
            promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT);
        } else {
            cancelDisCovery();
            requestPermission();

            pairedDevices = getPairedDevices();
            foundDevice = new ArrayList<>();

            WritableMap params = Arguments.createMap();
            params.putArray("devices", listToArray(pairedDevices));
            emitRNEvent(EVENT_DEVICE_ALREADY_PAIRED, params);
            if (!adapter.startDiscovery()) {
                promise.reject("DISCOVER", "NOT_STARTED");
                cancelDisCovery();
            } else {
                promiseMap.put(PROMISE_SCAN, promise);
            }
        }
    }

    @ReactMethod
    public void connect(String address, final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter != null && adapter.isEnabled()) {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            promiseMap.put(PROMISE_CONNECT, promise);
            mService.connect(device);
        } else {
            promise.reject("BT NOT ENABLED");
        }

    }

    @ReactMethod
    public void unpaire(String address, final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter != null && adapter.isEnabled()) {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            this.unpairDevice(device);
            promise.resolve(address);
        } else {
            promise.reject("BT NOT ENABLED");
        }

    }


    /*
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    // public static final int STATE_LISTEN = 1;     // now listening for incoming connections //feathure removed.
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
*/
    @ReactMethod
    public void isDeviceConnected(final Promise promise) {
        boolean isConnected = true;

        if (mService != null) {
            if (mService.getState() != 3) {
                isConnected = false;
            }
            promise.resolve(isConnected);
        } else {
            promise.resolve(false);
        }
    }


    /* Return the address of the currently connected device */
    @ReactMethod
    public void getConnectedDeviceAddress(final Promise promise) {
        if (mService != null) {
            promise.resolve(mService.getLastConnectedDeviceAddress());
        } else {
            promise.reject("NoDeviceConnected");
        }

    }


    private void unpairDevice(BluetoothDevice device) {
        try {
            Method m = device.getClass()
                    .getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void cancelDisCovery() {
        try {
            BluetoothAdapter adapter = this.getBluetoothAdapter();
            if (adapter != null && adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            Log.d(TAG, "Discover canceled");
        } catch (Exception e) {
            //ignore
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE: {
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(
                            EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    if (adapter != null && BluetoothAdapter.checkBluetoothAddress(address)) {
                        BluetoothDevice device = adapter
                                .getRemoteDevice(address);
                        // Attempt to connect to the device
                        mService.connect(device);
                    }
                }
                break;
            }
            case REQUEST_ENABLE_BT: {
                Promise promise = promiseMap.remove(PROMISE_ENABLE_BT);
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK && promise != null) {
                    // Bluetooth is now enabled, so set up a session
                    pairedDevices = getPairedDevices();
                    if (adapter != null) {
                        WritableMap map = Arguments.createMap();
                        map.putArray("paired", listToArray(pairedDevices));
                        promise.resolve(map);
                    } else {
                        promise.resolve(null);
                    }

                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d(TAG, "BT not enabled");
                    if (promise != null) {
                        promise.reject("ERR", new Exception("BT NOT ENABLED"));
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    @Override
    public String getName() {
        return "BluetoothManager";
    }


    private boolean objectFound(BluetoothDevice device) {
        boolean found = false;

        for (BluetoothDevice next : foundDevice) {
            try {
                if (next.getAddress().equals(device.getAddress())) {
                    found = true;
                }
            } catch (Exception ignored) {
            }
        }
        return found;
    }

    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "on receive:" + action);
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    WritableMap deviceFound = Arguments.createMap();
                    try {
                        deviceFound.putString("name", device.getName());
                        deviceFound.putString("address", device.getAddress());
                    } catch (Exception e) {
                        //ignore
                    }
                    if (!objectFound(device)) {
                        foundDevice.add(device);
                        WritableMap params = Arguments.createMap();
                        params.putMap("device", deviceFound);
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit(EVENT_DEVICE_FOUND, params);
                    }

                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                WritableMap params = Arguments.createMap();
                params.putArray("paired", listToArray(pairedDevices));
                params.putArray("found", listToArray(foundDevice));
                Promise promise = promiseMap.remove(PROMISE_SCAN);

                if (promise != null) {
                    promise.resolve(params);
                } else {
                    emitRNEvent(EVENT_DEVICE_DISCOVER_DONE, params);
                }
            }
        }
    };

    private void emitRNEvent(String event, @Nullable WritableMap params) {
        getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(event, params);
    }

    @Override
    public void onBluetoothServiceStateChanged(int state, Map<String, Object> bundle) {
        Log.d(TAG, "on bluetoothServiceStatChange:" + state);
        switch (state) {
            case BluetoothService.STATE_CONNECTED:
            case MESSAGE_DEVICE_NAME: {
                // save the connected device's name
                mConnectedDeviceName = (String) bundle.get(DEVICE_NAME);
                Promise p = promiseMap.remove(PROMISE_CONNECT);
                if (p == null) {
                    Log.d(TAG, "No Promise found.");
                    WritableMap params = Arguments.createMap();
                    params.putString(DEVICE_NAME, mConnectedDeviceName);
                    emitRNEvent(EVENT_CONNECTED, params);
                } else {
                    Log.d(TAG, "Promise Resolve.");
                    p.resolve(mConnectedDeviceName);
                }

                break;
            }
            case MESSAGE_CONNECTION_LOST: {
                //Connection lost should not be the connect result.
                // Promise p = promiseMap.remove(PROMISE_CONNECT);
                WritableMap params = Arguments.createMap();
                WritableMap device = Arguments.createMap();

                device.putString("name", (String) bundle.get(DEVICE_NAME));
                device.putString("address", (String) bundle.get(DEVICE_ADDRESS));

                params.putMap("device", device);
                // if (p == null) {
                emitRNEvent(EVENT_CONNECTION_LOST, params);
                // } else {
                //   p.reject("Device connection was lost");
                //}
                break;
            }
            case MESSAGE_UNABLE_CONNECT: {     //无法连接设备
                Promise p = promiseMap.remove(PROMISE_CONNECT);
                if (p == null) {
                    emitRNEvent(EVENT_UNABLE_CONNECT, null);
                } else {
                    p.reject("Unable to connect device");
                }

                break;
            }
            default:
                break;
        }
    }
}
