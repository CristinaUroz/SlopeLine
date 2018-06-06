package uroz.cristina.slopeline;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import static android.graphics.Bitmap.Config.ARGB_8888;

public class Processing extends AppCompatActivity {

    public static final String TAG = SelectFileActivity.class.getSimpleName();

    // Layout elements
    private ImageView image;
    private ImageView restart;
    private ImageView save;

    // Default values
    private int total_imatges = 852; // Number of images that make up the dictionary
    private int pix_max = 1200; // For resize the image if it too big
    private String image_dir = "/SlopeLine/data/"; // Storage directory

    private int mPhotoWidth; // Resized bitmap width
    private int mPhotoHeight; // Resized bitmap Heigh
    private int bar_value; // Tolerance bar value
    private int research_area; // Area for research pixels of the same line [1,15]
    private int skipped_pixels; // Skip pixels for searching in the dictionary
    private int[][] mat; // Pixels values
    private String[] colors = new String[416]; // List of colors
    private Map<String, String> replace_map = new HashMap<String, String>(); // Dictionary
    private Map<Integer, Set<int[]>> lines_fi; // Map with the id of the contour line and all its pixels
    private Uri ima_uri; // Uri of the map image
    private Bitmap bm; // Image map bitmap (resized)
    private Bitmap bm_mutable; // Mutable image map bitmap (resized)
    private int contour_lines_color;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        // Layout references
        image = findViewById(R.id.image);
        restart = findViewById(R.id.restart);
        save = findViewById(R.id.save);

        // Get values from previous activity
        Bundle extras = getIntent().getExtras();
        mPhotoWidth = extras.getInt("mPhotoWidth");
        mPhotoHeight = extras.getInt("mPhotoHeight");
        ima_uri = Uri.parse(extras.getString("ima_uri"));
        bar_value = extras.getInt("bar_value");
        research_area = extras.getInt("research_area");
        skipped_pixels = extras.getInt("skipped_pixels");
        contour_lines_color = Color.parseColor(extras.getString("color"));
        if(research_area<1){
            research_area=1;
        }
        else if(research_area>15){
            research_area=15;
        }
        if(skipped_pixels<1){
            skipped_pixels=1;
        }
        else if(skipped_pixels>5){
            skipped_pixels=5;
        }

        createDictionaryMap();
        readColorsList();
        binarization();

        bm.recycle(); // For saving memory

        // Create a new multy class to process the image
        Multi m = new Multi(mPhotoWidth, mPhotoHeight, mat, replace_map, research_area, skipped_pixels);
        m.run();
        lines_fi = m.get_lines();

        createColorBitmap(); // Create the colorfull final bitmap

        restart.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View view) {
                // Go to "SelectFileActivity" activity
                Intent intent = new Intent(Processing.this, SelectFileActivity.class);
                intent.putExtra("mPhotoWidth", mPhotoWidth);
                intent.putExtra("mPhotoHeight", mPhotoHeight);
                intent.putExtra("ima_uri", ima_uri.toString());
                intent.putExtra("bar_value", bar_value);
                startActivity(intent);
                finish();
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                final String name = getPhotoName();

                AlertDialog.Builder builder = new AlertDialog.Builder(Processing.this);
                builder.setTitle(R.string.save);
                builder.setMessage(R.string.what_save);
                builder.setCancelable(true);

                // To save only the final image
                builder.setNegativeButton(R.string.image, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        saveImage(bm_mutable, name);
                        Toast.makeText(Processing.this, R.string.image_saved, Toast.LENGTH_SHORT).show();
                    }
                });

                // To save only a txt with lines information
                builder.setPositiveButton(R.string.Lines_info, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        saveLines(name);
                        Toast.makeText(Processing.this, R.string.lines_saved, Toast.LENGTH_SHORT).show();
                    }
                });

                // To save all
                builder.setNeutralButton(R.string.Save_all, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        saveImage(bm_mutable, name);
                        saveLines(name);
                        Toast.makeText(Processing.this, R.string.image_lines_saved, Toast.LENGTH_SHORT).show();
                    }
                });

                builder.create().show();

            }
        });
    }

    // To create the dictionary map
    private void createDictionaryMap() {
        String[] binar = getResources().getStringArray(R.array.binaritzation);
        for (int i = 0; i < total_imatges; i++) {
            String q = binar[i];
            String[] parts = q.split(";");
            replace_map.put(parts[0], parts[1]);
        }
    }

    // Create color list
    private void readColorsList() {
        colors = getResources().getStringArray(R.array.colors_tots);
    }

    // Calculate the bitmap resize
    private void resize() {
        int Ho = bm.getHeight();
        int Wo = bm.getWidth();

        if (Ho > Wo) {
            if (Ho > pix_max) {
                int H = pix_max;
                int W = (int) ((double) (Wo) * ((double) (pix_max) / (double) (Ho)));
                resizeBitmap(W, H, Wo, Ho);
                Log.i(TAG, "s'ha canviat el tamany de la imatge");
            }
        } else {
            if (Wo > pix_max) {
                int W = pix_max;
                int H = (int) ((double) (Ho) * ((double) (pix_max) / (double) (Wo)));
                resizeBitmap(W, H, Wo, Ho);
                Log.i(TAG, "s'ha canviat el tamany de la imatge");
            }
        }
    }

    // Resize bitmap
    private void resizeBitmap(int newWidth, int newHeight, int Wo, int Ho) {
        float scaleWidth = ((float) newWidth) / Wo;
        float scaleHeight = ((float) newHeight) / Ho;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        bm = Bitmap.createBitmap(bm, 0, 0, Wo, Ho, matrix, false);
    }

    // Image binarization
    private void binarization() {
        try {
            bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), ima_uri);
            resize();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        int[] pix = new int[mPhotoWidth * mPhotoHeight];
        int index, r, g, b, yy, R, G, B, Y;
        R = (contour_lines_color >> 16) & 0xff;
        G = (contour_lines_color >> 8) & 0xff;
        B = contour_lines_color & 0xff;
        Y = (30 * R + 59 * G + 11 * B) / 100;
        mat = new int[mPhotoWidth][mPhotoHeight];

        int tol = bar_value;
        bm.getPixels(pix, 0, mPhotoWidth, 0, 0, mPhotoWidth, mPhotoHeight);
        for (int y = 0; y < mPhotoHeight; y++) {
            for (int x = 0; x < mPhotoWidth; x++) {
                index = y * mPhotoWidth + x;
                r = (pix[index] >> 16) & 0xff;
                g = (pix[index] >> 8) & 0xff;
                b = pix[index] & 0xff;
                yy = (30 * r + 59 * g + 11 * b) / 100;
                //Mirem si els valors del color es troben dins dels parametres de tolerancia
                if (!(Y + tol >= yy && Y - tol <= yy &&
                        R + tol >= r && R - tol <= r &&
                        B + tol >= b && B - tol <= b &&
                        G + tol >= g && G - tol <= g
                )) {
                    mat[x][y] = 0;

                } else {

                    mat[x][y] = 1;
                }
            }
        }
    }

    // Create the colorfull final bitmap
    private void createColorBitmap() {
        bm_mutable = Bitmap.createBitmap(mPhotoWidth, mPhotoHeight, ARGB_8888);
        for (int y = 0; y < mPhotoHeight; y++) {
            for (int x = 0; x < mPhotoWidth; x++) {
                bm_mutable.setPixel(x, y, Color.WHITE);
            }
        }
        Set<Integer> keys = lines_fi.keySet();
        for (int in : keys) {
            Set<int[]> list = lines_fi.get(in);
            for (int[] xy : list) {
                int aux = in - colors.length * (in / colors.length);
                bm_mutable.setPixel(xy[0], xy[1], Color.parseColor(colors[aux]));
            }
        }
        image.setImageBitmap(bm_mutable);
    }

    // Get actual time to creat a name
    private String getPhotoName() {
        Calendar calendar = Calendar.getInstance();
        String day = Integer.toString(calendar.get(Calendar.DAY_OF_MONTH));
        String month = Integer.toString(calendar.get(Calendar.MONTH) + 1);
        String year = Integer.toString(calendar.get(Calendar.YEAR));
        String hour = Integer.toString(calendar.get(Calendar.HOUR_OF_DAY));
        String minute = Integer.toString(calendar.get(Calendar.MINUTE));
        String second = Integer.toString(calendar.get(Calendar.SECOND));

        day = (day.length() == 1) ? "0" + day : day;
        month = (month.length() == 1) ? "0" + month : month;
        hour = (hour.length() == 1) ? "0" + hour : hour;
        minute = (minute.length() == 1) ? "0" + minute : minute;
        second = (second.length() == 1) ? "0" + second : second;

        return year + "." + month + "." + day + "_" + hour + "." + minute + "." + second + ".jpg";
    }

    // Save only a txt with lines information
    private void saveLines(String lines_name) {
        if (lines_fi != null) {

            try {
                String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath() + image_dir;

                File dir = new File(fullPath);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File file = new File(fullPath + "Lines-" + lines_name + ".txt");
                if (!file.exists()) {
                    file.createNewFile();

                }
                FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                for (int m = 0; m < lines_fi.size(); m++) {
                    Set<int[]> list = lines_fi.get(m);
                    String t = Integer.toString(m);
                    for (int[] n : list) {
                        t = t + ";" + Integer.toString(n[0]) + ";" + Integer.toString(n[1]);
                    }
                    fileOutputStream.write((t + System.getProperty("line.separator")).getBytes());
                }
            } catch (Exception e) {
                Log.e("TAG", "Error saving the lines information" + e.getMessage());
            }

        }

    }

    // Save the final bitmap
    private void saveImage(Bitmap finalBitmap, String image_name) {

        String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath() + image_dir;

        File dir = new File(fullPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fname = "Map-" + image_name;
        File file = new File(dir, fname);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.e("TAG", "Error saving the image" + e.getMessage());
        }
    }
}
