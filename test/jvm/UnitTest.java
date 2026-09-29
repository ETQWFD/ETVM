import com.et.vm.RomDetect;
import com.et.vm.License;

public class UnitTest {
    static int pass = 0, fail = 0;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (ok) pass++; else fail++;
    }
    public static void main(String[] a) {
        String s = "test/samples/";
        RomDetect.Result r = RomDetect.detect(s + "android-rom-arm64pure.zip", "arm64pure.zip", 0);
        check("arm64pure:ok=" + r.ok + " bits=" + r.bits, r.ok && "64".equals(r.bits));
        r = RomDetect.detect(s + "android-rom-arm32.zip", "arm32.zip", 0);
        check("arm32:ok=" + r.ok + " bits=" + r.bits, r.ok && "32".equals(r.bits));
        r = RomDetect.detect(s + "windows11.iso", "windows11.iso", 0);
        check("windows11:ok=" + r.ok + " type=" + r.type, !r.ok || "windows".equals(r.type));
        r = RomDetect.detect(s + "windows11_pkg.zip", "windows11_pkg.zip", 0);
        check("windows11_pkg:ok=" + r.ok, !r.ok || "windows".equals(r.type));

        RomDetect.ApkCompat rc = RomDetect.checkApk(s + "app64.apk", "64");
        check("app64:ok=" + rc.ok + " bits=" + rc.bits, rc.ok && ("64".equals(rc.bits) || "both".equals(rc.bits)));
        rc = RomDetect.checkApk(s + "app32.apk", "32");
        check("app32:ok=" + rc.ok + " bits=" + rc.bits, rc.ok && ("32".equals(rc.bits) || "both".equals(rc.bits)));
        rc = RomDetect.checkApk(s + "appmixed.apk", "32");
        check("appmixed:bits=" + rc.bits, rc.ok && ("both".equals(rc.bits) || "32".equals(rc.bits)));
        rc = RomDetect.checkApk(s + "appjava.apk", "32");
        check("appjava:bits=" + rc.bits, rc.ok && "both".equals(rc.bits));

        String k = License.licenseKey("4a7eee440f0a87274aff7ddbf690d9c8f5422826e71b0234a18727c0950d6a6b", "deadbeef");
        check("license:format=" + k.matches("ET-\\w{4}-\\w{4}-\\w{4}-\\w{4}") + " key=" + k, k.matches("ET-\\w{4}-\\w{4}-\\w{4}-\\w{4}"));

        r = RomDetect.detect("assets/etos/etos-7.0-x86.zip", "etos-7.0-x86.zip", 0);
        check("etos-builtin:ok=" + r.ok + " bits=" + r.bits + " type=" + r.type, r.ok && "32".equals(r.bits) && r.type.contains("Android"));
        System.out.println("RESULT pass=" + pass + " fail=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}
