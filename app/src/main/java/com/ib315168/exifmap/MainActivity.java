package com.ib315168.exifmap;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    InputStream selectedImage;
    Metadata metadata;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.button);
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
                pickImageLauncher.launch("image/*");
            }
        });
    }

    private void processSelectedImage(Uri uri) {
        try {
            selectedImage = getContentResolver().openInputStream(uri);
            metadata = ImageMetadataReader.readMetadata(selectedImage);

            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    System.out.println(tag);
                }
            }

            selectedImage.close();
        } catch (ImageProcessingException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}