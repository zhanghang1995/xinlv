package com.lyz.xinlv.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lyz.Bean.UserDataBean;
import com.lyz.SG.SGFilter;
import com.lyz.monitor.utils.CalculateHeartRate;
import com.lyz.monitor.utils.ImageProcessing;
import com.lyz.ui.MySurfaceView;
import com.lyz.ui.ProgressWheel;
import com.lyz.ui.TwinkleDrawable;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 程序的主入口
 *
 * @author liuyazhuang
 */
public class MainActivity extends Activity {
    //曲线
    private Timer timer;
    //Timer任务，与Timer配套使用
    private TimerTask task;

    // 灰度值
    private static double brightvalue;
    // 红色通道值
    private static double redvalue;
    private static double greenvalue;
    private static double bluevalue;
    private static int j;

    private static double flag = 1;
    private Handler handler = null;
    private Handler mRealTimeHandler = null;
    private String title = "pulse";
    private XYSeries series;
    private XYMultipleSeriesDataset mDataset;
    private GraphicalView chart;
    private XYMultipleSeriesRenderer renderer;
    private Context context;
    private int addX = -1;
    private double addY;
    private double maxY;
    private double minY;
    private int t = 0;

    // 用于计算实时心率的数据
    List<Double> mRealTimeDatas = new ArrayList<Double>();


    int[] xv = new int[AXISXMAX];
    double[] yv = new double[AXISXMAX];

    //	private static final String TAG = "HeartRateMonitor";
    private static final AtomicBoolean processing = new AtomicBoolean(false);
    //Android手机预览控件
    private static MySurfaceView preview = null;
    //预览设置信息
    private static SurfaceHolder previewHolder = null;
    //Android手机相机句柄
    private static Camera camera = null;

    private static WakeLock wakeLock = null;
    // 心率数值显示
    private TextView m_TvLabel;
    // 心跳的ImageView
    private ImageView m_IvHeart;
    // 心跳的动画
    private TwinkleDrawable m_HeartDrawable;
    // 心跳动画是否在播放的标志位
    private boolean mIsHeartAnimPlaying = false;
    // 最外圈的圆形进度条
    private ProgressWheel m_ProgressWheel;


    private static int averageIndex = 0;
    private static final int averageArraySize = 4;
    private static final int[] averageArray = new int[averageArraySize];
    private double YMAX = AXISYMAX;
    private double YMIN = AXISYMIN;
    // 两帧图像的间隔时间，ms
    private static final int INTERVAL = 50;
    private static final double AXISYMAX = 5;
    private static final double AXISYMIN = 4;
    private static final int AXISXMAX = 6000 / INTERVAL;


    private UserDataBean mUserDataBean = new UserDataBean();
    private List<Double> mDatas = new ArrayList<Double>();
    private List<Double> mRedDatas = new ArrayList<Double>();
    private List<Double> mGreenDatas = new ArrayList<Double>();
    private List<Double> mBlueDatas = new ArrayList<Double>();
    private int count;
    private double currentYtop = AXISYMAX;
    private double currentYbottom = AXISYMIN;
    private int mRealTimeHeartRate = 0;
    private int mHeartRate;

    private boolean mIsHeartRateCanSet=true;


    /**
     * 类型枚举
     */
    public static enum TYPE {
        GREEN, RED
    }


    //设置默认类型
    private static TYPE currentType = TYPE.GREEN;

    //获取当前类型
    public static TYPE getCurrent() {
        return currentType;
    }

    //心跳下标值
    private static int beatsIndex = 0;
    //心跳数组的大小
    private static final int beatsArraySize = 3;
    //心跳数组
    private static final int[] beatsArray = new int[beatsArraySize];
    //心跳脉冲
    private static double beats = 0;
    //开始时间
    private static long startTime = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        initView();
        initConfig();
        super.onCreate(savedInstanceState);
    }

    private void initView() {
        m_TvLabel = (TextView) findViewById(R.id.tv_data_measure);
        m_IvHeart = (ImageView) findViewById(R.id.iv_heart_measure);

        m_HeartDrawable = new TwinkleDrawable(m_IvHeart);
        m_HeartDrawable.addDrawable(getResources().getDrawable(R.drawable.ic_heart_big), true);
        m_HeartDrawable.addDrawable(getResources().getDrawable(R.drawable.ic_heart_small), false);

        m_ProgressWheel = (ProgressWheel) findViewById(R.id.pw_heartrate);
        m_ProgressWheel.setMax(2*AXISXMAX-10);
    }


    private void calRealTimeHeartRate() {
        int size = mRealTimeDatas.size();
        double[] realtime_data_origin = new double[size];
        for (int i = 0; i < size; i++) {
            realtime_data_origin[i] = mRealTimeDatas.get(i);
        }

        // SG算法的参数矩阵
        double[] coeffs = SGFilter.computeSGCoefficients(3, 3, 5);
        // SG算法去噪处理
        double[] realtime_data_smoothed = new SGFilter(3, 3).smooth(realtime_data_origin, coeffs);
        // SG算法去噪处理第二遍
        realtime_data_smoothed = new SGFilter(3, 3).smooth(realtime_data_smoothed, coeffs);


        List<Double> realtime_data_smoothed_list = new ArrayList<Double>();
        // 去头去尾
        for (int i = 5; i < realtime_data_smoothed.length-5; i++) {
            realtime_data_smoothed_list.add(realtime_data_smoothed[i]);
        }
        System.out.println("实时心率去噪数据");
        System.out.println(realtime_data_smoothed_list);
        // peaksList为峰的横坐标列表
        List<Integer> peaksList = CalculateHeartRate.findPeaks(realtime_data_smoothed_list);
        System.out.println("实时心率RR间隔");
        System.out.println(CalculateHeartRate.calRRInteval(peaksList));
        mRealTimeHeartRate = CalculateHeartRate.calHeartRate(peaksList, INTERVAL);
        Log.i("real time heartRate", mRealTimeHeartRate + "");
    }

    @Override
    protected void onStart() {
        count = 0;
        handler = null;
        mDatas.clear();
        mRedDatas.clear();
        mGreenDatas.clear();
        mBlueDatas.clear();
        mRealTimeDatas.clear();
        timer = null;
        task = null;
        m_ProgressWheel.setProgress(0);
//        mIsHeartRateCanSet=true;

        initTimer();
        Log.i("onStart", "onStart()");
        super.onStart();
    }


    @Override
    protected void onStop() {

        super.onStop();
    }

    /**
     * 初始化配置
     */
    private void initConfig() {
        //曲线
        context = getApplicationContext();

        //这里获得main界面上的布局，下面会把图表画在这个布局里面
        LinearLayout layout = (LinearLayout) findViewById(R.id.linearLayout1);

        //这个类用来放置曲线上的所有点，是一个点的集合，根据这些点画出曲线
        series = new XYSeries(title);

        //创建一个数据集的实例，这个数据集将被用来创建图表
        mDataset = new XYMultipleSeriesDataset();

        //将点集添加到这个数据集中
        mDataset.addSeries(series);

        //以下都是曲线的样式和属性等等的设置，renderer相当于一个用来给图表做渲染的句柄
        int color = Color.RED;
        PointStyle style = PointStyle.CIRCLE;
        renderer = buildRenderer(color, style, true);


        //设置好图表的样式
        setChartSettings(renderer, "X", "Y", 0, AXISXMAX, AXISYMIN, AXISYMAX, Color.WHITE, Color.WHITE);

        //生成图表
        chart = ChartFactory.getCubeLineChartView(context, mDataset, renderer, 0.3f);

        //将图表添加到布局中去
        layout.addView(chart, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        //这里的Handler实例将配合下面的Timer实例，完成定时更新图表的功能

        //获取SurfaceView控件
        preview = (MySurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        previewHolder.addCallback(preview);
        previewHolder.addCallback(surfaceCallback);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
    }

    /**
     * 初始化定时器
     */
    private void initTimer() {
        if (handler == null) {
            handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    //        		刷新图表
                    if (msg.what == 1) updateChart();
                    super.handleMessage(msg);
                }
            };
        }


        task = new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                if (handler != null) {

                    handler.sendMessage(message);
                }
            }
        };
        timer = new Timer();
        timer.schedule(task, 100, INTERVAL);
    }

    //	曲线
    @Override
    public void onDestroy() {
        //当结束程序时关掉Timer
        timer.cancel();
        super.onDestroy();
    }


    /**
     * 创建图表
     *
     * @param color
     * @param style
     * @param fill
     * @return
     */
    protected XYMultipleSeriesRenderer buildRenderer(int color, PointStyle style, boolean fill) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

        //设置图表中曲线本身的样式，包括颜色、点的大小以及线的粗细等
        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(color);
//		r.setPointStyle(null);
//		r.setFillPoints(fill);
        r.setLineWidth(10f);
        renderer.addSeriesRenderer(r);
        return renderer;
    }

    /**
     * 设置图标的样式
     *
     * @param renderer
     * @param xTitle：x标题
     * @param yTitle：y标题
     * @param xMin：x最小长度
     * @param xMax：x最大长度
     * @param yMin:y最小长度
     * @param yMax：y最大长度
     * @param axesColor：颜色
     * @param labelsColor：标签
     */
    protected void setChartSettings(XYMultipleSeriesRenderer renderer, String xTitle, String yTitle,
                                    double xMin, double xMax, double yMin, double yMax, int axesColor, int labelsColor) {
        //有关对图表的渲染可参看api文档
//        renderer.setChartTitle(title);
//        renderer.setXTitle(xTitle);
//        renderer.setYTitle(yTitle);
        renderer.setXAxisMin(xMin);
        renderer.setXAxisMax(xMax);
        renderer.setYAxisMin(yMin);
        renderer.setYAxisMax(yMax);
//        renderer.setAxesColor(axesColor);
//        renderer.setLabelsColor(labelsColor);
        renderer.setShowGrid(false);
//        renderer.setXLabels(20);
//        renderer.setYLabelsAlign(Align.RIGHT);
        renderer.setPointSize((float) 8);
        renderer.setShowLegend(false);
        renderer.setClickEnabled(false);
        renderer.setShowAxes(false);
        renderer.setShowLabels(false);
        renderer.setMargins(new int[]{0, 0, 0, 0});
//        renderer.setPanEnabled(false, false);
//        renderer.setZoomEnabled(false, false);
        Log.i("clickable", renderer.isClickEnabled() + "");
        Log.i("panable", renderer.isPanEnabled() + "");
        Log.i("zoomable", renderer.isZoomEnabled() + "");

    }


    /**
     * 更新图表信息
     */
    private void updateChart() {


        //设置好下一个需要增加的节点
//        addX = 0;
//        addY = (int)(Math.random() * 90);
        //移除数据集中旧的点集
        mDataset.removeSeries(series);
        //判断当前点集中到底有多少点，因为屏幕总共只能容纳100个，所以当点数超过100时，长度永远是100
        int length = series.getItemCount();

        // 数据有效则添加，无效则清空数据集
        // 另外R通道平均值在5.4以上
        if (brightvalue < AXISYMAX && brightvalue > AXISYMIN && redvalue > 5.4) {
            count++;
            addX = AXISXMAX;
            addY = brightvalue;
            // 把数据添加到mDatas中，用来保存到文件中
            if (count >= 10) {
                // 测试数值显示和心跳动画用
//
                if (!mIsHeartAnimPlaying) {
                    m_HeartDrawable.startTwinkle();
                    mIsHeartAnimPlaying = true;
                }
                m_ProgressWheel.setProgress(count-10);

                // 测试实时心率计算
                mRealTimeDatas.add(brightvalue);
                int n = mRealTimeDatas.size();
                if (n > 30 && n % 10 == 0) {
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            calRealTimeHeartRate();
                        }
                    });
                    thread.start();
                }
                if (count==10)
                {
                    mIsHeartRateCanSet=true;
                    mRealTimeHeartRate=mHeartRate;
                }
                if (mIsHeartRateCanSet)
                {
                    m_TvLabel.setText(mRealTimeHeartRate + "");
                    Log.i("m_TvLabel","设了一次实时心率值");

                }

                mDatas.add(brightvalue);
                mRedDatas.add(redvalue);
                mGreenDatas.add(greenvalue);
                mBlueDatas.add(bluevalue);


                // 如果有效数据采集到300个，就跳转到保存数据的界面
//                if (count >= 100) {
                if (count >= 2 * AXISXMAX) {


                    UserDataBean userDataBean = new UserDataBean();
                    userDataBean.setDatas(mDatas);
                    handler.removeCallbacksAndMessages(null);
                    handler= null;
                    timer.cancel();
                    mIsHeartRateCanSet=false;

                    for (int i = 0; i < 10; i++) {
                        int index = mRedDatas.size() - 1;
                        mRedDatas.remove(index);
                        mGreenDatas.remove(index);
                        mBlueDatas.remove(index);
                    }

                    for (int i = 0; i < 40; i++) {
                        mRedDatas.remove(0);
                        mGreenDatas.remove(0);
                        mBlueDatas.remove(0);
                    }

                    userDataBean.setRed_datas(mRedDatas);
                    userDataBean.setGreen_datas(mGreenDatas);
                    userDataBean.setBlue_datas(mBlueDatas);

                    // 以下是计算心率和RR间隔的代码
                    // 将源数据List转成数组
                    List<Double> data_origin_list = userDataBean.getDatas();
                    double[] data_origin = new double[data_origin_list.size()];
                    for (int i = 0; i < data_origin_list.size(); i++) {
                        data_origin[i] = data_origin_list.get(i);
                    }
                    double[] data_smoothed = new double[data_origin_list.size()];

                    // SG算法的参数矩阵
                    double[] coeffs = SGFilter.computeSGCoefficients(5, 5, 5);
                    // SG算法去噪处理
                    data_smoothed = new SGFilter(5, 5).smooth(data_origin, coeffs);
                    // SG算法去噪处理第二遍
                    data_smoothed = new SGFilter(5, 5).smooth(data_smoothed, coeffs);
                    data_smoothed = new SGFilter(5, 5).smooth(data_smoothed, coeffs);
                    data_smoothed = new SGFilter(5, 5).smooth(data_smoothed, coeffs);

                    // data_smoothed_list为SG算法处理前的值列表（去头去尾）
                    List<Double> data_origin_list2 = new ArrayList<Double>();
                    // data_smoothed_list为SG算法处理后的值列表（去头去尾）
                    List<Double> data_smoothed_list = new ArrayList<Double>();
                    for (int i = 40; i < data_smoothed.length - 10; i++) {
                        data_origin_list2.add(data_origin[i]);
                        data_smoothed_list.add(data_smoothed[i]);
                    }
                    Log.i("data_smoothed.length", data_smoothed.length + "");
                    System.out.println(data_origin_list2);
//                    System.out.println(data_smoothed_list);
                    // peaksList为峰的横坐标列表
                    List<Integer> peaksList = CalculateHeartRate.findPeaks(data_smoothed_list);
                    mHeartRate = CalculateHeartRate.calHeartRate(peaksList, INTERVAL);
                    userDataBean.setHeartrate(mHeartRate);
                    Log.i("heart rate", mHeartRate + "");
                    m_TvLabel.setText(mHeartRate+"");
                    Toast.makeText(MainActivity.this, "心率为" + mHeartRate, Toast.LENGTH_LONG).show();

                    userDataBean.setRr_datas(CalculateHeartRate.calRRInteval(peaksList));
                    System.out.println(userDataBean.getRr_datas());
                    userDataBean.setNew_datas(data_smoothed_list);
                    System.out.println(userDataBean.getNew_datas());

                    userDataBean.setDatas(data_origin_list2);
                    Intent intent = new Intent(MainActivity.this, SaveDateActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("userdatabean", userDataBean);
                    intent.putExtras(bundle);
                    startActivity(intent);
                } else {
                    //将旧的点集中x和y的数值取出来放入backup中，并且将x的值减1，造成曲线向左平移的效果

                    if (length > AXISXMAX) {
                        for (int i = 1; i < length; i++) {
                            xv[i - 1] = (int) series.getX(i) - 1;
                            yv[i - 1] = series.getY(i);
                        }
                        length = AXISXMAX;
                    } else {
                        for (int i = 0; i < length; i++) {
                            xv[i] = (int) series.getX(i) - 1;
                            yv[i] = series.getY(i);
//                    Log.i("xv yv", xv[i] + " " + yv[i] + " " + i + " " + length);
                        }
                    }

                    // 清空series中的坐标点
                    series.clear();
                    //将新产生的点首先加入到点集中，然后在循环体中将坐标变换后的一系列点都重新加入到点集中
                    for (int k = 0; k < length; k++) {
                        series.add(xv[k], yv[k]);
//            Log.i("series item", series.getX(k) + " " + series.getY(k) + " " + length + " " + k);
                    }
                    series.add(addX, addY);
//            Log.i("new add point", addY + "");

                    // 自动调整Y轴阈值算法
                    autoYthreshold();

                    //这里可以试验一下把顺序颠倒过来是什么效果，即先运行循环体，再添加新产生的点
                    //在数据集中添加新的点集

                    mDataset.addSeries(series);
                    //视图更新，没有这一步，曲线不会呈现动态
                    //如果在非UI主线程中，需要调用postInvalidate()，具体参考api
                    chart.invalidate();

                }

            }
        } else {
            if (mIsHeartAnimPlaying) {
                m_HeartDrawable.stopTwinkle();
                mIsHeartAnimPlaying = false;
            }
            count = 0;
            mDatas.clear();
            mRealTimeDatas.clear();
            mGreenDatas.clear();
            mRedDatas.clear();
            mBlueDatas.clear();
            m_ProgressWheel.setProgress(0);
            series.clear();
        }



    }

    /**
     * 自动调整Y轴阈值的算法
     */
    private void autoYthreshold() {
        if (series.getMaxY() > 0 && series.getMinY() < 100) {
            YMAX = series.getMaxY();
            YMIN = series.getMinY();
        }
//            Log.i("max min ",YMAX+" "+YMIN);

        if (currentYtop - YMAX < 0.035) {
            currentYtop += 0.01;
            renderer.setYAxisMax(currentYtop);
        } else if (currentYtop - YMAX > 0.045) {
            currentYtop -= 0.01;
            renderer.setYAxisMax(currentYtop);
        }

        if (YMIN - currentYbottom > 0.045) {
            currentYbottom += 0.01;
            renderer.setYAxisMin(currentYbottom);
        } else if (YMIN - currentYbottom < 0.035) {
            currentYbottom -= 0.01;
            renderer.setYAxisMin(currentYbottom);
        }
        Log.i("Ytop Ybottom", currentYtop + " " + currentYbottom);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onResume() {
        super.onResume();
        wakeLock.acquire();
        camera = Camera.open();
        startTime = System.currentTimeMillis();
//        mIsHeartRateCanSet=true;
    }

    @Override
    public void onPause() {
        super.onPause();
        wakeLock.release();
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
    }


    /**
     * 相机预览方法
     * 这个方法中实现动态更新界面UI的功能，
     * 通过获取手机摄像头的参数来实时动态计算平均像素值、脉冲数，从而实时动态计算心率值。
     */
    private static PreviewCallback previewCallback = new PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null)
                throw new NullPointerException();
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null)
                throw new NullPointerException();
            if (!processing.compareAndSet(false, true))
                return;
            int width = size.width;
            int height = size.height;
            //图像处理
            double[] imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), height, width);
            brightvalue = imgAvg[0];
            redvalue = imgAvg[1];
            greenvalue = imgAvg[2];
            bluevalue = imgAvg[3];


            processing.set(false);
        }
    };


    /**
     * 预览回调接口
     */
    private static SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        //创建时调用
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {

                camera = Camera.open();
                try {
                    camera.setPreviewDisplay(previewHolder);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // 设预览回调
                camera.setPreviewCallback(previewCallback);
//
//                camera.setPreviewDisplay(previewHolder);

                //启动摄像头预览
                camera.startPreview();
            } catch (Throwable t) {
                Log.e("PreviewDemo", "Exception in setPreviewDisplay()", t);
            }
        }

        //当预览改变的时候回调此方法
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            // 旋转90度
            camera.setDisplayOrientation(90);
            parameters.setRotation(90);
            camera.setParameters(parameters);
//            Camera.Size size = getSmallestPreviewSize(width, height, parameters);
////            size.width = 480;
////            size.height = 360;
//            if (size != null) {
//                parameters.setPreviewSize(size.width, size.height);
//                //				Log.d(TAG, "Using width=" + size.width + " height="	+ size.height);
//            }

        }

        //销毁的时候调用
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    /**
     * 获取相机最小的预览尺寸
     *
     * @param width
     * @param height
     * @param parameters
     * @return
     */
    private static Camera.Size getSmallestPreviewSize(int width, int height,
                                                      Camera.Parameters parameters) {
        Camera.Size result = null;
        List<Camera.Size> cameralist = parameters.getSupportedPreviewSizes();

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
//            Log.i("camera size", size.width + ":" + size.height);
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;
                    if (newArea < resultArea)
                        result = size;
                }
            }
        }

        return result;
    }


}