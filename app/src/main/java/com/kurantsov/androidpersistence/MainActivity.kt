package com.kurantsov.androidpersistence

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.kurantsov.androidpersistence.databinding.ActivityMainBinding
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val folderChooseLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { selectedFolder ->
            if (selectedFolder != null) {
                onFolderSelected(selectedFolder)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, PreferencesActivity::class.java))
        }
        binding.btnChooseFolder.setOnClickListener {
            folderChooseLauncher.launch(null)
        }

        filesExample()
        sharedPreferencesExample()
        mediaStoreExample()
    }

    private fun filesExample() {
        val cacheFile = File(cacheDir, "CacheFile.txt")
        cacheFile.writeText("Some cache data")

        val filesFile = File(filesDir, "FilesFile.txt")
        filesFile.writer().use { streamWriter ->
            streamWriter.write("Files file example")
        }

        val externalCacheFile = File(externalCacheDir, "CacheFile.txt")
        cacheFile.copyTo(externalCacheFile, overwrite = true)

        val externalDocumentsFile =
            File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MyDocument.txt")
        externalDocumentsFile.writeText("External documents directory file content")
    }

    private fun sharedPreferencesExample() {
        val preferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        //Write
        preferences
            .edit()
            .putInt("INT_PREF", 42)
            .putString("STRING_PREF", "Hello world")
            .apply()

        //Read
        val intValue = preferences.getInt("INT_PREF", 0)
        val stringValue = preferences.getString("STRING_PREF", "Not set")
    }

    private fun mediaStoreExample() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSIONS_REQUEST_CODE
                )
            } else {
                queryVideos()
            }
        } else {
            queryVideos()
        }
    }

    private fun queryVideos() {
        data class Video(
            val uri: Uri,
            val name: String,
            val duration: Int,
            val size: Int
        )

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        // Show only videos that are at least 5 seconds in duration.
        val selection = "${MediaStore.Video.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS).toString()
        )

        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        val query = contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
        query?.use { cursor ->
            // Cache column indices.
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getInt(durationColumn)
                val size = cursor.getInt(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                Log.d("MainActivity", "${Video(contentUri, name, duration, size)}")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE &&
            grantResults.first() == PackageManager.PERMISSION_GRANTED
        ) {
            queryVideos()
        }
    }

    private fun onFolderSelected(selectedFolder: Uri) {
        val folderFile = DocumentFile.fromTreeUri(this, selectedFolder) ?: return
        Log.d("MainActivity", "Files in ${folderFile.name}")
        for (fileInFolder in folderFile.listFiles()) {
            Log.d("MainActivity", " - ${fileInFolder.name}")
        }
        folderFile.createFile("text/plain", "test.txt")?.let {
            contentResolver.openOutputStream(it.uri).use { os ->
                os ?: return@use
                val writer = BufferedWriter(OutputStreamWriter(os))
                writer.write("Hello world!")

                writer.flush()
                writer.close()
            }
        }
    }

    private companion object {
        const val SHARED_PREFERENCES_NAME = "example.pref"
        const val PERMISSIONS_REQUEST_CODE = 100
    }
}