package cn.com.bluevideoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import cn.com.bluevideoplayer.fragments.LoaderCursor;
import cn.com.bluevideoplayer.util.Logger;
import cn.com.bluevideoplayer.util.SpManager;
import cn.com.bluevideoplayer.util.Util;
import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.MediaPlayer.OnBufferingUpdateListener;
import io.vov.vitamio.MediaPlayer.OnCompletionListener;
import io.vov.vitamio.MediaPlayer.OnPreparedListener;
import io.vov.vitamio.MediaPlayer.OnVideoSizeChangedListener;
import io.vov.vitamio.widget.VideoView;

public class PlayerView extends RelativeLayout implements OnClickListener,
        OnSeekBarChangeListener, Callback, OnBufferingUpdateListener,
        OnCompletionListener, OnPreparedListener, OnVideoSizeChangedListener {
    private static final int SEEK_BAR_UPDATE_DELAY = 200;
    private static final int CONTROLER_GONE_DELAY = 3000;
    private static final String TAG = "NetPlayActivity";
    protected boolean isAnimationRunning;
    private Button mLastBtn, mFastBackBtn, mPlayBtn, mFastForwardBtn, mNextBtn;
    private View mControler, mTitleBar, mTopView;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private TextView mCurrentTime, mTotalTime, mTitle, mTime,
            mTargetPositionView;
    private SeekBar mProgressBar;
    private String mPath;
    private MediaPlayer mMediaPlayer;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceViewWidth;
    private int mSurfaceViewHeight;
    private Bundle extras;
    private Context mContext = null;
    private boolean mIsVideoSizeKnown = false;
    private boolean mIsVideoReadyToBePlayed = false;
    private Cursor mCursor;
    private View mVolumeBrightnessLayout;
    private ImageView mOperationBg;
    private ImageButton mLockButton;
    private ImageView mOperationPercent;
    private AudioManager mAudioManager;
    /**
     * 最大声音
     */
    private int mMaxVolume;
    /**
     * 当前声音
     */
    private int mVolume = -1;
    /**
     * 当前亮度
     */
    private float mBrightness = -1f;
    private GestureDetector mGestureDetector;
    /**
     * 当前缩放模式
     */
    private int mLayout = VideoView.VIDEO_LAYOUT_ZOOM;
    private long mTargetPosition;
    private boolean mShouldAutoStart = true;
    private boolean mIsLoacked = false;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg1) {
            switch (msg1.what) {
                case 0:
                    if (mMediaPlayer != null) {
                        long position = mMediaPlayer.getCurrentPosition();
                        mCurrentTime.setText(Util.getDuration(position));
                        mProgressBar.setProgress((int) position);
                        mHandler.sendEmptyMessageDelayed(0, SEEK_BAR_UPDATE_DELAY);
                    }
                    break;
                case 1:
                    showControler(false);
                    break;
                case 2:
                    mVolumeBrightnessLayout.setVisibility(View.GONE);
                    break;
                case 3:
                    mTime.setText(Util.getCurrentTime(System.currentTimeMillis()));
                    mHandler.sendEmptyMessageDelayed(3, 1000);
                    break;
                case 4:
                    mMediaPlayer.seekTo(msg1.arg1);
                    mHandler.sendEmptyMessageDelayed(0, SEEK_BAR_UPDATE_DELAY);
                    break;
                case 5:
                    mLockButton.setVisibility(View.GONE);
                    break;
            }

            super.handleMessage(msg1);
        }

    };

    public PlayerView(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_player, this);
        mContext = context;

        mControler = findViewById(R.id.controler);
        mTitleBar = findViewById(R.id.title_bar);
        mTopView = findViewById(R.id.top_view);
        mSurfaceView = (SurfaceView) this.findViewById(R.id.surface);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setKeepScreenOn(true);
        mHolder.setFormat(PixelFormat.RGBA_8888);
        mLastBtn = (Button) findViewById(R.id.last_button);
        mFastBackBtn = (Button) findViewById(R.id.fast_back_button);
        mPlayBtn = (Button) findViewById(R.id.play_button);
        mFastForwardBtn = (Button) findViewById(R.id.fast_forward_button);
        mNextBtn = (Button) findViewById(R.id.next_button);
        mCurrentTime = (TextView) mControler.findViewById(R.id.current_time);
        mTotalTime = (TextView) mControler.findViewById(R.id.total_time);
        mTitle = (TextView) findViewById(R.id.title);
        mTime = (TextView) findViewById(R.id.time);
        mTargetPositionView = (TextView) findViewById(R.id.target_position);
        mProgressBar = (SeekBar) findViewById(R.id.progress);

        mVolumeBrightnessLayout = findViewById(R.id.operation_volume_brightness);
        mOperationBg = (ImageView) findViewById(R.id.operation_bg);
        mOperationPercent = (ImageView) findViewById(R.id.operation_percent);
        mLockButton = (ImageButton) findViewById(R.id.lock);

        mLockButton.setOnClickListener(this);
        mTopView.setOnClickListener(this);
        mProgressBar.setOnSeekBarChangeListener(this);
        mControler.setOnClickListener(this);
        mFastBackBtn.setOnClickListener(this);
        mLastBtn.setOnClickListener(this);
        mPlayBtn.setOnClickListener(this);
        mFastForwardBtn.setOnClickListener(this);
        mNextBtn.setOnClickListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void setControlerGone() {
        mTime.setText(Util.getCurrentTime(System.currentTimeMillis()));
        mHandler.removeMessages(1);
        mHandler.sendEmptyMessageDelayed(1, CONTROLER_GONE_DELAY);
        mHandler.sendEmptyMessageDelayed(3, 1000);
    }


    private void showLockButton() {
        mLockButton.setVisibility(View.VISIBLE);
        mHandler.removeMessages(5);
        mHandler.sendEmptyMessageDelayed(5, 2000);
    }

    private void startVideoPlayback() {
        Logger.v(TAG, "startVideoPlayback");
        if (mShouldAutoStart && mMediaPlayer != null
                && !mMediaPlayer.isPlaying()) {
            mProgressBar.setMax((int) mMediaPlayer.getDuration());
            mHolder.setFixedSize(mVideoWidth, mVideoHeight);
            int position = SpManager.getBreakPoint(mContext, mPath);

            if (position > 0 && position < mMediaPlayer.getDuration()) {
                mMediaPlayer.seekTo(position);
                mCurrentTime.setText(Util.getDuration(position));
                mProgressBar.setProgress(position);
            }
            start();
            mTotalTime.setText(Util.getDuration(mMediaPlayer.getDuration()));
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(mContext);
            mIsLoacked = prefs.getBoolean("lock", false);
            if (mIsLoacked) {
                mLockButton.setBackgroundDrawable(getResources().getDrawable(
                        R.drawable.lock_1));
            } else {
                mLockButton.setBackgroundDrawable(getResources().getDrawable(
                        R.drawable.lock_3));
            }
        }
    }

    private void playVideo(String path) {
        Logger.d(TAG, "play:" + path);
        if (!TextUtils.isEmpty(mPath)) {
            try {
                if (mMediaPlayer == null) {
                    mMediaPlayer = new MediaPlayer(mContext);
                    mMediaPlayer.setDisplay(mHolder);
                    mMediaPlayer.setOnBufferingUpdateListener(this);
                    mMediaPlayer.setOnCompletionListener(this);
                    mMediaPlayer.setOnPreparedListener(this);
                    mMediaPlayer.setOnVideoSizeChangedListener(this);
                    // mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                } else {
                    mMediaPlayer.reset();
                }
                mMediaPlayer.setDataSource(mPath);
                mMediaPlayer.prepare();
                mTitle.setText(Util.getTitle(path));
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }

    void showControler(boolean show) {
        if (show) {
            mTopView.setVisibility(View.VISIBLE);
            mTitleBar.setVisibility(View.VISIBLE);
            mControler.setVisibility(View.VISIBLE);
            startInAnimation();
        } else {
            mHandler.removeMessages(3);
            startOutAnimation();

        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        Logger.d(TAG, "onclick:" + id);
        switch (id) {
            case R.id.top_view:
                if (mControler.getVisibility() == View.VISIBLE) {
                    showControler(false);
                } else {
                    showControler(true);
                }
                // mTitle.setVisibility(View.VISIBLE);
                break;
            case R.id.controler:
                showControler(false);
                // mTitle.setVisibility(View.GONE);
                break;
            case R.id.last_button:
                if (mMediaPlayer != null) {
                    int breakPoint = (int) mMediaPlayer.getCurrentPosition();
                    SpManager.setBreakPoint(mPath, breakPoint);
                }
                playPrevious();
                break;
            case R.id.fast_back_button:
                long position = mMediaPlayer.getCurrentPosition();
                long target = position - 10000;
                if (target > 0) {
                    mMediaPlayer.seekTo(target);
                    mProgressBar.setProgress((int) position);
                    mHandler.removeMessages(0);
                    mHandler.sendEmptyMessageDelayed(0, SEEK_BAR_UPDATE_DELAY);
                }
                break;
            case R.id.play_button:
                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        mShouldAutoStart = false;
                        pause();
                    } else {
                        start();
                    }
                }
                break;
            case R.id.fast_forward_button:
                if (mMediaPlayer != null) {
                    position = mMediaPlayer.getCurrentPosition();
                    target = position + 10000;
                    if (target < mMediaPlayer.getDuration()) {
                        mMediaPlayer.seekTo(target);
                        mProgressBar.setProgress((int) position);
                        mHandler.removeMessages(0);
                        mHandler.sendEmptyMessageDelayed(0, SEEK_BAR_UPDATE_DELAY);
                    }
                }
                break;
            case R.id.next_button:
                if (mMediaPlayer != null) {
                    int breakPoint = (int) mMediaPlayer.getCurrentPosition();
                    SpManager.setBreakPoint(mPath, breakPoint);
                }
                playNext();
                break;
            case R.id.lock:
                if (mIsLoacked) {
                    mLockButton.setBackgroundDrawable(getResources().getDrawable(
                            R.drawable.lock_3));
                    mIsLoacked = false;
                } else {
                    mLockButton.setBackgroundDrawable(getResources().getDrawable(
                            R.drawable.lock_1));
                    mIsLoacked = true;
                }
                Logger.d(TAG, "mIsLoacked:" + mIsLoacked);
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(mContext);
                prefs.edit().putBoolean("lock", mIsLoacked).commit();
                showLockButton();
                break;
        }
        setControlerGone();
    }

    private void start() {
        Logger.d(TAG, "start");
        mShouldAutoStart = true;
        mHandler.removeMessages(0);
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
            mPlayBtn.setBackgroundResource(R.drawable.pause);
            mHandler.sendEmptyMessageDelayed(0, SEEK_BAR_UPDATE_DELAY);
        }
        setControlerGone();
    }

    private void pause() {
        Logger.d(TAG, "pause");
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            mPlayBtn.setBackgroundResource(R.drawable.play);
            mHandler.removeMessages(0);
            SpManager.saveLastPlay(mContext, mPath);
            SpManager.setBreakPoint(mPath,
                    (int) mMediaPlayer.getCurrentPosition());
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {
        if (fromUser && mMediaPlayer != null) {
            mHandler.removeMessages(0);
            Message msg = mHandler.obtainMessage();
            msg.what = 4;
            msg.arg1 = progress;
            mHandler.sendMessageDelayed(msg, SEEK_BAR_UPDATE_DELAY);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mHandler.removeMessages(1);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mHandler.sendEmptyMessageDelayed(1, CONTROLER_GONE_DELAY);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        playVideo(mPath);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if (width == 0 || height == 0) {
            Log.e(TAG, "invalid video width(" + width + ") or height(" + height
                    + ")");
            return;
        }
        Logger.d(TAG, "onVideoSizeChanged width:" + width + " height:" + height);
        mIsVideoSizeKnown = true;
        mVideoHeight = height;
        mVideoWidth = width;
        int wid = mMediaPlayer.getVideoWidth();
        int hig = mMediaPlayer.getVideoHeight();
        // 根据视频的属性调整其显示的模式

        DisplayMetrics dm = new DisplayMetrics();
        mSurfaceViewWidth = dm.widthPixels;
        mSurfaceViewHeight = dm.heightPixels;
        Logger.d(TAG, "mSurfaceViewWidth:" + mSurfaceViewWidth + " mSurfaceViewHeight:" + mSurfaceViewHeight);
        if (width > height) {
            // 竖屏录制的视频，调节其上下的空余
            int w = mSurfaceViewHeight * width / height;
            int margin = (mSurfaceViewWidth - w) / 2;
            Logger.d(TAG, "margin:" + margin);
            if (margin > 0) {
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
                lp.setMargins(margin, 0, margin, 0);
                mSurfaceView.setLayoutParams(lp);
            }
        } else {
            // 横屏录制的视频，调节其左右的空余
            int h = mSurfaceViewWidth * height / width;
            int margin = (mSurfaceViewHeight - h) / 2;
            Logger.d(TAG, "margin:" + margin);
            if (margin > 0) {
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
                lp.setMargins(0, margin, 0, margin);
                mSurfaceView.setLayoutParams(lp);
            }
        }
        if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
            mShouldAutoStart = true;
            startVideoPlayback();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mIsVideoReadyToBePlayed = true;
        if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
            startVideoPlayback();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mHandler.removeMessages(0);
        SpManager.setBreakPoint(mPath, (int) mMediaPlayer.getDuration());

        playNext();
    }

    private void playNext() {
        requeryCursor();
        if (LoaderCursor.mCursor != null && !LoaderCursor.mCursor.isClosed()
                && LoaderCursor.mCursor.moveToFirst()) {
            do {
                String path = LoaderCursor.mCursor
                        .getString(LoaderCursor.mCursor
                                .getColumnIndex(MediaStore.Video.Media.DATA));
                if (path.equals(mPath)) {
                    if (LoaderCursor.mCursor.moveToNext()) {
                        mPath = LoaderCursor.mCursor
                                .getString(LoaderCursor.mCursor
                                        .getColumnIndex(MediaStore.Video.Media.DATA));
                        playVideo(mPath);
                        break;
                    } else if (LoaderCursor.mCursor.moveToFirst()) {
                        mPath = LoaderCursor.mCursor
                                .getString(LoaderCursor.mCursor
                                        .getColumnIndex(MediaStore.Video.Media.DATA));
                        playVideo(mPath);
                        break;
                    } else {
                    }
                }
            } while (LoaderCursor.mCursor.moveToNext());
        } else {
            playVideo(mPath);
        }
    }

    private void requeryCursor() {
        if (LoaderCursor.mCursor == null || LoaderCursor.mCursor.isClosed()) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(mContext);
            String sort_type = prefs.getString("sort_type", "0");
            String sort_order = prefs.getString("sort_order", "0");
            LoaderCursor.mCursor = mContext.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Util.PROJECTIONS,
                    null,
                    null,
                    Util.PROJECTIONS[Integer.valueOf(sort_type)]
                            + (sort_order.equals("0") ? " asc" : " desc"));
        }
    }

    private void playPrevious() {
        requeryCursor();
        if (LoaderCursor.mCursor != null && !LoaderCursor.mCursor.isClosed()
                && LoaderCursor.mCursor.moveToFirst()) {
            do {
                String path = LoaderCursor.mCursor
                        .getString(LoaderCursor.mCursor
                                .getColumnIndex(MediaStore.Video.Media.DATA));
                if (path.equals(mPath)) {
                    if (LoaderCursor.mCursor.moveToPrevious()) {
                        mPath = LoaderCursor.mCursor
                                .getString(LoaderCursor.mCursor
                                        .getColumnIndex(MediaStore.Video.Media.DATA));
                        playVideo(mPath);
                        break;
                    } else {
                        break;
                    }
                }
            } while (LoaderCursor.mCursor.moveToNext());
        } else {
            playVideo(mPath);
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Logger.d(TAG, "on touch");
        if (mIsLoacked) {
            // 锁屏了
            showLockButton();
            return true;
        }
        setControlerGone();
        if (mGestureDetector.onTouchEvent(event))
            return true;

        // 处理手势结束
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_UP:
                endGesture();
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 手势结束
     */
    private void endGesture() {
        mVolume = -1;
        mBrightness = -1f;
        if (mMediaPlayer != null && mTargetPosition > 0) {
            mMediaPlayer.seekTo(mTargetPosition);
        }
        Logger.d(TAG, "seek to:" + mTargetPosition);
        mTargetPositionView.setVisibility(View.GONE);
        // 隐藏
        mHandler.removeMessages(2);
        mHandler.sendEmptyMessageDelayed(2, 500);

    }


    public void startInAnimation() {
        mTitleBar.clearAnimation();
        Animation animation_top_in = AnimationUtils.loadAnimation(mContext,
                R.anim.slide_top_in);
        animation_top_in.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation arg0) {
                isAnimationRunning = true;

            }

            @Override
            public void onAnimationRepeat(Animation arg0) {

            }

            @Override
            public void onAnimationEnd(Animation arg0) {
                isAnimationRunning = false;

            }
        });

        mTitleBar.setAnimation(animation_top_in);
        mTitleBar.startAnimation(animation_top_in);

        mControler.clearAnimation();
        Animation animation_buttom_in = AnimationUtils.loadAnimation(mContext,
                R.anim.slide_buttom_in);
        mControler.setAnimation(animation_buttom_in);
        mControler.startAnimation(animation_buttom_in);

    }

    public void startOutAnimation() {
        mTitleBar.clearAnimation();
        Animation animation_top_out = AnimationUtils.loadAnimation(mContext,
                R.anim.slide_top_out);
        animation_top_out.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation arg0) {
                isAnimationRunning = true;

            }

            @Override
            public void onAnimationRepeat(Animation arg0) {

            }

            @Override
            public void onAnimationEnd(Animation arg0) {
                isAnimationRunning = false;
                mTitleBar.setVisibility(View.GONE);
                mControler.setVisibility(View.GONE);
                mTopView.setVisibility(View.GONE);
            }

        });
        mTitleBar.setAnimation(animation_top_out);
        mTitleBar.startAnimation(animation_top_out);

        mControler.clearAnimation();
        Animation animation_buttom_out = AnimationUtils.loadAnimation(mContext,
                R.anim.slide_bottom_out);
        mControler.setAnimation(animation_buttom_out);
        mControler.startAnimation(animation_buttom_out);

    }
}
