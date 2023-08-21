package com.ib315168.exifmap;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.GpsDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    Metadata metadata;
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.button);
        imageView = (ImageView) findViewById(R.id.imageView);
        /*  Previous approach
            ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
                registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                    // Callback is invoked after the user selects a media item or closes the
                    // photo picker.
                    if (uri != null) {
                        Log.d("PhotoPicker", "Selected URI: " + uri);
                        processSelectedImage(uri);
                    } else {
                        Log.d("PhotoPicker", "No media selected");
                    }
                });
         */

        final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Log.d("PhotoPicker", "Selected URI: " + uri);
                        processSelectedImage(uri);
                    } else {
                        // Handle case when no image was selected
                    }
                }
        );

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /* Previous approach
                    pickMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                */
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                builder.setTitle("Use Google Photos when choosing your image!");
                builder.setMessage("Otherwise GPS data for your photo might not be available.");

                builder.setCancelable(false);
                builder.setNeutralButton("Ok",(DialogInterface.OnClickListener) (dialog, which) -> {
                    dialog.cancel();
                    pickImageLauncher.launch("image/*");
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
        });
    }

    private void processSelectedImage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            metadata = ImageMetadataReader.readMetadata(inputStream);

            GeoLocation location = null;

            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    System.out.println(tag);
                }
            }

            Collection<GpsDirectory> gpsDirectories = metadata.getDirectoriesOfType(GpsDirectory.class);
            for (GpsDirectory gpsDirectory : gpsDirectories) {
                GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                if (geoLocation != null && !geoLocation.isZero()) {
                    System.out.println(geoLocation.toString());

                    // Add to our collection for use below
                    location = geoLocation;
                    break;
                } else {
                    System.out.println("Geo empty");

                }
            }

            if (location != null) {
                StringBuilder staticMapUrl = new StringBuilder();
                staticMapUrl.append("https://maps.geoapify.com/v1/staticmap?style=osm-bright-smooth&width=600&height=400&center=lonlat:");
                staticMapUrl.append(location.getLongitude());
                staticMapUrl.append(",");
                staticMapUrl.append(location.getLatitude());
                staticMapUrl.append("&zoom=10&marker=lonlat:");
                staticMapUrl.append(location.getLongitude());
                staticMapUrl.append(",");
                staticMapUrl.append(location.getLatitude());
                staticMapUrl.append(";color:%23ff0000;size:large&apiKey=dcfc1dd590bd4f0d91a29a3253b604b0");

                new LoadImageTask().execute(staticMapUrl.toString(), uri.toString());
            }
        } catch (ImageProcessingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Bitmap combineImages(Bitmap map, Bitmap background) {
        Bitmap combinedBitmap = Bitmap.createBitmap(background.getWidth(), background.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combinedBitmap);

        Bitmap scaledMap = Bitmap.createScaledBitmap(map, 1200, 800, true);

        canvas.drawBitmap(background, 0, 0, null);
        canvas.drawBitmap(scaledMap, (background.getWidth()/2), (background.getHeight()/2), null);

        return combinedBitmap;
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "IMG_" + timeStamp + ".jpg";

        // Get the storage directory
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, fileName);

        try {
            OutputStream outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.flush();
            outputStream.close();

            // Insert the image into the media store
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "Image Title");
            values.put(MediaStore.Images.Media.DESCRIPTION, "Image Description");
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.DATA, imageFile.getAbsolutePath());

            ContentResolver contentResolver = getContentResolver();
            Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            // Notify the system that a new image has been added
            MediaScannerConnection.scanFile(this, new String[]{imageFile.getAbsolutePath()}, null, null);

            Log.d("PhotoSaver", "Image saved to gallery: " + imageUri);
            Toast success = new Toast(this);
            success.setText("Image successfully saved!");
            success.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            String imageUrl = params[0];
            FutureTarget<Bitmap> futureTarget = Glide.with(MainActivity.this)
                    .asBitmap()
                    .load(imageUrl)
                    .submit();

            Bitmap result;

            try {
                result = combineImages(futureTarget.get(), BitmapFactory.decodeStream(getContentResolver().openInputStream(Uri.parse(params[1]))));
            } catch (FileNotFoundException | ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            return result;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // Here, you can use the loaded bitmap on the UI thread
            if (bitmap != null) {
                Glide.with(MainActivity.this).load(bitmap).into(imageView);
                saveBitmapToGallery(bitmap);
            }
        }
    }
}
