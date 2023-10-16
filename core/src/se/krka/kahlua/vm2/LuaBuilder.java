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
import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static se.krka.kahlua.vm.KahluaThread.*;
import static se.krka.kahlua.vm2.KahluaThread2.opNames;

/**
 * @link https://the-ravi-programming-language.readthedocs.io/en/latest/lua_bytecode_reference.html
 */
public class LuaBuilder implements ClassMaker.IConst {

  final String classPath;
  final String className;
  protected MethodVisitor mv;
  protected final ClassMaker cm;
  protected final List<ClosureInf> plist;

  protected Label[] labels;
  protected Label label;
  protected int pc;
  protected int opcode;
  protected int op;
  protected int line;
  protected int id = 1;


  public LuaBuilder(String _classPath) {
    this.classPath = _classPath;
    this.className = Tool.formatClassName(classPath);
    this.cm = new ClassMaker(className);
    this.plist = new ArrayList<>(100);
  }


  public void makeJavacode(Prototype p) {
    cm.defaultInit();
    ClosureInf root = pushClosure(p, "run");
    newClosureFunction(root);
  }


  private void newClosureFunction(ClosureInf ci) {
    final int startIndex = plist.size();
    mv = cm.beginMethod(ci.funcName);
    cm.vClosureFunctionHeader(ci);

    final int[] opcodes = ci.prototype.code;
    labels = new Label[opcodes.length];

    for (int i=0; i<opcodes.length; ++i) {
      labels[i] = new Label();
    }

    int npc = 0;
    while (npc < opcodes.length) {
      pc = npc++;
      op = opcodes[pc];
      opcode = op & 0x3F;

      line = ci.prototype.lines[pc];
      label = labels[pc];

      mv.visitLabel(label);
      mv.visitLineNumber(line, label);

      Tool.pl("LL ",pc, Integer.toHexString(op), opNames[opcode], line);
      do_op_code(opcode, ci);
    }

    cm.vClosureFunctionFoot(ci);
    cm.endMethod();
    processSubClosure(startIndex, plist.size());
  }


  private void processSubClosure(int start, int end) {
    for (int i=start; i<end; ++i) {
      ClosureInf ci = plist.get(i);
      newClosureFunction(ci);
    }
  }


  protected void do_op_code(int opcode, ClosureInf ci) {
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
      case OP_CLOSURE: op_closure(ci); break;
      case OP_VARARG: op_vararg(); break;
    }
  }


  public LuaScript createJavaAgent() throws InvocationTargetException,
      NoSuchMethodException, InstantiationException, IllegalAccessException {
    LuaScript ls = cm.newInstance();
    ls.setClosureInf(plist);
    return ls;
  }


  private ClosureInf pushClosure(Prototype sp, String funcName) {
    int index = plist.size();
    ClosureInf inf = new ClosureInf(sp, index, funcName);
    plist.add(inf);
    return inf;
  }


  String closureFuncName() {
    return "closure_"+ (id++);
  }


  void op_closure(ClosureInf ci) {
    int a = getA8(op);
    int b = getBx(op);

    ClosureInf subci = pushClosure(ci.prototype.prototypes[b], closureFuncName());
    int pi = subci.arrIndex;

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInt(pi);
    mv.visitVarInsn(ALOAD, vCallframe);
    mv.visitVarInsn(ALOAD, vPrototype);
    cm.vInvokeFunc(LuaScript.class, "auto_op_closure", I,I,I,
      LuaCallFrame.class, Prototype.class);

    Tool.pl("New closure", a, b, pi);
  }


  void op_move() {
    int a = getA8(op);
    int b = getB9(op);

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(b);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }

  void op_loadk() {
    int a = getA8(op);
    int b = getBx(op);

    //callFrame.set(a, prototype.constants[b]);

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, vPrototype);
    cm.vField(Prototype.class, "constants");
    cm.vInt(b);
    mv.visitInsn(AALOAD);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }

  // TODO: op is changed on running ???
  void op_loadbool() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);
    String v = (b == 0 ? "FALSE" : "TRUE");

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    cm.vStatic(cm.getField(Boolean.class, v));
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);

    if (c != 0) {
      cm.vGoto(labels[pc + 1]);
    }
  }

  void op_loadnil() {
    int a = getA8(op);
    int b = getB9(op);

    //callFrame.stackClear(a, b);
    mv.visitVarInsn(ALOAD, vCallframe);
    mv.visitLdcInsn(a);
    mv.visitLdcInsn(b);
    cm.vInvokeFunc(LuaCallFrame.class, "stackClear", I, I);
  }

  void op_getupval() {
    int a = getA8(op);
    int b = getB9(op);

    //callFrame.set(a, closure.upvalues[b].getValue());
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);

    mv.visitVarInsn(ALOAD, vClosure);
    cm.vField(LuaClosure.class, "upvalues");
    mv.visitLdcInsn(b);
    mv.visitInsn(AALOAD);

    cm.vInvokeFunc(UpValue.class, "getValue");
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }

  void op_getglobal() {
    int a = getA8(op);
    int b = getBx(op);

    //Object res = tableGet(closure.env, prototype.constants[b]);
    //callFrame.set(a, res);

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    cm.vThis();

    mv.visitVarInsn(ALOAD, vClosure);
    cm.vField(LuaClosure.class, "env");

    mv.visitVarInsn(ALOAD, vPrototype);
    cm.vField(Prototype.class, "constants");
    cm.vInt(b);
    mv.visitInsn(AALOAD);

    cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }

  void op_gettable() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    cm.vThis();

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(b);
    //Object bObj = callFrame.get(b);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);

    cm.vThis();
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(c);
    mv.visitVarInsn(ALOAD, vPrototype);

    //Object key = getRegisterOrConstant(callFrame, c, prototype);
    cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

    //Object res = tableGet(bObj, key);
    cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);

    //callFrame.set(a, res);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }

  void op_setglobal() {
    int a = getA8(op);
    int b = getBx(op);

    //Object value = callFrame.get(a);
    //Object key = prototype.constants[b];
    //tableSet(closure.env, key, value);
    cm.vThis();

    mv.visitVarInsn(ALOAD, vClosure);
    cm.vField(LuaClosure.class, "env");

    mv.visitVarInsn(ALOAD, vPrototype);
    cm.vField(Prototype.class, "constants");
    cm.vInt(b);
    mv.visitInsn(AALOAD);

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);
    cm.vInvokeFunc(LuaScript.class, "tableSet", O, O, O);
  }

  void op_setupval() {
    int a = getA8(op);
    int b = getB9(op);

    //UpValue uv = closure.upvalues[b];
    //uv.setValue(callFrame.get(a));
    mv.visitVarInsn(ALOAD, vClosure);
    cm.vField(LuaClosure.class, "upvalues");
    mv.visitLdcInsn(b);
    mv.visitInsn(AALOAD);

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);

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

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);

    cm.vThis();
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(b);
    mv.visitVarInsn(ALOAD, vPrototype);
    cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

    cm.vThis();
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(c);
    mv.visitVarInsn(ALOAD, vPrototype);
    cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);

    cm.vInvokeFunc(LuaScript.class, "tableSet", O, O, O);
  }

  void op_newtable() {
    int a = getA8(op);

    //KahluaTable t = platform.newTable();
    //callFrame.set(a, t);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, vPlatform);
    cm.vInvokeFunc(Platform.class, "newTable");
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }

  void op_self() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    final int bObj = vUser + 1;

    /*
      Object bObj = callFrame.get(b);
      callFrame.set(a + 1, bObj);
      Object key = getRegisterOrConstant(callFrame, c, prototype);
      Object fun = tableGet(bObj, key);
      callFrame.set(a, fun); */

    mv.visitVarInsn(ALOAD, vCallframe);
    {
      mv.visitVarInsn(ALOAD, vCallframe);
      cm.vInt(b);
      cm.vInvokeFunc(LuaCallFrame.class, "get", I);
      mv.visitVarInsn(ASTORE, bObj);
    }
    cm.vInt(a+1);
    mv.visitVarInsn(ALOAD, bObj);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    {
      cm.vThis();
      mv.visitVarInsn(ALOAD, bObj);
      {
        cm.vThis();
        mv.visitVarInsn(ALOAD, vCallframe);
        cm.vInt(c);
        mv.visitVarInsn(ALOAD, vPrototype);
        cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
      }
      cm.vInvokeFunc(LuaScript.class, "tableGet", O, O);
    }
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
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
      final int bd = vUser +3;
      final int cd = vUser +4;
      mv.visitVarInsn(ALOAD, vPlatform);
      mv.visitVarInsn(ALOAD, bd);
      cm.vInvokeFunc(Double.class, "doubleValue");
      mv.visitVarInsn(ALOAD, cd);
      cm.vInvokeFunc(Double.class, "doubleValue");
      cm.vInvokeFunc(Platform.class, "pow", D, D);
    });
  }

  void op_mod() {
    math_cal("__mod", false, ()->{
      final int bd = vUser +3;
      final int cd = vUser +4;

      Label v2iszero = new Label();
      Label end = new Label();

      mv.visitVarInsn(ALOAD, cd);
      cm.vInvokeFunc(Double.class, "doubleValue");
      cm.vDouble(0);
      mv.visitInsn(DCMPL);
      mv.visitJumpInsn(IFEQ, v2iszero); // if v4 == 0 goto v2iszero

      // v4 != 0
      mv.visitVarInsn(ALOAD, bd);
      cm.vInvokeFunc(Double.class, "doubleValue");
      {
        mv.visitVarInsn(ALOAD, bd);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitVarInsn(ALOAD, cd);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitInsn(DDIV);
        mv.visitInsn(D2I);
      }
      mv.visitInsn(I2D);
      mv.visitVarInsn(ALOAD, cd);
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
    final int bo = vUser +1;
    final int co = vUser +2;
    final int bd = vUser +3;
    final int cd = vUser +4;
    final int res = vUser +5;

    Label saveRes = new Label();
    Label useMetaOp1 = new Label();
    Label useMetaOp2 = new Label();
    Label primitive = new Label();

    {
      cm.vThis();
      mv.visitVarInsn(ALOAD, vCallframe);
      cm.vInt(b);
      mv.visitVarInsn(ALOAD, vPrototype);
      cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
      mv.visitVarInsn(ASTORE, bo);

      cm.vThis();
      mv.visitVarInsn(ALOAD, vCallframe);
      cm.vInt(c);
      mv.visitVarInsn(ALOAD, vPrototype);
      cm.vInvokeFunc(LuaScript.class, "getRegisterOrConstant", FR, I, PT);
      mv.visitVarInsn(ASTORE, co);

      cm.vThis();
      mv.visitVarInsn(ALOAD, bo);
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      mv.visitVarInsn(ASTORE, bd);

      cm.vThis();
      mv.visitVarInsn(ALOAD, co);
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      mv.visitVarInsn(ASTORE, cd);

      // if (bd == null || cd == null) then useMetaOp();
      mv.visitVarInsn(ALOAD, bd);
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      mv.visitVarInsn(ALOAD, cd);
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      cm.vGoto(primitive);

      // primitiveMath();
      cm.vLabel(primitive, line);

      if (popValued) {
        mv.visitVarInsn(ALOAD, bd);
        cm.vInvokeFunc(Double.class, "doubleValue");
        mv.visitVarInsn(ALOAD, cd);
        cm.vInvokeFunc(Double.class, "doubleValue");
      }

      primitiveOp.run();

      cm.vInvokeStatic(Double.class, "valueOf", D); // Must TO Double(Object)
      mv.visitVarInsn(ASTORE, res);
      cm.vGoto(saveRes);

      // useMetaOp()
      cm.vLabel(useMetaOp1, line);
      mv.visitInsn(POP);

      cm.vLabel(useMetaOp2, line);
      cm.vThis();
      mv.visitVarInsn(ALOAD, bo);
      mv.visitVarInsn(ALOAD, co);
      mv.visitLdcInsn(meta_op);
      cm.vInvokeFunc(LuaScript.class, "metaOp", O, O, S);
      mv.visitVarInsn(ASTORE, res);
      cm.vGoto(saveRes);
    }
    // saveRes()
    cm.vLabel(saveRes, line);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, res);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I,O);
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

    final int aObj = vUser +1;
    final int aDouble = vUser +2;
    final int res = vUser +3;

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(b);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);
    mv.visitVarInsn(ASTORE, aObj);

    cm.vThis();
    mv.visitVarInsn(ALOAD, aObj);
    cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
    mv.visitVarInsn(ASTORE, aDouble);

    mv.visitVarInsn(ALOAD, aDouble);
    mv.visitJumpInsn(IFNULL, useDouble);

    // use meta op
    cm.vThis();
    mv.visitVarInsn(ALOAD, aObj);
    cm.vString("__unm");
    cm.vInvokeFunc(LuaScript.class, "getMetaOp", O, S);
    mv.visitVarInsn(ASTORE, res);
    cm.vGoto(save);

    // use double
    cm.vLabel(useDouble, line);
    mv.visitVarInsn(ALOAD, aDouble);
    cm.vInvokeFunc(Double.class, "doubleValue");
    mv.visitInsn(DNEG);
    cm.vInvokeStatic(Double.class, "valueOf", D);
    mv.visitVarInsn(ASTORE, res);

    // save
    cm.vLabel(save, line);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, res);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
  }

  void op_not() {
    int a = getA8(op);
    int b = getB9(op);

    //Object aObj = callFrame.get(b);
    //callFrame.set(a, KahluaUtil.toBoolean(!KahluaUtil.boolEval(aObj)));
    Label setFalse = new Label();
    Label setTrue = new Label();
    Label save = new Label();

    final int aObj = vUser +1;
    final int isnot = vUser +2;

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(b);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);
    mv.visitVarInsn(ASTORE, aObj);

    mv.visitVarInsn(ALOAD, aObj);
    mv.visitJumpInsn(IFNULL, setFalse);
    mv.visitVarInsn(ALOAD, aObj);
    cm.vStatic(cm.getField(Boolean.class, "FALSE"));
    mv.visitJumpInsn(IF_ACMPEQ, setFalse);
    cm.vGoto(setTrue);

    cm.vLabel(setFalse, line);
    cm.vStatic(cm.getField(Boolean.class, "FALSE"));
    mv.visitVarInsn(ASTORE, isnot);
    cm.vGoto(save);

    cm.vLabel(setTrue, line);
    cm.vStatic(cm.getField(Boolean.class, "TRUE"));
    mv.visitVarInsn(ASTORE, isnot);
    cm.vGoto(save);

    cm.vLabel(save, line);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, isnot);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I, O);
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

    final int obj = vUser +1;
    final int res = vUser +2;
    final int tbl = vUser +3;

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(b);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);
    mv.visitVarInsn(ASTORE, obj);

    mv.visitVarInsn(ALOAD, obj);
    cm.vIsof(String.class, isString);
    mv.visitVarInsn(ALOAD, obj);
    cm.vIsof(KahluaTable.class, isTable);
    cm.vGoto(isMeta);

    cm.vLabel(isString, line);
    {
      mv.visitVarInsn(ALOAD, obj);
      cm.vCast(String.class);
      cm.vInvokeFunc(String.class, "length");
      mv.visitInsn(I2D);
      cm.vInvokeStatic(Double.class, "valueOf", D);
      mv.visitVarInsn(ASTORE, res);
    }
    cm.vGoto(save);

    cm.vLabel(isTable, line);
    {
      mv.visitVarInsn(ALOAD, obj);
      cm.vCast(KahluaTable.class);
      cm.vInvokeFunc(KahluaTable.class, "len");
      mv.visitInsn(I2D);
      cm.vInvokeStatic(Double.class, "valueOf", D);
      mv.visitVarInsn(ASTORE, res);
    }
    cm.vGoto(save);

    cm.vLabel(isMeta, line);
    {
      cm.vThis();
      mv.visitVarInsn(ALOAD, obj);
      cm.vString("__len");
      cm.vInvokeFunc(LuaScript.class, "getMetaOp", O, S);
      mv.visitVarInsn(ASTORE, tbl);

      mv.visitVarInsn(ALOAD, tbl);
      mv.visitJumpInsn(IFNULL, throwError);

      cm.vThis();
      mv.visitVarInsn(ALOAD, 3);
      mv.visitVarInsn(ALOAD, obj);
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ACONST_NULL);
      cm.vInvokeFunc(LuaScript.class, "call", O, O, O, O);
      mv.visitVarInsn(ASTORE, res);
    }
    cm.vGoto(save);

    cm.vLabel(throwError, line);
    {
      cm.vThrow("__len not defined for operand");
    }

    cm.vLabel(save, line);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, res);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I,O);
  }

  void op_concat() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInt(c);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInvokeFunc(LuaScript.class, "auto_op_concat", I,I,I, LuaCallFrame.class);
  }

  void op_jmp() {
    int i = pc + getSBx(op);
    cm.vGoto(this.labels[i]);
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
    mv.visitVarInsn(ALOAD, vCallframe);
    mv.visitVarInsn(ALOAD, vPrototype);
    cm.vInvokeFunc(LuaScript.class, "auto_op_eq", I,I,I,I,
      LuaCallFrame.class, Prototype.class);
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

    final int value = vUser +1;

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);
    mv.visitVarInsn(ASTORE, value);

    mv.visitVarInsn(ALOAD, value);
    mv.visitJumpInsn(IFNULL, falseV);
    mv.visitVarInsn(ALOAD, value);
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
    mv.visitJumpInsn(IF_ICMPEQ, jumpto);
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

    final int value = vUser +1;

    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(b);
    cm.vInvokeFunc(LuaCallFrame.class, "get", I);
    mv.visitVarInsn(ASTORE, value);

    mv.visitVarInsn(ALOAD, value);
    mv.visitJumpInsn(IFNULL, falseV);
    mv.visitVarInsn(ALOAD, value);
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
    mv.visitJumpInsn(IF_ICMPEQ, jumpto);
    cm.vGoto(setvalue);

    cm.vLabel(setvalue, line);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, value);
    cm.vInvokeFunc(LuaCallFrame.class, "set", I,O);
  }

  void op_call() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInt(c);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInvokeFunc(LuaScript.class, "auto_op_call", I,I,I, LuaCallFrame.class);
  }

  void op_tailcall() {
    int a = getA8(op);
    int b = getB9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInvokeFunc(LuaScript.class, "auto_op_tailcall", I,I);
  }

  void op_return() {
    int a = getA8(op);
    int b = getB9(op) - 1;

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInvokeFunc(LuaScript.class, "auto_op_return", I,I);
  }

  void op_forprep() {
    int a = getA8(op);
    int b = getSBx(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInvokeFunc(LuaScript.class, "auto_op_forprep", I,I, LuaCallFrame.class);
  }

  void op_forloop() {
    int a = getA8(op);
    int b = getSBx(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInvokeFunc(LuaScript.class, "auto_op_forloop", I, I, LuaCallFrame.class);
  }

  void op_tforloop() {
    int a = getA8(op);
    int c = getC9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(c);
    cm.vInvokeFunc(LuaScript.class, "auto_op_tforloop", I,I);
  }

  void op_setlist() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    cm.vInt(c);
    cm.vInvokeFunc(LuaScript.class, "auto_op_setlist", I,I,I);
  }

  void op_close() {
    int a = getA8(op);

    cm.vThis();
    cm.vInt(a);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInvokeFunc(LuaScript.class, "auto_op_close", I, LuaCallFrame.class);
  }

  void op_vararg() {
    int a = getA8(op);
    int b = getB9(op) - 1;

    cm.vThis();
    cm.vInt(a);
    cm.vInt(b);
    mv.visitVarInsn(ALOAD, vCallframe);
    cm.vInvokeFunc(LuaScript.class, "auto_op_vararg", I,I, LuaCallFrame.class);
  }

  public void vDebugInf() {
    cm.vPrint(KahluaThread2.opName(opcode) +" ( "+ opcode +" ):"+ line);
  }
}
