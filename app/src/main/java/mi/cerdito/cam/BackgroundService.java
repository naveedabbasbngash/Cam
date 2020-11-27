package mi.cerdito.cam;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class BackgroundService extends Service {
    public final String TAG = "Webcam";

    private LinearLayout mOverlay = null;
    private SurfaceView mSurfaceView;

    private Camera mCamera;
    private MjpegServer mMjpegServer;

    private String mPort;
    private WindowManager.LayoutParams yourparams;


    public BackgroundService() {
    }

    @Override
    public void onCreate() {

        SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                Log.v(TAG, "surfaceCreated()");

                int cameraId;
                int previewWidth;
                int previewHeight;
                int rangeMin;
                int rangeMax;
                int quality;
                int port;

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(BackgroundService.this);
                String cameraIdString = preferences.getString("settings_camera", null);
                String previewSizeString = preferences.getString("settings_size", null);
                String rangeString = preferences.getString("settings_range", null);
                String qualityString = preferences.getString("settings_quality", "50");
                String portString = preferences.getString("settings_port", "8080");

                // if failed, it means settings is broken.
                assert(cameraIdString != null && previewSizeString != null && rangeString != null);

                int xIndex = previewSizeString.indexOf("x");
                int tildeIndex = rangeString.indexOf("~");

                // if failed, it means settings is broken.
                assert(xIndex > 0 && tildeIndex > 0);

                try {
                    cameraId = Integer.parseInt(cameraIdString);

                    previewWidth = Integer.parseInt(previewSizeString.substring(0, xIndex - 1));
                    previewHeight = Integer.parseInt(previewSizeString.substring(xIndex + 2));

                    rangeMin = Integer.parseInt(rangeString.substring(0, tildeIndex - 1));
                    rangeMax = Integer.parseInt(rangeString.substring(tildeIndex + 2));

                    quality = Integer.parseInt(qualityString);
                    port = Integer.parseInt(portString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Settings is broken");
                    Toast.makeText(BackgroundService.this, "Settings is broken", Toast.LENGTH_SHORT).show();

                    stopSelf();
                    return;
                }

                mCamera = Camera.open(cameraId);
                if (mCamera == null) {
                    Log.v(TAG, "Can't open camera" + cameraId);

                    Toast.makeText(BackgroundService.this, getString(R.string.can_not_open_camera),
                            Toast.LENGTH_SHORT).show();
                    stopSelf();

                    return;
                }

                try {
                    mCamera.setPreviewDisplay(holder);
                } catch (IOException e) {
                    Log.v(TAG, "SurfaceHolder is not available");

                    Toast.makeText(BackgroundService.this, "SurfaceHolder is not available",
                            Toast.LENGTH_SHORT).show();
                    stopSelf();

                    return;
                }

                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewSize(previewWidth, previewHeight);
                parameters.setPreviewFpsRange(rangeMin, rangeMax);
                mCamera.setParameters(parameters);
                mCamera.startPreview();

                JpegFactory jpegFactory = new JpegFactory(previewWidth,
                        previewHeight, quality);
                mCamera.setPreviewCallback(jpegFactory);

                mMjpegServer = new MjpegServer(jpegFactory);
                try {
                    mMjpegServer.start(port);
                } catch (IOException e) {
                    String message = "Port: " + port + " is not available";
                    Log.v(TAG, message);

                    Toast.makeText(BackgroundService.this, message, Toast.LENGTH_SHORT).show();
                    stopSelf();
                }

                Toast.makeText(BackgroundService.this, "Port: " + port, Toast.LENGTH_SHORT).show();
            }

            public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                       int height) {
                Log.v(TAG, "surfaceChanged()");
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.v(TAG, "surfaceDestroyed()");
            }
        };

        createOverlay();
        SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.addCallback(callback);

        mPort = PreferenceManager.getDefaultSharedPreferences(this).getString("settings_port", "8080");

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // We want BackgroundService.this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        // mNM.cancel(NOTIFICATION);

        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
        }

        destroyOverlay();

        if (mMjpegServer != null) {
            mMjpegServer.close();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showNotification() {
        // In BackgroundService.this sample, we'll use the same text for the ticker and the expanded notification
        // CharSequence text = getText(R.string.service_started);
        CharSequence text = "View webcam at " + getIpAddr() + ":" + mPort;

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_stat_webcam, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects BackgroundService.this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        @SuppressLint("WifiManagerLeak") WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        Log.d("my ip ",ip);

        showNotification(this,"Enjoy Mobile As WebCam","Your Camera webiste is "+ip+":8080",contentIntent);


        // Send the notification.
/*
        startForeground( R.string.service_started, notification);
*/
        startMyOwnForeground();
    }

    public String getIpAddr() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();

        String ipString = String.format(
                "%d.%d.%d.%d",
                (ip & 0xff),
                (ip >> 8 & 0xff),
                (ip >> 16 & 0xff),
                (ip >> 24 & 0xff));

        return ipString;
    }

    /**
     * Create a surface view overlay (for the camera's preview surface).
     */
    private void createOverlay() {
        assert (mOverlay == null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,  // technically automatically set by FLAG_NOT_FOCUSABLE
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mOverlay = (LinearLayout) inflater.inflate(R.layout.background, null);
        mSurfaceView = (SurfaceView) mOverlay.findViewById(R.id.backgroundSurfaceview);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);




        int layout_parms;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        {
            layout_parms = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        }

        else {

            layout_parms = WindowManager.LayoutParams.TYPE_PHONE;

        }

        yourparams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layout_parms,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        wm.addView(mOverlay, yourparams);
    }

    private void destroyOverlay() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.removeView(mOverlay);
    }

    public void showNotification(Context context, String title, String body, PendingIntent intent) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = 1;
        String channelId = "channel-01";
        String channelName = "Channel Name";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    channelId, channelName, importance);
            notificationManager.createNotificationChannel(mChannel);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
     /*   PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT
        );*/
        mBuilder.setContentIntent(intent);

        notificationManager.notify(notificationId, mBuilder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.example.simpleapp";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_action_search)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }
}

