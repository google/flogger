/*
 * Copyright (C) 2018 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger.backend;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.F_SAME1;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Class that generates a PlatformProvider class for creating instances of Platform, for use in
 * Platform discovery.
 *
 * <p>This generator is necessary in order to create a class that explicitly references the actual
 * platform implementation classes without having them visible as a build dependency. If the code
 * were compiled with javac that wouldn't work, because javac needs to observe the classes on its
 * classpath. By resorting to manually generating the class file, we can work around the limitation,
 * and avoid the dependency on platform implementations while still keeping an explicit reference to
 * the classes. The advantage of this approach is that tools that operate on bytecode (e.g.
 * proguard) observe the dependency correctly, which is not the case when reflection is used to look
 * up classes.
 */
public final class PlatformProviderGenerator {
  private static final String[] PLATFORM_CLASSES =
      new String[] {
        "Lcom/google/common/flogger/backend/system/DefaultPlatform;",
      };

  public static void main(String[] args) throws IOException {
    // Create the class.
    ClassWriter classWriter = new ClassWriter(0);
    classWriter.visit(
        V1_6,
        ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
        "com/google/common/flogger/backend/PlatformProvider",
        null,
        "java/lang/Object",
        null);

    // Create the no-op constructor.
    MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(ALOAD, 0);
    methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    methodVisitor.visitInsn(RETURN);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();

    // Create the static getter method.
    methodVisitor =
        classWriter.visitMethod(
            ACC_PUBLIC + ACC_STATIC,
            "getPlatform",
            "()Lcom/google/common/flogger/backend/Platform;",
            null,
            null);
    // Try the different platforms.
    for (String platformClass : PLATFORM_CLASSES) {
      tryBlockForPlatform(methodVisitor, platformClass);
    }
    // Return null if no platform is found.
    methodVisitor.visitInsn(ACONST_NULL);
    methodVisitor.visitInsn(ARETURN);
    methodVisitor.visitMaxs(2, 1);
    methodVisitor.visitEnd();

    // Finish creating the class.
    classWriter.visitEnd();

    // Write the class to the output file.
    Path path = Paths.get(args[0]);
    Files.createDirectories(path.getParent());
    try (JarOutputStream jar =
        new JarOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
      ZipEntry entry = new ZipEntry("com/google/common/flogger/backend/PlatformProvider.class");
      entry.setTime(0); // clear timestamp to ensure JAR is deterministic for cache during builds.
      jar.putNextEntry(entry);
      jar.write(classWriter.toByteArray());
      jar.closeEntry();
    }
  }

  private static void tryBlockForPlatform(MethodVisitor methodVisitor, String platformType) {
    methodVisitor.visitCode();

    // Generate the enveloping try/catch block:
    //
    //   try {
    //     ...
    //   } catch (NoClassDefFoundError | IllegalAccessException | InstantiationException
    //       | InvocationTargetException | NoSuchMethodException e) {
    //     ...
    //   }
    //
    // Note that the exception types need to be listed explicitly (rather than using
    // java.lang.ReflectiveOperationException) because that parent exception type isn't available
    // on Android until API level 19.
    Label startLabel = new Label();
    Label endLabel = new Label();
    Label handlerLabel = new Label();
    methodVisitor.visitTryCatchBlock(
        startLabel, endLabel, handlerLabel, "java/lang/NoClassDefFoundError");
    methodVisitor.visitTryCatchBlock(
        startLabel, endLabel, handlerLabel, "java/lang/IllegalAccessException");
    methodVisitor.visitTryCatchBlock(
        startLabel, endLabel, handlerLabel, "java/lang/InstantiationException");
    methodVisitor.visitTryCatchBlock(
        startLabel, endLabel, handlerLabel, "java/lang/reflect/InvocationTargetException");
    methodVisitor.visitTryCatchBlock(
        startLabel, endLabel, handlerLabel, "java/lang/NoSuchMethodException");
    methodVisitor.visitLabel(startLabel);

    // Generate the actual reflective constructor call inside the try block:
    //
    //   return (Platform) PlatformClass.class.getDeclaredConstructor().newInstance();
    //
    // Note that the constructor call happens reflectively to make sure that the platform class
    // isn't loaded until actually executing this instruction. That is important because an
    // earlier class load could happen outside of the try/catch block where we are explicitly
    // handling the case of the class not being present.
    methodVisitor.visitLdcInsn(Type.getType(platformType));
    methodVisitor.visitInsn(ICONST_0);
    methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Class");
    methodVisitor.visitMethodInsn(
        INVOKEVIRTUAL,
        "java/lang/Class",
        "getDeclaredConstructor",
        "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;",
        false);
    methodVisitor.visitInsn(ICONST_0);
    methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
    methodVisitor.visitMethodInsn(
        INVOKEVIRTUAL,
        "java/lang/reflect/Constructor",
        "newInstance",
        "([Ljava/lang/Object;)Ljava/lang/Object;",
        false);
    methodVisitor.visitTypeInsn(CHECKCAST, "com/google/common/flogger/backend/Platform");
    methodVisitor.visitLabel(endLabel);
    methodVisitor.visitInsn(ARETURN);

    // Generate the catch block of the overall try/catch. The catch block is actually just empty,
    // but Java does require the catch handler to have at least a frame in it to declare the
    // exception variable that is available within the catch block scope:
    // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-3.html#jvms-3.12
    methodVisitor.visitLabel(handlerLabel);
    methodVisitor.visitFrame(F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
    methodVisitor.visitVarInsn(ASTORE, 0);
  }
}
