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
public class LuaBuilder implements IConst {

  public final static String ROOT_FUNCTION_NAME = "run";

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


  public LuaBuilder(String _classPath, String _outDir) {
    this.classPath = _classPath;
    this.className = Tool.formatClassName(classPath);
    this.cm = new ClassMaker(className, _outDir);
    this.plist = new ArrayList<>(100);
  }


  public void makeJavacode(Prototype p) {
    cm.defaultInit();
    ClosureInf root = pushClosure(p, ROOT_FUNCTION_NAME, -1);
    newClosureFunction(root);
  }


  private void newClosureFunction(ClosureInf ci) {
    final int startIndex = plist.size();
    mv = cm.beginMethod(ci.funcName);
    State state = new State(ci);

    mv.visitLabel(state.initLabel);
    cm.vClosureFunctionHeader(state);
    mv.visitLabel(state.initOverLabel);

    while (state.hasNext()) {
      state.readNextOp();
      vDebugInf();
      do_op_code(opcode, state);
      state.checkIfNotEnd(opNames[opcode]);
    }

    cm.vClosureFunctionFoot(state);
    cm.endMethod(state);
    processSubClosure(startIndex, plist.size());
  }


  public class State extends StateBase {
    final ClosureInf ci;
    final Label returnLabel;
    final Label initLabel;
    final Label initOverLabel;
    private final int[] opcodes;
    private int npc = 0;

    final LocalVar vCallframe;
    final LocalVar vPlatform;
    final LocalVar vClosure;
    final LocalVar vPrototype;
    final LocalVar vCI;


    public State(ClosureInf ci) {
      super(mv);
      this.ci = ci;
      opcodes = ci.prototype.code;
      this.initLabel = new Label();
      this.initOverLabel = new Label();
      this.returnLabel = initLabels();

      this.vCallframe = internalVar(FR, IConst.vCallframe);
      this.vPlatform = internalVar(Platform.class, IConst.vPlatform);
      this.vClosure = internalVar(CU, IConst.vClosure);
      this.vPrototype = internalVar(PT, IConst.vPrototype);
      this.vCI = internalVar(CI, IConst.vCI);
    }


    private Label initLabels() {
      labels = new Label[opcodes.length + 1];
      for (int i=0; i<labels.length; ++i) {
        labels[i] = new Label();
      }
      return labels[ labels.length -1 ];
    }


    private LocalVar internalVar(Class c, int index) {
      String name = Tool.toLocalVarName(c);
      LocalVar v = new LocalVar(mv, c, index, name, initLabel, returnLabel);
      v.lock();
      return v;
    }


    private int readNextOp() {
      pc = npc++;
      op = opcodes[pc];
      opcode = op & 0x3F;

      line = ci.prototype.lines[pc];
      label = labels[pc];

      mv.visitLabel(label);
      mv.visitLineNumber(line, label);
      return op;
    }


    private boolean hasNext() {
      return npc < opcodes.length;
    }


    public LocalVar newVar(Class c, String name) {
      return super.newVar(c, name, label, labels[npc]);
    }


    public void vAllVariables() {
      super.vAllVariables();
      vCallframe.output();
      vPlatform.output();
      vClosure.output();
      vPrototype.output();
      vCI.output();
    }
  }


  private void processSubClosure(int start, int end) {
    for (int i=start; i<end; ++i) {
      ClosureInf ci = plist.get(i);
      newClosureFunction(ci);
    }
  }


  protected void do_op_code(int opcode, State state) {
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
      case OP_ADD: op_add(state); break;
      case OP_SUB: op_sub(state); break;
      case OP_MUL: op_mul(state); break;
      case OP_DIV: op_div(state); break;
      case OP_MOD: op_mod(state); break;
      case OP_POW: op_pow(state); break;
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
      case OP_CALL: op_call(state); break;
      case OP_TAILCALL: op_tailcall(state); break;
      case OP_RETURN: op_return(state); break;
      case OP_FORLOOP: op_forloop(); break;
      case OP_FORPREP: op_forprep(); break;
      case OP_TFORLOOP: op_tforloop(); break;
      case OP_SETLIST: op_setlist(state); break;
      case OP_CLOSE: op_close(); break;
      case OP_CLOSURE: op_closure(state); break;
      case OP_VARARG: op_vararg(); break;
    }
  }


  public LuaScript createJavaAgent() throws InvocationTargetException,
      NoSuchMethodException, InstantiationException, IllegalAccessException {
    LuaScript ls = cm.newInstance();
    ls.setClosureInf(plist);
    return ls;
  }


  private ClosureInf pushClosure(Prototype sp, String funcName, int stackIndex) {
    int index = plist.size();
    ClosureInf inf = new ClosureInf(sp, index, funcName, stackIndex);
    plist.add(inf);
    return inf;
  }


  private String closureFuncName() {
    return "closure_"+ (id++);
  }


  public void vDebugInf() {
    cm.vPrint(classPath +":"+ line +" "+ KahluaThread2.opName(opcode));

    Tool.pl("LL ",pc, Tool.str4byte(Integer.toHexString(op)),
      opNames[opcode], line);
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

    cm.vSetTableVar(new IBuildParam3() {
      public void param1() {
        cm.vEnvironment();
      }
      public void param2() {
        cm.vGetConstants(b);
      }
      public void param3() {
        cm.vGetStackVar(a);
      }
    });
  }

  void op_setupval() {
    int a = getA8(op);
    int b = getB9(op);

    VUpvalueOp upop = new VUpvalueOp(cm, mv, b, vUser);
    upop.setValue(() -> {
      cm.vGetStackVar(a);
    });
  }

  void op_settable() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

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

  void op_add(State s) {
    math_cal(s, "__add", true, (bd, cd)->{
      mv.visitInsn(DADD);
    });
  }

  void op_sub(State s) {
    math_cal(s, "__sub", true, (bd, cd)->{
      mv.visitInsn(DSUB);
    });
  }

  void op_mul(State s) {
    math_cal(s, "__mul", true, (bd, cd)->{
      mv.visitInsn(DMUL);
    });
  }

  void op_div(State s) {
    math_cal(s, "__div", true, (bd, cd)->{
      mv.visitInsn(DDIV);
    });
  }

  void op_pow(State s) {
    math_cal(s, "__pow", false, (bd, cd)->{
      mv.visitVarInsn(ALOAD, vPlatform);
      bd.load();
      cm.vToPrimitiveDouble(false);
      cd.load();
      cm.vToPrimitiveDouble(false);
      cm.vInvokeFunc(Platform.class, "pow", D, D);
    });
  }

  void op_mod(State s) {
    math_cal(s, "__mod", false, (bd, cd)->{
      Label v2iszero = new Label();
      Label end = new Label();

      cd.load();
      cm.vToPrimitiveDouble(false);
      cm.vDouble(0);
      mv.visitInsn(DCMPL);
      mv.visitJumpInsn(IFEQ, v2iszero); // if v4 == 0 goto v2iszero

      // v4 != 0
      bd.load();
      cm.vToPrimitiveDouble(false);
      {
        bd.load();
        cm.vToPrimitiveDouble(false);
        cd.load();
        cm.vToPrimitiveDouble(false);
        mv.visitInsn(DDIV);
        mv.visitInsn(D2I);
      }
      mv.visitInsn(I2D);
      cd.load();
      cm.vToPrimitiveDouble(false);
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
  void math_cal(State s, String meta_op, boolean popValued, IMathOp primitiveOp) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    s.beginInstruction();

    final LocalVar bo = s.newVar(O, "bo");
    final LocalVar co = s.newVar(O, "co");
    final LocalVar bd = s.newVar(O, "bd");
    final LocalVar cd = s.newVar(O, "cd");
    final LocalVar res = s.newVar(O, "res");
    final int tmp = s.nextVarid(-1);

    Label saveRes = new Label();
    Label useMetaOp1 = new Label();
    Label useMetaOp2 = new Label();
    Label primitive = new Label();

    {
      cm.vGetRegOrConst(b, tmp);
      bo.store();

      cm.vGetRegOrConst(c, tmp);
      co.store();

      cm.vThis();
      bo.load();
      //TODO: optimization
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      bd.store();

      cm.vThis();
      co.load();
      cm.vInvokeFunc(LuaScript.class, "rawTonumber", O);
      cd.store();

      // if (bd == null || cd == null) then useMetaOp();
      bd.load();
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      cd.load();
      mv.visitJumpInsn(IFNULL, useMetaOp2);
      cm.vGoto(primitive);
    }

    // primitiveMath();
    {
      cm.vLabel(primitive, line);

      if (popValued) {
        bd.load();
        cm.vToPrimitiveDouble(false);
        cd.load();
        cm.vToPrimitiveDouble(false);
      }

      primitiveOp.calc(bd, cd);
      // Must TO Double(Object)
      cm.vToObjectDouble(false);
      res.store();
      cm.vGoto(saveRes);
    }

    // useMetaOp()
    {
      cm.vLabel(useMetaOp1, line);
      mv.visitInsn(POP);

      cm.vLabel(useMetaOp2, line);
      cm.vCallMetaOp(meta_op, new IBuildParam2() {
        public void param1() {
          bo.load();
        }
        public void param2() {
          co.load();
        }
      });
      res.store();
      cm.vGoto(saveRes);
    }

    // saveRes()
    cm.vLabel(saveRes, line);
    cm.vSetStackVar(a, ()-> res.load());
    s.endInstruction();
  }

  void op_unm() {
    int a = getA8(op);
    int b = getB9(op);

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

    cm.vSetStackVar(a, ()->{
      cm.vGetStackVar(b);
      cm.vBoolEval(true, false);
    });
  }

  void op_len() {
    int a = getA8(op);
    int b = getB9(op);

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
      public void param1() { mv.visitVarInsn(ALOAD, func); }
      public void param2() { mv.visitVarInsn(ALOAD, obj); }
      public void param3() { cm.vNull(); }
      public void param4() { cm.vNull(); }
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
    op_cmp(new ICompOp() {
      public void intComp() {
        cm.vIf(IFEQ, new IIF() {
          public void doThen() {
            cm.vBoolean(true);
          }
          public void doElse() {
            cm.vBoolean(false);
          }
        });
      }

      public void metaComp(int bo, int co) {
        cm.vThis();
        mv.visitVarInsn(ALOAD, bo);
        mv.visitVarInsn(ALOAD, co);
        cm.vInvokeFunc(LS, "try_comp_eq", O,O);
      }
    });
  }

  void op_lt() {
    op_cmp(new ICompOp() {
      public void intComp() {
        cm.vIf(IFLT, new IIF() {
          public void doThen() {
            cm.vBoolean(true);
          }
          public void doElse() {
            cm.vBoolean(false);
          }
        });
      }

      public void metaComp(int bo, int co) {
        cm.vThis();
        mv.visitVarInsn(ALOAD, bo);
        mv.visitVarInsn(ALOAD, co);
        cm.vInvokeFunc(LS, "try_comp_lt", O,O);
      }
    });
  }

  void op_le() {
    op_cmp(new ICompOp() {
      public void intComp() {
        cm.vIf(IFLE, new IIF() {
          public void doThen() {
            cm.vBoolean(true);
          }
          public void doElse() {
            cm.vBoolean(false);
          }
        });
      }

      public void metaComp(int bo, int co) {
        cm.vThis();
        mv.visitVarInsn(ALOAD, bo);
        mv.visitVarInsn(ALOAD, co);
        cm.vInvokeFunc(LS, "try_comp_le", O,O);
      }
    });
  }

  void op_cmp(ICompOp cp) {
    final int a = getA8(op);
    final int b = getB9(op);
    final int c = getC9(op);

    final Label jump = labels[pc + 1];
    final Label njump = new Label();
    final Label isnum = new Label();
    final Label isstr = new Label();
    final Label rcomp = new Label();

    final int tmp = 0;
    final int bo = vUser +1;
    final int co = vUser +2;
    final int azero = vUser +3;

    cm.vBoolean(a == 0);
    mv.visitVarInsn(ISTORE, azero);

    cm.vGetRegOrConst(b, tmp);
    mv.visitVarInsn(ASTORE, bo);
    cm.vGetRegOrConst(c, tmp);
    mv.visitVarInsn(ASTORE, co);

    // ------- check if double

    mv.visitVarInsn(ALOAD, bo);
    cm.vIf(Double.class, new IIFwe() {
      public void doThen() {
        mv.visitVarInsn(ALOAD, co);
        cm.vIf(Double.class, new IIFwe() {
          public void doThen() {
            cm.vGoto(isnum);
          }
        });
      }
    });

    // ------- check if string

    mv.visitVarInsn(ALOAD, bo);
    cm.vIf(String.class, new IIFwe() {
      public void doThen() {
        mv.visitVarInsn(ALOAD, co);
        cm.vIf(String.class, new IIFwe() {
          public void doThen() {
            cm.vGoto(isstr);
          }
        });
      }
    });

    // ------- else

    cp.metaComp(bo, co);
    cm.vGoto(rcomp);

    // ------- string

    cm.vLabel(isstr, line);
    mv.visitVarInsn(ALOAD, bo);
    cm.vCast(String.class);
    mv.visitVarInsn(ALOAD, co);
    cm.vCast(String.class);
    cm.vInvokeFunc(String.class, "compareTo", S);

    cp.intComp();
    cm.vGoto(rcomp);

    // ------- number

    cm.vLabel(isnum, line);
    mv.visitVarInsn(ALOAD, bo);
    cm.vCast(Double.class);
    cm.vInvokeFunc(Double.class, "doubleValue");
    mv.visitVarInsn(ALOAD, co);
    cm.vCast(Double.class);
    cm.vInvokeFunc(Double.class, "doubleValue");
    mv.visitInsn(DCMPG);

    cp.intComp();
    cm.vGoto(rcomp);

    // ------- rcomp

    cm.vLabel(rcomp, line);
    mv.visitVarInsn(ILOAD, azero);
    cm.vIf(IF_ICMPEQ, new IIF() {
      public void doThen() {
        cm.vGoto(jump);
      }
      public void doElse() {
        cm.vGoto(njump);
      }
    });
    cm.vGoto(njump);

    // ------- no jump

    cm.vLabel(njump, line);
  }

  void op_test() {
    final int a = getA8(op);
    final int c = getC9(op);
    final boolean eqt = (c == 0);

    Label jumpto = this.labels[pc + 1];

    cm.vGetStackVar(a);
    cm.vBoolEval(false, true);
    cm.vBoolean(eqt);

    cm.vIf(IF_ICMPEQ, new IIFwe() {
      public void doThen() {
        cm.vGoto(jumpto);
      }
    });
  }

  void op_testset() {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);
    boolean eqt = (c == 0);

    Label jumpto = this.labels[pc + 1];
    final int value = vUser +1;

    cm.vGetStackVar(b);
    cm.vCopyRef();
    mv.visitVarInsn(ASTORE, value);
    cm.vBoolEval(false, true);
    cm.vBoolean(eqt);

    cm.vIf(IF_ICMPEQ, new IIF() {
      public void doThen() {
        cm.vGoto(jumpto);
      }

      public void doElse() {
        cm.vSetStackVar(a, () -> mv.visitVarInsn(ALOAD, value));
      }
    });
  }

  void op_forprep() {
    int a = getA8(op);
    int b = getSBx(op);

    final int iter = vUser +1;
    final int step = vUser +2;

    cm.vGetStackVar(a);
    cm.vToPrimitiveDouble(true);
    mv.visitVarInsn(DSTORE, iter);

    cm.vGetStackVar(a + 2);
    cm.vToPrimitiveDouble(true);
    mv.visitVarInsn(DSTORE, step);

    cm.vSetStackVar(a, () -> {
      mv.visitVarInsn(DLOAD, iter);
      mv.visitVarInsn(DLOAD, step);
      mv.visitInsn(DSUB);
      cm.vToObjectDouble(false);
    });

    cm.vGoto(labels[pc + b]);
  }

  void op_forloop() {
    int a = getA8(op);
    int b = getSBx(op);

    final Label jumpTo = labels[pc + b];
    final int iter = vUser +1;
    final int step = vUser +2;
    final int end  = vUser +3;
    final int iterDouble = vUser +4;

    cm.vGetStackVar(a);
    cm.vToPrimitiveDouble(true);
    mv.visitVarInsn(DSTORE, iter);

    cm.vGetStackVar(a + 1);
    cm.vToPrimitiveDouble(true);
    mv.visitVarInsn(DSTORE, end);

    cm.vGetStackVar(a + 2);
    cm.vToPrimitiveDouble(true);
    mv.visitVarInsn(DSTORE, step);

    mv.visitVarInsn(DLOAD, iter);
    mv.visitVarInsn(DLOAD, step);
    mv.visitInsn(DADD);
    mv.visitVarInsn(DSTORE, iter);

    cm.vSetStackVar(a, () -> {
      mv.visitVarInsn(DLOAD, iter);
      cm.vToObjectDouble(false);
      cm.vCopyRef();
      mv.visitVarInsn(DSTORE, iterDouble);
    });

    final IIF checkloop = new IIF() {
      public void doThen() {
        cm.vSetStackVar(a+3, ()-> mv.visitVarInsn(DLOAD, iterDouble));
        cm.vGoto(jumpTo);
      }
      public void doElse() {
        cm.vClearStack(a);
      }
    };

    mv.visitVarInsn(DLOAD, iter);
    mv.visitVarInsn(DLOAD, end);
    mv.visitInsn(DCMPG);

    mv.visitVarInsn(DLOAD, step);
    cm.vIf(IFGT, new IIF() {
      public void doThen() {
        cm.vIf(IFLE, checkloop);
      }
      public void doElse() {
        cm.vIf(IFGE, checkloop);
      }
    });
  }


  /**
   * OP_TFORLOOP,
   * A C R(A+3), ... ,R(A+2+C) := R(A)(R(A+1), R(A+2));
   * if R(A+3) ~= nil
   *  then R(A+2)=R(A+3)
   *  else pc++
   * ===================================================
   * func = callFrame.get(a)
   * s = callFrame.get(a+1)
   * i = callFrame.get(a+2)
   * for (; i<=c; ++i) {
   *    callFrame.set(a + 2, i) ??
   *    ret = func(s, i);
   *    callFrame.set(a + 2 + i, ret)
   * }
   * x = callFrame.get(a+3)
   * if (x != null) callFrame.set(a+2, x)
   * else jump pc+1
   */
  void op_tforloop() {
    final int a = getA8(op);
    final int c = getC9(op);

    final int func = vUser +1;
    final int s = vUser +2;
    final int i = vUser +3;
    final int a3 = vUser +4;
    final int ret = vUser +5;
    final int di = vUser +6;

    cm.vGetStackVar(a);
    mv.visitVarInsn(ASTORE, func);
    cm.vGetStackVar(a + 1);
    mv.visitVarInsn(ASTORE, s);
    cm.vGetStackVar(a + 2);
    cm.vToPrimitiveDouble(true);
    mv.visitInsn(D2I);
    mv.visitVarInsn(ISTORE, i);

    mv.visitVarInsn(ILOAD, i);
    cm.vInt(c);
    cm.vIf(IF_ICMPLE, new IIFwe() { //TODO: ??? goto loop
      public void doThen() {
        mv.visitVarInsn(ILOAD, i);
        mv.visitInsn(I2D);
        cm.vToObjectDouble(false);
        mv.visitVarInsn(ASTORE, di);

        cm.vSetStackVar(a + 2, ()->{
          mv.visitVarInsn(ALOAD, di);
        });

        cm.vCall(new IBuildParam4() {
          public void param1() {
            mv.visitVarInsn(ALOAD, func);
          }
          public void param2() {
            mv.visitVarInsn(ALOAD, s);
          }
          public void param3() {
            mv.visitVarInsn(ALOAD, di);
          }
          public void param4() {
            cm.vNull();
          }
        });
        mv.visitVarInsn(ASTORE, ret);

        cm.vSetStackVar(new IBuildParam2() {
          public void param1() {
            mv.visitVarInsn(ILOAD, i);
            cm.vInt(a + 2);
            mv.visitInsn(IADD);
          }
          public void param2() {
            mv.visitVarInsn(ALOAD, ret);
          }
        });

        mv.visitVarInsn(ILOAD, i);
        cm.vInt(1);
        mv.visitInsn(IADD);
        mv.visitVarInsn(ISTORE, i);
      }
    });

    cm.vGetStackVar(a + 3);
    cm.vCopyRef();
    mv.visitVarInsn(ASTORE, a3);

    cm.vIf(IFNONNULL, new IIF() {
      public void doThen() {
        cm.vSetStackVar(a + 2, ()-> mv.visitVarInsn(ALOAD, a3));
      }
      public void doElse() {
        cm.vGoto(labels[pc + 1]);
      }
    });
  }


  /**
   * SETLIST A B C   R(A)[(C-1)*FPF+i] := R(A+i), 1 <= i <= B
   * A: function
   * B: elementsCount
   * C:
   * @param state
   */
  void op_setlist(State state) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    state.beginInstruction();
    final LocalVar count = state.newVar(I, "count");
    final LocalVar table = state.newVar(O, "table");
    final LocalVar offset = state.newVar(I, "offset");
    final LocalVar i = state.newVar(I, "i");

    final Label check = new Label();
    final Label forend = new Label();

    if (b != 0) {
      cm.vInt(b);
      count.store();
    } else {
      cm.vGetTop();
      cm.vInt(a);
      mv.visitInsn(ISUB);
      cm.vInt(1);
      mv.visitInsn(ISUB);
      count.store();
    }

    if (c == 0) {
      c = state.readNextOp();
    }
    cm.vInt(c - 1 * KahluaThread2.FIELDS_PER_FLUSH);
    offset.store();

    cm.vGetStackVar(a);
    cm.vCast(KahluaTable.class);
    table.store();

    // init for()
    cm.vInt(1);
    i.store();
    cm.vGoto(check);

    // check for()
    mv.visitLabel(check);
    i.load();
    count.load();
    cm.vIf(IF_ICMPLE, new IIF() {
      public void doThen() {
        table.load();
        // param1
        offset.load();
        i.load();
        mv.visitInsn(IADD);
        mv.visitInsn(I2D);
        cm.vToObjectDouble(false);
        // param2
        cm.vGetStackVar(()->{
          cm.vInt(a);
          i.load();
          mv.visitInsn(IADD);
        });
        cm.vInvokeFunc(KahluaTable.class, "rawset", O,O);
      }
      public void doElse() {
        cm.vGoto(forend);
      }
    });
    i.load();
    cm.vInt(1);
    mv.visitInsn(IADD);
    i.store();
    cm.vGoto(check);

    // end for()
    mv.visitLabel(forend);
    state.endInstruction();
  }


  void op_close() {
    int a = getA8(op);

    cm.vCloseLocalUpvlues(()-> cm.vInt(a));
  }


  void op_vararg() {
    int a = getA8(op);
    int b = getB9(op) - 1;

    cm.vPushVarargs(new IBuildParam2() {
      public void param1() {
        cm.vInt(a);
      }
      @Override
      public void param2() {
        cm.vInt(b);
      }
    });
  }


  /**
   * CALL A B C    R(A), ... ,R(A+C-2) := R(A)(R(A+1), ... ,R(A+B-1))
   */
  void op_call(State state) {
    int a = getA8(op);
    int b = getB9(op);
    int c = getC9(op);

    state.beginInstruction();
    LocalVar func = state.newVar(O, "function");
    LocalVar localBase2 = state.newVar(I, "localBase2");
    LocalVar nArguments2 = state.newVar(I, "nArguments2");
    LocalVar returnBase2 = state.newVar(I, "returnBase2");
    LocalVar funcMeta = state.newVar(O, "funcMeta");
    LocalVar errMsg = state.newVar(String.class, "errMsg");

    Label javafunc = new Label();
    Label closure = new Label();
    Label end = new Label();
    Label meta = new Label();
    Label checkType = new Label();
    Label oldClosure = new Label();

    cm.vGetStackVar(a);
    func.store();

    state.vCallframe.load();
    cm.vBoolean(c != 0);
    cm.vPutField(FR, "restoreTop");

    if (b != 0) {
      cm.vSetFrameTop(()-> cm.vInt(a+b));
      cm.vInt(b - 1);
      nArguments2.store();
    } else {
      cm.vGetTop();
      cm.vInt(a+1);
      mv.visitInsn(ISUB);
      nArguments2.store();
    }

    state.vCallframe.load();
    cm.vField(FR, "localBase");
    cm.vCopyRef();

    cm.vInt(a + 1);
    mv.visitInsn(IADD);
    localBase2.store(); // localBase2 = base + a + 1;

    cm.vInt(a);
    mv.visitInsn(IADD);
    returnBase2.store(); // returnBase2 = base + a;

    mv.visitLabel(checkType);
    {
      func.load();
      cm.vIf(JavaFunction.class, javafunc);
      func.load();
      cm.vIf(ClosureInf.class, closure);
      func.load();
      cm.vIf(LuaClosure.class, oldClosure);
      // else
      cm.vGoto(meta); //TODO: Possibly an infinite loop
    }

    mv.visitLabel(meta);
    {
      cm.vGetMetaOp("__call", ()-> func.load());
      cm.vCopyRef();
      funcMeta.store();

      cm.vIf(IFNULL, ()-> {
        cm.vGetStackVar(a);
        func.store();
        cm.vConcatString("Object ", func, " did not have __call metatable set");
        errMsg.store();

        cm.vThrow(()-> errMsg.load());
      });

      returnBase2.load();
      localBase2.store();
      cm.vInt(1);
      nArguments2.load();
      mv.visitInsn(IADD);
      nArguments2.store();

      funcMeta.load();
      func.store();
      cm.vGoto(checkType);
    }
    cm.vGoto(end);

    mv.visitLabel(javafunc);
    {
      state.vCI.load();
      cm.vNewFrame(localBase2, returnBase2, nArguments2, false);

      state.vCI.load();
      func.load();
      cm.vCast(JavaFunction.class);
      cm.vInvokeFunc(CI, "call", JavaFunction.class);
      cm.vPopFrame();
    }
    cm.vGoto(end);

    mv.visitLabel(closure);
    {
      func.load();
      cm.vCast(ClosureInf.class);
      //cm.vCopyRef();
      cm.vNewFrame(localBase2, returnBase2, nArguments2, true);

      func.load();
      cm.vCast(ClosureInf.class);
      cm.vThis();
      cm.vInvokeFunc(CI, "call", LuaScript.class);
    }
    cm.vGoto(end);

    mv.visitLabel(oldClosure);
    {
      state.vCI.load();
      func.load();
      cm.vCast(LuaClosure.class);
      cm.vInvokeFunc(CI, "call", LuaClosure.class);
    }
    cm.vGoto(end);

    mv.visitLabel(end);
    state.endInstruction();
  }


  void op_tailcall(State state) {
    int a = getA8(op);
    int b = getB9(op);

    state.beginInstruction();
    state.endInstruction();
  }


  void op_return(State state) {
    int a = getA8(op);
    int b = getB9(op);

    state.beginInstruction();
    LocalVar vb = state.newVar(I, "b");

    cm.vCloseCoroutineUpvalues(()-> cm.vGetBase());

    if (b == 0) {
      cm.vGetTop();
      cm.vInt(a);
      mv.visitInsn(ISUB);
      vb.store();
    } else {
      cm.vInt(b - 1);
      vb.store();
    }

    cm.vStackCopy(new IBuildParam3() {
      public void param1() {
        cm.vGetBase();
        cm.vInt(a);
        mv.visitInsn(IADD);
      }
      public void param2() {
        cm.vReturnBase();
      }
      public void param3() {
        vb.load();
      }
    });

    cm.vSetCoroutineTop(()-> {
      cm.vReturnBase();
      cm.vInt(b);
      mv.visitInsn(IADD);
    });

    cm.vGoto(state.returnLabel);
    state.endInstruction();
  }


  void op_closure(State state) {
    int a = getA8(op);
    int b = getBx(op);

    Prototype p = state.ci.prototype.prototypes[b];
    ClosureInf newci = pushClosure(p, closureFuncName(), a);

    state.beginInstruction();
    LocalVar ci = state.newVar(ClosureInf.class, "ci");

    cm.vField("plist");
    cm.vInt(newci.arrIndex);
    mv.visitInsn(AALOAD);
    cm.vCopyRef();
    ci.store();

    cm.vThis();
    cm.vInvokeFunc(ClosureInf.class, "installMethod", LuaScript.class);
    cm.vSetStackVar(a, ()-> ci.load());

    for (int i=0; i<p.numUpvalues; ++i) {
      state.readNextOp();
      final int nb = getB9(op);

      // newci.upvalues[i] =
      ci.load();
      cm.vField(ClosureInf.class, "upvalues");
      cm.vInt(i);

      switch (opcode) {
      case OP_MOVE:
        cm.vFindUpvalue(()-> cm.vInt(nb));
        break;

      case OP_GETUPVAL:
        cm.vGetUpvalueFromClosure(()-> cm.vInt(nb));
        break;

      default:
        cm.vNull();
        throw new RuntimeException("bad operate code in op_closure");
      }
      mv.visitInsn(AASTORE);
    }

    state.endInstruction();
  }
}
