package com.tipsybird.game;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "TipsyBluetooth", permissions = {
    @Permission(alias = "tb12", strings = { Manifest.permission.BLUETOOTH_CONNECT }),
    @Permission(alias = "tbscan", strings = { Manifest.permission.BLUETOOTH_SCAN }),
    @Permission(alias = "tbold", strings = { Manifest.permission.ACCESS_FINE_LOCATION })
})
public class BluetoothPlugin extends Plugin {
    private static final UUID APP_UUID = UUID.fromString("e573ab3c-8e1f-4f7a-9b2d-31e6a1c7d001");
    private static final int MAX_GUESTS = 7;

    private final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
    private final AtomicBoolean hosting = new AtomicBoolean(false);
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final Map<String, Link> links = new ConcurrentHashMap<>();
    private final ArrayList<String> foundIds = new ArrayList<>();
    private BluetoothServerSocket server;
    private BroadcastReceiver recv;
    private String pendingOp = null;
    private String pendingMac = null;

    private static class Link {
        BluetoothSocket sock;
        DataInputStream in;
        DataOutputStream out;
        Thread rd;
        String name;
    }

    private interface Thunk { void run(); }

    private boolean granted(String p) {
        Activity a = bridge == null ? null : bridge.getActivity();
        if (a == null) return false;
        return Build.VERSION.SDK_INT < 23 || a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean gate(final PluginCall call, final String op, final String mac) {
        ArrayList<String> aliases = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) aliases.add("tb12");
            if ("scan".equals(op) && !granted(Manifest.permission.BLUETOOTH_SCAN)) aliases.add("tbscan");
        } else if ("scan".equals(op) && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            aliases.add("tbold");
        }
        if (!aliases.isEmpty()) {
            pendingOp = op;
            pendingMac = mac;
            requestPermissionForAliases(aliases.toArray(new String[0]), call, "permCb");
            return false;
        }
        return true;
    }

    @PermissionCallback
    private void permCb(PluginCall call) {
        String op = pendingOp;
        pendingOp = null;
        if ("host".equals(op)) host(call);
        else if ("connect".equals(op)) connect(call);
        else if ("scan".equals(op)) scan(call);
        else if ("paired".equals(op)) paired(call);
        else call.resolve();
    }

    private JSObject fail(String need) {
        JSObject r = new JSObject();
        r.put("ok", false);
        if (need != null) r.put("need", need);
        return r;
    }

    private JSObject okRes() {
        JSObject r = new JSObject();
        r.put("ok", true);
        return r;
    }

    private void emit(String type, String id, String name, String text) {
        JSObject ev = new JSObject();
        ev.put("type", type);
        if (id != null) ev.put("id", id);
        if (name != null) ev.put("name", name);
        if (text != null) ev.put("text", text);
        try {
            notifyListeners("btevent", ev);
        } catch (Exception ignored) {
        }
    }

    private void emitDevices(ArrayList<JSObject> items) {
        JSObject ev = new JSObject();
        ev.put("type", "devices");
        JSArray arr = new JSArray();
        for (JSObject o : items) arr.put(o);
        ev.put("items", arr);
        try {
            notifyListeners("btevent", ev);
        } catch (Exception ignored) {
        }
    }

    private void addLink(final BluetoothSocket sock, String name) {
        final String id = sock.getRemoteDevice().getAddress();
        Link old = links.remove(id);
        if (old != null) closeLink(old);
        final Link L = new Link();
        try {
            L.sock = sock;
            L.name = name;
            L.in = new DataInputStream(new BufferedInputStream(sock.getInputStream(), 8192));
            L.out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream(), 8192));
        } catch (Exception e) {
            closeLink(L);
            return;
        }
        links.put(id, L);
        emit("peer", id, name, null);
        L.rd = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        String msg = L.in.readUTF();
                        emit("data", id, null, msg);
                    }
                } catch (Exception ignored) {
                }
                dropLink(id, L);
            }
        });
        L.rd.setName("tb-read-" + id);
        L.rd.setDaemon(true);
        L.rd.start();
    }

    private void dropLink(String id, Link L) {
        boolean was = links.remove(id, L);
        closeLink(L);
        if (was) emit("close", id, null, null);
    }

    private void closeLink(Link L) {
        if (L == null) return;
        try {
            if (L.out != null) L.out.close();
        } catch (Exception ignored) {
        }
        try {
            if (L.in != null) L.in.close();
        } catch (Exception ignored) {
        }
        try {
            if (L.sock != null) L.sock.close();
        } catch (Exception ignored) {
        }
    }

    private void stopAll() {
        hosting.set(false);
        stopScanOnUiThread();
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        for (Map.Entry<String, Link> e : links.entrySet()) closeLink(e.getValue());
        links.clear();
    }

    private void stopScanOnUiThread() {
        if (ad != null && scanning.get()) {
            try {
                ad.cancelDiscovery();
            } catch (Exception ignored) {
            }
        }
        scanning.set(false);
        if (recv != null) {
            try {
                bridge.getActivity().unregisterReceiver(recv);
            } catch (Exception ignored) {
            }
            recv = null;
        }
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("ok", true);
        r.put("on", ad != null && ad.isEnabled());
        r.put("sdk", Build.VERSION.SDK_INT);
        call.resolve(r);
    }

    @PluginMethod
    public void enable(PluginCall call) {
        if (ad == null) {
            call.resolve(fail("nobt"));
            return;
        }
        if (ad.isEnabled()) {
            call.resolve(okRes());
            return;
        }
        try {
            Activity a = bridge.getActivity();
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            call.resolve(fail("enable"));
        } catch (Exception e) {
            call.resolve(fail("enable"));
        }
    }

    @PluginMethod
    public void discoverable(PluginCall call) {
        if (ad == null || !ad.isEnabled()) {
            call.resolve(fail(ad == null ? "nobt" : "enable"));
            return;
        }
        try {
            Activity a = bridge.getActivity();
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            call.resolve(okRes());
        } catch (Exception e) {
            call.resolve(fail("disc"));
        }
    }

    @PluginMethod
    public void paired(PluginCall call) {
        if (!gate(call, "paired", null)) return;
        JSArray items = new JSArray();
        if (ad != null && (Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT))) {
            Set<BluetoothDevice> bs = null;
            try {
                bs = ad.getBondedDevices();
            } catch (Exception ignored) {
            }
            if (bs != null) {
                for (BluetoothDevice d : bs) {
                    JSObject o = new JSObject();
                    o.put("id", d.getAddress());
                    try {
                        String n = d.getName();
                        if (n != null) o.put("name", n);
                    } catch (Exception ignored) {
                    }
                    o.put("paired", true);
                    try {
                        items.put(o);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        JSObject r = new JSObject();
        r.put("items", items);
        r.put("ok", true);
        call.resolve(r);
    }

    @PluginMethod
    public void scan(PluginCall call) {
        if (!gate(call, "scan", null)) return;
        if (ad == null) {
            call.resolve(fail("nobt"));
            return;
        }
        if (!ad.isEnabled()) {
            call.resolve(fail("enable"));
            return;
        }
        if (scanning.get()) {
            call.resolve(okRes());
            return;
        }
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(BluetoothDevice.ACTION_FOUND);
            f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            if (recv != null) {
                try {
                    bridge.getActivity().unregisterReceiver(recv);
                } catch (Exception ignored) {
                }
            }
            recv = new BroadcastReceiver() {
                public void onReceive(Context c, Intent i) {
                    String act = i.getAction();
                    if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(act)) {
                        scanning.set(false);
                        emit("scanend", null, null, null);
                    } else if (BluetoothDevice.ACTION_FOUND.equals(act)) {
                        BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (d == null) return;
                        String id = d.getAddress();
                        synchronized (foundIds) {
                            if (foundIds.contains(id)) return;
                            foundIds.add(id);
                        }
                        ArrayList<JSObject> one = new ArrayList<JSObject>();
                        JSObject o = new JSObject();
                        o.put("id", id);
                        try {
                            String n = d.getName();
                            if (n != null) o.put("name", n);
                        } catch (Exception ignored) {
                        }
                        o.put("paired", d.getBondState() == BluetoothDevice.BOND_BONDED);
                        one.add(o);
                        emitDevices(one);
                    }
                }
            };
            bridge.getActivity().registerReceiver(recv, f);
            foundIds.clear();
            scanning.set(true);
            ad.startDiscovery();
            call.resolve(okRes());
        } catch (Exception e) {
            scanning.set(false);
            call.resolve(fail("scan"));
        }
    }

    @PluginMethod
    public void stopScan(PluginCall call) {
        stopScanOnUiThread();
        call.resolve(okRes());
    }

    @PluginMethod
    public void host(PluginCall call) {
        if (ad == null) {
            call.resolve(fail("nobt"));
            return;
        }
        if (!gate(call, "host", null)) return;
        if (!ad.isEnabled()) {
            call.resolve(fail("enable"));
            return;
        }
        if (hosting.get()) {
            call.resolve(okRes());
            return;
        }
        try {
            BluetoothServerSocket s = ad.listenUsingInsecureRfcommWithServiceRecord("TipsyBird3D", APP_UUID);
            server = s;
        } catch (Exception e) {
            call.resolve(fail("busy"));
            return;
        }
        hosting.set(true);
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (hosting.get()) {
                    BluetoothSocket s = null;
                    try {
                        s = server.accept();
                    } catch (Exception e) {
                        break;
                    }
                    if (s == null) continue;
                    if (links.size() >= MAX_GUESTS) {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                        }
                        continue;
                    }
                    String n = null;
                    try {
                        n = s.getRemoteDevice().getName();
                    } catch (Exception ignored) {
                    }
                    addLink(s, n);
                }
                hosting.set(false);
            }
        });
        t.setName("tb-accept");
        t.setDaemon(true);
        t.start();
        call.resolve(okRes());
    }

    @PluginMethod
    public void connect(final PluginCall call) {
        final String addr = call.getString("address");
        if (addr == null || addr.length() < 12) {
            call.resolve(fail("bad"));
            return;
        }
        if (ad == null) {
            call.resolve(fail("nobt"));
            return;
        }
        if (!gate(call, "connect", addr)) return;
        if (!ad.isEnabled()) {
            call.resolve(fail("enable"));
            return;
        }
        final BluetoothDevice dev;
        try {
            dev = ad.getRemoteDevice(addr);
        } catch (Exception e) {
            call.resolve(fail("bad"));
            return;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    try {
                        ad.cancelDiscovery();
                    } catch (Exception ignored) {
                    }
                    BluetoothSocket sock = dev.createInsecureRfcommSocketToServiceRecord(APP_UUID);
                    sock.connect();
                    String n = dev.getName();
                    addLink(sock, n);
                    call.resolve(okRes());
                } catch (Exception e) {
                    call.resolve(fail("refused"));
                }
            }
        });
        t.setName("tb-connect");
        t.setDaemon(true);
        t.start();
    }

    @PluginMethod
    public void send(PluginCall call) {
        String text = call.getString("text");
        String id = call.getString("id");
        if (text != null) {
            if (id != null) {
                Link L = links.get(id);
                if (L != null && L.out != null) {
                    try {
                        L.out.writeUTF(text);
                        L.out.flush();
                    } catch (Exception ignored) {
                    }
                }
            } else {
                for (Link L : links.values()) {
                    try {
                        L.out.writeUTF(text);
                        L.out.flush();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        call.resolve(okRes());
    }

    @PluginMethod
    public void drop(PluginCall call) {
        String id = call.getString("id");
        if (id != null) {
            Link L = links.remove(id);
            if (L != null) closeLink(L);
        }
        call.resolve(okRes());
    }

    @PluginMethod
    public void stop(PluginCall call) {
        stopAll();
        call.resolve(okRes());
    }

    @Override
    protected void handleOnDestroy() {
        stopAll();
        super.handleOnDestroy();
    }
}
