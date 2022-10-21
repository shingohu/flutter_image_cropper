package vn.hunghd.flutter.plugins.imagecropper;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;
import com.yalantis.ucrop.model.AspectRatio;
import com.yalantis.ucrop.view.CropImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

import static android.app.Activity.RESULT_OK;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class ImageCropperDelegate implements PluginRegistry.ActivityResultListener {
    static final String FILENAME_CACHE_KEY = "imagecropper.FILENAME_CACHE_KEY";

    private final Activity activity;
    private final SharedPreferences preferences;
    private MethodChannel.Result pendingResult;
    private FileUtils fileUtils;

    public ImageCropperDelegate(Activity activity) {
        this.activity = activity;
        preferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
        fileUtils = new FileUtils();
    }

    public void startCrop(MethodCall call, MethodChannel.Result result) {
        if (activity == null) {
            return;
        }
        String sourcePath = call.argument("source_path");
        Integer maxWidth = call.argument("max_width");
        Integer maxHeight = call.argument("max_height");
        Double ratioX = call.argument("ratio_x");
        Double ratioY = call.argument("ratio_y");
        String cropStyle = call.argument("crop_style");
        String compressFormat = call.argument("compress_format");
        Integer compressQuality = call.argument("compress_quality");
        ArrayList<String> aspectRatioPresets = call.argument("aspect_ratio_presets");
        String initAspectRatio = call.argument("android.init_aspect_ratio");
        final boolean lightStatusBars = Boolean.TRUE.equals(call.argument("light_status_bar"));

        pendingResult = result;

        File outputDir = activity.getCacheDir();
        File outputFile;
        if ("png".equals(compressFormat)) {
            outputFile = new File(outputDir, "image_cropper_" + (new Date()).getTime() + ".png");
        } else {
            outputFile = new File(outputDir, "image_cropper_" + (new Date()).getTime() + ".jpg");
        }
        Uri sourceUri = Uri.fromFile(new File(sourcePath));
        Uri destinationUri = Uri.fromFile(outputFile);

        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat("png".equals(compressFormat) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(compressQuality != null ? compressQuality : 90);

        // UI customization settings
        if ("circle".equals(cropStyle)) {
            options.setCircleDimmedLayer(true);
        }
        setupUiCustomizedOptions(options, call);

        if (aspectRatioPresets != null) {
            ArrayList<AspectRatio> aspectRatioList = new ArrayList<>();
            int defaultIndex = 0;
            for (int i = 0; i < aspectRatioPresets.size(); i++) {
                String preset = aspectRatioPresets.get(i);
                if (preset != null) {
                    AspectRatio aspectRatio = parseAspectRatioName(preset);
                    aspectRatioList.add(aspectRatio);
                    if (preset.equals(initAspectRatio)) {
                        defaultIndex = i;
                    }
                }
            }
            options.setAspectRatioOptions(defaultIndex, aspectRatioList.toArray(new AspectRatio[]{}));
        }

        UCrop cropper = UCrop.of(sourceUri, destinationUri).withOptions(options);
        if (maxWidth != null && maxHeight != null) {
            cropper.withMaxResultSize(maxWidth, maxHeight);
        }
        if (ratioX != null && ratioY != null) {
            cropper.withAspectRatio(ratioX.floatValue(), ratioY.floatValue());
        }

        activityLifecycleCallbacks = new MyActivityLifecycleCallbacks(){
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (activity instanceof UCropActivity) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        new WindowInsetsControllerCompat(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightStatusBars(lightStatusBars);
                    }
                }
            }
        };

        activity.getApplication().registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
        activity.startActivityForResult(cropper.getIntent(activity), UCrop.REQUEST_CROP);
    }

    private MyActivityLifecycleCallbacks activityLifecycleCallbacks;


    private static class MyActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {

        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {

        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {

        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {

        }
    }


    public void recoverImage(MethodCall call, MethodChannel.Result result) {
        result.success(getAndClearCachedImage());
    }

    private void cacheImage(String filePath) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(FILENAME_CACHE_KEY, filePath);
        editor.apply();
    }

    private String getAndClearCachedImage() {
        if (preferences.contains(FILENAME_CACHE_KEY)) {
            String result = preferences.getString(FILENAME_CACHE_KEY, "");
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove(FILENAME_CACHE_KEY);
            editor.apply();
            return result;
        }
        return null;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UCrop.REQUEST_CROP) {
            if(activityLifecycleCallbacks!=null&&activity!=null) {
                activity.getApplication().unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
            }
            if (resultCode == RESULT_OK) {
                final Uri resultUri = UCrop.getOutput(data);
                final String imagePath = fileUtils.getPathFromUri(activity, resultUri);
                cacheImage(imagePath);
                finishWithSuccess(imagePath);
                return true;
            } else if (resultCode == UCrop.RESULT_ERROR) {
                final Throwable cropError = UCrop.getError(data);
                finishWithError("crop_error", cropError.getLocalizedMessage(), cropError);
                return true;
            } else if (pendingResult != null) {
                pendingResult.success(null);
                clearMethodCallAndResult();
                return true;
            }
        }
        return false;
    }

    private void finishWithSuccess(String imagePath) {
        if (pendingResult != null) {
            pendingResult.success(imagePath);
            clearMethodCallAndResult();
        }
    }

    private void finishWithError(String errorCode, String errorMessage, Throwable throwable) {
        if (pendingResult != null) {
            pendingResult.error(errorCode, errorMessage, throwable);
            clearMethodCallAndResult();
        }
    }

    private UCrop.Options setupUiCustomizedOptions(UCrop.Options options, MethodCall call) {
        String title = call.argument("android.toolbar_title");
        Integer toolbarColor = call.argument("android.toolbar_color");
        Integer statusBarColor = call.argument("android.statusbar_color");
        Integer toolbarWidgetColor = call.argument("android.toolbar_widget_color");
        Integer backgroundColor = call.argument("android.background_color");
        Integer activeControlsWidgetColor = call.argument("android.active_controls_widget_color");
        Integer dimmedLayerColor = call.argument("android.dimmed_layer_color");
        Integer cropFrameColor = call.argument("android.crop_frame_color");
        Integer cropGridColor = call.argument("android.crop_grid_color");
        Integer cropFrameStrokeWidth = call.argument("android.crop_frame_stroke_width");
        Integer cropGridRowCount = call.argument("android.crop_grid_row_count");
        Integer cropGridColumnCount = call.argument("android.crop_grid_column_count");
        Integer cropGridStrokeWidth = call.argument("android.crop_grid_stroke_width");
        Boolean showCropGrid = call.argument("android.show_crop_grid");
        Boolean lockAspectRatio = call.argument("android.lock_aspect_ratio");
        Boolean hideBottomControls = call.argument("android.hide_bottom_controls");

        if (title != null) {
            options.setToolbarTitle(title);
        }
        if (toolbarColor != null) {
            options.setToolbarColor(toolbarColor);
        }
        if (statusBarColor != null) {
            options.setStatusBarColor(statusBarColor);
        } else if (toolbarColor != null) {
            options.setStatusBarColor(darkenColor(toolbarColor));
        }
        if (toolbarWidgetColor != null) {
            options.setToolbarWidgetColor(toolbarWidgetColor);
        }
        if (backgroundColor != null) {
            options.setRootViewBackgroundColor(backgroundColor);
        }
        if (activeControlsWidgetColor != null) {
            options.setActiveControlsWidgetColor(activeControlsWidgetColor);
        }
        if (dimmedLayerColor != null) {
            options.setDimmedLayerColor(dimmedLayerColor);
        }
        if (cropFrameColor != null) {
            options.setCropFrameColor(cropFrameColor);
        }
        if (cropGridColor != null) {
            options.setCropGridColor(cropGridColor);
        }
        if (cropFrameStrokeWidth != null) {
            options.setCropFrameStrokeWidth(cropFrameStrokeWidth);
        }
        if (cropGridRowCount != null) {
            options.setCropGridRowCount(cropGridRowCount);
        }
        if (cropGridColumnCount != null) {
            options.setCropGridColumnCount(cropGridColumnCount);
        }
        if (cropGridStrokeWidth != null) {
            options.setCropGridStrokeWidth(cropGridStrokeWidth);
        }
        if (showCropGrid != null) {
            options.setShowCropGrid(showCropGrid);
        }
        if (lockAspectRatio != null) {
            options.setFreeStyleCropEnabled(!lockAspectRatio);
        }
        if (hideBottomControls != null) {
            options.setHideBottomControls(hideBottomControls);
        }

        return options;
    }


    private void clearMethodCallAndResult() {
        pendingResult = null;
    }

    private int darkenColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f;
        return Color.HSVToColor(hsv);
    }

    private AspectRatio parseAspectRatioName(String name) {
        if ("square".equals(name)) {
            return new AspectRatio(null, 1.0f, 1.0f);
        } else if ("original".equals(name)) {
            return new AspectRatio(activity.getString(R.string.ucrop_label_original).toUpperCase(),
                    CropImageView.SOURCE_IMAGE_ASPECT_RATIO, 1.0f);
        } else if ("3x2".equals(name)) {
            return new AspectRatio(null, 3.0f, 2.0f);
        } else if ("4x3".equals(name)) {
            return new AspectRatio(null, 4.0f, 3.0f);
        } else if ("5x3".equals(name)) {
            return new AspectRatio(null, 5.0f, 3.0f);
        } else if ("5x4".equals(name)) {
            return new AspectRatio(null, 5.0f, 4.0f);
        } else if ("7x5".equals(name)) {
            return new AspectRatio(null, 7.0f, 5.0f);
        } else if ("16x9".equals(name)) {
            return new AspectRatio(null, 16.0f, 9.0f);
        } else {
            return new AspectRatio(activity.getString(R.string.ucrop_label_original).toUpperCase(),
                    CropImageView.SOURCE_IMAGE_ASPECT_RATIO, 1.0f);
        }
    }
}
