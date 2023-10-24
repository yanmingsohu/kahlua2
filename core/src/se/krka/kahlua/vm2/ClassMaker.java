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


/**
 * The class will be cached after successful compilation
 *
 * About ASM Error message:
 *   "Index 0 out of bounds for length 0" : Variable type error
 *   "Index 1 out of bounds for length 0" : Extra variables on the stack
 *   "Index -1 out of bounds for length 0" : Try operating on an empty stack
 */
public class ClassMaker implements IConst {

  private ClassWriter cw;
  private FieldVisitor fv;
  private MethodVisitor mv;
  private AnnotationVisitor av0;
  private Class clazz;
  private LuaBuilder.State stat;

  final Class scriptSuperClass = LuaScript.class;
  final String className;
  final String classPath;
  final String superClassName = toClassPath(scriptSuperClass.getName());
  final String outputDir;
  final DebugInf di;


  /**
   * @param className '.' split class path
   */
  public ClassMaker(String className, String outputDir, DebugInf di) {
    this.outputDir = outputDir;
    this.className = className;
    this.classPath = toClassPath(className);
    this.di = di;

    cw = new ClassWriter(COMPUTE_FRAMES);
    cw.visit(52,
      ACC_PUBLIC + Opcodes.ACC_SUPER,
      classPath,
      null,
      superClassName,
      null);

    cw.visitSource(classPath +".lua", null);
  }


  public void updateState(LuaBuilder.State stat) {
    this.stat = stat;
  }


  public ClassMaker(String className, DebugInf di) {
    this(className, null, di);
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
  }


  public Class genClass() {
    if (clazz == null) {
      cw.visitEnd();
      byte[] buf = cw.toByteArray();
      clazz = LuaClassLoader.instance.defineClass(className, buf);
      cw = null;

      String outputFile = getOutputFile();
      if (outputFile != null) writeBuf(outputFile, buf);
    }
    return clazz;
  }


  private void writeBuf(String outputFile, byte[] buf) {
    try (FileOutputStream w = new FileOutputStream(outputFile)) {
      if (di.has(DebugInf.FILEMSG)) {
        Tool.pl("Write class file:", outputFile);
      }
      w.write(buf);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * return .class path or null if not set output dir
   */
  public String getOutputFile() {
    return outputDir == null ? null :
      outputDir +"/"+ Tool.flatPath(className)
        + (di.flag==DebugInf.NONE ? ".class" : ".debug.class");
  }


  public LuaScript newInstance() throws IllegalAccessException,
    InstantiationException, NoSuchMethodException,
    InvocationTargetException {
    Class c = genClass();
    return (LuaScript) c.getDeclaredConstructor().newInstance();
  }


  public void defaultConstructor() {
    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();

    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    mv = null;
  }


  public void vClosureFunctionHeader(LuaBuilder.State s) {
    final ClosureInf inf = s.ci;
    // final ClosureInf ci = plist[index];
    vField("plist");
    vInt(inf.arrIndex);
    mv.visitInsn(AALOAD);
    s.vCI.store();
    s.vCI._lock();

    // ci.newFrame(this.coroutine)
    s.vCI.load();
    vThis();
    vField(LS, "coroutine");
    vInvokeFunc(CI, "newFrame", CR);

    // final LuaClosure vClosure = ci.getOldClosure();
    s.vCI.load();
    vInvokeFunc(CI, "getOldClosure");
    s.vClosure.store();
    s.vClosure._lock();

    // final LuaCallFrame vCallframe = closure.getOldFrame();
    s.vCI.load();
    vInvokeFunc(CI, "getOldFrame");
    s.vCallframe.store();
    s.vCallframe._lock();

    // final Platform vPlatform = this.platform
    vField("platform");
    s.vPlatform.store();
    s.vPlatform._lock();

    // final Prototype vPrototype = ci.prototype
    s.vCI.load();
    vField(CI, "prototype");
    s.vPrototype.store();
    s.vPrototype._lock();

    s.vCallframe.load();
    vField(FR, "localBase");
    s.vLocalBase.store();
    s.vLocalBase._lock();

    for (int i=0; i<s.vConstants.length; ++i) {
      s.vPrototype.load();
      vField(Prototype.class, "constants");
      vInt(i);
      mv.visitInsn(AALOAD);
      LocalVar vConst = s.vConstants[i];
      vConst.store();
      vConst._lock();
    }

    vSyncStack();

    for (int i=0; i<s.vUpvalue.length; ++i) {
      s.vClosure.load();
      vField(LuaClosure.class, "upvalues");
      mv.visitLdcInsn(i);
      mv.visitInsn(AALOAD);
      LocalVar vu = s.vUpvalue[i];
      vu.store();
      vu._lock();
    }

    if (di.has(DebugInf.CALL)) {
      vPrint(">>>> vClosureFunctionHeader", s.vCI);
    }
    vNull();
    s.vError.store();
  }


  void vClosureFunctionFoot(LuaBuilder.State s) {
    final String t = "java/lang/Throwable";
    mv.visitTryCatchBlock(s.initOverLabel, s.finallyLabel, s.cacheLabel, t);

    if (di.has(DebugInf.STACK)) {
      di.stackAll();
      Tool.pl("endMethod");
    }
    vGoto(s.finallyLabel);

    mv.visitLabel(s.cacheLabel);
    s.vError.store();
    if (di.has(DebugInf.CALL)) {
      vPrint("!!!! Got", t, s.vCI);
    }
    vGoto(s.finallyLabel);

    mv.visitLabel(s.finallyLabel);
    if (di.has(DebugInf.CALL)) {
      vPrint("<<<< vClosureFunctionFoot", s.vCI);
    }

    //coroutine.popCallFrame();
    vField("coroutine");
    vInvokeFunc(CR, "popCallFrame");

    s.vError.load();
    vIf(IFNONNULL, ()-> {
      s.vError.load();
      mv.visitInsn(ATHROW);
    });
  }


  // Get field from Super Class
  public Field getField(String n) {
    return getField(scriptSuperClass, n);
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


  void vInvokeInterface(Class owner, String method, Class ...param) {
    Method m = getMethod(owner, method, param);

    mv.visitMethodInsn(INVOKEINTERFACE, toClassPath(owner),
      m.getName(), getMethodSi(m), true);
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


  /**
   * Call class constructor
   * @param owner
   * @param p  Not one but All params !!!
   * @param param
   */
  void vInvokeConstructor(Class owner, IBuildParam p, Class ...param) {
    Method m = getMethod(owner, CONSTRUCTOR, param);
    String classPath = toClassPath(owner);

    mv.visitTypeInsn(NEW, classPath);
    vCopyRef();
    p.param1();
    mv.visitMethodInsn(INVOKESPECIAL, classPath, CONSTRUCTOR, getMethodSi(m), false);
  }


  //TODO: optimization
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
    if (b) {
      mv.visitInsn(ICONST_1);
    } else {
      mv.visitInsn(ICONST_0);
    }
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
    vField(scriptSuperClass, fname);
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
    vThrow(()-> mv.visitLdcInsn(errMsg));
  }


  void vThrow(IBuildParam p) {
    final String CLP = "se/krka/kahlua/vm2/LuaFail"; //"java/lang/RuntimeException";
    mv.visitTypeInsn(NEW, CLP);
    vCopyRef();
    p.param1();
    mv.visitMethodInsn(INVOKESPECIAL, CLP, CONSTRUCTOR, "(Ljava/lang/String;)V", false);
    mv.visitInsn(ATHROW);
  }


  void vConcatString(Object ...oa) {
    final String SB = "java/lang/StringBuilder";
    mv.visitTypeInsn(NEW, SB);
    vCopyRef();
    mv.visitMethodInsn(INVOKESPECIAL, SB, CONSTRUCTOR, "()V", false);

    for (Object o : oa) {
      if (o instanceof LocalVar) {
        ((LocalVar)o).load();
        vInvokeFunc(StringBuilder.class, "append", Object.class);
      } else {
        vString(String.valueOf(o));
        vInvokeFunc(StringBuilder.class, "append", String.class);
      }
    }
    vInvokeFunc(StringBuilder.class, "toString");
  }


  public void vPrint(String msg) {
    mv.visitLdcInsn(msg);
    vInvokeStatic(Tool.class, "pl", Object.class);
  }


  public void vPrint(Object ...o) {
    vObjectArray(o);
    vInvokeStatic(Tool.class, "pl", Object[].class);
  }


  void vEnvironment() {
    stat.vClosure.load();
    vField(LuaClosure.class, "env");
  }


  /**
   * get var from lua stack[N] to Java stack top
   * @param i stack var index
   */
  void vGetStackVar(int i) {
    vGetStackVar(()->vInt(i));
  }


  void vCheckLocalBaseChanged() {
    LocalVar a = stat.newVar("a");
    LocalVar b = stat.newVar("b");
    stat.vLocalBase.load();
    vInvokeStatic(Integer.class, "valueOf", I);
    a.store();

    stat.vCallframe.load();
    vField(FR, "localBase");
    vInvokeStatic(Integer.class, "valueOf", I);
    b.store();

    a.load();
    vInvokeFunc(Integer.class, "intValue");
    b.load();
    vInvokeFunc(Integer.class, "intValue");
    vIf(IF_ICMPNE, ()->{
      vPrint("!!!! stack base changed", a, "~", b);
    });

    vField("coroutine");
    vField(CR, "objectStack");
    a.store();

    stat.vStack.load();
    a.load();
    vIf(IF_ACMPNE, ()-> {
      vPrint("!!!! stack ref changed", a, "~", stat.vStack);
    });
  }


  // callFrame.findUpvalue(b);
  void vFindUpvalue(IBuildParam p) {
    stat.vCallframe.load();
    p.param1();
    vInvokeFunc(FR, "findUpvalue", I);
  }


  // closure.upvalues[b];
  void vGetUpvalueFromClosure(IBuildParam p) {
    stat.vClosure.load();
    vField(LuaClosure.class, "upvalues");
    p.param1();
    mv.visitInsn(AALOAD);
  }


  void vGetUpValue(int i) {
    stat.vUpvalue[i].load();
    vInvokeFunc(UpValue.class, "getValue");
  }


  void vSetUpValue(int i, IBuildParam p) {
    stat.vUpvalue[i].load();
    p.param1();
    vInvokeFunc(UpValue.class, "setValue", O);
  }


  /**
   * stack variable may change after growing
   */
  void vSyncStack() {
    vField("coroutine");
    vField(CR, "objectStack");
    stat.vStack.store();
  }


  void vGetStackVar(IBuildParam bp) {
//    stat.vCallframe.load();
//    bp.param1();
//    vInvokeFunc(LuaCallFrame.class, "get", I);
    stat.vStack.load();
    stat.vLocalBase.load();
    bp.param1();
    mv.visitInsn(IADD);
    mv.visitInsn(AALOAD);
  }


  /**
   * @param bp Push some var to stack top
   */
  void vSetStackVar(int i, IBuildParam bp) {
//    stat.vCallframe.load();
//    vInt(i);
//    bp.param1();
//    vInvokeFunc(LuaCallFrame.class, "set", I, O);
    stat.vStack.load();
    stat.vLocalBase.load();
    vInt(i);
    mv.visitInsn(IADD);
    bp.param1();
    mv.visitInsn(AASTORE);
  }


  void vGetConstants(int i) {
//    stat.vPrototype.load();
//    vField(Prototype.class, "constants");
//    vInt(i);
//    mv.visitInsn(AALOAD);
    stat.vConstants[i].load();
  }


  void vClearStack(int from, int to) {
    stat.vCallframe.load();
    mv.visitLdcInsn(from);
    mv.visitLdcInsn(to);
    vInvokeFunc(LuaCallFrame.class, "stackClear", I, I);
  }


  void vClearStack(int from) {
    stat.vCallframe.load();
    vInt(from);
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
    stat.vPlatform.load();
    vInvokeInterface(Platform.class, "newTable");
  }


  /**
   * Convert the value at the top of the stack to
   * Double or original object.
   *
   * @see KahluaUtil#rawTonumber(Object)
   */
  void vToNumber(IToNumber tn) {
    vCopyRef();

    vIf(Double.class, new IIF() {
      public void doThen() {
        vCast(Double.class);
        tn.success();
      }

      public void doElse() {
        vCopyRef();

        vIf(String.class, new IIF() {
          public void doThen() {
            vCast(String.class);
            vInvokeStatic(KahluaUtil.class, "tonumber", S);
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
   * @param i index to stack or constants, i is the opcode and will not change at runtime
   */
  void vGetRegOrConst(int i) {
    final int cindex = i - LuaConstVarBegin;
    if (cindex < 0) {
      vGetStackVar(i);
    } else {
      vGetConstants(cindex);
    }
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
          vBoolean(!vTrue);
        } else {
          vBooleanObj(!vTrue);
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


  void vToInteger() {
    vInvokeStatic(Integer.class, "valueOf", I);
  }


  /**
   * get stack top index to java stack
   */
  void vGetTop() {
    stat.vCallframe.load();
    vInvokeFunc(FR, "getTop");
  }


  /**
   * get stack base index to java stack
   */
  void vGetBase() {
    stat.vCallframe.load();
    vField(FR, "localBase");
  }


  void vReturnBase() {
    stat.vCallframe.load();
    vField(FR, "returnBase");
  }


  void vCloseLocalUpvlues(IBuildParam p) {
    stat.vCallframe.load();
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


  void vFrameStackCopy(IBuildParam3 p) {
    stat.vCallframe.load();
    p.param1();
    p.param2();
    p.param3();
    vInvokeFunc(FR, "stackCopy", I,I,I);
  }


  // currentCoroutine.setTop(b);
  void vSetCoroutineTop(IBuildParam p) {
    vField("coroutine");
    p.param1();
    vInvokeFunc(Coroutine.class, "setTop", I);
    vSyncStack();
  }


  void vSetFrameTop(IBuildParam p) {
    stat.vCallframe.load();
    p.param1();
    vInvokeFunc(FR, "setTop", I);
    vSyncStack();
  }


  void vAutoRestoreTop() {
    stat.vCI.load();
    stat.vCallframe.load();
    vInvokeFunc(CI, "restoreStack", FR);
  }


  void vSetRestoreTop(boolean r) {
    stat.vCallframe.load();
    vBoolean(r);
    vPutField(FR, "restoreTop");
  }


  void vPushVarargs(IBuildParam2 p) {
    vThis();
    stat.vPrototype.load();
    stat.vCallframe.load();
    p.param1();
    p.param2();
    vInvokeFunc(LuaScript.class, "pushVarargs", PT,FR,I,I);
    vSyncStack();
  }


  void vNewFrameParams(LocalVar ci, LocalVar lb, LocalVar ret,
                       LocalVar arg) {
    ci.load();
    lb.load();
    ret.load();
    arg.load();
    vInvokeFunc(CI, "frameParams", I,I,I);
  }


  void vPopFrame() {
    vField("coroutine");
    vInvokeFunc(CR, "popCallFrame");
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
      public void doThen() { opfunc.doThen(); }
      public void doElse() { /* do nothing */ }
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


  void vIf(Class c, IIFot of) {
    this.vIf(c, new IIF() {
      public void doThen() { of.doThen(); }
      public void doElse() { /* do nothing */ }
    });
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


  void vBlock(Label define, Label end, IBuildParam p) {
    mv.visitLabel(define);
    p.param1();
    vGoto(end);
  }


  void vPrintStack(int ...i) {
    vThis();
    vField(LuaScript.class, "coroutine");
    stat.vCallframe.load();
    vIntArray(i);
    vInvokeStatic(DebugInf.class, "printLuaStack", CR, FR, int[].class);
  }


  void vPrintConsts(int ...i) {
    stat.vPrototype.load();
    vIntArray(i);
    vInvokeStatic(DebugInf.class, "printLuaConsts", PT, int[].class);
  }


  void vPrintOldClosureUpvaleu(int ...i) {
    stat.vClosure.load();
    vField(CU, "upvalues");
    vIntArray(i);
    vInvokeStatic(DebugInf.class, "printUpValues", UpValue[].class, int[].class);
  }


  void vPrintCiUpvalue(LocalVar ci, int ...i) {
    ci.load();
    vField(ClosureInf.class, "upvalues");
    vIntArray(0);
    vInvokeStatic(DebugInf.class, "printUpValues", UpValue[].class, int[].class);
  }


  void vIntArray(int ...ia) {
    vInt(ia.length);
    mv.visitIntInsn(NEWARRAY, T_INT);

    for (int i=0; i<ia.length; ++i) {
      vCopyRef();
      vInt(i);
      vInt(ia[i]);
      mv.visitInsn(IASTORE);
    }
  }


  void vObjectArray(Object ...oa) {
    vInt(oa.length);
    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

    for (int i=0; i<oa.length; ++i) {
      vCopyRef();
      vInt(i);
      if (oa[i] instanceof LocalVar) {
        ((LocalVar) oa[i]).load();
      } else if (oa[i] instanceof IBuildParam) {
        ((IBuildParam) oa[i]).param1();
      } else {
        vString(String.valueOf(oa[i]));
      }
      mv.visitInsn(AASTORE);
    }
  }
}
