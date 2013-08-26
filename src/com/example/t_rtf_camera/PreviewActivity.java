package com.example.t_rtf_camera;


import java.util.concurrent.Semaphore;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

public class PreviewActivity extends Activity implements Camera.PreviewCallback{

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preview);
		
        // Create an instance of Camera
        mCamera = getCameraInstance();
        mCamera.setDisplayOrientation(90);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera, this);
                
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        
        preview.addView(mPreview);
        
        mRS = RenderScript.create(this);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_preview, menu);
		return true;
	}
	
	
	
	/** Check if this device has a camera */
	private boolean checkCameraHardware(Context context) {
	    if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
	        // this device has a camera
	        return true;
	    } else {
	        // no camera on this device
	        return false;
	    }
	}
	
	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance(){
	    Camera c = null;
	    try {
	        c = Camera.open(); // attempt to get a Camera instance
	    }
	    catch (Exception e){
	        // Camera is not available (in use or does not exist)
	    }
	    return c; // returns null if camera is unavailable
	}
	
	private int min(int a, int b){
		if (a>b){
			return b;
		}
		else{
			return a;
		}
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// TODO Auto-generated method stub
		long startTime = SystemClock.uptimeMillis();
		long midTime = 0;
		long endTime = 0;
		
		Camera.Parameters params = camera.getParameters();
		int width = params.getPreviewSize().width;
		int height = params.getPreviewSize().height;
		
		if (null == mInAllocation){
			Type.Builder inTypeBuilder = new Type.Builder(mRS, Element.U8_2(mRS));
			Type.Builder outTypeBuilder = new Type.Builder(mRS, Element.U8_4(mRS));
			Type.Builder resultTypeBuilder = new Type.Builder(mRS, Element.I32(mRS));
			
			inTypeBuilder.setX(width).setY(height);
			outTypeBuilder.setX(width).setY(height);
			resultTypeBuilder.setX(width).setY(height);
			
			Type inType = inTypeBuilder.create();
			Type outType = outTypeBuilder.create();
			Type resultType = resultTypeBuilder.create();
			
			mInAllocation = Allocation.createTyped(mRS, inType);	
		    mRotateAllocation = Allocation.createTyped(mRS, outType);
		    
		    mBlackWhiteAllocation = Allocation.createTyped(mRS, resultType);
		    mSepiaToneAllocation = Allocation.createTyped(mRS, resultType);
		    mRevertAllocation = Allocation.createTyped(mRS, resultType);
		    
            int[] rowIndices = new int[height];
            for (int i = 0; i < height; i++) {
                rowIndices[i] = i;
            }
            mRowIndicesAllocaction = Allocation.createSized(mRS, Element.I32(mRS), height, Allocation.USAGE_SCRIPT);
            mRowIndicesAllocaction.copyFrom(rowIndices);
		    
		    mScript = new ScriptC_filter(mRS, getResources(), R.raw.filter);
		    
		    mScript.set_mImageWidth(width);
		    mScript.set_mImageHeight(height);
		    
		    mColorsBlackWhite = new int[width*height];
		    mColorsSepiaTone = new int[width*height];
		    mColorsRevert = new int[width*height];
		    
		    //will 90 rotate
		   mBNWPreview = new FilterPreview(this, readyBlackWhite, null, height, width);
		   mSepiaTonePreview = new FilterPreview(this, readySepiaTone, null, height, width);
		   mRevertPreview = new FilterPreview(this, readyRevert, null, height, width);
		    
		    mBNWPreview.getHolder().setFixedSize(height, width);
		    FrameLayout container = (FrameLayout)findViewById(R.id.bnw_preview);
		    container.addView(mBNWPreview);
		    
		    mSepiaTonePreview.getHolder().setFixedSize(height, width);
		    container = (FrameLayout)findViewById(R.id.sepia_tone_preview);
		    container.addView(mSepiaTonePreview);
		    
		    mRevertPreview.getHolder().setFixedSize(height, width);
		    container = (FrameLayout)findViewById(R.id.revert_preview);
		    container.addView(mRevertPreview);
		    
		}
		
		mInAllocation.copyFromUnchecked(data);
		
		
		mScript.bind_gInPixels(mInAllocation);
		mScript.bind_gRotatePixels(mRotateAllocation);
		
		mScript.forEach_root(mRowIndicesAllocaction, mRowIndicesAllocaction);
		
		mScript.forEach_blackwhite(mRotateAllocation, mBlackWhiteAllocation);
		synchronized(mColorsBlackWhite){
			mBlackWhiteAllocation.copyTo(mColorsBlackWhite);
		}
		mBNWPreview.drawFrame(mColorsBlackWhite, true);
		
		mScript.forEach_sepiatone(mRotateAllocation, mSepiaToneAllocation);
		synchronized(mColorsSepiaTone){
			mSepiaToneAllocation.copyTo(mColorsSepiaTone);
		}
		mSepiaTonePreview.drawFrame(mColorsSepiaTone, true);
		
		mScript.forEach_revert(mRotateAllocation, mRevertAllocation);
		synchronized(mColorsRevert){
			mRevertAllocation.copyTo(mColorsRevert);
		}
		mRevertPreview.drawFrame(mColorsRevert, true);
		
		midTime = SystemClock.uptimeMillis();
		
		/*
		Bitmap bmpBNW = Bitmap.createBitmap(colorsBNW, width, height, Bitmap.Config.ARGB_8888);
		mBlackWhiteView.setImageBitmap(bmpBNW);
		
		Bitmap bmpSepiaTone = Bitmap.createBitmap(colorsSepiaTone, width, height, Bitmap.Config.ARGB_8888);
		mSepiaToneView.setImageBitmap(bmpSepiaTone);
		
		Bitmap bmpRevert = Bitmap.createBitmap(colorsRevert, width, height, Bitmap.Config.ARGB_8888);
		mRevertView.setImageBitmap(bmpRevert);
		*/
		endTime = SystemClock.uptimeMillis();
		
		Log.e("Tang zhiming", "total="+(endTime-startTime));
		
		
	}
	
	
	
	private Camera mCamera;
	private CameraPreview mPreview;
	
	FilterPreview mBNWPreview;
	FilterPreview mSepiaTonePreview;
	FilterPreview mRevertPreview;
	
	private int[] mColorsBlackWhite;
	private int[] mColorsSepiaTone;
	private int[] mColorsRevert;
	
	private Semaphore readyBlackWhite;
	private Semaphore readySepiaTone;
	private Semaphore readyRevert;
	
    private RenderScript mRS;
    private ScriptC_filter mScript = null;
    private Allocation mInAllocation = null;
    private Allocation mRotateAllocation = null;
    
    private Allocation mBlackWhiteAllocation = null;
    private Allocation mSepiaToneAllocation = null;
    private Allocation mRevertAllocation = null;
    
    private Allocation mRowIndicesAllocaction = null;
}
