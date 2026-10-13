import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Looper;
import android.os.Process;
import java.util.Arrays;

/** Uses the installed scrcpy server's shell context; no system identity spoofing. */
public final class ShellCameraProbe {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class.forName("com.genymobile.scrcpy.Workarounds").getMethod("apply").invoke(null);
        Context context = (Context) Class.forName("com.genymobile.scrcpy.FakeContext")
                .getMethod("get").invoke(null);
        CameraManager manager = context.getSystemService(CameraManager.class);
        System.out.println("uid=" + Process.myUid() + ", package=" + context.getOpPackageName());
        System.out.println("public_ids=" + Arrays.toString(manager.getCameraIdList()));
        for (String id : new String[]{"0", "1", "4"}) {
            try {
                CameraCharacteristics info = manager.getCameraCharacteristics(id);
                System.out.println("id=" + id + ", facing=" + info.get(CameraCharacteristics.LENS_FACING)
                        + ", rotation=" + info.get(CameraCharacteristics.SENSOR_ORIENTATION)
                        + ", capabilities=" + Arrays.toString(info.get(
                                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)));
            } catch (Exception failure) {
                System.out.println("id=" + id + ", " + failure.getClass().getSimpleName()
                        + ": " + failure.getMessage());
            }
        }
        System.exit(0);
    }
}
