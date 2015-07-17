package com.ssrslabs.mobile.video;

import org.robovm.apple.assetslibrary.ALAssetsLibrary;
import org.robovm.apple.avfoundation.AVAudioSession;
import org.robovm.apple.avfoundation.AVAudioSessionCategory;
import org.robovm.apple.avfoundation.AVCaptureConnection;
import org.robovm.apple.avfoundation.AVCaptureDevice;
import org.robovm.apple.avfoundation.AVCaptureDeviceInput;
import org.robovm.apple.avfoundation.AVCaptureFileOutput;
import org.robovm.apple.avfoundation.AVCaptureFileOutputRecordingDelegateAdapter;
import org.robovm.apple.avfoundation.AVCaptureMovieFileOutput;
import org.robovm.apple.avfoundation.AVCaptureSession;
import org.robovm.apple.avfoundation.AVCaptureStillImageOutput;
import org.robovm.apple.avfoundation.AVCaptureVideoPreviewLayer;
import org.robovm.apple.avfoundation.AVLayerVideoGravity;
import org.robovm.apple.avfoundation.AVMediaType;
import org.robovm.apple.avfoundation.AVVideoSettings;
import org.robovm.apple.coreanimation.CALayer;
import org.robovm.apple.coregraphics.CGRect;
import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSError;
import org.robovm.apple.foundation.NSErrorException;
import org.robovm.apple.foundation.NSRunLoop;
import org.robovm.apple.foundation.NSRunLoopMode;
import org.robovm.apple.foundation.NSString;
import org.robovm.apple.foundation.NSTimer;
import org.robovm.apple.foundation.NSURL;
import org.robovm.apple.uikit.UIButton;
import org.robovm.apple.uikit.UIControlState;
import org.robovm.apple.uikit.UIImage;
import org.robovm.apple.uikit.UILabel;
import org.robovm.apple.uikit.UIView;
import org.robovm.apple.uikit.UIViewController;
import org.robovm.objc.annotation.CustomClass;
import org.robovm.objc.annotation.IBAction;
import org.robovm.objc.annotation.IBOutlet;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;

import java.io.File;
import java.io.IOException;

@CustomClass("MyViewController")
public class MyViewController extends UIViewController {

    AVCaptureSession captureSession;
    AVCaptureDeviceInput videoInput;
    AVCaptureDeviceInput audioInput;
    AVCaptureMovieFileOutput movieFileOutput;
    AVCaptureStillImageOutput stillImageOutput;

    UIView previewView;
    UIButton recordButton;
    UILabel timeLabel;

    NSTimer timer;
    int timeSec;
    int timeMin;

    @IBOutlet
    public void setPreviewView(UIView previewView) {
        this.previewView = previewView;
    }

    @IBOutlet
    public void setRecordButton(UIButton button) {
        this.recordButton = button;
    }

    @IBOutlet
    public void setTimeLabel(UILabel label) {
        this.timeLabel = label;
    }

    @IBAction
    public void toggleRecording() {
        if (! movieFileOutput.isRecording()) {
            recordButton.setImage(UIImage.create("player_stop"), UIControlState.Normal);
            startRecording();
        }
        else {
            recordButton.setImage(UIImage.create("player_record"), UIControlState.Normal);
            movieFileOutput.stopRecording();
            if (timer.isValid()) {
                timer.invalidate();
            }
        }
    }

    @Override
    public void viewDidLoad() {
        super.viewDidLoad();
        configureCaptureSession();
        configureVideoPreviewLayer();
    }

    /**
     *
     */
    private void configureCaptureSession() {
        try {
            AVAudioSession.getSharedInstance().setCategory(AVAudioSessionCategory.Record);
            AVAudioSession.getSharedInstance().setActive(true);

            captureSession = new AVCaptureSession();

            AVCaptureDevice videoDevice = AVCaptureDevice.getDefaultDeviceForMediaType(AVMediaType.Video);
            AVCaptureDevice audioDevice = AVCaptureDevice.getDefaultDeviceForMediaType(AVMediaType.Audio);

            videoInput = AVCaptureDeviceInput.create(videoDevice);
            audioInput = AVCaptureDeviceInput.create(audioDevice);

            stillImageOutput = new AVCaptureStillImageOutput();
            AVVideoSettings outputSettings = new AVVideoSettings();
            outputSettings.set(AVVideoSettings.Keys.Codec(), new NSString("jpeg"));
            stillImageOutput.setVideoOutputSettings(outputSettings);

            movieFileOutput = new AVCaptureMovieFileOutput();

            captureSession.beginConfiguration();
            captureSession.addInput(videoInput);
            captureSession.addInput(audioInput);
            captureSession.addOutput(movieFileOutput);
            captureSession.addOutput(stillImageOutput);
            captureSession.commitConfiguration();

            captureSession.startRunning();
        }
        catch (NSErrorException e) {
            e.printStackTrace();
        }
    }

    /**
     */
    private void startRecording() {
        try {
            setupTimer();

            File tempFile = File.createTempFile("movie", ".mp4", new File(System.getenv("TMPDIR")));
            if (tempFile.exists()) {
                tempFile.delete();
            }
            NSURL outputFileUrl = new NSURL(tempFile.toURI());
            movieFileOutput.startRecording(outputFileUrl, new AVCaptureFileOutputRecordingDelegateAdapter() {
                @Override
                public void didStartRecording(AVCaptureFileOutput captureOutput, NSURL fileURL, NSArray<AVCaptureConnection> connections) {
                    System.err.println("didStartRecording");
                }

                @Override
                public void didFinishRecording(AVCaptureFileOutput captureOutput, NSURL outputFileURL, NSArray<AVCaptureConnection> connections, NSError error) {
                    System.err.println("didFinishRecording");
                    saveRecording(outputFileURL);
                }
            });
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupTimer() {
        timeMin = 0;
        timeSec = 0;

        final String timeNow = String.format("%02d:%02d", timeMin, timeSec);
        timeLabel.setText(timeNow);

        timer = NSTimer.createScheduled(1.0, new VoidBlock1<NSTimer>() {
            @Override
            public void invoke(NSTimer nsTimer) {
                timeSec++;
                if (timeSec == 60) {
                    timeSec = 0;
                    timeMin++;
                }
                final String timeNow = String.format("%02d:%02d", timeMin, timeSec);
                timeLabel.setText(timeNow);
            }
        }, true);
        NSRunLoop.getCurrent().addTimer(NSRunLoopMode.Default, timer);
    }

    /**
     *
     * @param outputFileURL
     */
    private void saveRecording(NSURL outputFileURL) {
        final File file = new File(outputFileURL.getPath());
        System.err.println("saveRecording: " + file.getAbsolutePath());

        ALAssetsLibrary alAssetsLibrary = new ALAssetsLibrary();
        alAssetsLibrary.writeVideoToSavedPhotosAlbum(outputFileURL, new VoidBlock2<NSURL, NSError>() {
            @Override
            public void invoke(NSURL nsurl, NSError nsError) {
                if (nsError != null) {
                    System.err.println("saveRecording: nsError: " + nsError);
                }
                if (file.exists()) {
                    file.delete();
                }
            }
        });
    }

    /**
     *
     */
    private void configureVideoPreviewLayer() {
        AVCaptureVideoPreviewLayer previewLayer = new AVCaptureVideoPreviewLayer(captureSession);
        previewLayer.setVideoGravity(AVLayerVideoGravity.ResizeAspectFill);
        CALayer rootLayer = getView().getLayer();
        rootLayer.setMasksToBounds(true);
        CGRect frame = previewView.getFrame();
        previewLayer.setFrame(frame);
        rootLayer.insertSublayerAt(previewLayer, 0);
    }
}
