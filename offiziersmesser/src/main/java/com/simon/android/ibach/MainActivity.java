package com.simon.android.ibach;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;
import com.stericson.RootTools.RootTools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_READ_PHONE_STATE_FOR_QUERY = 0;
    private static final int REQUEST_READ_PHONE_STATE_FOR_CONFIGURE = 1;
    private static final int REQUEST_RECORD_AUDIO = 2;
    private CheckBox volteCheckBox;
    private ToggleButton recordingToggle;
    private Thread recordingThread;

    private static void setEnhanced4gLteModeSetting(Context context, boolean enabled) {
        try {
            Class imsManagerClass = Class.forName("com.android.ims.ImsManager");
            Method method = imsManagerClass.getMethod("setEnhanced4gLteModeSetting", Context.class, Boolean.TYPE);
            method.invoke(null, context, enabled);
            Log.i(TAG, "Enhanced 4G LTE mode is set to " + enabled);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Log.e(TAG, e.toString());
        }
    }

    private static boolean isEnhanced4gLteModeSettingEnabledByUser(Context context) {
        try {
            Class imsManagerClass = Class.forName("com.android.ims.ImsManager");
            Method method = imsManagerClass.getMethod("isEnhanced4gLteModeSettingEnabledByUser", Context.class);
            return (boolean) method.invoke(null, context);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        volteCheckBox = (CheckBox) findViewById(R.id.volteCheckBox);
        recordingToggle = (ToggleButton) findViewById(R.id.recordingToggleButton);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE_FOR_QUERY);
                } else {
                    volteCheckBox.setChecked(isEnhanced4gLteModeSettingEnabledByUser(MainActivity.this));
                }
            }
        });

        volteCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE_FOR_CONFIGURE);
                } else {
                    setEnhanced4gLteModeSetting(MainActivity.this, isChecked);
                }
            }
        });

        recordingToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_RECORD_AUDIO);
                } else {
                    handleRecording(isChecked);
                }
            }
        });

        try {
            Class ciiClass = Class.forName("com.samsung.commonimsinterface.imsinterface.CommonIMSInterface");
            Method getInstance = ciiClass.getMethod("getInstance", Integer.TYPE, Context.class);
            Object iiForGeneral = getInstance.invoke(null, 7, this);

            Class iiForGeneralClass = Class.forName("com.samsung.commonimsinterface.imsinterface.IMSInterfaceForGeneral");
            Method manualDeregister = iiForGeneralClass.getMethod("manualDeregister");
            manualDeregister.invoke(iiForGeneral);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void handleRecording(boolean isChecked) {
        if (isChecked) {
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int minBufferSize = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                        AudioRecord record = new AudioRecord.Builder()
                                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(48000)
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                        .build())
                                .setBufferSizeInBytes(minBufferSize)
                                .build();
                        FileOutputStream fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "pcm.raw"));
                        BufferedOutputStream bos = new BufferedOutputStream(fos);
                        byte[] audioData = new byte[minBufferSize];
                        record.startRecording();
                        int numOfBytesRead;
                        while (!Thread.currentThread().isInterrupted()) {
                            numOfBytesRead = record.read(audioData, 0, minBufferSize);
                            if (numOfBytesRead > 0) {
                                bos.write(audioData, 0, numOfBytesRead);
                            }
                        }
                        record.stop();
                        Log.i(TAG, "stop recording");
                        record.release();
                        bos.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }

                }
            });
            recordingThread.start();
        } else {
            if (recordingThread != null) {
                recordingThread.interrupt();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_READ_PHONE_STATE_FOR_QUERY:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "permission has been granted to read phone state");
                    volteCheckBox.setChecked(isEnhanced4gLteModeSettingEnabledByUser(this));
                }
                break;
            case REQUEST_READ_PHONE_STATE_FOR_CONFIGURE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "permission has been granted to read phone state");
                    setEnhanced4gLteModeSetting(MainActivity.this, volteCheckBox.isChecked());
                }
                break;
            case REQUEST_RECORD_AUDIO:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "permission has been granted to record audio");
                    handleRecording(recordingToggle.isChecked());
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void onLaunchUSBSettingsButtonClicked(View view) {
        try {
            RootTools.getShell(true, Shell.ShellContext.SYSTEM_APP).add(new Command(0, "am start -n 'com.sec.usbsettings/.USBSettings'"));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (RootDeniedException e) {
            e.printStackTrace();
        }
    }

    public void onLaunchServiceModeButtonClicked(View view) {
        try {
            RootTools.getShell(true, Shell.ShellContext.SYSTEM_APP).add(new Command(0, "am start -n 'com.sec.android.RilServiceModeApp/.ServiceModeApp' -e keyString '27663368378'"));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (RootDeniedException e) {
            e.printStackTrace();
        }
    }
}
