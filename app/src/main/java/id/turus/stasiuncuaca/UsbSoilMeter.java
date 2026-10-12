package id.turus.stasiuncuaca;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** USB CH340/CH34x Modbus RTU reader for common USB soil 8-in-1 meters. */
final class UsbSoilMeter {
    interface Listener {
        void onStatus(String status);
        void onValues(Values values);
    }
    static final class Values {
        final double moisturePct, temperatureC, ecUsCm, ph, nitrogen, phosphorus, potassium, fertility;
        Values(int[] r) {
            moisturePct = r[0] / 10.0;
            int temp = r[1] > 32767 ? r[1] - 65536 : r[1];
            temperatureC = temp / 10.0;
            ecUsCm = r[2];
            ph = r[3] / 10.0;
            nitrogen = r[4]; phosphorus = r[5]; potassium = r[6]; fertility = r[7];
        }
    }

    private static final String ACTION_USB_PERMISSION = "soil.meter.USB_SOIL_PERMISSION";
    private static final int BAUD = 9600;
    private final Context context;
    private final UsbManager manager;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile UsbSerialPort port;
    private UsbDeviceConnection connection;
    private boolean running, permissionPending, reading, opening;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            permissionPending = false;
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && device != null) openDevice(device);
            else status("Izin USB belum diberikan. Izinkan akses perangkat untuk membaca sensor tanah.");
        }
    };

    private final Runnable discoverTask = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try {
                if (port == null) tryDiscover();
                else readOnce();
            } catch (Exception e) { status("Pemindaian USB gagal: " + safeMessage(e)); }
            handler.postDelayed(this, port == null ? 1800 : 3500);
        }
    };

    UsbSoilMeter(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    void start() {
        if (running) return;
        running = true;
        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, f);
        receiverRegistered = true;
        status("Mencari USB Soil Meter 8-in-1 (CH340/CH34x)…");
        handler.post(discoverTask);
    }

    void scanNow() { if (!running) start(); else if (port != null) readOnce(); else tryDiscover(); }

    void stop() {
        running = false; handler.removeCallbacks(discoverTask);
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        io.execute(this::closePort);
    }

    void dispose() {
        stop();
        io.execute(() -> { closePort(); io.shutdown(); });
    }

    private void tryDiscover() {
        if (manager == null || port != null || permissionPending || opening) return;
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        for (UsbSerialDriver driver : drivers) {
            UsbDevice d = driver.getDevice();
            if (!isKnownSoilAdapter(d)) continue;
            if (!manager.hasPermission(d)) {
                permissionPending = true;
                Intent piIntent = new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName());
                PendingIntent pi = PendingIntent.getBroadcast(context, 19057, piIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                manager.requestPermission(d, pi);
                status("USB Soil Meter terdeteksi; menunggu izin USB…");
            } else {
                opening = true;
                io.execute(() -> { try { openDriver(driver); } finally { opening = false; } });
            }
            return;
        }
    }

    private boolean isKnownSoilAdapter(UsbDevice d) {
        if (d == null || d.getVendorId() != 0x1A86) return false;
        int p = d.getProductId();
        return p == 0x7523 || p == 0x5523 || p == 0x5512 || p == 0xE010;
    }

    private void openDevice(UsbDevice device) {
        if (opening) return;
        opening = true;
        io.execute(() -> {
            try {
                for (UsbSerialDriver driver : UsbSerialProber.getDefaultProber().findAllDrivers(manager)) {
                    if (driver.getDevice().getDeviceId() == device.getDeviceId()) { openDriver(driver); return; }
                }
                status("Adaptor USB terdeteksi tetapi driver serial CH34x tidak tersedia.");
            } catch (Exception e) { status("Gagal membuka USB Soil Meter: " + safeMessage(e)); }
            finally { opening = false; }
        });
    }

    private synchronized void openDriver(UsbSerialDriver driver) {
        if (port != null) return;
        try {
            UsbDeviceConnection conn = manager.openDevice(driver.getDevice());
            if (conn == null) { status("USB terdeteksi; Android tidak dapat membuka perangkat. Periksa izin/kabel OTG."); return; }
            UsbSerialPort p = driver.getPorts().get(0);
            p.open(conn);
            p.setParameters(BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            p.setDTR(true); p.setRTS(true);
            connection = conn; port = p;
            status("USB Soil Meter tersambung (CH340, " + BAUD + " baud). Membaca register Modbus…");
            readOnce();
        } catch (Exception e) {
            closePort(); status("Gagal membuka port serial: " + safeMessage(e));
        }
    }

    private synchronized void readOnce() {
        if (port == null || reading || !running) return;
        reading = true;
        io.execute(() -> {
            try {
                int[] regs = readRegisters(0x04, 0x0000, 8);
                if (regs == null) regs = readSplitRegisters(0x04);
                if (regs == null) regs = readRegisters(0x03, 0x0000, 8);
                if (regs == null) regs = readSplitRegisters(0x03);
                if (regs == null) {
                    status("USB tersambung, tetapi respons register 0x0000–0x0007 tidak valid. Periksa baud rate/protokol alat.");
                } else {
                    Values v = new Values(regs);
                    handler.post(() -> listener.onValues(v));
                    status("Data 8-in-1 terbaca • register 0x0000–0x0007 • " + BAUD + " baud");
                }
            } catch (Exception e) { status("Gagal membaca Modbus: " + safeMessage(e)); }
            finally { reading = false; }
        });
    }

    private int[] readSplitRegisters(int function) throws IOException {
        // Some firmware only accepts four registers per Modbus request; query both halves.
        int[] first = readRegisters(function, 0x0000, 4);
        if (first == null) return null;
        int[] second = readRegisters(function, 0x0004, 4);
        if (second == null) return null;
        int[] all = new int[8];
        System.arraycopy(first, 0, all, 0, 4);
        System.arraycopy(second, 0, all, 4, 4);
        return all;
    }

    private int[] readRegisters(int function, int start, int count) throws IOException {
        UsbSerialPort p = port;
        if (p == null) return null;
        byte[] request = makeRequest(1, function, start, count);
        try { p.purgeHwBuffers(true, true); } catch (Exception ignored) {}
        p.write(request, 700);
        long end = SystemClock.elapsedRealtime() + 1300;
        byte[] response = new byte[96]; int used = 0;
        while (SystemClock.elapsedRealtime() < end && used < response.length) {
            byte[] part = new byte[response.length - used];
            int n;
            try { n = p.read(part, 180); } catch (IOException e) { break; }
            if (n > 0) {
                System.arraycopy(part, 0, response, used, n); used += n;
                int[] parsed = findResponseFrame(response, used, function, count);
                if (parsed != null) return parsed;
            }
        }
        return findResponseFrame(response, used, function, count);
    }

    // Scan for a valid frame instead of assuming byte zero is the response; some USB-RS485
    // adapters echo the transmitted request before returning the meter's reply.
    private int[] findResponseFrame(byte[] b, int used, int function, int count) {
        for (int offset = 0; offset + 5 <= used; offset++) {
            if ((b[offset] & 0xFF) != 1) continue;
            int fn = b[offset + 1] & 0xFF;
            if (fn == (function | 0x80)) return null;
            if (fn != function || (b[offset + 2] & 0xFF) != count * 2) continue;
            int frameLength = 5 + count * 2;
            if (offset + frameLength > used) continue;
            if (!validCrcAt(b, offset, 3 + count * 2)) continue;
            int[] regs = new int[count];
            for (int i = 0; i < count; i++) regs[i] = ((b[offset + 3 + i * 2] & 0xFF) << 8) | (b[offset + 4 + i * 2] & 0xFF);
            return regs;
        }
        return null;
    }

    private boolean validCrcAt(byte[] b, int offset, int lengthWithoutCrc) {
        int crc = crc16(b, offset, lengthWithoutCrc);
        return (b[offset + lengthWithoutCrc] & 0xFF) == (crc & 0xFF)
                && (b[offset + lengthWithoutCrc + 1] & 0xFF) == ((crc >> 8) & 0xFF);
    }

    private byte[] makeRequest(int slave, int function, int start, int count) {
        byte[] b = new byte[]{(byte)slave, (byte)function, (byte)(start >> 8), (byte)start,
                (byte)(count >> 8), (byte)count, 0, 0};
        int crc = crc16(b, 6); b[6] = (byte)crc; b[7] = (byte)(crc >> 8); return b;
    }
    private int crc16(byte[] b, int len) { return crc16(b, 0, len); }
    private int crc16(byte[] b, int offset, int len) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + len; i++) {
            crc ^= b[i] & 0xFF;
            for (int j = 0; j < 8; j++) crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xA001 : crc >> 1;
        }
        return crc & 0xFFFF;
    }
    private void closePort() {
        synchronized (this) {
            try { if (port != null) port.close(); } catch (Exception ignored) {}
            try { if (connection != null) connection.close(); } catch (Exception ignored) {}
            port = null; connection = null; reading = false;
        }
    }
    private void status(String s) { handler.post(() -> listener.onStatus(s)); }
    private String safeMessage(Exception e) { String m = e.getMessage(); return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m; }
}
