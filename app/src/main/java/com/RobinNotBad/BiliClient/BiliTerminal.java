package com.RobinNotBad.BiliClient;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.app.Activity;

import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;

import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;

// 导入你的二维码库
import com.swetake.util.Qrcode;

import java.lang.ref.WeakReference;

public class BiliTerminal extends Application {

    @SuppressLint("StaticFieldLeak")
    public static Context context;

    public static boolean DPI_FORCE_CHANGE = false;

    private static WeakReference<InstanceActivity> instance = new WeakReference<>(null);

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        enableHardwareAccelerationForModernDevices();
        if (context == null) {
            SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default", MODE_PRIVATE);
            context = getFitDisplayContext(this);
            ErrorCatch errorCatch = ErrorCatch.getInstance();
            errorCatch.init(context);

            // 初始化二维码库（从 assets 加载数据文件）
            Qrcode.init(this);

            boolean debugBuild = isDebugBuild();
            Logu.LOGV_ENABLED = SharedPreferencesUtil.getBoolean("dev_logv", debugBuild);
            Logu.LOGD_ENABLED = SharedPreferencesUtil.getBoolean("dev_logd", debugBuild);
            Logu.LOGI_ENABLED = SharedPreferencesUtil.getBoolean("dev_logi", debugBuild);
        }
    }

    //硬件加速在 Manifest 中默认关闭（为兼容 Android 4.0，其硬件加速画布不支持 clipPath）。
    //这里对 4.1（API 16）及以上的每个 Activity 统一用窗口级 flag 重新开启硬件加速，
    //使现代设备的体验与全程开启硬件加速时完全一致（窗口级只允许开启、不允许关闭）。
    private void enableHardwareAccelerationForModernDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                activity.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            }

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {}

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    public static void setInstance(InstanceActivity instanceActivity) {
        instance = new WeakReference<>(instanceActivity);
    }

    @Nullable
    public static InstanceActivity getInstanceActivityOnTop() {
        return instance.get();
    }

    /**
     * 重写attachBaseContext方法，用于调整应用内dpi
     * 尝试下这种风格代码是否会导致低版本设备异常
     *
     * @param old The origin context.
     */
    public static Context getFitDisplayContext(Context old) {
        float dpiTimes = SharedPreferencesUtil.getFloat("dpi", 1.0F);
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return old;
        if(!DPI_FORCE_CHANGE && dpiTimes == 1.0F) return old;
        try {
            DisplayMetrics displayMetrics = old.getResources().getDisplayMetrics();
            Configuration configuration = old.getResources().getConfiguration();
            configuration.densityDpi = (int) (displayMetrics.densityDpi * dpiTimes);
            return old.createConfigurationContext(configuration);
        } catch (Exception e) {
            //MsgUtil.err(e,old);
            return old;
        }
    }

    public static int getVersion() throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
    }

    public static boolean isDebugBuild() {
        return "debug".equals(BuildConfig.BUILD_TYPE);
    }

    public static void jumpToVideo(Context context, long aid) {
        TerminalContext.getInstance().enterVideoDetailPage(context, aid);
    }

    public static void jumpToVideo(Context context, String bvid) {
        TerminalContext.getInstance().enterVideoDetailPage(context, bvid);
    }

    public static void jumpToArticle(Context context, long cvid) {
        TerminalContext.getInstance().enterArticleDetailPage(context, cvid);
    }

    public static void jumpToUser(Context context, long mid) {
        Intent intent = new Intent();
        intent.setClass(context, UserInfoActivity.class);
        intent.putExtra("mid", mid);
        context.startActivity(intent);
    }

}