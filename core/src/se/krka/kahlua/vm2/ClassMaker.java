/*
 Copyright (c) 2023 Kristofer Karlsson <kristofer.karlsson@gmail.com>

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

package se.krka.kahlua.vm2;


import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.*;
import static se.krka.kahlua.vm2.KahluaThread2.*;

public class ClassMaker {

  private ClassWriter cw;
  private FieldVisitor fv;
  private MethodVisitor mv;
  private AnnotationVisitor av0;
  private Class clazz;

  final String className;
  final String classPath;
  final Class  superClass = LuaScript.class;
  final String superClassName = toClassPath(superClass.getName());

  /**
   * @param className '.' split class path
   */
  public ClassMaker(String className) {
    this.className = className;
    this.classPath = toClassPath(className);

    cw = new ClassWriter(COMPUTE_FRAMES);
    cw.visit(52,
      ACC_PUBLIC + Opcodes.ACC_SUPER,
      classPath,
      null,
      superClassName,
      null);

    cw.visitSource(classPath +".lua", null);
  }

  MethodVisitor beginMethod(String mname) {
    if (mv != null) throw new RuntimeException();
    mv = cw.visitMethod(ACC_PUBLIC, mname, "()V", null, null);
    mv.visitCode();
    return mv;
  }

  void endMethod() {
    mv.visitInsn(RETURN);
    mv.visitMaxs(10, 10);
    //mv.visitMaxs(0 ,0);
    mv.visitEnd();
    mv = null;
  }

  public Class genClass() {
    if (clazz == null) {
      cw.visitEnd();
      byte[] buf = cw.toByteArray();
      clazz = LuaClassLoader.instance.defineClass(className, buf);
      cw = null;

      if (true) try {
        FileOutputStream w = new FileOutputStream("./bin/temp-test.class");
        w.write(buf);
        w.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return clazz;
  }

  public LuaScript newInstance() throws IllegalAccessException,
    InstantiationException, NoSuchMethodException,
    InvocationTargetException {
    Class c = genClass();
    return (LuaScript) c.getDeclaredConstructor().newInstance();
  }

  public void defaultInit() {
    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();

    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    mv = null;
  }

  // Execution of methods in members
  void vInvokeFieldFunc(String member, String method, Class ...param) {
    Field f = getField(member);
    Class t = f.getType();
    Method m = getMethod(t, method, param);

    mv.visitMethodInsn(INVOKEVIRTUAL, toClassPath(t),
      m.getName(), getMethodSi(m), false);
  }

  void vInvokeFunc(Class owner, String method, Class ...param) {
    Method m = getMethod(owner, method, param);

    mv.visitMethodInsn(INVOKEVIRTUAL, toClassPath(owner),
      m.getName(), getMethodSi(m), false);
  }

  void vInvokeStatic(Class owner, String method, Class ...param) {
    Method m = getMethod(owner, method, param);

    mv.visitMethodInsn(INVOKESTATIC, toClassPath(owner),
      m.getName(), getMethodSi(m), false);
  }

  void vInt(int a) {
    mv.visitLdcInsn(a);
  }

  void vDouble(double a) {
    mv.visitLdcInsn(a);
  }

  void vString(String s) {
    mv.visitLdcInsn(s);
  }

  void vBoolean(boolean b) {
    mv.visitLdcInsn(b);
  }

  void vThis() {
    // Zero is This
    mv.visitVarInsn(ALOAD, 0);
  }

  void vIsof(Class s, Label whenIsinstanceof) {
    mv.visitTypeInsn(INSTANCEOF, toClassPath(s.getName()));
    mv.visitJumpInsn(IFNE, whenIsinstanceof);
  }

  void vCast(Class s) {
    mv.visitTypeInsn(CHECKCAST, toClassPath(s.getName()));
  }

  void vField(String fname) {
    vThis();
    vField(superClass, fname);
  }

  void vField(Class owner, String fname) {
    String oclass = toClassPath(owner.getName());

    Field f = getField(owner, fname);
    StringBuilder desc = new StringBuilder();
    typeName(desc, f.getType());

    mv.visitFieldInsn(GETFIELD, oclass, f.getName(), desc.toString());
  }

  void vStatic(Field f) {
    Class c = f.getDeclaringClass();
    String oclass = toClassPath(c.getName());
    String v = f.getName();

    StringBuilder desc = new StringBuilder();
    typeName(desc, f.getType());

    mv.visitFieldInsn(GETSTATIC, oclass, v, desc.toString());
  }

  void vStatic(Method m) {
    Class c = m.getDeclaringClass();
    String oclass = toClassPath(c.getName());
    String v = m.getName();

    StringBuilder desc = new StringBuilder();
    typeName(desc, c);

    mv.visitFieldInsn(GETSTATIC, oclass, v, desc.toString());
  }

  void vGoto(Label l) {
    mv.visitJumpInsn(GOTO, l);
  }

  void vLabel(Label l, int line) {
    mv.visitLabel(l);
    mv.visitLineNumber(line, l);
  }

  void vThrow(String errMsg) {
    mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
    mv.visitInsn(DUP);
    mv.visitLdcInsn(errMsg);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException",
      "<init>", "(Ljava/lang/String;)V", false);
    mv.visitInsn(ATHROW);
  }

  // Get field from Super Class
  Field getField(String n) {
    return getField(superClass, n);
  }

  Field getField(Class c, String n) {
    try {
      return c.getDeclaredField(n);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  Method getMethod(Class c, String m, Class ...param) {
    try {
      return c.getDeclaredMethod(m, param);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
