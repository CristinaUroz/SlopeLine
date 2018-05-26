package uroz.cristina.slopeline;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;

public class SelectFileActivity extends AppCompatActivity{

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1;
    private Uri ima_uri;
    private ImageView ima;
    private ImageView next;
    private TextView text_ima;
    private String fileName;
    private File dir;
    private String image_dir = "/SlopeLine/data/";
    public static final String TAG = SelectFileActivity.class.getSimpleName();
    private int pix_max = 1200;
    private boolean guardada = false;
    private Bitmap bm;
    private int num;
    private char CG;
    private int H;
    private int W;
    private int Ho;
    private int Wo;
    private int valor_barra=50;
    private int color_chroma = Color.parseColor("#7c8767");
    private int color_white = Color.parseColor("#ffffff");
    private int color_blue = Color.parseColor("#0000ff");
    private int color_black = Color.parseColor("#000000");
    private SeekBar barra_chroma;
    private int[][] mat;
    private int[][] mat_new;
    private String[] binar;
    private Map<String, String> replace_map;
    private int total_imatges=852;
    private Bitmap bm2;
    int mPhotoWidth;
    int mPhotoHeight;


    public SelectFileActivity() {
    }

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
        barra_chroma = (SeekBar) findViewById(R.id.tolerance_bar);

        Create_map();

        // Configuracio de la barra
        barra_chroma.setMax(100);
        barra_chroma.setProgress(50);
        if (getIntent() != null && getIntent().getExtras() != null) {
            barra_chroma.setProgress(valor_barra);
        }


        // Fem invisible el botó next
        next.setVisibility(View.INVISIBLE);
        barra_chroma.setVisibility(View.INVISIBLE);

        barra_chroma.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                valor_barra = i;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Per fer que no es pugui tornar a premer mentre sesta executant
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                // Mostra el valor de la barra
                Toast.makeText(SelectFileActivity.this, String.valueOf(valor_barra), Toast.LENGTH_SHORT).show();

                // Quan s'aixeca el click s'aplica l'efecte
               change_Color();

            }
        });



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
                barra_chroma.setVisibility(View.INVISIBLE);
                guardarImatgeInicial();
                Processat();
               // start();
            }
        });
    }

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
                            //ima.setImageBitmap(bm);
                            text_ima.setVisibility(View.INVISIBLE);
                            guardada=false;
                            next.setVisibility(View.VISIBLE);
                            barra_chroma.setVisibility(View.VISIBLE);
                            CG='G';
                            change_Color();
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
                    //ima.setImageBitmap(bm);
                    text_ima.setVisibility(View.INVISIBLE);
                    guardada=false;
                    //bMap.recycle();
                    next.setVisibility(View.VISIBLE);
                    barra_chroma.setVisibility(View.VISIBLE);
                    CG='C';
                    change_Color();
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
                //startActivity(intent);
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

    public void change_Color() {
        bm2 = convertToMutable(bm);
        mPhotoWidth = bm2.getWidth();
        mPhotoHeight = bm2.getHeight();
        int[] pix = new int[mPhotoWidth * mPhotoHeight];
        int index, r, g, b, yy, R, G, B, Y;
        R = (color_chroma >> 16) & 0xff;
        G = (color_chroma >> 8) & 0xff;
        B = color_chroma & 0xff;
        Y = (30 * R + 59 * G + 11 * B) / 100;
        mat=new int[mPhotoWidth][mPhotoHeight];
        mat_new=new int[mPhotoWidth][mPhotoHeight];
        int tol=valor_barra;
        bm2.getPixels(pix, 0, mPhotoWidth, 0, 0, mPhotoWidth, mPhotoHeight);
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
                    //pix[index] = getResources().getColor(transparent);
                    pix[index] = color_white;
                    mat[x][y]=0;
                    mat_new[x][y]=-1;

                }
                else{
                    pix[index] = color_black;
                    mat[x][y]=1;
                    mat_new[x][y]=-1;
                }
            }
        }

        bm2.setPixels(pix, 0, mPhotoWidth, 0, 0, mPhotoWidth, mPhotoHeight);
        // Fem visible el nou bitmap
        ima.setImageBitmap(bm2);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public static Bitmap convertToMutable(Bitmap imgIn) {
        try {
            // Fitxer temporal de treball que conte els bits de la imatge (no es una imatge)
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + "temp.tmp");

            // Es crea un RandomAccessFile
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");

            // Ample i alt del bitmap
            int width = imgIn.getWidth();
            int height = imgIn.getHeight();
            Bitmap.Config type = imgIn.getConfig();

            // Copy the byte to the file
            //Assume source bitmap loaded using options.inPreferredConfig = Config.ARGB_8888;
            FileChannel channel = randomAccessFile.getChannel();
            MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, 0, imgIn.getRowBytes() * height);
            imgIn.copyPixelsToBuffer(map);
            //imgIn.recycle();
            System.gc();

            // Es crea el bitmap que es podra editar i s'hi carrega l'anterior
            imgIn = Bitmap.createBitmap(width, height, type);
            map.position(0);
            // load it back from temporary
            imgIn.copyPixelsFromBuffer(map);
            // close the temporary file and channel , then delete that also
            channel.close();
            randomAccessFile.close();
            // delete the temp file
            file.delete();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imgIn;
    }

    private void Create_map() {
        binar = getResources().getStringArray(R.array.binaritzation);
        replace_map = new TreeMap<String, String>();
        for (int i = 0; i < total_imatges; i++) {
            String q = binar[i];
            String[] parts = q.split(";");
            replace_map.put(parts[0],parts[1]);
        }
    }

    private void Processat(){
        for (int y = 0; y < mPhotoHeight; y++) {
            for (int x = 0; x < mPhotoWidth; x++) {
                String t="";
                for (int yy=0;yy<5;yy++){
                    int auxy=yy+y;
                    for (int xx=0;xx<5;xx++){
                        int auxx=xx+x;
                        if (mat[0].length>auxy & mat.length>auxx){
                        t=t+Integer.toString(mat[auxx][auxy]);}
                    }
                }
                String t_n= replace_map.get(t);
                if (t_n!=null){
                    char[] tArray= t_n.toCharArray();
                    for (int y2=0;y2<5;y2++){
                        int auxy2=y2+y;
                        for (int x2=0;x2<5;x2++){
                            int auxx2=x2+x;
                            bm2.setPixel(auxx2,auxy2,color_blue);
                            mat_new[auxx2][auxy2]=Character.getNumericValue(tArray[y2*5+x2]);
                        }
                    }
                }
            }
        }
    }
}