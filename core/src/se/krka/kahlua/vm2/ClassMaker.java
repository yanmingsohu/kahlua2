/*
 Copyright (c) 2023 Yanming <yanmingsohu@gmail.com>

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
import se.krka.kahlua.vm.*;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.*;
import static se.krka.kahlua.vm2.ClassMaker.IConst.*;
import static se.krka.kahlua.vm2.Tool.*;

public class ClassMaker {

  public interface IConst {
    int rootClosure = 0;
    int vThis = 0;
    int vCallframe = 1;
    int vPlatform = 2;
    int vClosure = 3;
    int vPrototype = 4;
    int vUser = 5;

    Class O = Object.class;
    Class S = String.class;
    Class I = int.class;
    Class F = float.class;
    Class D = double.class;
    Class PT = Prototype.class;
    Class FR = LuaCallFrame.class;
    Class CU = LuaClosure.class;
    Class LS = LuaScript.class;

    String TS = "TRUE";
    String FS = "FALSE";
  }


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


  public MethodVisitor beginMethod(String mname) {
    if (mv != null) throw new RuntimeException();
    mv = cw.visitMethod(ACC_PUBLIC, mname, "()V", null, null);
    mv.visitCode();
    return mv;
  }


  public void endMethod() {
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    mv = null;
    Tool.pl("endMethod");
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


  // Get field from Super Class
  public Field getField(String n) {
    return getField(superClass, n);
  }


  public Field getField(Class c, String n) {
    try {
      return c.getDeclaredField(n);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  public Method getMethod(Class c, String m, Class ...param) {
    try {
      return c.getDeclaredMethod(m, param);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }


  void vInvokeFunc(Class owner, String method, Class ...param) {
    Method m = getMethod(owner, method, param);

    mv.visitMethodInsn(INVOKEVIRTUAL, toClassPath(owner),
      m.getName(), getMethodSi(m), false);
  }


  // Execution of methods in members
  void vInvokeFieldFunc(String member, String method, Class ...param) {
    Field f = getField(member);
    Class t = f.getType();
    Method m = getMethod(t, method, param);

    mv.visitMethodInsn(INVOKEVIRTUAL, toClassPath(t),
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


  /**
   * Push Boolean constants to java stack
   * @param val constants value
   */
  void vBooleanObj(boolean val) {
    vStatic(getField(Boolean.class, (val ? "TRUE" : "FALSE")));
  }


  void vThis() {
    // Zero is This
    mv.visitVarInsn(ALOAD, IConst.vThis);
  }


  void vNull() {
    mv.visitInsn(ACONST_NULL);
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


  public void vPrint(String msg) {
    mv.visitLdcInsn(msg);
    vInvokeStatic(Tool.class, "pl", Object.class);
  }


  public void vClosureFunctionHeader(ClosureInf inf) {
    // final LuaClosure vClosure = newClosure(rootClosure);
    vThis();
    vInt(inf.arrIndex);
    vInvokeFunc(LuaScript.class, "newClosure", I);
    mv.visitVarInsn(ASTORE, vClosure);

    // final LuaCallFrame vCallframe = newFrame(closure);
    vThis();
    mv.visitVarInsn(ALOAD, vClosure);
    vInvokeFunc(LuaScript.class, "newFrame", CU);
    mv.visitVarInsn(ASTORE, vCallframe);

    // final Platform vPlatform = this.platform
    vField("platform");
    mv.visitVarInsn(ASTORE, vPlatform);

    // final Prototype vPrototype = this.findPrototype(i)
    vThis();
    vInt(inf.arrIndex);
    vInvokeFunc(LuaScript.class, "findPrototype", I);
    mv.visitVarInsn(ASTORE, vPrototype);
  }


  void vClosureFunctionFoot(ClosureInf inf) {
    vThis();
    mv.visitVarInsn(ALOAD, vCallframe);
    vInvokeFunc(LuaScript.class, "closureReturn", FR);
  }


  void vEnvironment() {
    mv.visitVarInsn(ALOAD, vClosure);
    vField(LuaClosure.class, "env");
  }


  /**
   * get var from lua stack[N] to Java stack top
   * @param i stack var index
   */
  void vGetStackVar(int i) {
    mv.visitVarInsn(ALOAD, vCallframe);
    vInt(i);
    vInvokeFunc(LuaCallFrame.class, "get", I);
  }


  /**
   * @param bp Push some var to stack top
   */
  void vSetStackVar(int i, IBuildParam bp) {
    mv.visitVarInsn(ALOAD, vCallframe);
    vInt(i);
    bp.param1();
    vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }


  void vSetStackVar(IBuildParam2 bp) {
    mv.visitVarInsn(ALOAD, vCallframe);
    bp.param1();
    bp.param2();
    vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }


  void vGetConstants(int i) {
    mv.visitVarInsn(ALOAD, vPrototype);
    vField(Prototype.class, "constants");
    vInt(i);
    mv.visitInsn(AALOAD);
  }


  void vGetConstants(IBuildParam index) {
    mv.visitVarInsn(ALOAD, vPrototype);
    vField(Prototype.class, "constants");
    index.param1();
    mv.visitInsn(AALOAD);
  }


  void vClearStack(int from, int to) {
    mv.visitVarInsn(ALOAD, vCallframe);
    mv.visitLdcInsn(from);
    mv.visitLdcInsn(to);
    vInvokeFunc(LuaCallFrame.class, "stackClear", I, I);
  }


  void vClearStack(int from) {
    mv.visitVarInsn(ALOAD, vCallframe);
    mv.visitLdcInsn(from);
    vInvokeFunc(LuaCallFrame.class, "clearFromIndex", I);
  }


  void vCopyRef() {
    mv.visitInsn(DUP);
  }


  void vPop() {
    mv.visitInsn(POP);
  }


  /**
   * get var from lua to Java stack top
   * @param keyBuilder Push some var to stack top
   */
  void vGetGlobalVar(IBuildParam keyBuilder) {
    vGetTableVar(new IBuildParam2() {
      public void param1() {
        vEnvironment();
      }
      public void param2() {
        keyBuilder.param1();
      }
    });
  }


  void vGetTableVar(IBuildParam2 p) {
    vThis();
    p.param1();
    p.param2();
    vInvokeFunc(LuaScript.class, "tableGet", O, O);
  }


  void vSetTableVar(IBuildParam3 p) {
    vThis();
    p.param1();
    p.param2();
    p.param3();
    vInvokeFunc(LuaScript.class, "tableSet", O, O, O);
  }


  void vNewTable() {
    mv.visitVarInsn(ALOAD, vPlatform);
    vInvokeFunc(Platform.class, "newTable");
  }


  /**
   * Convert the value at the top of the stack to
   * primitive double or original object.
   *
   * @see KahluaUtil#rawTonumber(Object)
   */
  void vToNumber(IToNumber tn) {
    vCopyRef();

    vIf(Double.class, new IIF() {
      public void doThen() {
        vCast(Double.class);
        vInvokeFunc(Double.class, "doubleValue");
        tn.success();
      }

      public void doElse() {
        vCopyRef();

        vIf(String.class, new IIF() {
          public void doThen() {
            vCast(String.class);
            vInvokeStatic(KahluaUtil.class, "tonumber", S);
            vInvokeFunc(Double.class, "doubleValue");
            tn.success();
          }
          public void doElse() {
            tn.nan();
          }
        });
      }
    });
  }


  /**
   * @see KahluaThread#getRegisterOrConstant
   * @param i index to stack or constants
   * @param varindex This variable will be used for calculations,
   *                 so it must be reserved in advance
   */
  void vGetRegOrConst(int i, int varindex) {
    final int cindex = vUser + varindex;

    vInt(i);
    vInt(256);
    mv.visitInsn(ISUB);
    mv.visitInsn(DUP);
    mv.visitVarInsn(ISTORE, cindex);
    vIf(IFLT, new IIF() {
      public void doThen() {
        vGetStackVar(i);
      }
      public void doElse() {
        vGetConstants(() -> {
          mv.visitVarInsn(ILOAD, cindex);
        });
      }
    });
  }


  void vGetMetaOp(String metaOp, IBuildParam p) {
    vThis();
    p.param1();
    vString(metaOp);
    vInvokeFunc(LuaScript.class, "getMetaOp", O, S);
  }


  void vCall(IBuildParam4 p) {
    vThis();
    p.param1();
    p.param2();
    p.param3();
    p.param4();
    vInvokeFunc(LuaScript.class, "call", O,O,O,O);
  }


  void vBoolEval(boolean invert, boolean primitive) {
    final boolean vTrue = invert ? false : true;
    vCopyRef();

    vIf(IFNULL, new IIF() {
      public void doThen() {
        vPop();
        if (primitive) {
          vBoolean(vTrue);
        } else {
          vBooleanObj(vTrue);
        }
      }

      public void doElse() {
        vStatic(getField(Boolean.class, "FALSE"));

        vIf(IF_ACMPEQ, new IIF() {
          public void doThen() {
            if (primitive) {
              vBoolean(!vTrue);
            } else {
              vBooleanObj(!vTrue);
            }
          }
          public void doElse() {
            if (primitive) {
              vBoolean(vTrue);
            } else {
              vBooleanObj(vTrue);
            }
          }
        });
      }
    });
  }


  void vToPrimitiveDouble(boolean needCast) {
    if (needCast) vCast(Double.class);
    vInvokeFunc(Double.class, "doubleValue");
  }


  void vToObjectDouble(boolean isString) {
    Class p = isString ? String.class : D;
    vInvokeStatic(Double.class, "valueOf", p);
  }


  /**
   * Read the top (1 or 2) value of the stack and perform judgment operations.
   * When the conditions are met, doThen() is executed,
   * otherwise doElse() is executed.
   * @param ifop This opcode is either IFEQ(v = 0),
   *             IFNE(i ≠ 0),
   *             IFLT(i < 0),
   *             IFGE(i ≥ 0),
   *             IFGT(i > 0),
   *             IFLE(i ≤ 0),
   *             IF_ICMPEQ(i1 = i2),
   *             IF_ICMPNE(i1 ≠ i2),
   *             IF_ICMPLT(i1 < i2),
   *             IF_ICMPGE(i1 ≥ i2),
   *             IF_ICMPGT(i1 > i2),
   *             IF_ICMPLE(i1 ≤ i2),
   *             IF_ACMPEQ(ref1 = ref2),
   *             IF_ACMPNE(ref1 ≠ ref2),
   *             IFNULL(ref = null)
   *             IFNONNULL(ref ≠ null).
   * @param opfunc
   */
  void vIf(int ifop, IIF opfunc) {
    vIf((_then)->{
      mv.visitJumpInsn(ifop, _then);
    }, opfunc);
  }


  /**
   * if var instanceof c,
   * You need to execute vCast() yourself to do the conversion
   */
  void vIf(Class c, IIF opfunc) {
    vIf((_then)->{
      mv.visitTypeInsn(INSTANCEOF, toClassPath(c.getName()));
      mv.visitJumpInsn(IFNE, _then);
    }, opfunc);
  }


  void vIf(IIfCheck check, IIF opfunc) {
    Label _end = new Label();
    Label _then = new Label();
    Label _else = new Label();

    check.ifop(_then);
    vGoto(_else);

    mv.visitLabel(_then);
    opfunc.doThen();
    vGoto(_end);

    mv.visitLabel(_else);
    opfunc.doElse();
    vGoto(_end);

    mv.visitLabel(_end);
  }
}
