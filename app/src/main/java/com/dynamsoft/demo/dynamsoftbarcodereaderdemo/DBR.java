package com.dynamsoft.demo.dynamsoftbarcodereaderdemo;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.dynamsoft.dbr.BarcodeReader;
import com.dynamsoft.dbr.BarcodeReaderException;
import com.dynamsoft.dbr.DBRLTSLicenseVerificationListener;
import com.dynamsoft.dbr.DMLTSConnectionParameters;
import com.dynamsoft.dbr.EnumDMUUIDGenerationMethod;
import com.dynamsoft.dbr.EnumBarcodeFormat;
import com.dynamsoft.dbr.EnumBarcodeFormat_2;
import com.dynamsoft.dbr.EnumImagePixelFormat;
import com.dynamsoft.dbr.PublicRuntimeSettings;
import com.dynamsoft.dbr.RegionDefinition;
import com.dynamsoft.dbr.TextResult;
import com.dynamsoft.dbr.DBRServerLicenseVerificationListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DBR extends Activity implements Camera.PreviewCallback {
    public static String TAG = "DBRDemo";
    private String mStrLicense;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //apply for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=  PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
            }
            else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            }
        }
        mPreview = findViewById(R.id.camera_preview);
	    try {
            mBarcodeReader = new BarcodeReader();
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }
        try {
            mStrLicense = getIntent().getStringExtra("barcodeLicense");
            int exCount = getIntent().getIntExtra("expectedBarcodesCount",0);
            int regionTop = getIntent().getIntExtra("regionTop",0);
            int regionLeft = getIntent().getIntExtra("regionLeft",0);
            int regionRight = getIntent().getIntExtra("regionRight",0);
            int regionBottom = getIntent().getIntExtra("regionBottom",0);
            int regionMeasuredByPercentage = getIntent().getIntExtra("regionMeasuredByPercentage",0);
            if (mStrLicense != null && !"".equals(mStrLicense)) {
                mBarcodeReader.initLicense(mStrLicense);
            } else {
                String strLicenseKey = getIntent().getStringExtra("barcodeLicenseKey");
                if (strLicenseKey != null && !"".equals(strLicenseKey)) {
                    mBarcodeReader.initLicenseFromServer("", strLicenseKey, new DBRServerLicenseVerificationListener() {
                        @Override
                        public void licenseVerificationCallback(boolean isSuccess, Exception error) {
                            if (!isSuccess) {
                                Log.e("DBR", "DBR license verify failed due to " + error.getMessage());
                            }
                        }
                    });
                } else {
                    String handshakeCode = getIntent().getStringExtra("handshakeCode");
                    String organizationID = getIntent().getStringExtra("organizationID");
                    String mainServerURL = getIntent().getStringExtra("mainServerURL");
                    String standbyServerURL = getIntent().getStringExtra("standbyServerURL");
                    String sessionPassword = getIntent().getStringExtra("sessionPassword");
                    int uuidGenerationMethod = getIntent().getIntExtra("uuidGenerationMethod", EnumDMUUIDGenerationMethod.DM_UUIDGM_RANDOM);
                    //1 for DM_UUIDGM_RANDOM; 2 for DM_UUIDGM_HARDWARE
                    int maxBufferDays = getIntent().getIntExtra("maxBufferDays", 0);
                    int chargeWay = getIntent().getIntExtra("chargeWay", 0);
                    String strLimitedLicenseModules = getIntent().getStringExtra("limitedLicenseModules");
                    strLimitedLicenseModules = strLimitedLicenseModules.substring(1, strLimitedLicenseModules.length() - 1);
                    String[] modules = strLimitedLicenseModules.split(",");
                    List<Integer> limitedLicenseModules = new ArrayList<>();
                    for (String str : modules) {
                        limitedLicenseModules.add(Integer.parseInt(str));
                    }
                    DMLTSConnectionParameters parameters = new DMLTSConnectionParameters();
                    parameters.handshakeCode = handshakeCode;
                    parameters.organizationID = organizationID;
                    parameters.mainServerURL = mainServerURL;
                    parameters.standbyServerURL = standbyServerURL;
                    parameters.sessionPassword = sessionPassword;
                    parameters.uuidGenerationMethod = uuidGenerationMethod;
                    parameters.maxBufferDays = maxBufferDays;
                    parameters.chargeWay = chargeWay;
                    parameters.limitedLicenseModules = limitedLicenseModules;
                    mBarcodeReader.initLicenseFromLTS(parameters, new DBRLTSLicenseVerificationListener() {
                        @Override
                        public void LTSLicenseVerificationCallback(boolean isSuccess, Exception error) {
                            if (!isSuccess) {
                                Log.e("DBR", "DBR lts license verify failed due to " + error.getMessage());
                            }
                        }
                    });
                }
            }
            try {
                RegionDefinition region = new RegionDefinition();
                region.regionLeft = regionTop;
                region.regionTop = regionLeft;
                region.regionRight = regionBottom;
                region.regionBottom = regionRight;
                region.regionMeasuredByPercentage = regionMeasuredByPercentage;
                PublicRuntimeSettings settings = mBarcodeReader.getRuntimeSettings();
                settings.expectedBarcodesCount = exCount;
                settings.barcodeFormatIds_2 = EnumBarcodeFormat_2.BF2_POSTALCODE | EnumBarcodeFormat_2.BF2_NONSTANDARD_BARCODE | EnumBarcodeFormat_2.BF2_DOTCODE;
                settings.region = region;
                mBarcodeReader.updateRuntimeSettings(settings);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e){
            Log.e("DBR", e.getMessage());
            e.printStackTrace();
        }
        mFlashImageView = findViewById(R.id.ivFlash);
        mFlashTextView = findViewById(R.id.tvFlash);
        mRectLayer = findViewById(R.id.rectLayer);

        mSurfaceHolder = new CameraPreview(DBR.this);
        mPreview.addView(mSurfaceHolder);

        Intent intent = getIntent();
        if (intent.getAction().equals("com.dynamsoft.dbr")) {
            mIsIntent = true;
        }
    }

    static final int PERMISSIONS_REQUEST_CAMERA = 473;
    static final int RC_BARCODE_TYPE = 8563;
    private FrameLayout mPreview = null;
    private CameraPreview mSurfaceHolder = null;
    private Camera mCamera = null;
    private BarcodeReader mBarcodeReader;
    private long mBarcodeFormat = EnumBarcodeFormat.BF_ONED | EnumBarcodeFormat.BF_QR_CODE | EnumBarcodeFormat.BF_PDF417 | EnumBarcodeFormat.BF_DATAMATRIX;
    private ImageView mFlashImageView;
    private TextView mFlashTextView;
    private RectLayer mRectLayer;
    private boolean mIsDialogShowing = false;
    private boolean mIsReleasing = false;
    final ReentrantReadWriteLock mRWLock = new ReentrantReadWriteLock();

    @Override protected void onResume() {
        super.onResume();
        waitForRelease();
        if (mCamera == null) {
            openCamera();
        } else {
            mCamera.startPreview();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (mCamera != null) {
            mSurfaceHolder.stopPreview();
            mCamera.setPreviewCallback(null);
            mIsReleasing = true;
            releaseCamera();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        waitForRelease();
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public void showAbout(View v) {
        CustomDialog.Builder builder = new CustomDialog.Builder(this);
        builder.setTitle("About");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mIsDialogShowing = false;
            }
        });
        Spanned spanned = Html.fromHtml("<font color='#FF8F0D'><a href=\"http://www.dynamsoft.com\">Download Free Trial here</a></font><br/>");
        builder.setMessage(spanned);
        builder.create(R.layout.about, R.style.AboutDialog).show();
        mIsDialogShowing = true;
    }

    public void setFlash(View v) {
        if (mCamera != null) {
            Camera.Parameters p = mCamera.getParameters();
            String flashMode = p.getFlashMode();
             if (flashMode.equals(Camera.Parameters.FLASH_MODE_OFF)) {
                 p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                 mFlashImageView.setImageResource(R.mipmap.flash_on);
                 mFlashTextView.setText("Flash on");
             }
            else {
                 p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                 mFlashImageView.setImageResource(R.mipmap.flash_off);
                 mFlashTextView.setText("Flash off");
             }
            mCamera.setParameters(p);
            mCamera.startPreview();
        }
    }

    private static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
            // attempt to get a Camera instance
        }
        catch (Exception e){
            Log.i(TAG, "Camera is not available (in use or does not exist)");
        }
        return c;
        // returns null if camera is unavailable
    }

    private void openCamera()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mCamera = getCameraInstance();
                if (mCamera != null) {
                    mCamera.setDisplayOrientation(90);
                    Camera.Parameters cameraParameters = mCamera.getParameters();
                    List<String> supportList= cameraParameters.getSupportedFocusModes();
                    if(supportList.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    } else if(supportList.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    }

                    mCamera.setParameters(cameraParameters);
                }


                Message message = handler.obtainMessage(OPEN_CAMERA, 1);
                message.sendToTarget();
            }
        }).start();
    }

    private void releaseCamera()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mCamera.release();
                mCamera = null;
                mRWLock.writeLock().lock();
                mIsReleasing = false;
                mRWLock.writeLock().unlock();
            }
        }).start();
    }

    private void waitForRelease() {
        while (true) {
            mRWLock.readLock().lock();
            if (mIsReleasing) {
                mRWLock.readLock().unlock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                mRWLock.readLock().unlock();
                break;
            }
        }
    }

    private boolean mFinished = true;
    private long mStartTime;
    private final static int READ_RESULT = 1;
    private final static int OPEN_CAMERA = 2;
    private final static int RELEASE_CAMERA = 3;
    private boolean mIsIntent = false;

    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case READ_RESULT:
                    TextResult[] result = (TextResult[])msg.obj;
                    TextView view = findViewById(R.id.textView);
                    //long lTime = (SystemClock.currentThreadTimeMillis() - mStartTime);
                    long lTime = new Date().getTime()-mStartTime;
                    if (result != null && result.length>0) {
                        TextResult barcode = result[0];
                        String msgText = "";
                        String msgFormat = "";

                        JSONArray jsonArray = new JSONArray();
                        for(int i = 0; i<result.length ;i++){
                            msgText = result[i].barcodeText;
                            if (result[i].barcodeFormat_2 != 0){
                                msgFormat = result[i].barcodeFormatString_2;
                                //msgText = msgText + "\nResult: " + result[i].barcodeText + "\nFormat: "+ result[i].barcodeFormatString_2 + "\n";
                            }else {
                                msgFormat = result[i].barcodeFormatString;
                                //msgText = msgText + "\nResult: " + result[i].barcodeText + "\nFormat: "+ result[i].barcodeFormatString + "\n";
                            }
                            try {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("Result", msgText);
                                jsonObject.put("Format", msgFormat);
                                jsonArray.put(i, jsonObject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        if (mIsIntent) {
                            Intent data = new Intent();
                            data.putExtra("SCAN_RESULT", jsonArray.toString());
                            data.putExtra("SCAN_RESULT_FORMAT", "");
                            data.putExtra("FROMJS", mStrLicense);
                            DBR.this.setResult(DBR.RESULT_OK, data);
                            DBR.this.finish();
                            return;
                        }

                        CustomDialog.Builder builder = new CustomDialog.Builder(DBR.this);
                        builder.setTitle("Result");
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                mIsDialogShowing = false;
                            }
                        });
                        int y = Integer.MIN_VALUE;
                        //rotate cornerPoints by 90, NewLeft = H - Ymax, NewTop = Left, NewWidth = Height, NewHeight = Width
                        for (com.dynamsoft.dbr.Point vertex : barcode.localizationResult.resultPoints) {
                            if (y < vertex.y) {
                                y = vertex.y;
                            }
                        }

                        builder.setMessage(barcode).setExtraInfo(lTime/1000f + "");
                        CustomDialog dialog = builder.create(R.layout.result, R.style.ResultDialog);
                        dialog.getWindow().setLayout(mRectLayer.getWidth() * 10 / 12, (mRectLayer.getHeight() >> 1) + 16);
                        dialog.show();
                        mIsDialogShowing = true;
                    }
                    mFinished = true;
                    break;
                case OPEN_CAMERA:
                    if (mCamera != null) {
                        mCamera.setPreviewCallback(DBR.this);
                        mSurfaceHolder.setCamera(mCamera);
                        Camera.Parameters p = mCamera.getParameters();
                        if (mFlashTextView.getText().equals("Flash on")) {
                            p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        }
                        mCamera.setParameters(p);
                        mSurfaceHolder.startPreview();
                    }
                    break;
                case RELEASE_CAMERA:

                    break;
            }
        }
    };

    private boolean isFirstTime = true;

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (isFirstTime) {
            isFirstTime = false;
            try {
                File file = new File(Environment.getExternalStorageDirectory() + "/tmp.yuv");
                FileOutputStream output = new FileOutputStream(file);
                output.write(data);
                output.flush();
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mFinished && !mIsDialogShowing) {
            mFinished = false;
            //mStartTime = SystemClock.currentThreadTimeMillis();
            mStartTime = new Date().getTime();
            Camera.Size size = camera.getParameters().getPreviewSize();
            try {
                TextResult[] readResult = mBarcodeReader.decodeBuffer(data, size.width, size.height, size.width, EnumImagePixelFormat.IPF_NV21, "");
                Message message = handler.obtainMessage(READ_RESULT, readResult);
                message.sendToTarget();
            }catch (BarcodeReaderException e){
                e.printStackTrace();
            }
        }
    }
}
