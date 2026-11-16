package com.neon.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.provider.MediaStore;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CapacitorPlugin(name = "Apps")
public class AppsPlugin extends Plugin {

    private static final int ICON_SIZE = 96;
    private BroadcastReceiver receiver;

    @Override
    public void load() {
        try {
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    notifyListeners("appsChanged", new JSObject());
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addDataScheme("package");
            ContextCompat.registerReceiver(getContext(), receiver, f, ContextCompat.RECEIVER_EXPORTED);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void handleOnDestroy() {
        try {
            if (receiver != null) getContext().unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
    }

    @PluginMethod
    public void getApps(final PluginCall call) {
        new Thread(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN, null);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
                String self = getContext().getPackageName();
                Set<String> seen = new HashSet<>();
                JSArray arr = new JSArray();
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(self) || !seen.add(pkg)) continue;
                    JSObject o = new JSObject();
                    o.put("pk", pkg);
                    o.put("n", String.valueOf(ri.loadLabel(pm)));
                    o.put("img", iconToDataUrl(ri.loadIcon(pm)));
                    arr.put(o);
                }
                JSObject res = new JSObject();
                res.put("apps", arr);
                call.resolve(res);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    @PluginMethod
    public void launch(PluginCall call) {
        String pkg = call.getString("pk");
        Intent i = pkg == null ? null : getContext().getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            call.reject("App not found");
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void uninstall(PluginCall call) {
        String pkg = call.getString("pk");
        if (pkg == null) {
            call.reject("Missing pk");
            return;
        }
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void appInfo(PluginCall call) {
        String pkg = call.getString("pk");
        if (pkg == null) {
            call.reject("Missing pk");
            return;
        }
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openHomeSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(i);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(fallback);
        }
        call.resolve();
    }

    private boolean torchOn = false;

    @PluginMethod
    public void flash(PluginCall call) {
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            String target = null;
            for (String id : cm.getCameraIdList()) {
                Boolean has = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (has != null && has) {
                    target = id;
                    break;
                }
            }
            if (target == null) {
                call.reject("No flash");
                return;
            }
            torchOn = !torchOn;
            cm.setTorchMode(target, torchOn);
            JSObject r = new JSObject();
            r.put("on", torchOn);
            call.resolve(r);
        } catch (Exception e) {
            torchOn = false;
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void openType(PluginCall call) {
        String t = call.getString("t", "");
        Intent i = null;
        try {
            switch (t) {
                case "tel":
                    i = new Intent(Intent.ACTION_DIAL);
                    break;
                case "sms":
                    i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING);
                    break;
                case "web":
                    i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
                    break;
                case "gm":
                    i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL);
                    break;
                case "map":
                    i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS);
                    break;
                case "ph":
                    i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY);
                    break;
                case "cam":
                    i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    break;
                default:
                    break;
            }
            if (i == null) {
                call.reject("Unknown type");
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void expandNotifications(PluginCall call) {
        try {
            Object sb = getContext().getSystemService("statusbar");
            Class<?> c = Class.forName("android.app.StatusBarManager");
            c.getMethod("expandNotificationsPanel").invoke(sb);
            call.resolve();
        } catch (Exception e) {
            call.reject("Unsupported");
        }
    }

    private String iconToDataUrl(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
        d.draw(c);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
        return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }
}
