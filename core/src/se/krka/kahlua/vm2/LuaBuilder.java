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

    cm.vSetStackVar(a, () -> {
      cm.vGetStackVar(b);
    });
  }

  void op_loadk() {
    int a = getA8(op);
    int b = getBx(op);

    cm.vSetStackVar(a, () -> {
      cm.vGetConstants(b);
    });
  }

  // TODO: op is changed on running ???
  void op_loadbool() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    String v = (b == 0 ? "FALSE" : "TRUE");

    cm.vSetStackVar(a, () -> {
      cm.vBooleanObj(b != 0);
    });

    if (c != 0) {
      cm.vGoto(labels[pc + 1]);
    }
  }

  void op_loadnil() {
    int a = getA8(op);
    int b = getB9(op);

    cm.vClearStack(a, b);
  }

  void op_getupval() {
    int a = getA8(op);
    int b = getB9(op);

    VUpvalueOp up = new VUpvalueOp(cm, mv, b, vUser);
    cm.vSetStackVar(a, () -> {
      up.getValue();
    });
  }

  void op_getglobal() {
    int a = getA8(op);
    int b = getBx(op);

    cm.vSetStackVar(a, () -> {
      cm.vGetGlobalVar(() -> {
        cm.vGetConstants(b);
      });
    });
  }

  void op_gettable() {
    final int a = getA8(op);
    final int b = getB9(op);
    final int c = getC9(op);
    final int notused = 0;

    cm.vSetStackVar(a, () -> {
      cm.vGetTableVar(new IBuildParam2() {
        public void param2() {
          cm.vGetStackVar(b);
        }
        public void param1() {
          cm.vGetRegOrConst(c, notused);
        }
      });
    });
  }

  void op_setglobal() {
    final int a = getA8(op);
    final int b = getBx(op);

    //Object value = callFrame.get(a);
    //Object key = prototype.constants[b];
    //tableSet(closure.env, key, value);

    cm.vSetTableVar(new IBuildParam3() {
      public void param1() {
        cm.vEnvironment();
      }
      public void param2() {
        cm.vGetStackVar(a);
      }
      public void param3() {
        cm.vGetConstants(b);
      }
    });
  }

  void op_setupval() {
    int a = getA8(op);
    int b = getB9(op);

    //UpValue uv = closure.upvalues[b];
    //uv.setValue(callFrame.get(a));

    VUpvalueOp upop = new VUpvalueOp(cm, mv, b, vUser);
    upop.setValue(() -> {
      cm.vGetStackVar(a);
    });
  }

  void op_settable() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    //Object aObj = callFrame.get(a);
    //Object key = getRegisterOrConstant(callFrame, b, prototype);
    //Object value = getRegisterOrConstant(callFrame, c, prototype);
    //tableSet(aObj, key, value);

    final int vTmp = 0;

    cm.vSetTableVar(new IBuildParam3() {
      public void param1() {
        cm.vGetStackVar(a);
      }
      public void param2() {
        cm.vGetRegOrConst(b, vTmp);
      }
      public void param3() {
        cm.vGetRegOrConst(c, vTmp);
      }
    });
  }

  void op_newtable() {
    int a = getA8(op);

    //KahluaTable t = platform.newTable();
    //callFrame.set(a, t);

    cm.vSetStackVar(a, ()->{
      cm.vNewTable();
    });
  }

  void op_self() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    final int tmp = 0;
    final int bObj = vUser + 1;


    /*
      Object bObj = callFrame.get(b);
      callFrame.set(a + 1, bObj);
      Object key = getRegisterOrConstant(callFrame, c, prototype);
      Object fun = tableGet(bObj, key);
      callFrame.set(a, fun); */

    cm.vGetStackVar(b);
    mv.visitVarInsn(ASTORE, bObj);

    cm.vSetStackVar(a+1, ()->{
      mv.visitVarInsn(ALOAD, bObj);
    });

    cm.vSetStackVar(a, ()->{
      cm.vGetTableVar(new IBuildParam2() {
        public void param1() {
          mv.visitVarInsn(ALOAD, bObj);
        }
        public void param2() {
          cm.vGetRegOrConst(c, tmp);
        }
      });
    });
  }

  void op_add() {
    math_cal("__add", true, (bd, cd)->{
      mv.visitInsn(DADD);
    });
  }

  void op_sub() {
    math_cal("__sub", true, (bd, cd)->{
      mv.visitInsn(DSUB);
    });
  }

  void op_mul() {
    math_cal("__mul", true, (bd, cd)->{
      mv.visitInsn(DMUL);
    });
  }

  void op_div() {
    math_cal("__div", true, (bd, cd)->{
      mv.visitInsn(DDIV);
    });
  }

  void op_pow() {
    math_cal("__pow", false, (bd, cd)->{
      mv.visitVarInsn(ALOAD, vPlatform);
      mv.visitVarInsn(ALOAD, bd);
      cm.vInvokeFunc(Double.class, "doubleValue");
      mv.visitVarInsn(ALOAD, cd);
      cm.vInvokeFunc(Double.class, "doubleValue");
      cm.vInvokeFunc(Platform.class, "pow", D, D);
    });
  }

  void op_mod() {
    math_cal("__mod", false, (bd, cd)->{
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
  void math_cal(String meta_op, boolean popValued, IMathOp primitiveOp) {
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
      callFrame.set(a, res);
     */
    final int tmp = 0;
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
      cm.vGetRegOrConst(b, tmp);
      mv.visitVarInsn(ASTORE, bo);

      cm.vGetRegOrConst(c, tmp);
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

      primitiveOp.calc(bd, cd);

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
      callFrame.set(a, res);
    */

    final int aObj = vUser +1;
    final int res = vUser +2;
    final int metafun = vUser +3;

    cm.vGetStackVar(b);

    cm.vToNumber(new IToNumber() {
      public void success() {
        mv.visitInsn(DNEG);
        cm.vInvokeStatic(Double.class, "valueOf", D);
        mv.visitVarInsn(ASTORE, res);
      }

      public void nan() {
        mv.visitVarInsn(ASTORE, aObj);

        cm.vGetMetaOp("__unm", ()->{
          mv.visitVarInsn(ALOAD, aObj);
        });

        mv.visitVarInsn(ASTORE, metafun);

        cm.vCall(new IBuildParam4() {
          public void param1() {
            mv.visitVarInsn(ALOAD, metafun);
          }
          public void param2() {
            mv.visitVarInsn(ALOAD, aObj);
          }
          public void param3() {
            cm.vNull();
          }
          public void param4() {
            cm.vNull();
          }
        });
        mv.visitVarInsn(ASTORE, res);
      }
    });

    cm.vSetStackVar(a, ()->{
      mv.visitVarInsn(ALOAD, res);
    });
  }

  void op_not() {
    int a = getA8(op);
    int b = getB9(op);

    //Object aObj = callFrame.get(b);
    //callFrame.set(a, KahluaUtil.toBoolean(!KahluaUtil.boolEval(aObj)));

    cm.vSetStackVar(a, ()->{
      cm.vGetStackVar(b);
      cm.vCopyRef();

      cm.vIf(IFNULL, new IIF() {
        public void doThen() {
          cm.vPop();
          cm.vBooleanObj(true);
        }
        public void doElse() {
          cm.vStatic(cm.getField(Boolean.class, "FALSE"));
          cm.vIf(IF_ACMPEQ, new IIF() {
            public void doThen() {
              cm.vBooleanObj(true);
            }
            public void doElse() {
              cm.vBooleanObj(false);
            }
          });
        }
      });
    });
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
      callFrame.set(a, res);
     */

    final Label end = new Label();
    final int res = vUser +1;
    final int obj = vUser +2;
    final int func = vUser +3;
    cm.vGetStackVar(b);
    cm.vCopyRef();
    mv.visitVarInsn(ASTORE, obj);

    cm.vIf(KahluaTable.class, new IIFwe() {
      public void doThen() {
        mv.visitVarInsn(ALOAD, obj);
        cm.vCast(KahluaTable.class);
        cm.vInvokeFunc(KahluaTable.class, "len");
        mv.visitInsn(I2D);
        cm.vInvokeStatic(Double.class, "valueOf", D);
        mv.visitVarInsn(ASTORE, res);
        cm.vGoto(end);
      }
    });

    mv.visitVarInsn(ALOAD, obj);
    cm.vIf(String.class, new IIFwe() {
      public void doThen() {
        mv.visitVarInsn(ALOAD, obj);
        cm.vCast(String.class);
        cm.vInvokeFunc(String.class, "length");
        mv.visitInsn(I2D);
        cm.vInvokeStatic(Double.class, "valueOf", D);
        mv.visitVarInsn(ASTORE, res);
        cm.vGoto(end);
      }
    });

    cm.vGetMetaOp("__len", ()->{
      mv.visitVarInsn(ALOAD, obj);
    });
    cm.vCopyRef();

    mv.visitVarInsn(ASTORE, func);
    cm.vIf(IFNULL, new IIFwe() {
      public void doThen() {
        cm.vThrow("__len not defined for operand");
      }
    });

    cm.vCall(new IBuildParam4() {
      public void param1() {
        mv.visitVarInsn(ALOAD, func);
      }
      public void param2() {
        mv.visitVarInsn(ALOAD, obj);
      }
      public void param3() {
        cm.vNull();
      }
      public void param4() {
        cm.vNull();
      }
    });
    mv.visitVarInsn(ASTORE, res);
    cm.vGoto(end);

    cm.vLabel(end, line);
    cm.vSetStackVar(a, ()->{
      mv.visitVarInsn(ALOAD, res);
    });
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
