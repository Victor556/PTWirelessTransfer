package com.putao.ptx.wirelesstransfer.gui;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.putao.ptx.wirelesstransfer.FsService;
import com.putao.ptx.wirelesstransfer.FsSettings;
import com.putao.ptx.wirelesstransfer.R;

import java.net.InetAddress;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.putao.ptx.wirelesstransfer.gui.PTMainActivity.EmStatus.statusError;
import static com.putao.ptx.wirelesstransfer.gui.PTMainActivity.EmStatus.statusNoWifi;
import static com.putao.ptx.wirelesstransfer.gui.PTMainActivity.EmStatus.statusOff;
import static com.putao.ptx.wirelesstransfer.gui.PTMainActivity.EmStatus.statusOn;

public class PTMainActivity extends Activity {

    private static final String KEY_SHOW_DLG = "show";
    @Bind(R.id.tvWifiStatus)
    public TextView tvWifiStatus;

    @Bind(R.id.ivImageMid)
    public ImageView ivImageMid;

    @Bind(R.id.flAddr)
    public ViewGroup flAddr;

    @Bind(R.id.tvFtpAddr)
    public TextView tvFtpAddr;

    @Bind(R.id.tvNotice)
    public TextView tvNotice;


    @Bind(R.id.btnSwitch)
    public TextView btnSwitch;


    private Handler mHandler = new Handler();

    private EmStatus mEmStatus = statusOff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long tm = SystemClock.elapsedRealtime();
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate1: waste time:" + (SystemClock.elapsedRealtime() - tm));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = getWindow();
            // 透明状态栏
            //window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            //取消设置透明状态栏,使 ContentView 内容不再覆盖状态栏
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

            // 透明导航栏
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            //需要设置这个 flag 才能调用 setStatusBarColor 来设置状态栏颜色
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        Log.d(TAG, "onCreate2: waste time:" + (SystemClock.elapsedRealtime() - tm));
        ButterKnife.bind(PTMainActivity.this);
        Log.d(TAG, "onCreate3: waste time:" + (SystemClock.elapsedRealtime() - tm));
        mNetWorkChangeBroadcastReceiver = new NetWorkChangeBroadcastReceiver();
        registerReceiver(mNetWorkChangeBroadcastReceiver,
                new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        if (savedInstanceState != null && savedInstanceState.getBoolean(KEY_SHOW_DLG, false)) {
            tryShowExitDlg();
        }

        Log.d(TAG, "onCreate4: waste time:" + (SystemClock.elapsedRealtime() - tm));
        IntentFilter filter = new IntentFilter();
        filter.addAction(FsService.ACTION_STARTED);
        filter.addAction(FsService.ACTION_STOPPED);
        filter.addAction(FsService.ACTION_FAILEDTOSTART);
        registerReceiver(mFsActionsReceiver, filter);
        Log.d(TAG, "onCreate: waste time:" + (SystemClock.elapsedRealtime() - tm));
    }

    private Dialog mExit;

    @Override
    public void onBackPressed() {
        updateRunningState();
        if (mEmStatus == statusOn) {
            tryShowExitDlg();
        } else {
            super.onBackPressed();
        }
    }

    private void tryShowExitDlg() {
        if (mExit == null) {
            View view = LayoutInflater.from(PTMainActivity.this).inflate(R.layout.dlg_exit, null);
            View btnKeep = view.findViewById(R.id.btnKeep);
            View btnClose = view.findViewById(R.id.btnClose);
            btnKeep.setOnClickListener(v -> finish());
            btnClose.setOnClickListener(v -> {
                stopServer();
                finish();
            });
            mExit = new ExitDialog(PTMainActivity.this,
                    (int) getResources().getDimension(R.dimen.width_exit_dlg),
                    (int) getResources().getDimension(R.dimen.height_exit_dlg),
                    view, R.style.pano_dialog);
        }
        if (mExit != null || !mExit.isShowing()) {
            mExit.show();
        }
    }

    @Override
    public void finish() {
        tryCloseExitDlg();
        super.finish();
    }

    /**
     * This receiver will check FTPServer.ACTION* messages and will update the button,
     * running_state, if the server is running and will also display at what url the
     * server is running.
     */
    BroadcastReceiver mFsActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "BroadcastReceiver: received: intent:" + intent);
            // remove all pending callbacks
            mHandler.removeCallbacksAndMessages(null);
            // action will be ACTION_STARTED or ACTION_STOPPED
            updateRunningState();
            String action = intent.getAction();
            boolean connectedToWifi = isConnectedToWifi();
            if (action.equalsIgnoreCase(FsService.ACTION_FAILEDTOSTART)) {
                if (connectedToWifi) {
                    mEmStatus = statusError;
                } else {
                    mEmStatus = statusNoWifi;
                }
            } else if (action.equalsIgnoreCase(FsService.ACTION_STOPPED)) {
                if (mEmStatus == statusOn) {
                    mEmStatus = statusOff;
                    tryCloseExitDlg();
                }
            } else if (action.equalsIgnoreCase(FsService.ACTION_STARTED)) {
                mEmStatus = statusOn;
            }
            updateStatusUi();
        }
    };

    private BroadcastReceiver mNetWorkChangeBroadcastReceiver = new NetWorkChangeBroadcastReceiver();

    public class NetWorkChangeBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long tm = SystemClock.elapsedRealtime();
            boolean connectedToWifi = isConnectedToWifi();
            boolean connectedToMobile = isConnectedToMobile();

            if (!connectedToMobile && !connectedToWifi) {
                //网络状态全部不可用
            }

            if (connectedToWifi) {
                //切换到wifi网络
            }

            if (connectedToMobile && !connectedToWifi) {
                // 手机没有处于wifi网络而是处于移动网络
            }
            switch (mEmStatus) {
                case statusOff:
                    break;
                case statusOn:
                    mEmStatus = connectedToWifi ? mEmStatus : statusNoWifi;
                    if (mEmStatus != statusOn) {
                        stopServer();
                    }
                    break;
                case statusNoWifi:
                    mEmStatus = connectedToWifi ? statusOff : mEmStatus;
                    break;
                case statusError:
                    break;
            }
            updateStatusUi();

            Log.d(TAG, "onReceive: NetWorkChangeBroadcastReceiver : waste time:" + (SystemClock.elapsedRealtime() - tm));
        }
    }

    public boolean isConnectedToWifi() {
        long tm = SystemClock.elapsedRealtime();
        ConnectivityManager connectivityManager = (ConnectivityManager) PTMainActivity.this.getSystemService(Context.CONNECTIVITY_SERVICE);
        //wifi网络
        NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean ret = wifiNetInfo != null && wifiNetInfo.isConnected();

        Log.d(TAG, "isConnectedToWifi: waste time:" + (SystemClock.elapsedRealtime() - tm));
        return ret;
    }

    public boolean isConnectedToMobile() {
        ConnectivityManager connectivityManager = (ConnectivityManager) PTMainActivity.this.getSystemService(Context.CONNECTIVITY_SERVICE);
        //wifi网络
        NetworkInfo mobileNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return mobileNetInfo != null && mobileNetInfo.isConnected();
    }

    @Override
    protected void onResume() {
        super.onResume();
        long tm = SystemClock.elapsedRealtime();
        updateRunningState();
        updateStatusUi();
        Log.d(TAG, "onResume: Registering the FTP server actions waste time:" + (SystemClock.elapsedRealtime() - tm));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause: Unregistering the FTPServer actions");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mFsActionsReceiver);
        unregisterReceiver(mNetWorkChangeBroadcastReceiver);
    }


    private static final String TAG = PTMainActivity.class.getSimpleName();

    public void updateStatusUi() {
        EmStatus status = mEmStatus;
        if (status == null) {
            return;
        }
        ivImageMid.setImageResource(status.mImageId);
        tvNotice.setText(status.mTextInfoId);
        btnSwitch.setText(status.mTextButtonId);
        tvFtpAddr.setText("ftp://" + getIpAddress() + ":" + getPort());
        boolean statusOn = status.isStatusOn();
        flAddr.setVisibility(statusOn ? View.VISIBLE : View.GONE);
        btnSwitch.setBackgroundResource(statusOn ?
                R.drawable.ptx_selector_button_background_red
                : R.drawable.ptx_selector_button_background_blue);
        updateSsidStatus();
        Log.d(TAG, "updateStatusUi: EmStatus:" + status);
        if (mEmStatus != EmStatus.statusOn) {
            tryCloseExitDlg();
        }
    }

    private void updateSsidStatus() {
        String ssid = getSsid();
        if (!TextUtils.isEmpty(ssid)) {
            tvWifiStatus.setVisibility(View.VISIBLE);
            tvWifiStatus.setText(ssid);
        } else {
            tvWifiStatus.setText("");
            tvWifiStatus.setVisibility(View.GONE);
        }
        if (mEmStatus == statusOff) {//开始界面不显示wifi
            tvWifiStatus.setVisibility(View.GONE);
        }
        Log.d(TAG, "updateSsidStatus:   ssid:" + ssid);
    }

    private String getIpAddress() {
        InetAddress address = FsService.getLocalInetAddress();
        String ip = null;
        if (address == null) {
            Log.v(TAG, "Unable to retrieve wifi ip address");
        } else {
            ip = address.getHostAddress();
        }
        Log.d(TAG, "getIpAddress: " + ip);
        return ip;
    }

    private String getSsid() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = null;
        if (wifiInfo != null && isConnectedToWifi()) {
            Log.d(TAG, "wifiInfo:" + wifiInfo.toString());
            ssid = wifiInfo.getSSID();
            ssid = ssid.replaceAll("\"", "");
            if (ssid.equalsIgnoreCase("<unknown ssid>")) {
                ssid = null;
            }
        }
        Log.d(TAG, "SSID:" + ssid);
        return ssid;
    }


    public String getPort() {
        return FsSettings.getPortNumber() + "";
    }

    enum EmStatus {
        statusOff(R.mipmap.ftp_pic_first, R.string.ptx_notice_first, R.string.ptx_turn_on),
        statusOn(R.mipmap.ftp_pic_succeeded, R.string.ptx_notice_turn_on_succeeded, R.string.ptx_turn_off),
        statusNoWifi(R.mipmap.ftp_pic_no_wifi, R.string.ptx_notice_wifi, R.string.ptx_goto_settings),
        statusError(R.mipmap.ftp_pic_error, R.string.ptx_notice_turn_on_exception, R.string.ptx_reset);

        final int mImageId;
        final int mTextInfoId;
        final int mTextButtonId;


        EmStatus(int imageId, int textInfoId, int textButtonId) {
            mImageId = imageId;
            mTextInfoId = textInfoId;
            mTextButtonId = textButtonId;
        }

        public boolean isStatusOn() {
            return statusOn == EmStatus.this;
        }
    }

    @OnClick(R.id.btnSwitch)
    public void onClick(View btn) {
        if (btn.getId() == R.id.btnSwitch) {
            switch (mEmStatus) {
                case statusOn:
                    stopServer();
                    break;
                case statusOff:
                    if (isConnectedToWifi()) {
                        startServer();
                    } else {
                        mEmStatus = statusNoWifi;
                        updateStatusUi();
                    }
                    break;
                case statusNoWifi:
                    gotoSettings();
                    break;
                case statusError:
                    tryStartServer();
                    break;
                default:
            }
        }
        Log.d(TAG, "onClick:   getCurrentStatus：" + mEmStatus);
    }


    private void startServer() {
        sendBroadcast(new Intent(FsService.ACTION_START_FTPSERVER));
    }

    private void stopServer() {
        sendBroadcast(new Intent(FsService.ACTION_STOP_FTPSERVER));
        tryCloseExitDlg();
    }

    private boolean tryCloseExitDlg() {
        boolean ret = false;
        if (mExit != null && mExit.isShowing()) {
            mExit.cancel();
            mExit = null;
            ret = true;
        }
        return ret;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (tryCloseExitDlg()) {
            outState.putBoolean(KEY_SHOW_DLG, true);
        }
    }

    private void gotoSettings() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS/*IRELESS_SETTINGS*/);
        startActivity(intent);
    }

    private void tryStartServer() {
        startServer();
    }


    private boolean updateRunningState() {
        boolean connectedToWifi = isConnectedToWifi();
        try {
            boolean running = FsService.isRunning();
            if (running && connectedToWifi) {
                mEmStatus = statusOn;
            } else {
                if (running && !connectedToWifi) {
                    stopServer();
                }
            }
            if (mEmStatus == statusOn && !running) {
                mEmStatus = statusOff;
            }
        } catch (Exception e) {
            Log.e(TAG, "updateRunningState: " + e);
            mEmStatus = statusError;
        }
        Log.d(TAG, "updateRunningState:  emStatus:" + mEmStatus);
        if (mEmStatus != statusOn) {
            tryCloseExitDlg();
        }
        return connectedToWifi;
    }

}
