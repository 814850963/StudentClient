package com.dryht.mobile.Activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.ImageWriter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dryht.mobile.R;
import com.dryht.mobile.utils.XToastUtils;
import com.xuexiang.xui.utils.StatusBarUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import org.opencv.imgcodecs.Imgcodecs.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FdActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    protected static int LIMIT_TIME = 3;//临界的时间，超过3秒保存图片一次
    protected static Bitmap mFaceBitmap;//包含有人脸的图像
    private CameraBridgeViewBase openCvCameraView;

    private static final String TAG = "数据结果：";

    private Handler mHandler;

    private CascadeClassifier mFrontalFaceClassifier = null; //正脸 级联分类器

    //文件名/文件
    private ArrayList<String> filename = new ArrayList<>();
    private ArrayList<String> file = new ArrayList<>();
    //录制/识别判断标识
    private int limit = 0;
    //考勤的课程
    private String classid;
    private String account;
    private String passwd;

    private Mat mRgba; //图像容器
    private Mat mGray;
    private TextView hint;


    //记录的时间，秒级别
    private long mRecodeTime;
    //记录的时间，毫秒级别，空闲时间，用来计数，实现0.5秒一次检查
    private long mRecodeFreeTime;
    //当前的时间，秒级别
    private long mCurrentTime = 0;
    //当前的时间，毫秒级别
    private long mMilliCurrentTime = 0;

    //解决横屏问题
    private Mat Matlin;
    private Mat gMatlin;
    private int absoluteFaceSize;
    private SharedPreferences sharedPreferences;
    //人脸矩形框
    Rect facerect = null;
    //人脸垫子
    Mat faceimage = null;
    private String baseUrl=null;
    //请求状态码
    private static int REQUEST_PERMISSION_CODE = 1;

    //图片保存路径
    private String picPath = "/storage/emulated/0/DCIM/Camera/";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_detect_surface_view);
        sharedPreferences= getSharedPreferences("data", Context.MODE_PRIVATE);
        mHandler = new Handler();
        //获取flag判断是录入还是识别
        limit =  getIntent().getIntExtra("flag",1);
        classid =  getIntent().getStringExtra("classid");
        passwd = getIntent().getStringExtra("passwd");
        account = getIntent().getStringExtra("account");
        //设置顶部导航栏
        StatusBarUtils.setStatusBarDarkMode(this);
        getWindow().setStatusBarColor(getResources().getColor(R.color.thiscolor));
        if(limit>1)
            baseUrl = "recordfacedata/";
        else
            baseUrl = "facelogin/";
        checkPermission();
        //初始化控件
        initComponent();
        //初始化摄像头
        initCamera();
        //opencv资源的初始化在父类的 onResume 方法中
        //去寻找是否已经有了相机的权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
        }else {
            //否则去请求相机权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV init error");
        }
        //初始化opencv资源
        initOpencv();
    }

    /**
     * @Description 初始化组件
     */
    protected void initComponent() {
        openCvCameraView = findViewById(R.id.javaCameraView);
        hint = findViewById(R.id.hint);
    }

    /**
     * @Description 初始化摄像头
     */
    protected void initCamera() {
        int camerId = 1;
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            Log.v("notice", "在 CameraRenderer 类的 openCamera 方法 中执行了开启摄像 Camera.open 方法,cameraId:" + i);
            camerId = i;
            break;
        }
        openCvCameraView.setCameraIndex(1); //摄像头索引        -1/0：后置双摄     1：前置
        openCvCameraView.enableFpsMeter(); //显示FPS
        openCvCameraView.setCvCameraViewListener(this);//监听
        openCvCameraView.setMaxFrameSize(950, 500);//设置帧大小
    }

    /**
     * @Description 初始化opencv资源
     */
    protected void initOpencv() {
        initFrontalFace();
        // 显示
        openCvCameraView.enableView();
    }

    /**
     * @Description 初始化正脸分类器
     */
    public void initFrontalFace() {
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt); //OpenCV的人脸模型文件： lbpcascade_frontalface_improved
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            // 加载 正脸分类器
            mFrontalFaceClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }



    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        mGray = new Mat();
        //解决横屏问题
        Matlin = new Mat(width, height, CvType.CV_8UC3);
        gMatlin = new Mat(width, height, CvType.CV_8UC3);

        absoluteFaceSize = (int)(height * 0.6);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        gMatlin.release();
        Matlin.release();
    }

    /**
     * @Description 在这里实现人脸检测
     */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mMilliCurrentTime = System.currentTimeMillis() / 100;//获取当前时间毫秒级别
        mCurrentTime = mMilliCurrentTime / 10;//获取当前时间，秒级别
        int rotation = openCvCameraView.getDisplay().getRotation();

        //使前置的图像也是正的
        mRgba = inputFrame.rgba(); //RGBA
        mGray = inputFrame.gray(); //单通道灰度图
        //解决  前置摄像头旋转显示问题
        Core.flip(mRgba, mRgba, 1); //旋转,变成镜像
        Core.flip(mGray, mGray, 1);


        Rect[] faceArray = new Rect[0];
        if (mRecodeFreeTime + 10 <= mMilliCurrentTime) {
            mRecodeFreeTime = mMilliCurrentTime;
            if (mRecodeTime == 0 || mRecodeTime < mCurrentTime) {//识别到人之后，1秒做一次检测
                mRecodeTime = mCurrentTime;//记录当前时间
                if (rotation == Surface.ROTATION_0) {
                    MatOfRect faces = new MatOfRect();
                    Core.rotate(mGray, gMatlin, Core.ROTATE_90_CLOCKWISE);
                    Core.rotate(mRgba, Matlin, Core.ROTATE_90_CLOCKWISE);
                    if (mFrontalFaceClassifier != null) {
                        mFrontalFaceClassifier.detectMultiScale(gMatlin, faces, 1.1, 5, 2, new Size(absoluteFaceSize, absoluteFaceSize), new Size());
                    }
                    faceArray = faces.toArray();

                    for (int i = 0; i < faceArray.length; i++) {
                        Imgproc.rectangle(Matlin, faceArray[i].tl(), faceArray[i].br(), new Scalar(255, 255, 255), 2);
                        if (i == faceArray.length - 1) {
                            facerect = new Rect(faceArray[i].x, faceArray[i].y, faceArray[i].width, faceArray[i].height);
                        }
                    }
                    Core.rotate(Matlin, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);

                }
                if(1<= faceArray.length)
                {
                    faceimage = new Mat(Matlin,facerect);
                    //把mat转换成bitmap
                    saveImg(faceimage);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            hint.setText("还需录入"+(limit-file.size()+1)+"次");
                        }
                    },0);
                    if (file.size()>limit)
                    {
                        //挂起
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                hint.setText("正在上传数据");
                                openCvCameraView.disableView();
                            }
                        }, 0);
                        OkHttpClient mOkHttpClient=new OkHttpClient();
                        MultipartBody.Builder builder=  new MultipartBody.Builder().setType(MultipartBody.FORM);
                            for (int i = 0;i<file.size();i++) {
                                RequestBody fileBody = RequestBody.create(MediaType.parse("image/*"), new File(String.valueOf(file.get(i))));
                                builder.addFormDataPart("img",filename.get(i),fileBody);
                            }
                        builder.addFormDataPart("auth",sharedPreferences.getString("auth","0"));
                        builder.addFormDataPart("identity",sharedPreferences.getString("identity","0"));
                        if(classid!=null)
                            builder.addFormDataPart("classid",classid);
                        if(passwd!=null)
                            builder.addFormDataPart("passwd",passwd);
                        if(account!=null)
                            builder.addFormDataPart("account",account);
                        Request mRequest=new Request.Builder()
                                .url(com.dryht.mobile.Util.Utils.generalUrl+baseUrl)
                                .post(builder.build())
                                .build();

                        mOkHttpClient.newCall(mRequest).enqueue(new Callback(){
                            @Override
                            public void onResponse(@NotNull okhttp3.Call call, @NotNull Response response) throws IOException {
                                Looper.prepare();
                                //String转JSONObject
                                JSONObject result = null;
                                try {
                                    result = new JSONObject(response.body().string());
                                    System.out.println(result.get("status"));
                                    for (int i = 0;i<file.size();i++) {
                                        File f = new File(String.valueOf(file.get(i)));
                                        f.delete();
                                    }
                                    //取数据
                                    if(result.get("status").equals("1"))
                                    {
                                        file=null;
                                        filename=null;
                                        Intent intent=new Intent();
                                        if(limit>1)
                                            XToastUtils.success("人脸录制成功");
                                        else
                                            XToastUtils.success("人脸识别成功");
                                        finish();

                                    }
                                    else
                                    {
                                        if(limit>1)
                                            XToastUtils.error("人脸录制失败");
                                        else
                                            XToastUtils.error("人脸识别失败");
                                        finish();
                                    }

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                Looper.loop();

                            }
                            @Override
                            public void onFailure(@NotNull okhttp3.Call call, @NotNull IOException e) {
                                Looper.prepare();
                                Toast.makeText(FdActivity.this,"录制失败",Toast.LENGTH_LONG).show();
                                Looper.loop();
                            }
                        });
                    }

                    }
                }
            }

        return mRgba;
    }



    private void saveImg(Mat rgba) {
        //先把mat转成bitmap
        Bitmap mBitmap = null;
        //Imgproc.cvtColor(seedsImage, rgba, Imgproc.COLOR_GRAY2RGBA, 4); //转换通道
        mBitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgba, mBitmap);
        FileOutputStream fileOutputStream = null;
        File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/smartass/");
        if(!f.exists()){
            //创建文件夹
            f.mkdirs();
        }
        int q=(int)(Math.random()*100);
        try {
            File img = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/smartass/"+q+".jpeg");
            if(!img.exists()){
                //创建文件夹
                img.createNewFile();
                System.out.println(img.exists());
            }
            fileOutputStream = new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+"/smartass/"+q+".jpeg");
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            filename.add(q+".jpeg");
            file.add(Environment.getExternalStorageDirectory().getAbsolutePath()+"/smartass/"+q+".jpeg");
            //回收
            fileOutputStream.flush();
            fileOutputStream.close();
            mBitmap.recycle();
            Log.d(TAG, "图片已保存至本地");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            int write = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int read = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (write != PackageManager.PERMISSION_GRANTED || read != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 300);
            } else {
                String name = "CrashDirectory";
                File file1 = new File(Environment.getExternalStorageDirectory(), name);
                if (file1.mkdirs()) {
                    Log.i("wytings", "permission -------------> " + file1.getAbsolutePath());
                } else {
                    Log.i("wytings", "permission -------------fail to make file ");
                }
            }
        } else {
            Log.i("wytings", "------------- Build.VERSION.SDK_INT < 23 ------------");
        }
    }


}

