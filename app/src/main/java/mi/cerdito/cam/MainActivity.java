package mi.cerdito.cam;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class MainActivity extends Activity implements View.OnClickListener {

    public final String TAG = "Webcam";

    private boolean mIsServiceRunning = false;

    private Button mBackgroundButton;
    private Button mForegroundButton;
    SharedPreferences mSharedPreferences;
    private Button mSettingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.ACCESS_NETWORK_STATE,Manifest.permission
                .SYSTEM_ALERT_WINDOW,Manifest.permission.ACCESS_WIFI_STATE};

        Permissions.check(this/*context*/, permissions, null/*rationale*/, null/*options*/, new PermissionHandler() {
            @Override
            public void onGranted() {
                // do your task.


            }
        });
        mBackgroundButton = (Button) findViewById(R.id.backgroundButton);
        mForegroundButton = (Button) findViewById(R.id.foregroundButton);
        mSettingButton = (Button) findViewById(R.id.settingsButton);
        mBackgroundButton.setOnClickListener(this);
        mForegroundButton.setOnClickListener(this);
        mSettingButton.setOnClickListener(this);

        if (!initialize()) {
            Toast.makeText(this, "Can not initialize parameters", Toast.LENGTH_LONG).show();
        }



    }


    private boolean initialize() {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean firstRun = ! mSharedPreferences.contains("settings_camera");
        if (firstRun) {
            Log.v(TAG, "First run");

            SharedPreferences.Editor editor = mSharedPreferences.edit();

            int cameraNumber = Camera.getNumberOfCameras();
            Log.v(TAG, "Camera number: " + cameraNumber);

            /*
             * Get camera name set
             */
            TreeSet<String> cameraNameSet = new TreeSet<String>();
            if (cameraNumber == 1) {
                cameraNameSet.add("back");
            } else if (cameraNumber == 2) {
                cameraNameSet.add("back");
                cameraNameSet.add("front");
            } else if (cameraNumber > 2) {           // rarely happen
                for (int id = 0; id < cameraNumber; id++) {
                    cameraNameSet.add(String.valueOf(id));
                }
            } else {                                 // no camera available
                Log.v(TAG, "No camrea available");
                Toast.makeText(this, "No camera available", Toast.LENGTH_SHORT).show();

                return false;
            }

            /*
             * Get camera id set
             */
            String[] cameraIds = new String[cameraNumber];
            TreeSet<String> cameraIdSet = new TreeSet<String>();
            for (int id = 0; id < cameraNumber; id++) {
                cameraIdSet.add(String.valueOf(id));
            }

            /*
             * Save camera name set and id set
             */
            editor.putStringSet("camera_name_set", cameraNameSet);
            editor.putStringSet("camera_id_set", cameraIdSet);

            /*
             * Get and save camera parameters
             */
            for (int id = 0; id < cameraNumber; id++) {
                Camera camera = Camera.open(id);
                if (camera == null) {
                    String msg = "Camera " + id + " is not available";
                    Log.v(TAG, msg);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                    return false;
                }

                Camera.Parameters parameters = camera.getParameters();

                /*
                 * Get and save preview sizes
                 */
                List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();

                TreeSet<String> sizeSet = new TreeSet<String>(new Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        int spaceIndex1 = s1.indexOf(" ");
                        int spaceIndex2 = s2.indexOf(" ");
                        int width1 = Integer.parseInt(s1.substring(0, spaceIndex1));
                        int width2 = Integer.parseInt(s2.substring(0, spaceIndex2));

                        return width2 - width1;
                    }
                });
                for (Camera.Size size : sizes) {
                    sizeSet.add(size.width + " x " + size.height);
                }
                editor.putStringSet("preview_sizes_" + id, sizeSet);

                Log.v(TAG, sizeSet.toString());

                /*
                 * Set default preview size, use camera 0
                 */
                if (id == 0) {
                    Log.v(TAG, "Set default preview size");

                    Camera.Size defaultSize = parameters.getPreviewSize();
                    editor.putString("settings_size", defaultSize.width + " x " + defaultSize.height);
                }

                /*
                 * Get and save
                 */
                List<int[]> ranges = parameters.getSupportedPreviewFpsRange();
                TreeSet<String> rangeSet = new TreeSet<String>();
                for (int[] range : ranges) {
                    rangeSet.add(range[0] + " ~ " + range[1]);
                }
                editor.putStringSet("preview_ranges_" + id, rangeSet);

                if (id == 0) {
                    Log.v(TAG, "Set default fps range");

                    int[] defaultRange = new int[2];
                    parameters.getPreviewFpsRange(defaultRange);
                    editor.putString("settings_range", defaultRange[0] + " ~ " + defaultRange[1]);
                }

                camera.release();

            }

            editor.putString("settings_camera", "0");
            editor.commit();
        }

        return true;
    }

    private void updateButton(boolean state) {
        if (state) {
            mBackgroundButton.setText(R.string.stop_running);
        } else {
            mBackgroundButton.setText(R.string.run_background);
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ((BackgroundService.class.getName()).equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        mIsServiceRunning = isServiceRunning();
        updateButton(mIsServiceRunning);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mIsServiceRunning) {
            finish();
        }
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.settingsButton:
                startActivity(new Intent(this , SettingsActivity.class));
                break;
            case R.id.foregroundButton:
                if (mIsServiceRunning) {
                    stopService(new Intent(this, BackgroundService.class));
                    mIsServiceRunning = false;
                    updateButton(false);
                }
                startActivity(new Intent(this , ForegroundActivity.class));
                break;
            case R.id.backgroundButton:
                if (!mIsServiceRunning) {
                    //doBindService();
                    startService(new Intent(this, BackgroundService.class));
                    mIsServiceRunning = true;
                } else {
                    //doUnbindService();
                    stopService(new Intent(this, BackgroundService.class));
                    mIsServiceRunning = false;
                }
                updateButton(mIsServiceRunning);
                break;
        }

    }



}