package com.example.shu.renderscriptvideo;

import android.content.res.AssetFileDescriptor;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity implements
        SurfaceHolder.Callback {

    MediaExtractor extractor;
    int mVideoWidth, mVideoHeight, mBufferWidth, mBufferHeight;


    private static final String TAG = MainActivity.TAG;

    private Surface mOutputSurface;

    private final Object mStopper = new Object();   // used to signal stop


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        videoView = new SurfaceView(this);
        setContentView(videoView);

        videoView.getHolder().addCallback(this);
        videoView.getHolder().setFixedSize(1280,740);

        videoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dosomething();
            }
        });

    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mOutputSurface == null) {
            mOutputSurface = holder.getSurface();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

//
//    private void adjustAspectRatio(int videoWidth, int videoHeight) {
//        int viewWidth = videoView.getWidth();
//        int viewHeight = videoView.getHeight();
//        double aspectRatio = (double) videoHeight / videoWidth;
//
//        int newWidth, newHeight;
//        if (viewHeight > (int) (viewWidth * aspectRatio)) {
//            // limited by narrow width; restrict height
//            newWidth = viewWidth;
//            newHeight = (int) (viewWidth * aspectRatio);
//        } else {
//            // limited by short height; restrict width
//            newWidth = (int) (viewHeight / aspectRatio);
//            newHeight = viewHeight;
//        }
//        int xoff = (viewWidth - newWidth) / 2;
//        int yoff = (viewHeight - newHeight) / 2;
//        Log.v(TAG, "video=" + videoWidth + "x" + videoHeight +
//                " view=" + viewWidth + "x" + viewHeight +
//                " newView=" + newWidth + "x" + newHeight +
//                " off=" + xoff + "," + yoff);
//
//        Matrix txform = new Matrix();
//        videoView.getTransform(txform);
//        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
//        //txform.postRotate(10);          // just for fun
//        txform.postTranslate(xoff, yoff);
//        videoView.setTransform(txform);
//    }

    private void dosomething() {
        //https://storage.googleapis.com/osl-workbench/bipbop.m4v
        //Uri url = Uri.parse("file:///android_asset/bipbop.m4v");


        try {
            final AssetFileDescriptor assetFileDescriptor = getAssets().openFd("bipbop.m4v");

            extractor = new MediaExtractor();
            extractor.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());
            int numTracks = extractor.getTrackCount();

            for (int i = 0; i < numTracks; i++) {
                extractor.unselectTrack(i);
            }

            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mimeType = format.getString(MediaFormat.KEY_MIME);

                if (mimeType.contains("video")) {
                    mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);


                    //mOutputSurface = new Surface(videoView.getSurfaceTexture());

                    mBufferWidth = mVideoWidth;
                    mBufferHeight = mVideoHeight;

                    initRenderScript();
                    MediaCodec decoder = null;
                    {
                        decoder = MediaCodec.createDecoderByType(mimeType);
                        decoder.configure(format, null, null, 0);
                        if (decoder != null) {
                            extractor.selectTrack(i);

                            decoder.start();

                            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
                            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
                            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                            boolean isEOS = false;
                            long startMs = System.currentTimeMillis();

                            while (!Thread.interrupted()) {
                                if (!isEOS) {
                                    int inIndex = decoder.dequeueInputBuffer(10000);
                                    if (inIndex >= 0) {
                                        ByteBuffer buffer = inputBuffers[inIndex];
                                        int sampleSize = extractor.readSampleData(buffer, 0);
                                        if (sampleSize < 0) {
                                            // We shouldn't stop the playback at this point, just pass the EOS
                                            // flag to decoder, we will get it again from the
                                            // dequeueOutputBuffer
                                            Log.d("DecodeActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                            isEOS = true;
                                        } else {
                                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                            extractor.advance();
                                        }
                                    }
                                }

                                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                                switch (outIndex) {
                                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                        Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                                        outputBuffers = decoder.getOutputBuffers();


                                        break;
                                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                        Log.d("DecodeActivity", "New format " + decoder.getOutputFormat());
                                        break;
                                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                                        Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                                        break;
                                    default:
                                        ByteBuffer buffer = outputBuffers[outIndex];
                                        Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + buffer);

//from
                                        if(info.size>0) {
                                            // allocate byte buffer to temporarily hold decoded frame, input to renderscript
                                            // 3/2 because the decoder outputs NV12 which has 12bits per pixel
                                            byte[] mLocalOutputBuffers = new byte[info.size];

                                            buffer.get(mLocalOutputBuffers);
                                            //outputBuffers[outIndex].rewind();

                                            mAllocationYUV.copyFrom(mLocalOutputBuffers);
                                            outputBuffers[outIndex].rewind();
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mScript.forEach_yuvToRgb_greyscale(mAllocationOUT);
                                                    mAllocationOUT.ioSend();
                                                }
                                            }).start();
                                        }

//to


                                        // We use a very simple clock to keep the video FPS, or the video
                                        // playback will be too fast
                                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                                            try {
                                                sleep(10);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                                break;
                                            }
                                        }
                                        decoder.releaseOutputBuffer(outIndex, true);
                                        break;
                                }

                                // All decoded frames have been rendered, we can stop playing now
                                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                                    break;
                                }
                            }

                            decoder.stop();
                            decoder.release();
                            extractor.release();


                            break;
                        }
                    }
                }

            }

        } catch (Exception e) {
            Log.e(TAG, "---->", e);
        } finally {
            if (extractor != null) {
                extractor.release();
            }
        }


    }

    ScriptC_yuv mScript;
    SurfaceView videoView;
    RenderScript mRS;
    Allocation mAllocationYUV, mAllocationOUT;

    private void initRenderScript() {
        mRS = RenderScript.create(this);

        mScript = new ScriptC_yuv(mRS);

        Element elemYUV = Element.createPixel(mRS, Element.DataType.UNSIGNED_8, Element.DataKind.PIXEL_YUV);

        Type.Builder TypeYUV = new Type.Builder(mRS, elemYUV);

        TypeYUV.setYuvFormat(ImageFormat.NV21);

        mAllocationYUV = Allocation.createTyped(mRS, TypeYUV.setX(mVideoWidth).setY(mVideoHeight).create(),
                Allocation.USAGE_SCRIPT);

        Element elemOUT = Element.createPixel(mRS, Element.DataType.UNSIGNED_8, Element.DataKind.PIXEL_RGBA);

        Type.Builder TypeOUT = new Type.Builder(mRS, elemOUT);

        mAllocationOUT = Allocation.createTyped(mRS,
                TypeOUT.setX(mVideoWidth).setY(mVideoHeight).create(),  // Allocation Type
                Allocation.MipmapControl.MIPMAP_NONE,                   // No MIPMAP
                Allocation.USAGE_SCRIPT |                                // will be used by a script
                        Allocation.USAGE_IO_OUTPUT);                    // will be used as a SurfaceTexture producer


        if (mOutputSurface != null) {
            // Create a new surface
            // set allocation surface
            mAllocationOUT.setSurface(mOutputSurface);
        }

        // Set the Input Allocation
        mScript.set_gIn(mAllocationYUV);
    }
}

