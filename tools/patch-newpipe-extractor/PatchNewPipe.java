import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Rewrites NewPipeExtractor v0.27.1 so it runs on Android 8 through 12.
 *
 * NewPipeExtractor is compiled against JDK 11 and calls a handful of Java APIs
 * that Android only added in API 33/34. D8 backports most of them, but not
 * these, so they throw NoSuchMethodError at runtime and crash playback:
 *
 *   - Collectors.toUnmodifiableList()  -> Collectors.toList()      (API 33)
 *   - URLEncoder.encode(String, Charset) -> encode(String, String) (API 33)
 *   - URLDecoder.decode(String, Charset) -> decode(String, String) (API 33)
 *   - Stream.toList() -> stream.collect(Collectors.toList())       (API 34)
 *
 * Usage: java -cp asm.jar:. PatchNewPipe <in.jar> <out.jar>
 */
public final class PatchNewPipe {

    public static void main(String[] args) throws Exception {
        File input = new File(args[0]);
        File output = new File(args[1]);
        output.getParentFile().mkdirs();
        int patched = 0;
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(output))) {
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int read;
                while ((read = zin.read(buffer)) > 0) bytes.write(buffer, 0, read);
                byte[] data = bytes.toByteArray();
                if (entry.getName().endsWith(".class")) {
                    byte[] patchedData = patch(data);
                    if (patchedData != data) patched++;
                    data = patchedData;
                }
                ZipEntry out = new ZipEntry(entry.getName());
                out.setTime(entry.getTime());
                zout.putNextEntry(out);
                zout.write(data);
                zout.closeEntry();
            }
        }
        System.out.println("Patched " + patched + " classes -> " + output);
    }

    private static byte[] patch(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, next) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String descriptor, boolean isInterface) {
                        if (!isInterface
                                && owner.equals("java/util/stream/Collectors")
                                && method.equals("toUnmodifiableList")) {
                            super.visitMethodInsn(opcode, owner, "toList", descriptor, false);
                            return;
                        }
                        if (owner.equals("java/net/URLEncoder") && method.equals("encode")
                                && descriptor.equals("(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;")) {
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/charset/Charset",
                                    "name", "()Ljava/lang/String;", false);
                            super.visitMethodInsn(opcode, owner, method,
                                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                            return;
                        }
                        if (owner.equals("java/net/URLDecoder") && method.equals("decode")
                                && descriptor.equals("(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;")) {
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/charset/Charset",
                                    "name", "()Ljava/lang/String;", false);
                            super.visitMethodInsn(opcode, owner, method,
                                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                            return;
                        }
                        if ((opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)
                                && owner.equals("java/util/stream/Stream")
                                && method.equals("toList")
                                && descriptor.equals("()Ljava/util/List;")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/stream/Collectors",
                                    "toList", "()Ljava/util/stream/Collector;", false);
                            super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/stream/Stream",
                                    "collect", "(Ljava/util/stream/Collector;)Ljava/lang/Object;", true);
                            super.visitTypeInsn(Opcodes.CHECKCAST, "java/util/List");
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
}
