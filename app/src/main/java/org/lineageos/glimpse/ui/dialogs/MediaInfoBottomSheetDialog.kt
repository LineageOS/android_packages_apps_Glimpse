/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.dialogs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.apertureValue
import org.lineageos.glimpse.ext.artist
import org.lineageos.glimpse.ext.copyright
import org.lineageos.glimpse.ext.createWriteRequest
import org.lineageos.glimpse.ext.exposureTime
import org.lineageos.glimpse.ext.focalLength
import org.lineageos.glimpse.ext.isSupportedFormatForSavingAttributes
import org.lineageos.glimpse.ext.isoSpeed
import org.lineageos.glimpse.ext.make
import org.lineageos.glimpse.ext.model
import org.lineageos.glimpse.ext.round
import org.lineageos.glimpse.ext.software
import org.lineageos.glimpse.ext.toFraction
import org.lineageos.glimpse.ext.userComment
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.ui.views.ListItem
import java.text.SimpleDateFormat
import java.util.Locale

class MediaInfoBottomSheetDialog(
    context: Context,
    media: Media,
    callbacks: Callbacks,
    secure: Boolean = false,
) : BottomSheetDialog(context) {
    // Views
    private val artistInfoListItem by lazy { findViewById<ListItem>(R.id.artistInfoListItem)!! }
    private val cameraInfoListItem by lazy { findViewById<ListItem>(R.id.cameraInfoListItem)!! }
    private val contentView by lazy { findViewById<View>(android.R.id.content)!! }
    private val dateTextView by lazy { findViewById<TextView>(R.id.dateTextView)!! }
    private val descriptionEditText by lazy { findViewById<EditText>(R.id.descriptionEditText)!! }
    private val locationInfoListItem by lazy { findViewById<ListItem>(R.id.locationInfoListItem)!! }
    private val mediaInfoListItem by lazy { findViewById<ListItem>(R.id.mediaInfoListItem)!! }
    private val timeTextView by lazy { findViewById<TextView>(R.id.timeTextView)!! }

    // Coroutines
    private val mainScope = CoroutineScope(Job() + Dispatchers.Main)
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)

    // Geocoder
    private val geocoder by lazy { Geocoder(context) }

    private val unknownString: String
        get() = context.resources.getString(R.string.media_info_unknown)

    init {
        setContentView(R.layout.media_info_bottom_sheet_dialog)

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            contentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        descriptionEditText.setOnEditorActionListener { _, _, _ ->
            callbacks.onEditDescription(media, descriptionEditText.text.toString().trim())

            false
        }

        val unknownString = unknownString

        dateTextView.text = dateFormatter.format(media.dateModified)
        timeTextView.text = timeFormatter.format(media.dateModified)

        mediaInfoListItem.leadingIconImage = ResourcesCompat.getDrawable(
            context.resources,
            when (media.mediaType) {
                MediaType.ALBUM -> R.drawable.ic_albums
                MediaType.IMAGE -> R.drawable.ic_image
                MediaType.VIDEO -> R.drawable.ic_video_camera_back
            },
            null
        )
        mediaInfoListItem.headlineText = media.displayName

        val contentResolver = context.contentResolver

        contentResolver.openInputStream(media.uri)?.use { inputStream ->
            val exifInterface = ExifInterface(inputStream)

            val userComment = exifInterface.userComment?.takeIf {
                it.isNotBlank()
            }
            val isSupportedFormatForSavingAttributes =
                exifInterface.isSupportedFormatForSavingAttributes && !secure
            descriptionEditText.setText(userComment ?: "")
            descriptionEditText.inputType = when (isSupportedFormatForSavingAttributes) {
                true -> InputType.TYPE_CLASS_TEXT
                false -> InputType.TYPE_NULL
            }
            descriptionEditText.isVisible =
                userComment != null || isSupportedFormatForSavingAttributes

            artistInfoListItem.headlineText = exifInterface.artist ?: unknownString

            artistInfoListItem.supportingText = listOfNotNull(
                exifInterface.software,
                exifInterface.copyright,
            ).joinToString(SEPARATOR)

            artistInfoListItem.isVisible = listOf(
                artistInfoListItem.headlineText,
                artistInfoListItem.supportingText,
            ).any { !it.isNullOrBlank() && it != unknownString }

            cameraInfoListItem.headlineText = listOfNotNull(
                exifInterface.make,
                exifInterface.model,
            ).joinToString(" ").takeIf { it.isNotBlank() } ?: unknownString

            cameraInfoListItem.supportingText = listOfNotNull(
                exifInterface.exposureTime?.let { "${it.toFraction()}s" },
                exifInterface.apertureValue?.let { "ƒ/${it.round(2)}" },
                exifInterface.isoSpeed?.let { "ISO $it" },
                exifInterface.focalLength?.let { "${it.round(2)}mm" },
            ).joinToString(SEPARATOR)

            cameraInfoListItem.isVisible = listOf(
                cameraInfoListItem.headlineText,
                cameraInfoListItem.supportingText,
            ).any { !it.isNullOrBlank() && it != unknownString }

            mediaInfoListItem.supportingText = listOf(
                media.mimeType,
                "${((media.width.toDouble() * media.height) / 1024000).round(1)}MP",
                "${media.width} x ${media.height}",
            ).joinToString(SEPARATOR)

            exifInterface.latLong?.let {
                val (lat, long) = it

                locationInfoListItem.setOnClickListener {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:?q=%.8f,%.8f".format(Locale.US, lat, long))
                    )

                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            context.resources.getString(R.string.media_info_location_open_with)
                        )
                    )
                }

                val latLongString = listOf(
                    lat.round(8),
                    long.round(8),
                ).joinToString(SEPARATOR)

                if (Geocoder.isPresent()) {
                    locationInfoListItem.headlineText = context.resources.getString(
                        R.string.media_info_location_loading_placeholder
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(lat, long, 1, ::updateLocation)
                    } else {
                        ioScope.launch {
                            @Suppress("DEPRECATION")
                            updateLocation(
                                geocoder.getFromLocation(lat, long, 1) ?: listOf()
                            )
                        }
                    }
                    locationInfoListItem.supportingText = latLongString
                } else {
                    locationInfoListItem.headlineText = latLongString
                }

                locationInfoListItem.isVisible = true
            }
        }
    }

    private fun updateLocation(addresses: List<Address>) {
        mainScope.launch {
            locationInfoListItem.headlineText = addresses.getOrNull(0)?.let { address ->
                address.getAddressLine(0) ?: listOfNotNull(
                    listOfNotNull(
                        address.featureName,
                        address.thoroughfare,
                    ).takeIf { it.isNotEmpty() }?.joinToString(" "),
                    address.locality,
                    listOfNotNull(
                        address.postalCode,
                        address.subAdminArea,
                    ).takeIf { it.isNotEmpty() }?.joinToString(" "),
                    address.adminArea,
                    address.countryName,
                ).joinToString(", ").takeIf { it.isNotBlank() }
            } ?: unknownString
        }
    }

    class Callbacks(private val activity: AppCompatActivity) {
        private lateinit var editDescriptionMedia: Media
        private lateinit var editDescriptionDescription: String

        private val editDescriptionCallback = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) {
                editDescription(editDescriptionMedia, editDescriptionDescription)
            }
        }

        fun onEditDescription(media: Media, description: String = "") {
            editDescriptionMedia = media
            editDescriptionDescription = description

            val contentResolver = activity.contentResolver

            editDescriptionCallback.launch(
                contentResolver.createWriteRequest(media.uri)
            )
        }

        private fun editDescription(media: Media, description: String) {
            val contentResolver = activity.contentResolver

            contentResolver.openFileDescriptor(
                media.uri, "rw"
            )?.use { assetFileDescriptor ->
                val exifInterface = ExifInterface(assetFileDescriptor.fileDescriptor)

                exifInterface.userComment = description

                runCatching {
                    exifInterface.saveAttributes()
                }.onFailure {
                    Toast.makeText(
                        activity,
                        R.string.media_info_write_description_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val SEPARATOR = " • "

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()
    }
}
