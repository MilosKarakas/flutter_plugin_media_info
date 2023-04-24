package asia.ivity.mediainfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.media.MediaMetadataRetriever;
import androidx.annotation.NonNull;
import asia.ivity.mediainfo.util.OutputSurface;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java9.util.concurrent.CompletableFuture;

public class MediaInfoPlugin implements MethodCallHandler, FlutterPlugin {

  private static final String TAG = "MediaInfoPlugin";

  private static final String NAMESPACE = "asia.ivity.flutter";

  private Context applicationContext;
  private MethodChannel methodChannel;

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
  }

  private void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
    this.applicationContext = applicationContext;
    methodChannel = new MethodChannel(messenger, NAMESPACE + "/media_info");
    methodChannel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    applicationContext = null;
    methodChannel.setMethodCallHandler(null);
    methodChannel = null;
  }

  private ThreadPoolExecutor executorService;

  private Handler mainThreadHandler;

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (executorService == null) {
      executorService =
              (ThreadPoolExecutor)
                      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    if (mainThreadHandler == null) {
      mainThreadHandler = new Handler(Looper.myLooper());
    }

    if (call.method.equalsIgnoreCase("getMediaInfo")) {
      String path = (String) call.arguments;

      handleMediaInfo(applicationContext, path, result);
    }
  }

  private void handleMediaInfo(Context context, String path, Result result) {
    final CompletableFuture<MediaDetail> future = new CompletableFuture<>();
    executorService.execute(
            () -> {
              try {
                mainThreadHandler.post(() -> handleMediaInfoExoPlayer(context, path, future));
              } catch (RuntimeException e) {
                mainThreadHandler.post(() -> result.error("MediaInfo", e.getMessage(), null));
              }

              try {
                MediaDetail info = future.get();
                mainThreadHandler.post(
                        () -> {
                          if (info != null) {
                            result.success(info.toMap());
                          } else {
                            result.error("MediaInfo", "InvalidFile", null);
                          }
                        });

              } catch (InterruptedException e) {
                mainThreadHandler.post(() -> result.error("MediaInfo", e.getMessage(), null));
              } catch (ExecutionException e) {
                mainThreadHandler.post(
                        () -> result.error("MediaInfo", e.getCause().getMessage(), null));
              } catch (RuntimeException e) {
                mainThreadHandler.post(() -> result.error("MediaInfo", e.getMessage(), null));
              }
            });
  }

  private void handleMediaInfoExoPlayer(
          Context context, String path, CompletableFuture<MediaDetail> future) {

    try {
      MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

      mediaMetadataRetriever.setDataSource(path);
      String durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

      AudioDetail audio =
              new AudioDetail(Long.parseLong(durationStr), 64000, "audio/aac");
      future.complete(audio);
    } catch (RuntimeException e) {
      return;
    }

    return;
  }
}
