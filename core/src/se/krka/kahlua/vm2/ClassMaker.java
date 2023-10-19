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
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.*;
import static se.krka.kahlua.vm2.Tool.*;

public class ClassMaker implements IConst {


  private ClassWriter cw;
  private FieldVisitor fv;
  private MethodVisitor mv;
  private AnnotationVisitor av0;
  private Class clazz;

  final String className;
  final String classPath;
  final Class  superClass = LuaScript.class;
  final String superClassName = toClassPath(superClass.getName());
  final String outputDir;


  /**
   * @param className '.' split class path
   */
  public ClassMaker(String className, String outputDir) {
    this.outputDir = outputDir;
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


  public ClassMaker(String className) {
    this(className, null);
  }


  public MethodVisitor beginMethod(String mname) {
    if (mv != null) throw new RuntimeException();
    mv = cw.visitMethod(ACC_PUBLIC, mname, "()V", null, null);
    mv.visitCode();
    return mv;
  }


  public void endMethod(StateBase st) {
    mv.visitInsn(RETURN);
    st.vAllVariables();
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

      if (outputDir != null) try {
        String file = outputDir +"/"+ Tool.flatPath(className) +".class";
        FileOutputStream w = new FileOutputStream(file);
        w.write(buf);
        w.close();
        Tool.pl("Write class file:", file);
      } catch (IOException e) {
        throw new RuntimeException(e);
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


  void vPutField(Class owner, String fname) {
    String oclass = toClassPath(owner.getName());

    Field f = getField(owner, fname);
    StringBuilder desc = new StringBuilder();
    typeName(desc, f.getType());

    mv.visitFieldInsn(PUTFIELD, oclass, f.getName(), desc.toString());
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


  public void vClosureFunctionHeader(LuaBuilder.State s) {
    final ClosureInf inf = s.ci;
    // final ClosureInf ci = plist[index];
    vField("plist");
    vInt(inf.arrIndex);
    mv.visitInsn(AALOAD);
    mv.visitVarInsn(ASTORE, vCI);

    // final LuaClosure vClosure = ci.getOldClosure();
    mv.visitVarInsn(ALOAD, vCI);
    vInvokeFunc(ClosureInf.class, "getOldClosure");
    mv.visitVarInsn(ASTORE, vClosure);

    // final LuaCallFrame vCallframe = closure.getOldFrame();
    mv.visitVarInsn(ALOAD, vCI);
    vInvokeFunc(ClosureInf.class, "getOldFrame");
    mv.visitVarInsn(ASTORE, vCallframe);

    // final Platform vPlatform = this.platform
    vField("platform");
    mv.visitVarInsn(ASTORE, vPlatform);

    // final Prototype vPrototype = ci.prototype
    mv.visitVarInsn(ALOAD, vCI);
    vField(ClosureInf.class, "prototype");
    mv.visitVarInsn(ASTORE, vPrototype);
  }


  void vClosureFunctionFoot(LuaBuilder.State s) {
    mv.visitLabel(s.returnLabel);
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
    vGetStackVar(()->vInt(i));
  }


  void vGetStackVar(IBuildParam bp) {
    mv.visitVarInsn(ALOAD, vCallframe);
    bp.param1();
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


  void vCallMetaOp(String metaOp, IBuildParam2 p) {
    vThis();
    p.param1();
    p.param2();
    mv.visitLdcInsn(metaOp);
    vInvokeFunc(LuaScript.class, "metaOp", O, O, S);
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
   * get stack top index to java stack
   */
  void vGetTop() {
    mv.visitVarInsn(ALOAD, vCallframe);
    vInvokeFunc(FR, "getTop");
  }


  /**
   * get stack base index to java stack
   */
  void vGetBase() {
    mv.visitVarInsn(ALOAD, vCallframe);
    vField(FR, "localBase");
  }


  void vCloseLocalUpvlues(IBuildParam p) {
    mv.visitVarInsn(ALOAD, vCallframe);
    p.param1();
    vInvokeFunc(LuaCallFrame.class, "closeUpvalues", I);
  }


  // coroutine.closeUpvalues(base);
  void vCloseCoroutineUpvalues(IBuildParam p) {
    vField("coroutine");
    p.param1();
    vInvokeFunc(Coroutine.class, "closeUpvalues", I);
  }


  // coroutine.stackCopy(a, returnBase, b);
  void vStackCopy(IBuildParam3 p) {
    vField("coroutine");
    p.param1();
    p.param2();
    p.param3();
    vInvokeFunc(Coroutine.class, "stackCopy", I,I,I);
  }


  // currentCoroutine.setTop(b);
  void vSetCoroutineTop(IBuildParam p) {
    vField("coroutine");
    p.param1();
    vInvokeFunc(Coroutine.class, "setTop", I);
  }


  void vSetFrameTop(IBuildParam p) {
    mv.visitVarInsn(ALOAD, vCallframe);
    p.param1();
    vInvokeFunc(FR, "setTop", I);
  }


  void vPushVarargs(IBuildParam2 p) {
    mv.visitVarInsn(ALOAD, vCallframe);
    p.param1();
    p.param2();
    vInvokeFunc(LuaCallFrame.class, "pushVarargs", I,I);
  }


  // callFrame.findUpvalue(b);
  void vFindUpvalue(IBuildParam p) {
    mv.visitVarInsn(ALOAD, vCallframe);
    p.param1();
    vInvokeFunc(FR, "findUpvalue", I);
  }


  // closure.upvalues[b];
  void vGetUpvalueFromClosure(IBuildParam p) {
    mv.visitVarInsn(ALOAD, vClosure);
    vField(LuaClosure.class, "upvalues");
    p.param1();
    mv.visitInsn(AALOAD);
  }


  void vNewFrame(LocalVar lb, LocalVar ret, LocalVar arg, boolean isLua) {
    vField("coroutine");
    lb.load();
    ret.load();
    arg.load();
    vBoolean(isLua);
    vInvokeFunc(CI, "newFrame", CR,I,I,I,B);
  }


  void vPopFrame() {
    vField("coroutine");
    vInvokeFunc(CR, "popCallFrame");
  }


  void vReturnBase() {
    mv.visitVarInsn(ALOAD, vCallframe);
    vField(FR, "returnBase");
  }


  void vStore(LocalVar v) {
    v.store();
  }


  void vLoad(LocalVar v) {
    v.load();
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


  void vIf(int ifop, IIFot opfunc) {
    vIf(ifop, new IIF() {
      public void doThen() {
        opfunc.doThen();
      }
      public void doElse() {
        // do nothing
      }
    });
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


  void vIf(Class c, Label jump) {
    mv.visitTypeInsn(INSTANCEOF, toClassPath(c.getName()));
    mv.visitJumpInsn(IFNE, jump);
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
