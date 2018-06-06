package uroz.cristina.slopeline;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import static uroz.cristina.slopeline.R.string.preference_entry_value_map_type_icgc;


public class SelectFileActivity extends AppCompatActivity {

    public static final String TAG = SelectFileActivity.class.getSimpleName();

    // Permissions request
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1;

    // Layout elements
    private ImageView ima;
    private ImageView next;
    private TextView text_ima;
    private SeekBar tol_bar;

    // Default values
    private int bar_value = 50; // Default tolerance bar value
    private int pix_max = 1200; // For resize the image if it too big

    // For saving files
    private File dir;
    private String image_dir = "/SlopeLine/data/";

    private int mPhotoWidth; // Resized bitmap width
    private int mPhotoHeight; // Resized bitmap Heigh
    private int[] mat; // Pixels values  array after binarization
    private Uri ima_uri; // Image uri
    private Bitmap bm; // Image map bitmap (resized)
    private Bitmap bm_mutable; // Mutable image map bitmap (resized)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_file);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        // Ask for permissions (first camera and second external storage)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
            }
        }

        // Create external storage directory
        dir = new File(Environment.getExternalStorageDirectory(), image_dir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Layout references
        ima = findViewById(R.id.image_Map);
        next = findViewById(R.id.image_Next);
        text_ima = findViewById(R.id.text_SelectPick);
        tol_bar = findViewById(R.id.tolerance_bar);

        next.setVisibility(View.INVISIBLE);
        tol_bar.setVisibility(View.INVISIBLE);

        // Get intent extras if we come from "Processing" activity.¡
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.getString("ima_uri") != null) {
                mPhotoWidth = extras.getInt("mPhotoWidth");
                mPhotoHeight = extras.getInt("mPhotoHeight");
                ima_uri = Uri.parse(extras.getString("ima_uri"));
                bar_value = extras.getInt("bar_value");
                try {
                    bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), ima_uri);
                    resize();
                    text_ima.setVisibility(View.INVISIBLE);
                    next.setVisibility(View.VISIBLE);
                    tol_bar.setVisibility(View.VISIBLE);
                    binarization(); //Apply binarization
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        } else {
            // Put default preferences
            PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        }


        // Bar settings
        tol_bar.setMax(100);
        tol_bar.setProgress(50);
        if (getIntent() != null && getIntent().getExtras() != null) {
            tol_bar.setProgress(bar_value);
        }

        tol_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                bar_value = i;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Set flags not touchable while it is applying binarization
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                Toast.makeText(SelectFileActivity.this, String.valueOf(bar_value), Toast.LENGTH_SHORT).show(); // Show bar value
                binarization(); //Apply binarization
            }
        });

        ima.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Open gallery to choose an image
                Intent pickPhoto = new Intent(Intent.ACTION_OPEN_DOCUMENT, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, 0);
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //New intent to "Processing" activity with extra values
                bm.recycle();
                bm_mutable.recycle();
                Intent intent = new Intent(SelectFileActivity.this, Processing.class);
                intent.putExtra("mPhotoWidth", mPhotoWidth);
                intent.putExtra("mPhotoHeight", mPhotoHeight);
                intent.putExtra("ima_uri", ima_uri.toString());
                intent.putExtra("bar_value", bar_value);
                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SelectFileActivity.this);
                int s_p = Integer.parseInt(preferences.getString(getString(R.string.preference_key_skipped_pixels),
                        getString(R.string.preference_default_value_skipped_pixels)));
                int r_a = Integer.parseInt(preferences.getString(getString(R.string.preference_key_research_area),
                        getString(R.string.preference_default_value_research_area)));
                String color = preferences.getString(getString(R.string.preference_key_map_type),
                        getString(preference_entry_value_map_type_icgc));
                intent.putExtra("skipped_pixels", s_p);
                intent.putExtra("color", color);
                intent.putExtra("research_area", r_a);

                startActivity(intent);
                finish();
            }
        });
    }

    // Ask for permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Camera permission granted");
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        } else {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                        }
                    }
                } else {
                    Log.i(TAG, "Camera permission denied");
                }
                return;
            }

            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Write external storage permission granted");
                } else {
                    Log.i(TAG, "Write external storage permission denied");
                }
            }
        }
    }

    // Create option Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // Select a menu option
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.info:
                AlertDialog.Builder builder = new AlertDialog.Builder(SelectFileActivity.this);
                builder.setTitle(R.string.choose_title);
                builder.setMessage(R.string.info_1);
                builder.setCancelable(true);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                });
                builder.create().show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // Pick an image from gallery
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        switch (requestCode) {
            case 0: // Gallery
                if (resultCode == RESULT_OK) {
                    ima_uri = imageReturnedIntent.getData();
                    if (ima_uri != null) {
                        try {
                            bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), ima_uri);
                            resize();
                            //ima.setImageBitmap(bm);
                            text_ima.setVisibility(View.INVISIBLE);
                            next.setVisibility(View.VISIBLE);
                            tol_bar.setVisibility(View.VISIBLE);
                            binarization();
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }

                    }
                }
                break;
        }
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
                Log.i(TAG, "Bitmap resized");
            }
        } else {
            if (Wo > pix_max) {
                int W = pix_max;
                int H = (int) ((double) (Ho) * ((double) (pix_max) / (double) (Wo)));
                resizeBitmap(W, H, Wo, Ho);
                Log.i(TAG, "Bitmap resized");
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

    // Convert bitmap to mutable
    private static Bitmap convertToMutable(Bitmap imgIn) {
        try {
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + "temp.tmp");

            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            int width = imgIn.getWidth();
            int height = imgIn.getHeight();
            Bitmap.Config type = imgIn.getConfig();

            FileChannel channel = randomAccessFile.getChannel();
            MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, 0, imgIn.getRowBytes() * height);
            imgIn.copyPixelsToBuffer(map);
            System.gc();

            imgIn = Bitmap.createBitmap(width, height, type);
            map.position(0);

            imgIn.copyPixelsFromBuffer(map);
            channel.close();

            randomAccessFile.close();

            file.delete();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imgIn;

    }

    // Bitmap binarization
    private void binarization() {

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SelectFileActivity.this);
        final int contour_lines_color = Color.parseColor(preferences.getString(getString(R.string.preference_key_map_type),
                getString(preference_entry_value_map_type_icgc)));

        bm_mutable = convertToMutable(bm);
        mPhotoWidth = bm_mutable.getWidth();
        mPhotoHeight = bm_mutable.getHeight();

        int index, r, g, b, yy, R, G, B, Y;
        int tol = bar_value;
        int[] pix = new int[mPhotoWidth * mPhotoHeight];
        mat = new int[mPhotoWidth * mPhotoHeight];


        R = (contour_lines_color >> 16) & 0xff;
        G = (contour_lines_color >> 8) & 0xff;
        B = contour_lines_color & 0xff;
        Y = (30 * R + 59 * G + 11 * B) / 100;

        bm_mutable.getPixels(pix, 0, mPhotoWidth, 0, 0, mPhotoWidth, mPhotoHeight);

        for (int y = 0; y < mPhotoHeight; y++) {
            for (int x = 0; x < mPhotoWidth; x++) {
                index = y * mPhotoWidth + x;
                r = (pix[index] >> 16) & 0xff;
                g = (pix[index] >> 8) & 0xff;
                b = pix[index] & 0xff;
                yy = (30 * r + 59 * g + 11 * b) / 100;
                if (!(Y + tol >= yy && Y - tol <= yy &&
                        R + tol >= r && R - tol <= r &&
                        B + tol >= b && B - tol <= b &&
                        G + tol >= g && G - tol <= g
                )) {
                    pix[index] = Color.WHITE;
                    mat[x + y * mPhotoWidth] = 0;

                } else {
                    pix[index] = Color.BLACK;
                    mat[x + y * mPhotoWidth] = 1;
                }
            }
        }

        bm_mutable.setPixels(pix, 0, mPhotoWidth, 0, 0, mPhotoWidth, mPhotoHeight);
        ima.setImageBitmap(bm_mutable);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

}