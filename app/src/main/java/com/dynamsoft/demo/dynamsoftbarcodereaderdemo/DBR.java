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

import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.BarcodeReaderException;
import com.dynamsoft.barcode.EnumBarcodeFormat;
import com.dynamsoft.barcode.EnumBarcodeFormat_2;
import com.dynamsoft.barcode.EnumImagePixelFormat;
import com.dynamsoft.barcode.PublicRuntimeSettings;
import com.dynamsoft.barcode.TextResult;
import com.dynamsoft.barcode.DBRServerLicenseVerificationListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
        mStrLicense = getIntent().getStringExtra("barcodeLicense");
        Log.i("mStrLicense", mStrLicense);
	      try {
            mBarcodeReader = new BarcodeReader();
        } catch (BarcodeReaderException e) {
            e.printStackTrace();
        }
        try {
            mBarcodeReader.initLicense(mStrLicense);
            String strLicenseKey = getIntent().getStringExtra("barcodeLicenseKey");
            Log.i("strLicenseKey", strLicenseKey);
            if(strLicenseKey !=null){
                mBarcodeReader.initLicenseFromServer("",strLicenseKey, new DBRServerLicenseVerificationListener() {
                    @Override
                    public void licenseVerificationCallback(boolean isSuccess, Exception error) {
                        if (!isSuccess) {
                            Log.e("DBR", "DBR license verify failed due to " + error.getMessage());
                        }
                    }
                });
            }
        }catch (Exception e){
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
                        for(int i = 0; i<result.length ;i++){
                            if (result[i].barcodeFormat_2 != 0){
                                msgText = msgText + "\nResult: " + result[i].barcodeText + "\nFormat: "+ result[i].barcodeFormatString_2 + "\n";
                            }else {
                                msgText = msgText + "\nResult: " + result[i].barcodeText + "\nFormat: "+ result[i].barcodeFormatString + "\n";
                            }
                        }
                        if (mIsIntent) {
                            Intent data = new Intent();
                            data.putExtra("SCAN_RESULT", msgText);
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
                        for (com.dynamsoft.barcode.Point vertex : barcode.localizationResult.resultPoints) {
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
            PublicRuntimeSettings settings;
            try {
                settings = mBarcodeReader.getRuntimeSettings();
                
                settings.barcodeFormatIds_2 = EnumBarcodeFormat_2.BF2_POSTALCODE | EnumBarcodeFormat_2.BF2_NONSTANDARD_BARCODE | EnumBarcodeFormat_2.BF2_DOTCODE;
                mBarcodeReader.updateRuntimeSettings(settings);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
