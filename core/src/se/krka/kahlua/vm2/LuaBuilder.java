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


import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import se.krka.kahlua.vm.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

import static org.objectweb.asm.Opcodes.*;
import static se.krka.kahlua.vm.KahluaThread.*;
import static se.krka.kahlua.vm2.KahluaThread2.opNames;

/**
 * @link https://the-ravi-programming-language.readthedocs.io/en/latest/lua_bytecode_reference.html
 */
public class LuaBuilder {
  
  private final Class O = Object.class;
  private final Class S = String.class;
  private final Class I = int.class;
  private final Class F = float.class;
  private final Class D = double.class;
  private final Class PT = Prototype.class;
  private final Class FR = LuaCallFrame.class;

  private LuaCallFrame callFrame;
  private LuaClosure closure;
  private Prototype prototype;
  private ClassMaker cm;
  final String classPath;
  final String className;
  private MethodVisitor mv;

  Label[] labels;
  Label label;
  int pc;
  int opcode;
  int op;
  int line;

  // TMP remove
  String meta_ops[];
  int[] opcodes;
  int returnBase;
  Coroutine rootCoroutine;
  Coroutine currentCoroutine;


  public LuaBuilder(Coroutine c) {
    callFrame = c.currentCallFrame();
    closure = callFrame.closure;
    prototype = closure.prototype;
    classPath = prototype.name;
    className = Tool.formatClassName(classPath);
    cm = new ClassMaker(className);
  }

  public void makeJavacode() {
    cm.defaultInit();
    mv = cm.beginMethod("run");

    int[] opcodes = prototype.code;
    this.opcodes = opcodes;
    labels = new Label[opcodes.length];

    for (int i=0; i<opcodes.length; ++i) {
      labels[i] = new Label();
    }

    while (callFrame.pc < opcodes.length) {
      pc = callFrame.pc++;
      op = opcodes[pc];
      opcode = op & 0x3F;
      line = prototype.lines[pc];
      label = labels[pc];

      mv.visitLabel(label);
      mv.visitLineNumber(line, label);

      Tool.pl("LL ",pc, op, opNames[opcode], line);

      switch (opcode) {
        case OP_MOVE: op_move(); break;
        case OP_LOADK: op_loadk(); break;
        case OP_LOADBOOL: op_loadbool(); break;
        case OP_LOADNIL: op_loadnil(); break;
        case OP_GETUPVAL: op_getupval(); break;
        case OP_GETGLOBAL: op_getglobal(); break;
        case OP_GETTABLE: op_gettable(); break;
        case OP_SETGLOBAL: op_setglobal(); break;
        case OP_SETUPVAL: op_setupval(); break;
        case OP_SETTABLE: op_settable(); break;
        case OP_NEWTABLE: op_newtable(); break;
        case OP_SELF: op_self(); break;
        case OP_ADD: op_add(); break;
        case OP_SUB: op_sub(); break;
        case OP_MUL: op_mul(); break;
        case OP_DIV: op_div(); break;
        case OP_MOD: op_mod(); break;
        case OP_POW: op_pow(); break;
        case OP_UNM: op_unm(); break;
        case OP_NOT: op_not(); break;
        case OP_LEN: op_len(); break;
        case OP_CONCAT: op_concat(); break;
        case OP_JMP: op_jmp(); break;
        case OP_EQ: op_eq(); break;
        case OP_LT: op_lt(); break;
        case OP_LE: op_le(); break;
        case OP_TEST: op_test(); break;
        case OP_TESTSET: op_testset(); break;
        case OP_CALL: op_call(); break;
        case OP_TAILCALL: op_tailcall(); break;
        case OP_RETURN: op_return(); break;
        case OP_FORLOOP: op_forprep(); break;
        case OP_FORPREP: op_forloop(); break;
        case OP_TFORLOOP: op_tforloop(); break;
        case OP_SETLIST: op_setlist(); break;
        case OP_CLOSE: op_close(); break;
        case OP_CLOSURE: op_closure(); break;
        case OP_VARARG: op_vararg(); break;
      }
    }

    cm.endMethod();
  }

  public LuaScript createJavaAgent() throws InvocationTargetException,
    NoSuchMethodException,  InstantiationException,  IllegalAccessException {
    return cm.newInstance();
  }



  void op_move() {
    int a = getA8(op);
    int b = getB9(op);

    //callFrame.set(a, callFrame.get(b));
    cm.vField("callFrame");
    cm.vInt(a);
    cm.vField("callFrame");
    cm.vInt(b);
    cm.vInvokeFieldFunc("callFrame","get", int.class);
    cm.vInvokeFieldFunc("callFrame","set", int.class, O);
  }

  void op_loadk() {
    int a = getA8(op);
    int b = getBx(op);

    //callFrame.set(a, prototype.constants[b]);

    cm.vField("callFrame");
    cm.vInt(a);
    cm.vField("prototype");
    cm.vField(Prototype.class, "constants");
    cm.vInt(b);
    mv.visitInsn(AALOAD);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  // TODO: op is changed on running ???
  void op_loadbool() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);
    String v = (b == 0 ? "FALSE" : "TRUE");

    //callFrame.set(88, Boolean.FALSE);
    cm.vField("callFrame");
    cm.vInt(a);
    cm.vStatic(cm.getField(Boolean.class, v));
    cm.vInvokeFieldFunc("callFrame", "set", I, O);

    if (c != 0) {
      cm.vGoto(labels[pc + 1]);
    }
  }

  void op_loadnil() {
    int a = getA8(op);
    int b = getB9(op);

    //callFrame.stackClear(a, b);
    cm.vField("callFrame");
    mv.visitLdcInsn(a);
    mv.visitLdcInsn(b);
    cm.vInvokeFieldFunc("callFrame", "stackClear", I, I);
  }

  void op_getupval() {
    int a = getA8(op);
    int b = getB9(op);

    //callFrame.set(a, closure.upvalues[b].getValue());
    cm.vField("callFrame");
    cm.vInt(a);

    cm.vField("closure");
    cm.vField(LuaClosure.class, "upvalues");
    mv.visitLdcInsn(b);
    mv.visitInsn(AALOAD);

    cm.vInvokeFunc(UpValue.class, "getValue");
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_getglobal() {
    int a = getA8(op);
    int b = getBx(op);

    //Object res = tableGet(closure.env, prototype.constants[b]);
    //callFrame.set(a, res);

    cm.vField("callFrame");
    cm.vInt(a);
    cm.vThis();

    cm.vField("closure");
    cm.vField(LuaClosure.class, "env");

    cm.vField("prototype");
    cm.vField(Prototype.class, "constants");
    cm.vInt(b);
    mv.visitInsn(AALOAD);

    cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_gettable() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    cm.vField("callFrame");
    cm.vInt(a);
    cm.vThis();

    cm.vField("callFrame");
    cm.vInt(b);
    //Object bObj = callFrame.get(b);
    cm.vInvokeFieldFunc("callFrame","get", I);

    cm.vThis();
    cm.vField("callFrame");
    cm.vInt(c);
    cm.vField("prototype");
    //Object key = getRegisterOrConstant(callFrame, c, prototype);
    cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

    //Object res = tableGet(bObj, key);
    cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);

    //callFrame.set(a, res);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_setglobal() {
    int a = getA8(op);
    int b = getBx(op);

    //Object value = callFrame.get(a);
    //Object key = prototype.constants[b];
    //tableSet(closure.env, key, value);
    cm.vThis();

    cm.vField("closure");
    cm.vField(LuaClosure.class, "env");

    cm.vField("prototype");
    cm.vField(Prototype.class, "constants");
    cm.vInt(b);
    mv.visitInsn(AALOAD);

    cm.vField("callFrame");
    cm.vInt(a);
    cm.vInvokeFieldFunc("callFrame","get", I);
    cm.vInvokeFunc(LuaScript.class, "tableSet", O, O, O);
  }

  void op_setupval() {
    int a = getA8(op);
    int b = getB9(op);

    //UpValue uv = closure.upvalues[b];
    //uv.setValue(callFrame.get(a));
    cm.vField("closure");
    cm.vField(LuaClosure.class, "upvalues");
    mv.visitLdcInsn(b);
    mv.visitInsn(AALOAD);

    cm.vField("callFrame");
    cm.vInt(a);
    cm.vInvokeFieldFunc("callFrame","get", I);

    cm.vInvokeFunc(UpValue.class, "setValue", O);
  }

  void op_settable() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    //Object aObj = callFrame.get(a);
    //Object key = getRegisterOrConstant(callFrame, b, prototype);
    //Object value = getRegisterOrConstant(callFrame, c, prototype);
    //tableSet(aObj, key, value);
    cm.vThis();

    cm.vField("callFrame");
    cm.vInt(a);
    cm.vInvokeFieldFunc("callFrame","get", I);

    cm.vThis();
    cm.vField("callFrame");
    cm.vInt(b);
    cm.vField("prototype");
    cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

    cm.vThis();
    cm.vField("callFrame");
    cm.vInt(c);
    cm.vField("prototype");
    cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

    cm.vInvokeFunc(LuaScript.class, "tableSet", O, O, O);
  }

  void op_newtable() {
    int a = getA8(op);

    //KahluaTable t = platform.newTable();
    //callFrame.set(a, t);
    cm.vField("callFrame");
    cm.vInt(a);
    cm.vField("platform");
    cm.vInvokeFieldFunc("platform","newTable");
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_self() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

      /*
      Object bObj = callFrame.get(b);
      callFrame.set(a + 1, bObj);
      Object key = getRegisterOrConstant(callFrame, c, prototype);
      Object fun = tableGet(bObj, key);
      callFrame.set(a, fun); */

    cm.vField("callFrame");
    {
      cm.vField("callFrame");
      cm.vInt(b);
      cm.vInvokeFieldFunc("callFrame", "get", I);
      mv.visitVarInsn(ASTORE, 1);
    }
    cm.vInt(a+1);
    mv.visitVarInsn(ALOAD, 1);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);

    cm.vField("callFrame");
    cm.vInt(a);
    {
      cm.vThis();
      mv.visitVarInsn(ALOAD, 1);
      {
        cm.vThis();
        cm.vField("callFrame");
        cm.vInt(c);
        cm.vField("prototype");
        cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
      }
      cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);
    }
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_add() {
    math_cal("__add", true, ()->{
      mv.visitInsn(DADD);
    });
  }

  void op_sub() {
    math_cal("__sub", true, ()->{
      mv.visitInsn(DSUB);
    });
  }

  void op_mul() {
    math_cal("__mul", true, ()->{
      mv.visitInsn(DMUL);
    });
  }

  void op_div() {
    math_cal("__div", true, ()->{
      mv.visitInsn(DDIV);
    });
  }

  void op_pow() {
    math_cal("__pow", false, ()->{
      cm.vField("platform");
      mv.visitVarInsn(ALOAD, 3);
      cm.vInvokeFunc(Double.class, "doubleValue");
      mv.visitVarInsn(ALOAD, 4);
      cm.vInvokeFunc(Double.class, "doubleValue");
      cm.vInvokeFieldFunc("platform", "pow", D, D);
    });
  }

  void op_mod() {
    math_cal("__mod", false, ()->{
      Label v2iszero = new Label();
      Label end = new Label();

      mv.visitVarInsn(ALOAD, 4);
      cm.vInvokeFunc(Double.class, "doubleValue");
      cm.vDouble(0);
      mv.visitInsn(DCMPL);
      mv.visitJumpInsn(IFEQ, v2iszero); // if v4 == 0 goto v2iszero

      // v4 != 0
      mv.visitVarInsn(ALOAD, 3);
      cm.vInvokeFunc(Double.class, "doubleValue");
      {
        mv.visitVarInsn(ALOAD, 3);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitVarInsn(ALOAD, 4);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitInsn(DDIV);
        mv.visitInsn(D2I);
      }
      mv.visitInsn(I2D);
      mv.visitVarInsn(ALOAD, 4);
      cm.vInvokeFunc(Double.class, "doubleValue");
      mv.visitInsn(DMUL);
      mv.visitInsn(DSUB);

      cm.vGoto(end);

      // v4 == 0
      cm.vLabel(v2iszero, line);
      cm.vDouble(Double.NaN);

      cm.vLabel(end, line);
    });
  }

  // add sub mul div mod pow
  void math_cal(String meta_op, boolean popValued, Runnable primitiveOp) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

      /*
      Object bo = getRegisterOrConstant(callFrame, b, prototype);
      Object co = getRegisterOrConstant(callFrame, c, prototype);

      Double bd = KahluaUtil.rawTonumber(bo);
      Double cd = KahluaUtil.rawTonumber(co);
      Object res = null;

      if (bd == null || cd == null) {
        res = metaOp(bo, co, meta_op);
      } else {
        res = bd (+) cd;
      }
      callFrame.set(a, res);*/

    Label saveRes = new Label();
    Label useMetaOp1 = new Label();
    Label useMetaOp2 = new Label();
    Label primitive = new Label();

    {
      cm.vThis();
      cm.vField("callFrame");
      cm.vInt(b);
      cm.vField("prototype");
      cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
      mv.visitVarInsn(ASTORE, 1); // bo:1

      cm.vThis();
      cm.vField("callFrame");
      cm.vInt(c);
      cm.vField("prototype");
      cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
      mv.visitVarInsn(ASTORE, 2); // co:2

      cm.vThis();
      mv.visitVarInsn(ALOAD, 1);
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      mv.visitVarInsn(ASTORE, 3); // bd:3

      cm.vThis();
      mv.visitVarInsn(ALOAD, 2);
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      mv.visitVarInsn(ASTORE, 4); // cd:4

      // if (bd == null || cd == null) then useMetaOp();
      mv.visitVarInsn(ALOAD, 3);
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      cm.vGoto(primitive);

      // primitiveMath();
      cm.vLabel(primitive, line);

      if (popValued) {
        mv.visitVarInsn(ALOAD, 3);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitVarInsn(ALOAD, 4);
        cm.vInvokeFunc(Double.class, "doubleValue");
      }

      primitiveOp.run();

      cm.vInvokeStatic(Double.class, "valueOf", D); // Must TO Double(Object)
      mv.visitVarInsn(ASTORE, 5);
      cm.vGoto(saveRes);

      // useMetaOp()
      cm.vLabel(useMetaOp1, line);
      mv.visitInsn(POP);

      cm.vLabel(useMetaOp2, line);
      cm.vThis();
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitLdcInsn(meta_op);
      cm.vInvokeFunc(LuaScript.class, "metaOp", O, O, S);
      mv.visitVarInsn(ASTORE, 5);
      cm.vGoto(saveRes);
    }
    // saveRes()
    cm.vLabel(saveRes, line);
    cm.vField("callFrame");
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, 5);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_unm() {
    int a = getA8(op);
    int b = getB9(op);

      /*
      Object aObj = callFrame.get(b);
      Double aDouble = KahluaUtil.rawTonumber(aObj);
      Object res;
      if (aDouble != null) {
        res = KahluaUtil.toDouble(-KahluaUtil.fromDouble(aDouble));
      } else {
        Object metafun = getMetaOp(aObj, "__unm");
        //BaseLib.luaAssert(metafun != null, "__unm not defined for operand");
        res = call(metafun, aObj, null, null);
      }
      callFrame.set(a, res);*/
    Label save = new Label();
    Label useDouble = new Label();

    cm.vField("callFrame");
    cm.vInt(b);
    cm.vInvokeFieldFunc("callFrame", "get", I);
    mv.visitVarInsn(ASTORE, 1);

    cm.vThis();
    mv.visitVarInsn(ALOAD, 1);
    cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
    mv.visitVarInsn(ASTORE, 2);

    mv.visitVarInsn(ALOAD, 2);
    mv.visitJumpInsn(IFNULL, useDouble);

    // use meta op
    cm.vThis();
    mv.visitVarInsn(ALOAD, 1);
    cm.vString("__unm");
    cm.vInvokeFunc(LuaScript.class, "getMetaOp", O, S);
    mv.visitVarInsn(ASTORE, 3);
    cm.vGoto(save);

    // use double
    cm.vLabel(useDouble, line);
    mv.visitVarInsn(ALOAD, 2);
    cm.vInvokeFunc(Double.class, "doubleValue");
    mv.visitInsn(DNEG);
    cm.vInvokeStatic(Double.class, "valueOf", D);
    mv.visitVarInsn(ASTORE, 3);

    // save
    cm.vLabel(save, line);
    cm.vField("callFrame");
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, 3);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_not() {
    int a = getA8(op);
    int b = getB9(op);

    //Object aObj = callFrame.get(b);
    //callFrame.set(a, KahluaUtil.toBoolean(!KahluaUtil.boolEval(aObj)));
    Label setFalse = new Label();
    Label setTrue = new Label();
    Label save = new Label();

    cm.vField("callFrame");
    cm.vInt(b);
    cm.vInvokeFieldFunc("callFrame", "get", I);
    mv.visitVarInsn(ASTORE, 1);

    mv.visitVarInsn(ALOAD, 1);
    mv.visitJumpInsn(IFNULL, setFalse);
    mv.visitVarInsn(ALOAD, 1);
    cm.vStatic(cm.getField(Boolean.class, "FALSE"));
    mv.visitJumpInsn(IF_ACMPEQ, setFalse);
    cm.vGoto(setTrue);

    cm.vLabel(setFalse, line);
    cm.vStatic(cm.getField(Boolean.class, "FALSE"));
    mv.visitVarInsn(ASTORE, 2);
    cm.vGoto(save);

    cm.vLabel(setTrue, line);
    cm.vStatic(cm.getField(Boolean.class, "TRUE"));
    mv.visitVarInsn(ASTORE, 2);
    cm.vGoto(save);

    cm.vLabel(save, line);
    cm.vField("callFrame");
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, 2);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_len() {
    int a = getA8(op);
    int b = getB9(op);

      /*
      Object o = callFrame.get(b);
      Object res;
      if (o instanceof KahluaTable) {
        KahluaTable t = (KahluaTable) o;
        res = KahluaUtil.toDouble(t.len());
      }
      else if (o instanceof String) {
        String s = (String) o;
        res = KahluaUtil.toDouble(s.length());
      }
      else {
        Object f = getMetaOp(o, "__len");
        KahluaUtil.luaAssert(f != null, "__len not defined for operand");
        res = call(f, o, null, null);
      }
      callFrame.set(a, res);*/
    Label isString = new Label();
    Label isTable = new Label();
    Label isMeta = new Label();
    Label save = new Label();
    Label throwError = new Label();

    cm.vField("callFrame");
    cm.vInt(b);
    cm.vInvokeFieldFunc("callFrame", "get", I);
    mv.visitVarInsn(ASTORE, 1);

    mv.visitVarInsn(ALOAD, 1);
    cm.vIsof(String.class, isString);
    mv.visitVarInsn(ALOAD, 1);
    cm.vIsof(KahluaTable.class, isTable);
    cm.vGoto(isMeta);

    cm.vLabel(isString, line);
    {
      mv.visitVarInsn(ALOAD, 1);
      cm.vCast(String.class);
      cm.vInvokeFunc(String.class, "length");
      mv.visitInsn(I2D);
      cm.vInvokeStatic(Double.class, "valueOf", D);
      mv.visitVarInsn(ASTORE, 2);
    }
    cm.vGoto(save);

    cm.vLabel(isTable, line);
    {
      mv.visitVarInsn(ALOAD, 1);
      cm.vCast(KahluaTable.class);
      cm.vInvokeFunc(KahluaTable.class, "len");
      mv.visitInsn(I2D);
      cm.vInvokeStatic(Double.class, "valueOf", D);
      mv.visitVarInsn(ASTORE, 2);
    }
    cm.vGoto(save);

    cm.vLabel(isMeta, line);
    {
      cm.vThis();
      mv.visitVarInsn(ALOAD, 1);
      cm.vString("__len");
      cm.vInvokeFunc(LuaScript.class, "getMetaOp", O, S);
      mv.visitVarInsn(ASTORE, 3);

      mv.visitVarInsn(ALOAD, 3);
      mv.visitJumpInsn(IFNULL, throwError);

      cm.vThis();
      mv.visitVarInsn(ALOAD, 3);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ACONST_NULL);
      cm.vInvokeFunc(LuaScript.class, "call", O, O, O, O);
      mv.visitVarInsn(ASTORE, 2);
    }
    cm.vGoto(save);

    cm.vLabel(throwError, line);
    {
      cm.vThrow("__len not defined for operand");
    }

    cm.vLabel(save, line);
    cm.vField("callFrame");
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, 2);
    cm.vInvokeFieldFunc("callFrame", "set", I, O);
  }

  void op_concat() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInt(c);
    cm.vInvokeFunc(LuaScript.class, "need_rebuild_op_concat", I,I,I);
  }

  void op_jmp() {
    if (true) return;
    // callFrame.pc += getSBx(op);
    int i = callFrame.pc + getSBx(op);
    Label jumpto = this.labels[i];
    cm.vGoto(jumpto);
  }

  // eq lt le
  void op_eq() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInt(c);
    cm.vInt(opcode);
    cm.vInvokeFunc(LuaScript.class, "need_rebuild_op_eq", I,I,I,I);
  }

  void op_lt() {
    op_eq();
  }

  void op_le() {
    op_eq();
  }

  void op_test() {
    int a = getA8(op);
    // b = getB9(op);
    int c = getC9(op);
    boolean eqt = (c == 0);

      /*
      Object value = callFrame.get(a);
      if (KahluaUtil.boolEval(value) == (c == 0)) {
        callFrame.pc++;
      }*/
    Label jumpto = this.labels[pc + 1];
    Label end = new Label();
    Label trueV = new Label();
    Label falseV = new Label();
    Label check = new Label();

    cm.vField("callFrame");
    cm.vInt(a);
    cm.vInvokeFieldFunc("callFrame", "get", I);
    mv.visitVarInsn(ASTORE, 1);

    mv.visitVarInsn(ALOAD, 1);
    mv.visitJumpInsn(IFNULL, falseV);
    cm.vStatic(cm.getField(Boolean.class, "FALSE"));
    mv.visitJumpInsn(IF_ACMPEQ, falseV);
    cm.vGoto(trueV);

    cm.vLabel(falseV, line);
    cm.vBoolean(false);
    cm.vGoto(check);

    cm.vLabel(trueV, line);
    cm.vBoolean(true);
    cm.vGoto(check);

    cm.vLabel(check, line);
    cm.vBoolean(eqt);
    mv.visitJumpInsn(IF_ACMPEQ, jumpto);
    cm.vGoto(end);

    cm.vLabel(end, line);
  }

  void op_testset() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);
    boolean eqt = (c == 0);

      /*
      Object value = callFrame.get(b);
      if (KahluaUtil.boolEval(value) != (c == 0)) {
        callFrame.set(a, value);
      } else {
        callFrame.pc++;
      } */
    Label jumpto = this.labels[pc + 1];
    Label setvalue = new Label();
    Label trueV = new Label();
    Label falseV = new Label();
    Label check = new Label();

    cm.vField("callFrame");
    cm.vInt(b);
    cm.vInvokeFieldFunc("callFrame", "get", I);
    mv.visitVarInsn(ASTORE, 1);

    mv.visitVarInsn(ALOAD, 1);
    mv.visitJumpInsn(IFNULL, falseV);
    cm.vStatic(cm.getField(Boolean.class, "FALSE"));
    mv.visitJumpInsn(IF_ACMPEQ, falseV);
    cm.vGoto(trueV);

    cm.vLabel(falseV, line);
    cm.vBoolean(false);
    cm.vGoto(check);

    cm.vLabel(trueV, line);
    cm.vBoolean(true);
    cm.vGoto(check);

    cm.vLabel(check, line);
    cm.vBoolean(eqt);
    mv.visitJumpInsn(IF_ACMPEQ, jumpto);
    cm.vGoto(setvalue);

    cm.vLabel(setvalue, line);
    cm.vField("callFrame");
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, 1);
    cm.vInvokeFieldFunc("callFrame", "set", I,O);
  }

  void op_call() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);
    currentCoroutine.toString();
  }

  void op_tailcall() {
    int base = callFrame.localBase;

    currentCoroutine.closeUpvalues(base);

    int a = getA8(op);
    int b = getB9(op);
    int nArguments2 = b - 1;
    if (nArguments2 == -1) {
      nArguments2 = callFrame.getTop() - a - 1;
    }

    callFrame.restoreTop = false;

    Object funObject = callFrame.get(a);
    KahluaUtil.luaAssert(funObject != null, "Tried to call nil");
    Object fun = prepareMetatableCall(funObject);
    if (fun == null) {
      KahluaUtil.fail(("Object " + funObject + " did not have __call metatable set"));
    }

    int localBase2 = returnBase + 1;

    // If it's a metatable __call, prepend the caller as the
    // first argument
    if (fun != funObject) {
      localBase2 = returnBase;
      nArguments2++;
    }

    currentCoroutine.stackCopy(base + a, returnBase,
      nArguments2 + 1);
    currentCoroutine.setTop(returnBase + nArguments2 + 1);

    if (fun instanceof LuaClosure) {
      callFrame.localBase = localBase2;
      callFrame.nArguments = nArguments2;
      callFrame.closure = (LuaClosure) fun;
      callFrame.init();
    } else {
      if (!(fun instanceof JavaFunction)) {
        KahluaUtil.fail(("Tried to call a non-function: " + fun));
      }
      Coroutine oldCoroutine = currentCoroutine;
      callJava((JavaFunction) fun, localBase2, returnBase,
        nArguments2);

      callFrame = currentCoroutine.currentCallFrame();
      oldCoroutine.popCallFrame();

      if (oldCoroutine != currentCoroutine) {
        if (oldCoroutine.isDead()) {
          if (oldCoroutine == rootCoroutine) {
            // do something clever here
          } else if (currentCoroutine.getParent() == oldCoroutine) {
            currentCoroutine.resume(oldCoroutine.getParent());
            oldCoroutine.destroy();

            // This is an implicit yield, so push a TRUE
            // to the parent
            currentCoroutine.getParent().currentCallFrame()
              .push(Boolean.TRUE);
          }
        }

        callFrame = currentCoroutine.currentCallFrame();
        if (callFrame.isJava()) {
          return;
        }
      } else {
        if (!callFrame.fromLua) {
          return;
        }
        callFrame = currentCoroutine.currentCallFrame();

        if (callFrame.restoreTop) {
          callFrame
            .setTop(callFrame.closure.prototype.maxStacksize);
        }
      }
    }

    closure = callFrame.closure;
    prototype = closure.prototype;
    opcodes = prototype.code;
    returnBase = callFrame.returnBase;
  }

  void op_return() {
    int a = getA8(op);
    int b = getB9(op) - 1;

    int base = callFrame.localBase;
    currentCoroutine.closeUpvalues(base);

    if (b == -1) {
      b = callFrame.getTop() - a;
    }

    currentCoroutine.stackCopy(callFrame.localBase + a,
      returnBase, b);
    currentCoroutine.setTop(returnBase + b);

    if (callFrame.fromLua) {
      if (callFrame.canYield
        && currentCoroutine.atBottom()) {
        callFrame.localBase = callFrame.returnBase;
        Coroutine coroutine = currentCoroutine;
        Coroutine.yieldHelper(callFrame, callFrame, b);
        coroutine.popCallFrame();

        // If this coroutine is called from a java function,
        // return immediately
        callFrame = currentCoroutine.currentCallFrame();
        if (callFrame == null || callFrame.isJava()) {
          return;
        }
      } else {
        currentCoroutine.popCallFrame();
      }
      callFrame = currentCoroutine.currentCallFrame();

      closure = callFrame.closure;
      prototype = closure.prototype;
      opcodes = prototype.code;
      returnBase = callFrame.returnBase;

      if (callFrame.restoreTop) {
        callFrame.setTop(prototype.maxStacksize);
      }
    } else {
      currentCoroutine.popCallFrame();
      return;
    }
  }

  void op_forprep() {
    int a = getA8(op);
    int b = getSBx(op);

    double iter = KahluaUtil.fromDouble(callFrame.get(a));
    double step = KahluaUtil.fromDouble(callFrame.get(a + 2));
    callFrame.set(a, KahluaUtil.toDouble(iter - step));
    callFrame.pc += b;
  }

  void op_forloop() {
    int a = getA8(op);

    double iter = KahluaUtil.fromDouble(callFrame.get(a));
    double end = KahluaUtil.fromDouble(callFrame.get(a + 1));
    double step = KahluaUtil.fromDouble(callFrame.get(a + 2));
    iter += step;
    Double iterDouble = KahluaUtil.toDouble(iter);
    callFrame.set(a, iterDouble);

    if ((step > 0) ? iter <= end : iter >= end) {
      int b = getSBx(op);
      callFrame.pc += b;
      callFrame.set(a + 3, iterDouble);
    } else {
      callFrame.clearFromIndex(a);
    }
  }

  void op_tforloop() {
    int a = getA8(op);
    int c = getC9(op);

    callFrame.setTop(a + 6);
    callFrame.stackCopy(a, a + 3, 3);
    call(2);
    callFrame.clearFromIndex(a + 3 + c);
    callFrame.setPrototypeStacksize();

    Object aObj3 = callFrame.get(a + 3);
    if (aObj3 != null) {
      callFrame.set(a + 2, aObj3);
    } else {
      callFrame.pc++;
    }
  }

  void op_setlist() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    if (b == 0) {
      b = callFrame.getTop() - a - 1;
    }

    if (c == 0) {
      c = opcodes[callFrame.pc++];
    }

    int offset = (c - 1) * FIELDS_PER_FLUSH;

    KahluaTable t = (KahluaTable) callFrame.get(a);
    for (int i = 1; i <= b; i++) {
      Object key = KahluaUtil.toDouble(offset + i);
      Object value = callFrame.get(a + i);
      t.rawset(key, value);
    }
  }

  void op_close() {
    int a = getA8(op);
    callFrame.closeUpvalues(a);
  }

  void op_closure() {
    int a = getA8(op);
    int b = getBx(op);
    Prototype newPrototype = prototype.prototypes[b];
    LuaClosure newClosure = new LuaClosure(newPrototype,
      closure.env);
    callFrame.set(a, newClosure);
    int numUpvalues = newPrototype.numUpvalues;
    for (int i = 0; i < numUpvalues; i++) {
      op = opcodes[callFrame.pc++];
      opcode = op & 63;
      b = getB9(op);
      switch (opcode) {
        case OP_MOVE: {
          newClosure.upvalues[i] = callFrame.findUpvalue(b);
          break;
        }
        case OP_GETUPVAL: {
          newClosure.upvalues[i] = closure.upvalues[b];
          break;
        }
        default:
          // should never happen
      }
    }
  }

  void op_vararg() {
    int a = getA8(op);
    int b = getB9(op) - 1;

    callFrame.pushVarargs(a, b);
  }


  public static void test_every_lua_code(Platform platform, int endIndex)
      throws Exception {
    final String line = " -------------------------------------------- ";
    Prototype p = new Prototype();
    p.name = "./testsuite/lua/testhelper.lua";
    p.constants = new Object[]{};
    p.lines = new int[opNames.length];

    KahluaTable env = platform.newTable();
    LuaClosure lc = new LuaClosure(p, env);
    Coroutine cr = new Coroutine(platform, env, this);
    cr.pushNewCallFrame(lc, null, 0,0,0, false, true);
    LuaBuilder luab = new LuaBuilder(cr);
    luab.op = (1<<6) | (2<<14) | (3<<23);
    luab.labels = new Label[p.lines.length];

    luab.cm.defaultInit();
    luab.mv = luab.cm.beginMethod("run");

    for (int i=0; i<p.lines.length; ++i) {
      p.lines[i] = i;
      luab.labels[i] = new Label();
    }

    for (int i=0; i<=endIndex; ++i) {
      luab.pc = i+1;
      luab.op = ((1)<<6) | ((3)<<14) | ((2)<<23);
      Label label = luab.labels[i];
      luab.line = p.lines[i];

      Method m = LuaBuilder.class.getDeclaredMethod(opNames[i].toLowerCase());
      Tool.pl(">> ", m.getName(), "()");

      luab.mv.visitLabel(label);
      luab.mv.visitLineNumber(p.lines[i], label);
      luab.mv.visitLdcInsn(line + m.getName() +" ( "+ i +" )");
      luab.cm.vInvokeStatic(Tool.class, "pl", Object.class);
      m.invoke(luab);

      Tool.pl("> ok");
    }

    luab.mv.visitLdcInsn("END / "+ new Date().toString());
    luab.cm.vInvokeStatic(Tool.class, "pl", Object.class);
    luab.cm.endMethod();

    LuaScript agent = luab.createJavaAgent();
    agent.reinit(this, cr);
    agent.run();
  }
}
