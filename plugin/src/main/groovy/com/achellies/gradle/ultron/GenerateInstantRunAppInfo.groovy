package com.achellies.gradle.ultron;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable
import com.android.build.gradle.internal.incremental.ByteCodeUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

/**
 * Created by achellies on 16/10/19.
 */

public class GenerateInstantRunAppInfo {

    static byte[] generateAppInfoClass(
            @NonNull String applicationId,
            @Nullable String applicationClass,
            long token)
            throws IOException {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;

        String appInfoOwner = "com/android/tools/fd/runtime/AppInfo";
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, appInfoOwner, null, "java/lang/Object", null);

        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationId", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationClass", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "token", "J", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "usingApkSplits", "Z", null, null);
        fv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, ByteCodeUtils.CONSTRUCTOR, "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", ByteCodeUtils.CONSTRUCTOR, "()V", false);
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "L" + appInfoOwner + ";", null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_STATIC, ByteCodeUtils.CLASS_INITIALIZER, "()V", null, null);
        mv.visitCode();
        if (applicationId != null) {
            mv.visitLdcInsn(applicationId);
        } else {
            mv.visitInsn(ACONST_NULL);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationId", "Ljava/lang/String;");
        if (applicationClass != null) {
            mv.visitLdcInsn(applicationClass);
        } else {
            mv.visitInsn(ACONST_NULL);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationClass", "Ljava/lang/String;");
        if (token != 0L) {
            mv.visitLdcInsn(token);
        } else {
            mv.visitInsn(LCONST_0);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "token", "J");
//        if (isUsingMultiApks()) {
//            mv.visitInsn(ICONST_1);
//        } else {
        mv.visitInsn(ICONST_0);
//        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "usingApkSplits", "Z");

        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        return bytes;
    }
}
