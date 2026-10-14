package com.sturgeon.xflipcamera;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.sturgeon.xflipcamera.core.PhotoInspectionState;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gallery share target; does not open a camera or modify the original JPEG. */
public final class PhotoInspectionActivity extends ComponentActivity {
    private static final String STATE_SOURCE = "inspection.source";
    private static final String STATE_REPORT = "inspection.report";
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) inspect(uri);
            });
    private TextView result;
    private Button copy;
    private PhotoInspectionState inspection = new PhotoInspectionState(null, null);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            inspection = new PhotoInspectionState(savedInstanceState.getString(STATE_SOURCE),
                    savedInstanceState.getString(STATE_REPORT));
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left + 8, bars.top + 8, bars.right + 8, bars.bottom + 8);
            return insets;
        });
        Button choose = new Button(this);
        choose.setText("选择 JPEG 检查像素");
        choose.setOnClickListener(view -> picker.launch(new String[]{"image/jpeg"}));
        content.addView(choose);
        copy = new Button(this);
        copy.setText("复制检查结果");
        copy.setEnabled(false);
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            String report = inspection.report();
            if (clipboard != null && report != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("JPEG 像素检查", report));
            }
        });
        content.addView(copy);
        result = new TextView(this);
        result.setTextSize(13);
        result.setTextIsSelectable(true);
        result.setText("从图库分享 JPEG 到“照片像素检查”，或选择一张 JPEG。\n"
                + "仅在本机读取采样像素，不上传或修改原图。结果不证明镜像方向正确。");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(result);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(content);
        content.requestApplyInsets();
        if (inspection.report() != null) {
            showReport();
        } else if (inspection.selectedSource() != null) {
            // Resume the latest picker/share selection, not the original launch intent.
            // A restored URI can have lost its grant; normal read failures remain explicit.
            inspect(Uri.parse(inspection.selectedSource()));
        } else {
            inspectIntent(getIntent());
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        inspectIntent(intent);
    }

    private void inspectIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            if (uri != null) inspect(uri);
        }
    }

    private void inspect(Uri uri) {
        long token = inspection.begin(uri.toString());
        copy.setEnabled(false);
        result.setText("正在读取 JPEG 采样像素…");
        reader.execute(() -> {
            String text;
            try {
                text = JpegPixelInspector.inspect(getContentResolver(), uri);
            } catch (Exception failure) {
                text = "检查未完成：" + failure.getClass().getSimpleName()
                        + "\n请选择可读取的 JPEG；解码失败不能计为全黑或验收通过。";
            }
            final String completed = text;
            runOnUiThread(() -> {
                if (isDestroyed() || !inspection.complete(token, completed)) return;
                showReport();
            });
        });
    }

    private void showReport() {
        result.setText(inspection.report());
        copy.setEnabled(true);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SOURCE, inspection.selectedSource());
        outState.putString(STATE_REPORT, inspection.report());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        inspection.cancel();
        reader.shutdownNow();
        super.onDestroy();
    }
}
