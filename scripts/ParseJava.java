import com.sun.source.util.JavacTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Syntax-only check: deliberately does not claim Android API/type resolution. */
public final class ParseJava {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK compiler module is required");
        List<String> files = new ArrayList<>();
        for (String directory : new String[]{"app/src/main/java", "camera-core/src"}) {
            try (var paths = Files.walk(Path.of(directory))) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> files.add(path.toString()));
            }
        }
        if (files.isEmpty()) throw new IllegalStateException("No Java source files");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "17", "-proc:none"), null,
                    manager.getJavaFileObjectsFromStrings(files));
            task.parse();
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IllegalStateException(diagnostic.toString());
                }
            }
        }
        System.out.println("PASS Java syntax: " + files.size()
                + " files (Android types and dependencies not resolved)");
    }
}
