package uroz.cristina.slopeline;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.TextView;


import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class OpenCvRunnable implements Runnable {

    private final static String TAG = OpenCvRunnable.class.getSimpleName();
    private final String mImagePath;
    private int mBlurKernelSize;
    private int mCannyThresholdMin;
    private int mCannyThresholdMax;
    private boolean mUseOtsuThreshold;
    private int mContourType;
    private int mShapeIgnoreSize;
    private boolean mShapeIgnoreConcave;
    private boolean mdrawFilteredShapes;
    private Context context;
    private Bitmap bm=null;
    private int status=0;
    private int num_imatge=0;
    private String image_dir = "/SlopeLine/data/";
    private String countourT;

    public OpenCvRunnable(final String imagePath,
                          final int blurKernelSize,
                          final int cannyThresholdMin,
                          final int cannyThresholdMax,
                          final boolean useOtsuThreshold,
                          final int contouType,
                          final int shapeIgnoreSize,
                          final boolean shapeIgnoreConcave,
                          final boolean drawFilteredShapes,
                          Context context) {

        this.context=context;
        mImagePath = imagePath;
        mBlurKernelSize = blurKernelSize;
        mCannyThresholdMin = cannyThresholdMin;
        mCannyThresholdMax = cannyThresholdMax;
        mUseOtsuThreshold = useOtsuThreshold;

        switch (contouType) {
            case 0:
                mContourType = Imgproc.RETR_EXTERNAL;
                countourT="EXTERNAL";
                break;
            case 1:
                mContourType = Imgproc.RETR_LIST;
                countourT="LIST";
                break;
            case 2:
                mContourType = Imgproc.RETR_CCOMP;
                countourT="CCOMP";
                break;
            case 3:
                mContourType = Imgproc.RETR_TREE;
                countourT="TREE";
                break;
            case 4:
                mContourType = Imgproc.RETR_FLOODFILL;
                countourT="FLOODFILL";
                break;
            default:
                mContourType = Imgproc.RETR_EXTERNAL;
                countourT="EXTERNAL";
        }
        mShapeIgnoreSize = shapeIgnoreSize;
        mShapeIgnoreConcave = shapeIgnoreConcave;
        mdrawFilteredShapes = drawFilteredShapes;


            String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath() + image_dir;
            new File(fullPath ).mkdir();
            File file = new File(fullPath+ "Settings.txt");

               StringBuilder text = new StringBuilder();
                try {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;

                    while ((line = br.readLine()) != null) {
                        text.append(line);
                        text.append('\n');

                        num_imatge++;
                    }


                }
                catch (IOException e) {
                    //You'll need to add proper error handling here
                }
    }

    @Override
    public void run() {
        this.status=5;
        final Mat orignalImage = Imgcodecs.imread(mImagePath);
        Mat finalMat = new Mat(orignalImage.rows(), orignalImage.cols(), orignalImage.type());
        Mat tmpImage = new Mat();
        Mat detectedEdges = new Mat();
        final Mat hierarchy = new Mat();
        final List<MatOfPoint> contours = new ArrayList<>();
        Scalar red = new Scalar(255, 0, 0);
        Scalar blue = new Scalar(0, 0, 255);
        Scalar green = new Scalar(0, 255, 0);
        Scalar white = new Scalar(255, 255, 255);
        Scalar yellow = new Scalar(255, 222, 0);
        Scalar black = new Scalar(0, 0, 0);


        this.status=25;

        // convert to grey scale
        Imgproc.cvtColor(orignalImage, tmpImage, Imgproc.COLOR_RGBA2GRAY);

        // blur image & show
        Imgproc.blur(tmpImage, tmpImage, new Size(mBlurKernelSize, mBlurKernelSize));
        //BlurImageFragment.getInstance().showMatImage(tmpImage);
        saveToStorage(tmpImage,"2_tmpImage");

        // use threshold as set by preferences
        double maxThreshold = mCannyThresholdMax * 1.0;
        double minThreshold = mCannyThresholdMin * 1.0;
        // if ostu threshold is enabled, then use it instead
        if (mUseOtsuThreshold) {
            Mat tmp2 = new Mat();
            double otsuThreshold = Imgproc.threshold(tmpImage, tmp2, (double) 0, (double) 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            maxThreshold = otsuThreshold;
            minThreshold = otsuThreshold * 0.5;
        }
        // detect & show edges
        Imgproc.Canny(tmpImage, detectedEdges, minThreshold, maxThreshold);
        //CannyEdgesFragment.getInstance().showMatImage(detectedEdges);
        saveToStorage(detectedEdges,"3_detectedEdges");

        this.status = 50;

        // find contours
        Imgproc.findContours(detectedEdges, contours, hierarchy, mContourType, Imgproc.CHAIN_APPROX_NONE);

        // iterate over all identified contours and analyze them
        MatOfPoint2f contour2f;
        for (int i = 0; i < contours.size(); i++) {
            // do some approximations to the idenitfied curves
            contour2f = new MatOfPoint2f(contours.get(i).toArray());
            double approxDistance = 0.03 * Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approxCurve2f = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approxCurve2f, approxDistance, true);
            MatOfPoint approxCurve = new MatOfPoint();
            approxCurve2f.convertTo(approxCurve, CvType.CV_32S);

            // skip small areas
            if (Math.abs(Imgproc.contourArea(contour2f)) < mShapeIgnoreSize) {
                Log.d(TAG, "small shape...skipping");
                // show for debugging visualization
                if (mdrawFilteredShapes) {
                    Imgproc.drawContours(finalMat, contours, i, white, 3, 8, hierarchy, 0, new Point(0, 0));
                }
                continue;
            }

            // skip if concave
            if (mShapeIgnoreConcave && !Imgproc.isContourConvex(approxCurve)) {
                Log.d(TAG, "concave shape...skipping");
                // show for debugging visualization
                if (mdrawFilteredShapes) {
                    Imgproc.drawContours(finalMat, contours, i, green, 3, 8, hierarchy, 0, new Point(0, 0));
                    //MatToBitmap(finalMat);
                }
                continue;
            }

            // filter based on number of vertices
            int vertices = approxCurve.height();
            Log.d(TAG, "Vertices = " + vertices);
            if (vertices == 4) {
                Log.d(TAG, "Found rectangle");
                Imgproc.drawContours(finalMat, contours, i, red, 3, 8, hierarchy, 0, new Point(0, 0));
                // draw bounding rectangle around our selected contour
                Rect boundingRect = Imgproc.boundingRect(approxCurve);
                Imgproc.rectangle(finalMat, new Point(boundingRect.x - 5 , boundingRect.y - 5),
                        new Point(boundingRect.x + boundingRect.width + 5 , boundingRect.y + boundingRect.height + 5),
                        yellow, 3);
                //MatToBitmap(finalMat);
            } else {
                Imgproc.drawContours(finalMat, contours, i, blue, 3, 8, hierarchy, 0, new Point(0, 0));
                Log.d(TAG, "skipping vertices = " + vertices);
                //MatToBitmap(finalMat);
            }
            this.status = i*100/contours.size();
        }
        saveToStorage(finalMat,"4_finalMat");
        this.status = 100;
        MatToBitmap(finalMat);
    }

    private void MatToBitmap(Mat finalMat) {
        bm = Bitmap.createBitmap(finalMat.width(), finalMat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, bm, true);
    }


    private void saveToStorage(Mat mMat, String name) {

        Bitmap bmp = Bitmap.createBitmap(mMat.width(),  mMat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mMat, bmp, true);

        String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath() + image_dir;
        Log.d(TAG, "saveToStorage");
        try {
            File dir = new File(fullPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            OutputStream fOut = null;
            File file = new File(fullPath, "_" + num_imatge + "_" +  name + ".png");
            file.createNewFile();
            fOut = new FileOutputStream(file);

            // 100 means no compression, the lower you go, the stronger the compression
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();

            MediaStore.Images.Media.insertImage(context.getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
        } catch (Exception e) {
            Log.d(TAG, "catch");
            Log.e("saveToStorage()", e.getMessage());
        }
        bmp.recycle();
    }

    public Bitmap getFinalbitmap() {
        return bm;
    }

    public int getStatus() {
        return this.status;
    }

    public String toString(){
        return num_imatge + ";" +  mBlurKernelSize + ";" +
                mCannyThresholdMin + ";" + mUseOtsuThreshold + ";" + countourT + ";" +
                mShapeIgnoreSize + ";" + mShapeIgnoreConcave + ";" + mdrawFilteredShapes;
    }


}



