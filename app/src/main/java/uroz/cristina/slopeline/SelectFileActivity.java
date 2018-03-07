package uroz.cristina.slopeline;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;

//TODO: Arreglar ProgressBar
//TODO: Preferencies per defecte


public class SelectFileActivity extends AppCompatActivity  /*implements View.OnTouchListener*/ {

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1;
    private Uri ima_uri;
    private ImageView ima;
    private ImageView next;
    private TextView text_ima;
    private String fileName;
    private File dir;
    private String image_dir = "/SlopeLine/data/";
    private static boolean sOpenCVAvailable = true;
    public static final String TAG = SelectFileActivity.class.getSimpleName();
    private int pix_max = 1600;
    private boolean guardada = false;
    private Bitmap bm;
    private int num;
    private char CG;
    private int H;
    private int W;
    private int Ho;
    private int Wo;

    private ProgressBar mProgressBar;
    private int mProgressStatus = 0;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_file);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        // Es demana el permis per utilitzar la camera, i quan s'acepta, es demana el permis per escriure fitxers
        // Demana permisos de camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
            }
        }

        // Creació del directori on es guarden les fotos que es fan en aquesta activitat
        dir = new File(Environment.getExternalStorageDirectory(), image_dir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Obtencio de referencies a elements de la pantalla
        ima = (ImageView) findViewById(R.id.image_Map);
        next = (ImageView) findViewById(R.id.image_Next);
        text_ima = (TextView) findViewById(R.id.text_SelectPick);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);


        // Fem invisible el botó next
        next.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.INVISIBLE);

        // Posem a les preferencies els valors per defecte
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        //TODO... NO LES POSA!

        // Quan es toca a la pantalla demana s'obriràun quadre de text per escollir d'on volem agafar la imatge
        ima.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseCameraGallery();
            }
        });

        // Quan apretem el next/play es guarda la imatge inicial i es posa el marxa la detecció de corbes de nivell
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                next.setVisibility(View.INVISIBLE);
                mProgressBar.setProgress(0);
                guardarImatgeInicial();
                start();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Mirem si l'openCV està disponible cada vegada que l'app es torna a carregar
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mBaseLoaderCallback);
    }

    private BaseLoaderCallback mBaseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                // OpenCV disponlibre
                case LoaderCallbackInterface.SUCCESS:
                    sOpenCVAvailable = true;
                    Log.i(TAG, "OpenCV carregat correctament");
                    break;
                // Casos que poden donar error
                case LoaderCallbackInterface.INCOMPATIBLE_MANAGER_VERSION:
                case LoaderCallbackInterface.INIT_FAILED:
                case LoaderCallbackInterface.INSTALL_CANCELED:
                case LoaderCallbackInterface.MARKET_ERROR:
                    sOpenCVAvailable = false;
                    Log.i(TAG, "Error al carregar OpenCV - codi = " + status);
                default:
                    super.onManagerConnected(status);
            }
        }
    };

    // Es guarda l'uri de la imatge escollida i es fa el display a l'image view corresponent
    // Es posa visible el boto next/play
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        switch (requestCode) {
            case 0: // Galeria
                if (resultCode == RESULT_OK) {
                    ima_uri = imageReturnedIntent.getData();
                    if (ima_uri != null) {
                        try{
                        bm=MediaStore.Images.Media.getBitmap(this.getContentResolver(), ima_uri);
                            Resize();
                            ima.setImageBitmap(bm);
                            text_ima.setVisibility(View.INVISIBLE);
                            guardada=false;
                            next.setVisibility(View.VISIBLE);
                            mProgressBar.setVisibility(View.VISIBLE);
                            mProgressBar.setProgress(0);
                            CG='G';
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }

                    }
                }
                break;
            case 1: // Càmera
                if (resultCode == RESULT_OK) {
                    bm = BitmapFactory.decodeFile(fileName);
                    Resize();
                    ima.setImageBitmap(bm);
                    text_ima.setVisibility(View.INVISIBLE);
                    guardada=false;
                    //bMap.recycle();
                    next.setVisibility(View.VISIBLE);
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setProgress(0);
                    CG='C';
                }
                break;
        }
    }

    // S'obra un quadre de text per escollir entre càmera o galeria
    private void chooseCameraGallery() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.choose);
        builder.setMessage(R.string.choose_from);
        builder.setCancelable(true);

        // Si s'agafa la imatge de la camera
        builder.setNegativeButton(R.string.camera, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                fileName = dir + "/" + getPhotoName();
                File photoFile = new File(fileName);
                try {
                    photoFile.createNewFile();
                    ima_uri = Uri.fromFile(photoFile);
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, ima_uri);
                    startActivityForResult(cameraIntent, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

            }
        });

        // Si s'agafa la imatge de la galeria
        builder.setPositiveButton(R.string.gallery, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, 0);
            }
        });

        builder.create().show();
    }

    // Genera un nom per una imatge EX: '2017.12.15_20.31.46.jpg'
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

    // Metode per demanar permisos
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "permis camera concedit");
                    // Demana permisos d'escriptura
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        } else {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                        }
                    }
                } else {
                    Log.i(TAG, "permis camera denegat");
                }
                return;
            }

            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "permis escriptura concedit");
                } else {
                    Log.i(TAG, "permis escriptura denegat");
                }
            }
        }
    }

    //Metode per crear el menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // Metode per saaber que fer en el cada cas de les opcions del menú
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // start our preferences settings activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    // Posa en marxa l'OpenCVRunnable
    private void start() {
        // Comprovació de que l'openCV esta disponible
        if (!sOpenCVAvailable) {
            // Si no està disponible es notifica a l'usuari i es torna a intentar

            AlertDialog.Builder builder = new AlertDialog.Builder(SelectFileActivity.this);
            builder.setTitle(R.string.Opencvnotavailalbe);
            builder.setMessage(R.string.Retry);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    start();
                }
            });
            builder.create().show();

        }
        // OpenCV esta disponible i s'ha carregat correctament
        // S'Agafen les preferències de l'usuari
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final int blurKernelSize = Integer.parseInt(preferences.getString(getString(R.string.preference_key_blur_kernel),
                getString(R.string.preference_default_value_blur_kernel)));
        final boolean useOtsu = preferences.getBoolean(getString(R.string.preference_key_canny_otsu), true);
        final int cannyMinThreshold = Integer.parseInt(preferences.getString(getString(R.string.preference_key_canny_threshold_min),
                getString(R.string.preference_default_value_canny_threshold_min)));
        final int cannyMaxThreshold = Integer.parseInt(preferences.getString(getString(R.string.preference_key_canny_threshold_max),
                getString(R.string.preference_default_value_canny_threshold_max)));
        final int contourType = Integer.parseInt(preferences.getString(getString(R.string.preference_key_contours_type),
                getString(R.string.preference_entry_value_contours_type_external)));
        final int shapeIgnoreSize = Integer.parseInt(preferences.getString(getString(R.string.preference_key_contours_ignore_area_size),
                getString(R.string.preference_default_contours_ignore_area_size)));
        final boolean shapreIgnoreConcave = preferences.getBoolean(getString(R.string.preference_key_contours_ignore_concave), true);
        final boolean drawFilteredShapes = preferences.getBoolean(getString(R.string.preference_key_contours_draw_filtered_shapes), true);

        // S'inicia l'OpenCV runnable
        final OpenCvRunnable cvRunnable = new OpenCvRunnable((Environment.getExternalStorageDirectory() + image_dir + "_" + num + "_1_originalImage" + ".png"),
                blurKernelSize,
                cannyMinThreshold,
                cannyMaxThreshold,
                useOtsu,
                contourType,
                shapeIgnoreSize,
                shapreIgnoreConcave,
                drawFilteredShapes,
                getApplicationContext());
        // Les accions de l'openCV requereixen CPU
        // Posarem les acctions en marxa apart per a no colapsar el main
        Thread cvThread = new Thread(cvRunnable);

        bm = null;


        Thread pbThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mProgressStatus < 100) {
                    mProgressStatus =cvRunnable.getStatus();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mProgressBar.setProgress(mProgressStatus);
                        }
                    });
                }
            }
        });

        cvThread.start();
        pbThread.start();

        while (bm==null){
            bm=cvRunnable.getFinalbitmap();
        }
        ima.setImageBitmap(bm);
        String fullPath = Environment.getExternalStorageDirectory() + image_dir;

        num=0;
        // Comprova si el directori existeix per crear-lo en cas de que no sigui així
        try{
        File dir = new File(fullPath);
        File file = new File(fullPath+ "Settings.txt");
        String text_settings=cvRunnable.toString()+";"+CG+";"+Wo+"x"+Ho+ ";"+W+"x"+H;
        FileOutputStream fileOutputStream = new FileOutputStream(file,true);
        fileOutputStream.write((text_settings + System.getProperty("line.separator")).getBytes());}
        catch (IOException e) {
        }

    }

    public void Resize() {
        // Es fa un resize de la imatge a un tamany mes petit fent que el costat mes gran de la
        // imatge no sigui superior a pix_max
        Ho=bm.getHeight();
        Wo= bm.getWidth();

        if (Ho > Wo) {
            if (Ho > pix_max) {
                H = pix_max;
                W = (int) ((double) (Wo) * ((double) (pix_max) / (double) (Ho)));
                ResizeBitmap(W, H);
                Log.i(TAG, "s'ha canviat el tamany de la imatge" );
            }
        } else {
            if (Wo > pix_max) {
                W = pix_max;
                H = (int) ((double) (Ho) * ((double) (pix_max) / (double) (Wo)));
                ResizeBitmap(W, H);
                Log.i(TAG, "s'ha canviat el tamany de la imatge" );
            }
        }
    }

    public void ResizeBitmap(int newWidth, int newHeight) {
        float scaleWidth = ((float) newWidth) / Wo;
        float scaleHeight = ((float) newHeight) / Ho;
        // Crea una matriu, mes facil de manipular
        Matrix matrix = new Matrix();
        // Canvia el tamany de la matriu
        matrix.postScale(scaleWidth, scaleHeight);
        // Emplena el bitmap amb la matriu amb el tamany nou
        bm = Bitmap.createBitmap(bm, 0, 0, Wo, Ho, matrix, false);
    }

    public void guardarImatgeInicial()  {
            if (!guardada) {
                try {
                    String fullPath = Environment.getExternalStorageDirectory() + image_dir;

                    num=0;
                    // Comprova si el directori existeix per crear-lo en cas de que no sigui així
                    File dir = new File(fullPath);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }

                    File file = new File(fullPath+ "Settings.txt");
                    if (!file.exists()) {
                        num++;
                        file.createNewFile();
                        String text_settings_T= "IMAGE;blurKernelSize;cannyThresholdMin;useOtsuThreshold;contourType;shapeIgnoreSize"+
                                ";shapeIgnoreConcave;drawFilteredShapes;CameraGalery;OriginalSize;FinalSize";

                        FileOutputStream fileOutputStream = new FileOutputStream(file,true);
                        fileOutputStream.write((text_settings_T + System.getProperty("line.separator")).getBytes());
                    }
                    else {
                        //Contar liinies
                        StringBuilder text = new StringBuilder();
                        try {
                            BufferedReader br = new BufferedReader(new FileReader(file));
                            String line;

                            while ((line = br.readLine()) != null) {
                                text.append(line);
                                text.append('\n');

                                num++;
                            }
                        }
                        catch (IOException e) {
                            //You'll need to add proper error handling here
                        }
                    }
                    OutputStream fOut = null;
                    File file2 = new File(fullPath,"_" +num + "_1_originalImage" + ".png");
                    file2.createNewFile();
                    fOut = new FileOutputStream(file2);

                    // 100 significa no compressió, com més baix és el número més es comprimeix la imatge
                    bm.compress(Bitmap.CompressFormat.PNG, 100, fOut);
                    fOut.flush();
                    fOut.close();
                   // bm.recycle();
                    guardada = true;
                    Log.i(TAG, "Imatge original guardada");

                } catch (Exception e) {
                    Log.e(TAG,"Error al guardar la imatge original"+ e.getMessage());
                }

            }

    }

}