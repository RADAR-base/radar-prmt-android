/*
 Copyright (c) 2015 audEERING UG. All rights reserved.

 Date: 17.08.2015
 Author(s): Florian Eyben
 E-mail:  fe@audeering.com

 This is the interface between the Android app and the openSMILE binary.
 openSMILE is called via SMILExtractJNI()
 Messages from openSMILE are received by implementing the SmileJNI.Listener interface.
*/

package org.radarcns.opensmile;

import org.radarcns.util.IOUtil;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class SmileJNI {

    public interface ThreadListener {
        public void onFinishedRecording();
    }

    String conf;
    String recordingPath;
    ThreadListener listener = null;


    /**
     * load the JNI interface
     */
    static {
        System.loadLibrary("smile_jni");
    }

    /**
     * method to execute openSMILE binary from the Android app activity, see smile_jni.cpp.
     *
     * @param configfile
     * @param updateProfile
     * @param externalStoragePath
     * @return
     */
    public static native String SMILExtractJNI(String configFile, int updateProfile, String outputfile);

    public static native String SMILEndJNI();

    public void runOpenSMILE(String conf, String recordingPath, long second) {
        this.conf = conf;
        this.recordingPath = recordingPath;

        final SmileThread obj = new SmileThread();
        final Thread newThread = new Thread(obj);
        newThread.start();
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                stopOpenSMILE();
            }
        }, second * 1000);
    }

    public void addListener(ThreadListener listener) {
        this.listener = listener;
    }

    public void stopOpenSMILE() {
        SmileJNI.SMILEndJNI();
    }

    class SmileThread implements Runnable {
        //String  conf = getCacheDir()+"/liveinput_android.conf";
        private volatile boolean paused = false;
        private final Object signal = new Object();

        @Override
        public void run() {
            //String recordingPath = getExternalFilesDir("") + "/test50.bin";
            SmileJNI.SMILExtractJNI(conf, 1, recordingPath);
            try {
                byte[] b = IOUtil.readFile(recordingPath);
                /*final String b64 = Base64.encodeToString(b,Base64.DEFAULT);
                String dataPathText = getExternalFilesDir("") + "/test50.txt";
                try {
                    File f = new File(dataPathText);
                    FileOutputStream fos = new FileOutputStream(f);
                    PrintWriter pw = new PrintWriter(fos);
                    pw.print(b64);
                    pw.flush();
                    pw.close();
                    fos.close();
                    ///OutputStreamWriter outputStreamWriter = new OutputStreamWriter(getApplicationContext().openFileOutput("test50.txt", Context.MODE_PRIVATE));
                    //outputStreamWriter.write(b64);
                    //outputStreamWriter.close();
                }
                catch (IOException e) {
                    Log.e("Exception", "File write failed: " + e.toString());
                }*/
                listener.onFinishedRecording();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //Log.d("SmileLog1:","After SmileExtract:"+conf+" datapath:"+dataPath);

        }
    }

    /**
     * process the messages from openSMILE (redirect to app activity etc.)
     */
    public interface Listener {
        void onSmileMessageReceived(String text);
    }

    private static Listener listener_;

    public static void registerListener(Listener listener) {
        listener_ = listener;
    }

    /**
     * this is the first method called by openSMILE binary. it redirects the call to the Android
     * app activity.
     *
     * @param text JSON encoded string
     */
    static void receiveText(String text) {
        if (listener_ != null)
            listener_.onSmileMessageReceived(text);
    }
}
